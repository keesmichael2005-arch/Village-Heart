package dev.villageheart.core.heart;

import dev.villageheart.core.Rank;

/** The rank matrix of DESIGN section 2: a rank may do what its minimum allows. Servers' operators get no bypass. */
public final class Permissions {
	private Permissions() {}

	public static boolean allows(Rank rank, Permission p) {
		return rank.atLeast(p.minimum());
	}
}
