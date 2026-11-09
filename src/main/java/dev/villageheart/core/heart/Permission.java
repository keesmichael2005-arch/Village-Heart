package dev.villageheart.core.heart;

import static dev.villageheart.core.Rank.*;

import dev.villageheart.core.Rank;

/**
 * Things a player can do in a village. DESIGN section 2 is cumulative, so each permission has one minimum rank.
 * Never serialized (saves and the wire carry ranks), so appending in any order is safe. The compiler forces every
 * new permission to name its minimum rank.
 */
public enum Permission {
	VIEW_OVERVIEW(VISITOR), VIEW_DETAILS(RESIDENT), HIRE(STEWARD), FIRE(STEWARD), RECHECK_BUILDING(STEWARD),
	/** Defined now, enforced when M18 adds the action: gate the action with this permission. */
	SET_GUARD_SHIFT(STEWARD),
	/** Open and break containers inside the village's buildings (the Visitor rule). */
	OPEN_CONTAINERS(RESIDENT), MANAGE_RANKS(OWNER), TRANSFER_OWNERSHIP(OWNER), BREAK_HEART(OWNER),
	/** Defined now, enforced when M20 adds the action: gate the action with this permission. */
	START_BLUEPRINT(STEWARD),
	/** Put emeralds into the treasury (M12): right-click the Heart with emeralds, or "Deposit all" on the Overview. */
	DEPOSIT_TREASURY(RESIDENT),
	/** Take emeralds out of the treasury (M12): the Owner only, a Steward may not. */
	WITHDRAW_TREASURY(OWNER);

	private final Rank minimum;

	Permission(Rank minimum) { this.minimum = minimum; }

	public Rank minimum() { return minimum; }
}
