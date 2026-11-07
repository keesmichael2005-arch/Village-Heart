package dev.villageheart.core.room;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.jetbrains.annotations.Nullable;

/**
 * The result of one room scan. Immutable: the collections are copied on construction, so a cached scan cannot be changed.
 *
 * @param marker    the marker the scan started from; null for {@link RoomScanner#scanFromDoor}
 * @param door      lower half of the door used, null when there was none
 * @param start     first air cell inside the door, null when the fill never started
 * @param enclosed  the fill finished without NOT_LOADED / leak / TOO_BIG (and a door and start were found)
 * @param contents  content key to positions (keys sorted, positions in discovery order)
 * @param problems  empty when valid
 * @param interior  packed positions ({@link Pos#pack()}) of the air cells
 * @param shell     packed positions of the solid cells touching the interior
 * @param roomKey   minimum packed interior position; equal for any two enclosed scans of the same room
 */
public record RoomScan(
		@Nullable Pos marker,
		@Nullable Pos door,
		@Nullable Pos start,
		boolean enclosed,
		int volume,
		int floorArea,
		Map<String, List<Pos>> contents,
		List<RoomProblem> problems,
		Set<Long> interior,
		Set<Long> shell,
		long roomKey) {

	public RoomScan {
		TreeMap<String, List<Pos>> sorted = new TreeMap<>();
		for (Map.Entry<String, List<Pos>> e : contents.entrySet()) sorted.put(e.getKey(), List.copyOf(e.getValue()));
		contents = Collections.unmodifiableMap(sorted);
		problems = List.copyOf(problems);
		interior = Set.copyOf(interior);
		shell = Set.copyOf(shell);
	}

	public boolean valid() { return enclosed && problems.isEmpty(); }

	public int count(String key) { return positions(key).size(); }

	public List<Pos> positions(String key) { return contents.getOrDefault(key, List.of()); }

	/** True when {@code p} is an air cell of the room. */
	public boolean contains(Pos p) { return interior.contains(p.pack()); }

	/** True when {@code p} is an air cell or a wall/floor/roof cell of the room (a blueprint site must not overlap it). */
	public boolean occupies(Pos p) {
		long k = p.pack();
		return interior.contains(k) || shell.contains(k);
	}

	public RoomScan withProblem(RoomProblem p) {
		List<RoomProblem> all = new ArrayList<>(problems);
		all.add(p);
		return new RoomScan(marker, door, start, enclosed, volume, floorArea, contents, all, interior, shell, roomKey);
	}
}
