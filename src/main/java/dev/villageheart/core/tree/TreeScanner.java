package dev.villageheart.core.tree;

import dev.villageheart.core.Balance;
import dev.villageheart.core.room.Pos;
import dev.villageheart.core.tree.TreeScan.NotATree;
import dev.villageheart.core.tree.TreeScan.Tree;
import dev.villageheart.core.tree.TreeScan.Why;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/**
 * Decides whether a log belongs to a natural tree (DESIGN 5.1, "Rules as built (M9)"). A tree is a 26-connected component of logs that
 * (a) touches at least {@link Balance#TREE_MIN_LEAF_CONTACTS} natural (non-persistent) leaf blocks face to face, (b) stands on soil with
 * all its soil-resting logs inside one 2x2 footprint at its lowest y, (c) has no log inside a valid building's room and (d) is at most
 * 192 logs, 17 wide and 48 tall. Reads at most 26 cells per log.
 */
public final class TreeScanner {
	private TreeScanner() {}

	private static final int[][] FACES = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

	private static final class Extent {
		int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
		int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;

		void add(Pos p) {
			minX = Math.min(minX, p.x());
			maxX = Math.max(maxX, p.x());
			minY = Math.min(minY, p.y());
			maxY = Math.max(maxY, p.y());
			minZ = Math.min(minZ, p.z());
			maxZ = Math.max(maxZ, p.z());
		}

		boolean tooBig() {
			return maxX - minX + 1 > Balance.MAX_TREE_WIDTH || maxZ - minZ + 1 > Balance.MAX_TREE_WIDTH
					|| maxY - minY + 1 > Balance.MAX_TREE_HEIGHT;
		}
	}

