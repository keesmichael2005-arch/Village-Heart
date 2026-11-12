package dev.villageheart.core.raid;

import java.util.Objects;
import java.util.Optional;
import java.util.function.IntSupplier;

/**
 * When a raid becomes the village's raid, and when it ends (M25). Pure. The glue passes facts for the
 * tracked id once one is latched, so a nearer raid cannot take its place.
 */
public final class RaidTracker {
	private RaidTracker() {}

	/**
	 * What was true at the moment a raid started. The short {@link #step} overload uses {@link Start#NONE}
	 * (a test raid, no guard census). The glue passes the real census, which is read only on that transition.
	 */
	public record Start(RaidOrigin origin, RaidPlan plan, int guardsAtStart, int guardsQualifiedAtStart, int guardsWoundedAtStart,
			int guardsUnloadedAtStart) {
		public static final Start NONE = new Start(RaidOrigin.TEST, RaidPlan.VANILLA, 0, 0, 0, 0);

		public Start {
			Objects.requireNonNull(origin, "origin");
			Objects.requireNonNull(plan, "plan");
			if (guardsAtStart < 0 || guardsQualifiedAtStart < 0 || guardsWoundedAtStart < 0 || guardsUnloadedAtStart < 0) {
				throw new IllegalArgumentException("guard counts must be >= 0");
			}
		}
	}

	/** {@code started} is true only on the transition that creates a record. {@code ended} is set when that record closes. */
	public record Step(Optional<RaidRecord> next, Optional<RaidSummary> ended, boolean started) {
		public Step {
			Objects.requireNonNull(next, "next");
			Objects.requireNonNull(ended, "ended");
		}
	}

	public static Step step(Optional<RaidRecord> current, RaidFacts facts, long now, int day, IntSupplier villagersNow) {
		return step(current, facts, now, day, villagersNow, Start.NONE);
	}

	/**
	 * No record and relevant facts start one (the supplier is read then). The same id updates.
	 * Victory, then loss, then a stop or an over raid, closes it; a later stop does not replace a victory or a loss
	 * because the record is already gone. A missing id is {@link RaidOutcome#GONE}. A different id is ignored.
	 * The supplier is read again only when a raid ends, for {@code villagersAtEnd}, never on an update.
	 */
	public static Step step(Optional<RaidRecord> current, RaidFacts facts, long now, int day, IntSupplier villagersNow, Start start) {
		Objects.requireNonNull(current, "current");
		Objects.requireNonNull(facts, "facts");
		Objects.requireNonNull(villagersNow, "villagersNow");
		Objects.requireNonNull(start, "start");
		if (current.isEmpty()) {
			if (!facts.relevant()) return new Step(Optional.empty(), Optional.empty(), false);
			RaidRecord created = RaidRecord.start(facts, start, now, day, villagersNow.getAsInt());
			return new Step(Optional.of(created), Optional.empty(), true);
		}
		RaidRecord cur = current.get();
		if (!facts.present()) return close(cur, RaidOutcome.GONE, now, villagersNow);
		if (facts.vanillaId() != cur.vanillaId()) return new Step(current, Optional.empty(), false);
		if (facts.victory()) return close(cur.updated(facts), RaidOutcome.VICTORY, now, villagersNow);
		if (facts.loss()) return close(cur.updated(facts), RaidOutcome.LOSS, now, villagersNow);
		if (facts.stopped() || facts.over()) return close(cur.updated(facts), RaidOutcome.STOPPED, now, villagersNow);
		return new Step(Optional.of(cur.updated(facts)), Optional.empty(), false);
	}

	/**
	 * The claim was released while a raid was tracked. The record goes away with the village and no summary is written.
	 */
	public static Step release(Optional<RaidRecord> current) {
		Objects.requireNonNull(current, "current");
		return new Step(Optional.empty(), Optional.empty(), false);
	}

	/** A death counts only while a raid is tracked. The glue calls this for an in-claim death. */
	public static Optional<RaidRecord> onVillagerDeath(Optional<RaidRecord> current) {
		Objects.requireNonNull(current, "current");
		return current.map(r -> r.withVillagerDeaths(r.villagerDeaths() + 1));
	}

	/** A raider kill counts only while a raid is tracked. {@code extra} is one of the mod's added raiders. */
	public static Optional<RaidRecord> onRaiderKilled(Optional<RaidRecord> current, boolean extra) {
		Objects.requireNonNull(current, "current");
		return current.map(r -> r.withKills(r.raidersKilled() + 1, r.extraRaidersKilled() + (extra ? 1 : 0)));
	}

	/** Keeps the first time. A later call does not move it. */
	public static RaidRecord onAllGuardsOnDuty(RaidRecord record, long now) {
		Objects.requireNonNull(record, "record");
		if (record.allGuardsOnDutyAt() >= 0) return record;
		return record.withAllGuardsOnDutyAt(now);
	}

	private static Step close(RaidRecord cur, RaidOutcome outcome, long now, IntSupplier villagersNow) {
		return new Step(Optional.empty(), Optional.of(cur.finish(outcome, now, villagersNow.getAsInt())), false);
	}
}
