package dev.villageheart.core.room;

import dev.villageheart.core.room.RoomProblem.Kind;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Two markers that open into the same room both fail (DESIGN section 3.2). Pure. */
public final class SharedRooms {
	private SharedRooms() {}

	/**
	 * For every enclosed scan whose {@code roomKey} equals another enclosed scan's, add a SHARED problem pointing at the lowest
	 * other marker. Returns a new map in the same iteration order; scans that are not shared are returned unchanged.
	 *
	 * @throws IllegalArgumentException for a scan without a marker (a {@link RoomScanner#scanFromDoor} result)
	 */
	public static <K> Map<K, RoomScan> apply(Map<K, RoomScan> scansByMarker) {
		Map<Long, List<RoomScan>> byRoom = new HashMap<>();
		for (RoomScan s : scansByMarker.values()) {
			if (s.marker() == null) throw new IllegalArgumentException("scan without a marker: " + s);
			if (s.enclosed()) byRoom.computeIfAbsent(s.roomKey(), k -> new ArrayList<>()).add(s);
		}
		Map<K, RoomScan> out = new LinkedHashMap<>();
		for (Map.Entry<K, RoomScan> e : scansByMarker.entrySet()) {
			RoomScan s = e.getValue();
			RoomScan result = s;
			if (s.enclosed()) {
				Pos lowest = null;
				for (RoomScan other : byRoom.get(s.roomKey())) {
					Pos m = other.marker();
					if (m.equals(s.marker())) continue;
					if (lowest == null || m.compareTo(lowest) < 0) lowest = m;
				}
				if (lowest != null) result = s.withProblem(new RoomProblem(Kind.SHARED, lowest));
			}
			out.put(e.getKey(), result);
		}
		return out;
	}
}
