package dev.villageheart.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Makes ranks and the name book of a loaded (old, damaged or hand-edited) save satisfy the {@link VillageState}
 * invariants, so one bad entry never loses the village. Deterministic: the same input gives the same output.
 */
public final class MemberRepair {
	private MemberRepair() {}

	/** The repaired pair and the UUIDs that were dropped (from the name book or demoted), for one WARN line. */
	public record Result(Map<UUID, Rank> ranks, List<KnownPlayer> known, List<UUID> dropped) {}

	public static Result repair(UUID owner, Map<UUID, Rank> ranksIn, List<KnownPlayer> knownIn) {
		List<UUID> dropped = new ArrayList<>();
		Map<UUID, KnownPlayer> known = new LinkedHashMap<>();
		for (KnownPlayer k : knownIn) {
			if (k.id().equals(owner) || known.containsKey(k.id())) {
				if (!k.id().equals(owner)) dropped.add(k.id());
				continue;
			}
			known.put(k.id(), k);
		}
		Map<UUID, Rank> ranks = new HashMap<>(ranksIn);
		// A ranked player always has a name-book entry (a placeholder until they join again).
		for (UUID ranked : ranks.keySet()) {
			if (!ranked.equals(owner)) known.putIfAbsent(ranked, new KnownPlayer(ranked, "", 0));
		}
		int maxRanked = Balance.MAX_KNOWN_PLAYERS - 1;
		if (ranks.size() > maxRanked) {
			List<Map.Entry<UUID, Rank>> order = new ArrayList<>(ranks.entrySet());
			order.sort(Comparator.<Map.Entry<UUID, Rank>>comparingInt(e -> e.getValue().ordinal())
					.thenComparing(e -> -known.get(e.getKey()).lastSeen())
					.thenComparing(Map.Entry::getKey));
			for (int i = maxRanked; i < order.size(); i++) {
				ranks.remove(order.get(i).getKey());
				dropped.add(order.get(i).getKey());
			}
		}
		if (known.size() > Balance.MAX_KNOWN_PLAYERS) {
			List<KnownPlayer> unranked = new ArrayList<>();
			for (KnownPlayer k : known.values()) if (!ranks.containsKey(k.id())) unranked.add(k);
			// Newest first; the oldest unranked entries go.
			unranked.sort(Comparator.comparingLong(KnownPlayer::lastSeen).reversed().thenComparing(KnownPlayer::id));
			int excess = known.size() - Balance.MAX_KNOWN_PLAYERS;
			Set<UUID> gone = new HashSet<>();
			for (int i = unranked.size() - 1; i >= 0 && gone.size() < excess; i--) gone.add(unranked.get(i).id());
			for (UUID id : gone) {
				known.remove(id);
				dropped.add(id);
			}
		}
		return new Result(ranks, new ArrayList<>(known.values()), dropped);
	}
}
