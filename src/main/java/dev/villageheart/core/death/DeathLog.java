package dev.villageheart.core.death;

import dev.villageheart.core.Balance;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The village's death log (M25). At most {@link Balance#DEATH_LOG_MAX} rows are kept (oldest dropped).
 * {@code nextSeq} and {@code inClaimLogged} are not capped: a metric can count deaths the list no longer holds.
 * Rows are stored oldest first.
 */
public record DeathLog(List<DeathEntry> entries, long nextSeq, long inClaimLogged) {
	public static final DeathLog EMPTY = new DeathLog(List.of(), 0, 0);

	public DeathLog {
		Objects.requireNonNull(entries, "entries");
		List<DeathEntry> sorted = new ArrayList<>(entries);
		sorted.sort(Comparator.comparingLong(DeathEntry::seq));
		if (sorted.size() > Balance.DEATH_LOG_MAX) {
			sorted = new ArrayList<>(sorted.subList(sorted.size() - Balance.DEATH_LOG_MAX, sorted.size()));
		}
		entries = List.copyOf(sorted);
		if (nextSeq < 0) nextSeq = 0;
		if (inClaimLogged < 0) inClaimLogged = 0;
	}

	/** Appends a row. The draft's {@code seq} is ignored; the log assigns {@code nextSeq}. */
	public DeathLog append(DeathEntry draft) {
		Objects.requireNonNull(draft, "draft");
		DeathEntry row = new DeathEntry(nextSeq, draft.villager(), draft.name(), draft.jobId(), draft.cause(), draft.byMob(),
				draft.gameTime(), draft.day(), draft.inClaim(), draft.duringRaid(), draft.duringSiege());
		List<DeathEntry> next = new ArrayList<>(entries);
		next.add(row);
		return new DeathLog(next, nextSeq + 1, inClaimLogged + (row.inClaim() ? 1 : 0));
	}

	/**
	 * A zombie conversion. Merges into a death of the same villager within one tick (one {@code zombified} row);
	 * otherwise appends a row with an empty name, the day taken from {@code gameTime}, and {@code inClaim} true.
	 * The glue uses {@link #markConverted(UUID, long, String, String, String, int, boolean, boolean, boolean)} when it
	 * knows the villager.
	 */
	public DeathLog markConverted(UUID villager, long gameTime) {
		return markConverted(villager, gameTime, "zombified", "", "", dayOf(gameTime), true, false, false);
	}

	/**
	 * A conversion ({@code cause} from {@link DeathCauses#conversion}) of {@code villager} at {@code gameTime}.
	 * One row when a death of that villager falls inside {@link DeathCauses#sameLoss}; otherwise a new row.
	 * A merge does not move {@code nextSeq} or {@code inClaimLogged}.
	 */
	public DeathLog markConverted(UUID villager, long gameTime, String cause, String name, String jobId, int day,
			boolean inClaim, boolean duringRaid, boolean duringSiege) {
		Objects.requireNonNull(villager, "villager");
		for (int i = entries.size() - 1; i >= 0; i--) {
			DeathEntry e = entries.get(i);
			if (e.villager().equals(villager) && DeathCauses.sameLoss(e.gameTime(), gameTime)) {
				List<DeathEntry> next = new ArrayList<>(entries);
				next.set(i, e.asConversion(cause));
				return new DeathLog(next, nextSeq, inClaimLogged);
			}
		}
		return append(new DeathEntry(0, villager, name, jobId, cause, DeathCauses.conversionCountsAsMob(cause),
				gameTime, day, inClaim, duringRaid, duringSiege));
	}

	/** Rows with {@code seq} strictly above {@code seq}, oldest first. */
	public List<DeathEntry> since(long seq) {
		List<DeathEntry> out = new ArrayList<>();
		for (DeathEntry e : entries) if (e.seq() > seq) out.add(e);
		return List.copyOf(out);
	}

	/** The newest {@code n} rows, newest first. */
	public List<DeathEntry> recent(int n) {
		if (n <= 0 || entries.isEmpty()) return List.of();
		int from = Math.max(0, entries.size() - n);
		List<DeathEntry> out = new ArrayList<>(entries.subList(from, entries.size()));
		java.util.Collections.reverse(out);
		return List.copyOf(out);
	}

	/** {@code gameTime / 24000 + 1}, the day index the log stores when it has no clock of its own. */
	public static int dayOf(long gameTime) {
		return (int) (gameTime / Balance.DAY_TICKS) + 1;
	}
}
