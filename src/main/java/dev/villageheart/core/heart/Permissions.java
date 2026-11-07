package dev.villageheart.core.heart;

import dev.villageheart.core.Rank;

/** The rank matrix of DESIGN section 2 (M24 extends it). Servers' operators get no bypass. */
public final class Permissions {
	private Permissions() {}

	public static boolean allows(Rank rank, Permission p) {
		return switch (rank) {
			case OWNER, STEWARD -> true;
			case RESIDENT -> p == Permission.VIEW_OVERVIEW || p == Permission.VIEW_DETAILS;
			case VISITOR -> p == Permission.VIEW_OVERVIEW;
		};
	}
}
