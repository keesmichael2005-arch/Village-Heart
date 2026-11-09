package dev.villageheart.core.tree;

import java.util.EnumMap;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * What one block position means to the tree scanner. Instances are shared (no allocation per read). {@code species} is set for LOG
 * and NATURAL_LEAVES only.
 */
public record TreeCell(Kind kind, @Nullable Species species) {
	public enum Kind { AIR, LOG, NATURAL_LEAVES, PLACED_LEAVES, SOIL, OTHER, UNLOADED }

	public static final TreeCell AIR = new TreeCell(Kind.AIR, null);
	public static final TreeCell PLACED_LEAVES = new TreeCell(Kind.PLACED_LEAVES, null);
	public static final TreeCell SOIL = new TreeCell(Kind.SOIL, null);
	public static final TreeCell OTHER = new TreeCell(Kind.OTHER, null);
	public static final TreeCell UNLOADED = new TreeCell(Kind.UNLOADED, null);

	private static final Map<Species, TreeCell> LOGS = new EnumMap<>(Species.class);
	private static final Map<Species, TreeCell> LEAVES = new EnumMap<>(Species.class);

	static {
		for (Species s : Species.values()) {
			LOGS.put(s, new TreeCell(Kind.LOG, s));
			LEAVES.put(s, new TreeCell(Kind.NATURAL_LEAVES, s));
		}
	}

	public static TreeCell log(Species s) { return LOGS.get(s); }

	public static TreeCell naturalLeaves(Species s) { return LEAVES.get(s); }

	public boolean isLeaves() { return kind == Kind.NATURAL_LEAVES || kind == Kind.PLACED_LEAVES; }
}
