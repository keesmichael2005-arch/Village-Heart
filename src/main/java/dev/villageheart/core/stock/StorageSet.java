package dev.villageheart.core.stock;

import dev.villageheart.core.room.Pos;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Which positions of which Storage buildings are stockpile containers (positions only, pure). */
public final class StorageSet {
	private StorageSet() {}

	/** One valid Storage scan: its marker, the door-side anchor, and every {@code #chest} / barrel position of the scan. */
	public record SiteCandidate(Pos marker, Pos anchor, List<Pos> containers) {}

	public record Resolved(List<SiteCandidate> sites, List<Pos> unknownMarkers) {}

	/**
	 * Sorts sites by marker; a Storage with an unscanned room building within {@code neighbourRange} of its marker is
	 * unknown (a House chest in a shared wall must not leak in right after a restart); drops positions that belong to other
	 * buildings; de-duplicates positions within and across sites (the first site keeps it); sorts each container list. A
	 * site may end with no containers.
	 */
	public static Resolved resolve(List<SiteCandidate> storages, Set<Pos> claimedByOtherBuildings,
			List<Pos> otherMarkersWithoutScan, int neighbourRange) {
		List<SiteCandidate> ordered = new ArrayList<>(storages);
		ordered.sort(Comparator.comparing(SiteCandidate::marker));
		long r2 = (long) neighbourRange * neighbourRange;
		Set<Pos> taken = new HashSet<>();
		List<SiteCandidate> sites = new ArrayList<>();
		List<Pos> unknown = new ArrayList<>();
		for (SiteCandidate c : ordered) {
			boolean near = false;
			for (Pos o : otherMarkersWithoutScan) {
				if (dist2(c.marker(), o) <= r2) {
					near = true;
					break;
				}
			}
			if (near) {
				unknown.add(c.marker());
				continue;
			}
			TreeSet<Pos> mine = new TreeSet<>();
			for (Pos p : c.containers()) {
				if (!claimedByOtherBuildings.contains(p) && taken.add(p)) mine.add(p);
			}
			sites.add(new SiteCandidate(c.marker(), c.anchor(), List.copyOf(mine)));
		}
		return new Resolved(sites, unknown);
	}

	static long dist2(Pos a, Pos b) {
		long dx = a.x() - b.x(), dy = a.y() - b.y(), dz = a.z() - b.z();
		return dx * dx + dy * dy + dz * dz;
	}
}
