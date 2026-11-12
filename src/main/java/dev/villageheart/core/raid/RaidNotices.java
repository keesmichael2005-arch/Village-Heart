package dev.villageheart.core.raid;

import dev.villageheart.core.Rank;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Who hears a raid line, and the words of that line. The owner is not in {@code VillageState.ranks}, so the caller
 * puts them in. The glue sends the line; tests replace the sink.
 */
public final class RaidNotices {
	public static final String START = "A raid is coming to %s! Guards are called to duty.";
	public static final String END = "The raid on %s is over: %s. %s villagers lost.";
	public static final String GATHERING = "Raiders are gathering near %s!";
	public static final String PAID = "Victory! The raid on %s is beaten: +%s emeralds to the treasury";

	private RaidNotices() {}

	public static String start(String village) { return START.formatted(village); }

	public static String gathering(String village) { return GATHERING.formatted(village); }

	public static String paid(String village, int emeralds) { return PAID.formatted(village, emeralds); }

	public static String end(String village, RaidOutcome outcome, int villagersLost) {
		String how = switch (outcome) {
			case VICTORY -> "won";
			case LOSS -> "lost";
			case STOPPED, GONE -> "ended";
		};
		return END.formatted(village, how, Math.max(0, villagersLost));
	}

	public static Set<UUID> audience(Map<UUID, Rank> ranks, Set<UUID> online) {
		Set<UUID> out = new HashSet<>();
		for (Map.Entry<UUID, Rank> e : ranks.entrySet()) {
			if (online.contains(e.getKey()) && e.getValue().atLeast(Rank.RESIDENT)) out.add(e.getKey());
		}
		return Set.copyOf(out);
	}
}
