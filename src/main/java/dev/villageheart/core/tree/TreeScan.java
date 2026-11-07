package dev.villageheart.core.tree;

import dev.villageheart.core.room.Pos;
import java.util.List;

/** The verdict on a log: a natural tree, or why not. */
public sealed interface TreeScan {
	/**
	 * @param base      the logs resting on soil, sorted by position (1 for most trees, 4 for 2x2 trunks)
	 * @param fellOrder every log of the tree, highest first, the base last
	 */
	record Tree(Species species, List<Pos> base, List<Pos> fellOrder, int leafContacts) implements TreeScan {
		public Tree {
			base = List.copyOf(base);
			fellOrder = List.copyOf(fellOrder);
		}
	}

	/** {@code logs}: the logs the flood visited (so a finder does not scan the same player build again from every column). */
	record NotATree(Why why, List<Pos> logs) implements TreeScan {
		public NotATree {
			logs = List.copyOf(logs);
		}
	}

	enum Why {
		/** The start cell is not a log (any more). */
		GONE,
		NO_LEAVES, NO_GROUND, BASE_NOT_TREE_SHAPE, IN_ROOM, TOO_BIG,
		/** The flood touched a cell that is not loaded: try again later. */
		UNKNOWN
	}
}
