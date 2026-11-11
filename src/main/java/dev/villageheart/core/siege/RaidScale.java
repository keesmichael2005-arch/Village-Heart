package dev.villageheart.core.siege;

/** Omen and extra raiders of a full random raid (docs/plans/M25.md 3.14). */
public record RaidScale(int omen, int extraVindicators, int extraRavagers) {
	public static RaidScale of(double threat) {
		int omen = Math.min(5, Math.max(1, 1 + (int) Math.floor(threat / 3)));
		int vindicators = Math.min(4, Math.max(0, (int) Math.floor(threat / 2)));
		int ravagers = Math.min(2, Math.max(0, (int) Math.floor(threat / 4)));
		return new RaidScale(omen, vindicators, ravagers);
	}
}
