package dev.villageheart.core.hunt;

/** The bow bonus: one extra meat when the weapon is a bow or crossbow and an arrow was taken from the Lodge chest. */
public final class HuntBonus {
	private HuntBonus() {}

	public static boolean applies(boolean ranged, boolean arrowTaken) { return ranged && arrowTaken; }
}
