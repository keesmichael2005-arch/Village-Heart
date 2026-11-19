package dev.villageheart.core.site;

import dev.villageheart.core.room.Pos;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/**
 * Which camp a work flag belongs to. A living link stays until the flag is picked up. A waiting flag takes the nearest
 * free camp; two flags the same distance from that camp go to the one registered first. A flag with no free camp waits.
 */
public final class FlagLinks {
	private FlagLinks() {}

	/** @param link the camp it is already linked to, or null when it is waiting; {@code order} is registration order (lower first) */
	public record Flag(Pos at, @Nullable Pos link, int order) {}

	public record Camp(Pos at) {}

	/**
	 * Flag position to the camp it links to. Flags with no camp are absent. A link whose camp is gone is not living, so
	 * that flag waits again.
	 */
	public static Map<Pos, Pos> assign(List<Flag> flags, List<Camp> camps) {
		Map<Pos, Pos> out = new HashMap<>();
		Set<Pos> campAt = new HashSet<>();
		for (Camp c : camps) campAt.add(c.at());
		Set<Pos> taken = new HashSet<>();
		for (Flag f : flags) {
			if (f.link() != null && campAt.contains(f.link())) {
				out.put(f.at(), f.link());
				taken.add(f.link());
			}
		}
		List<Flag> waiting = flags.stream().filter(f -> !out.containsKey(f.at())).toList();
		Set<Pos> free = new HashSet<>(campAt);
		free.removeAll(taken);
		boolean progressed = true;
		while (progressed && !free.isEmpty() && !waiting.isEmpty()) {
			progressed = false;
			Flag bestFlag = null;
			Pos bestCamp = null;
			long bestD = Long.MAX_VALUE;
			for (Flag f : waiting) {
				if (out.containsKey(f.at())) continue;
				for (Pos c : free) {
					long d = dist2(f.at(), c);
					if (better(f, c, d, bestFlag, bestCamp, bestD)) {
						bestFlag = f;
						bestCamp = c;
						bestD = d;
					}
				}
			}
			if (bestFlag != null) {
				out.put(bestFlag.at(), bestCamp);
				free.remove(bestCamp);
				progressed = true;
			}
		}
		return out;
	}

	/** Nearer pair wins. Same distance: the flag registered first, then the smaller camp position. */
	private static boolean better(Flag flag, Pos camp, long d, @Nullable Flag bestFlag, @Nullable Pos bestCamp, long bestD) {
		if (bestFlag == null) return true;
		if (d != bestD) return d < bestD;
		if (flag.order() != bestFlag.order()) return flag.order() < bestFlag.order();
		return camp.compareTo(bestCamp) < 0;
	}

	private static long dist2(Pos a, Pos b) {
		long dx = (long) a.x() - b.x(), dy = (long) a.y() - b.y(), dz = (long) a.z() - b.z();
		return dx * dx + dy * dy + dz * dz;
	}
}
