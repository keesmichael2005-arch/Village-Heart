package dev.villageheart.core.worker;

import java.util.Locale;

/**
 * Why a hire was refused, in the order the checks run. The first five are the glue's checks (in {@code Workers.hire});
 * the last four come from {@link JobSlots#decide}. Every one has a {@code message.villageheart.hire.<key>} lang key.
 */
public enum HireRefusal {
	NO_VILLAGE, NOT_A_VILLAGER, NOT_ADULT, NITWIT, OUTSIDE_CLAIM,
	ALREADY_EMPLOYED, TIER_LOCKED, NOT_IN_PREFERRED_BUILDING, NO_FREE_SLOT;

	public String langKey() { return "message.villageheart.hire." + name().toLowerCase(Locale.ROOT); }
}
