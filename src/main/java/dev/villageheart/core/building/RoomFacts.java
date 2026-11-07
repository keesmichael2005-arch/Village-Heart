package dev.villageheart.core.building;

import dev.villageheart.core.room.RoomScan;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * What the building rules need to know about a scanned room; nothing else of the scanner leaks into them.
 *
 * @param enclosed   the scan's fill finished without leaks
 * @param structural one {@link Reason.Code#ROOM} reason per scan problem (empty for a good room)
 * @param floorArea  floor cells of the room
 * @param counts     content key to number of cells
 */
public record RoomFacts(boolean enclosed, List<Reason> structural, int floorArea, Map<String, Integer> counts) {
	public RoomFacts {
		structural = List.copyOf(structural);
		counts = Map.copyOf(counts);
		if (!enclosed && structural.isEmpty()) throw new IllegalArgumentException("a room that is not enclosed needs a reason");
	}

	public static RoomFacts of(RoomScan scan) {
		Map<String, Integer> counts = new TreeMap<>();
		scan.contents().forEach((key, positions) -> counts.put(key, positions.size()));
		return new RoomFacts(scan.enclosed(), scan.problems().stream().map(Reason::room).toList(), scan.floorArea(), counts);
	}

	public int count(String key) { return counts.getOrDefault(key, 0); }
}