	public static TreeScan scan(TreeView v, Pos anyLog) {
		if (v.cell(anyLog).kind() != TreeCell.Kind.LOG) return new NotATree(Why.GONE, List.of());
		Set<Long> seen = new HashSet<>();
		List<Pos> logs = new ArrayList<>();
		Set<Long> leaves = new HashSet<>();
		EnumMap<Species, Integer> leafSpecies = new EnumMap<>(Species.class);
		Map<Pos, Species> resting = new LinkedHashMap<>();
		Extent ext = new Extent();
		ArrayDeque<Pos> queue = new ArrayDeque<>();
		queue.add(anyLog);
		seen.add(anyLog.pack());
		boolean unknown = false;
		Why abort = null;
		while (!queue.isEmpty() && abort == null) {
			Pos p = queue.poll();
			logs.add(p);
			ext.add(p);
			if (logs.size() > Balance.MAX_TREE_LOGS || ext.tooBig()) {
				abort = Why.TOO_BIG;
				break;
			}
			Species mine = v.cell(p).species();
			for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
				if (dx == 0 && dy == 0 && dz == 0) continue;
				int x = p.x() + dx, y = p.y() + dy, z = p.z() + dz;
				TreeCell c = v.cell(x, y, z);
				switch (c.kind()) {
					case UNLOADED -> unknown = true;
					case LOG -> {
						if (seen.add(Pos.pack(x, y, z))) queue.add(new Pos(x, y, z));
					}
					case NATURAL_LEAVES -> {
						if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) == 1 && leaves.add(Pos.pack(x, y, z))) {
							leafSpecies.merge(c.species(), 1, Integer::sum);
						}
					}
					case SOIL -> {
						if (dx == 0 && dz == 0 && dy == -1) resting.put(p, mine);
					}
					default -> { }
				}
			}
		}
		if (abort == Why.TOO_BIG) return new NotATree(Why.TOO_BIG, logs);
		if (unknown) return new NotATree(Why.UNKNOWN, logs);
		for (Pos p : logs) if (v.inRoom(p)) return new NotATree(Why.IN_ROOM, logs);
		if (leaves.size() < Balance.TREE_MIN_LEAF_CONTACTS) return new NotATree(Why.NO_LEAVES, logs);
		if (resting.isEmpty()) return new NotATree(Why.NO_GROUND, logs);
		List<Pos> base = new ArrayList<>(resting.keySet());
		base.sort(null);
		int bminX = Integer.MAX_VALUE, bmaxX = Integer.MIN_VALUE, bminZ = Integer.MAX_VALUE, bmaxZ = Integer.MIN_VALUE;
		boolean lowest = true;
		for (Pos b : base) {
			bminX = Math.min(bminX, b.x());
			bmaxX = Math.max(bmaxX, b.x());
			bminZ = Math.min(bminZ, b.z());
			bmaxZ = Math.max(bmaxZ, b.z());
			if (b.y() != ext.minY) lowest = false;
		}
		if (!lowest || bmaxX - bminX > 1 || bmaxZ - bminZ > 1) return new NotATree(Why.BASE_NOT_TREE_SHAPE, logs);
		Species species = resting.get(base.get(0));
		if (species == Species.OAK) {
			int azalea = leafSpecies.getOrDefault(Species.AZALEA, 0);
			if (azalea * 2 > leaves.size()) species = Species.AZALEA;
		}
		return new Tree(species, base, FellOrder.topDown(logs, base), leaves.size());
	}

	/**
	 * A fell in progress: flood from the recorded base logs that are still standing, only inside the saved box. The leaf and ground
	 * rules do not apply (the leaves may have decayed), but a log inside a valid building's room or a flood over 192 logs still refuses.
	 */
	public static TreeScan resume(TreeView v, List<Pos> recordedBase, Pos boxMin, Pos boxMax) {
		Set<Long> seen = new HashSet<>();
		List<Pos> logs = new ArrayList<>();
		ArrayDeque<Pos> queue = new ArrayDeque<>();
		Species species = null;
		for (Pos b : recordedBase) {
			TreeCell c = v.cell(b);
			if (c.kind() == TreeCell.Kind.UNLOADED) return new NotATree(Why.UNKNOWN, List.of());
			if (c.kind() != TreeCell.Kind.LOG) continue;
			if (species == null) species = c.species();
			if (seen.add(b.pack())) queue.add(b);
		}
		if (queue.isEmpty()) return new NotATree(Why.GONE, List.of());
		boolean unknown = false;
		while (!queue.isEmpty()) {
			Pos p = queue.poll();
			logs.add(p);
			if (logs.size() > Balance.MAX_TREE_LOGS) return new NotATree(Why.TOO_BIG, logs);
			for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
				if (dx == 0 && dy == 0 && dz == 0) continue;
				int x = p.x() + dx, y = p.y() + dy, z = p.z() + dz;
				if (x < boxMin.x() || x > boxMax.x() || y < boxMin.y() || y > boxMax.y() || z < boxMin.z() || z > boxMax.z()) continue;
				TreeCell c = v.cell(x, y, z);
				if (c.kind() == TreeCell.Kind.UNLOADED) unknown = true;
				else if (c.kind() == TreeCell.Kind.LOG && seen.add(Pos.pack(x, y, z))) queue.add(new Pos(x, y, z));
			}
		}
		if (unknown) return new NotATree(Why.UNKNOWN, logs);
		for (Pos p : logs) if (v.inRoom(p)) return new NotATree(Why.IN_ROOM, logs);
		List<Pos> base = new ArrayList<>(recordedBase);
		base.sort(null);
		return new Tree(species, base, FellOrder.topDown(logs, base), 0);
	}

	/** Pure helper for glue and tests: the extent of a log set. */
	public static @Nullable Pos[] box(List<Pos> logs) {
		if (logs.isEmpty()) return null;
		Extent e = new Extent();
		for (Pos p : logs) e.add(p);
		return new Pos[] {new Pos(e.minX, e.minY, e.minZ), new Pos(e.maxX, e.maxY, e.maxZ)};
	}
}
