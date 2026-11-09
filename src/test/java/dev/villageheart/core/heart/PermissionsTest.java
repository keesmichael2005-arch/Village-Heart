package dev.villageheart.core.heart;

import static dev.villageheart.core.heart.Permission.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.Rank;
import dev.villageheart.core.VillageState;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PermissionsTest {
	/** DESIGN section 2, literally: expected {OWNER, STEWARD, RESIDENT, VISITOR} per permission. Not computed from minimum(). */
	private static final Map<Permission, boolean[]> TABLE = new EnumMap<>(Permission.class);

	private static void row(Permission p, boolean owner, boolean steward, boolean resident, boolean visitor) {
		TABLE.put(p, new boolean[] {owner, steward, resident, visitor});
	}

	static {
		row(VIEW_OVERVIEW, true, true, true, true);
		row(VIEW_DETAILS, true, true, true, false);
		row(OPEN_CONTAINERS, true, true, true, false);
		row(HIRE, true, true, false, false);
		row(FIRE, true, true, false, false);
		row(RECHECK_BUILDING, true, true, false, false);
		row(SET_GUARD_SHIFT, true, true, false, false);
		row(START_BLUEPRINT, true, true, false, false);
		row(MANAGE_RANKS, true, false, false, false);
		row(TRANSFER_OWNERSHIP, true, false, false, false);
		row(BREAK_HEART, true, false, false, false);
		row(DEPOSIT_TREASURY, true, true, true, false);
		row(WITHDRAW_TREASURY, true, false, false, false); // a Steward may not take emeralds out (M12, T-L1-10)
	}

	@Test void rankOrderIsPinned() {
		assertEquals(List.of(Rank.OWNER, Rank.STEWARD, Rank.RESIDENT, Rank.VISITOR), Arrays.asList(Rank.values()));
	}

	@Test void everyPermissionHasARow() {
		for (Permission p : Permission.values()) assertTrue(TABLE.containsKey(p), "no row for " + p);
		assertEquals(Permission.values().length, TABLE.size());
	}

	@Test void matrixIsPinned() {
		for (Map.Entry<Permission, boolean[]> e : TABLE.entrySet()) {
			for (Rank r : Rank.values()) {
				assertEquals(e.getValue()[r.ordinal()], Permissions.allows(r, e.getKey()), r + " / " + e.getKey());
			}
		}
	}

	@Test void higherRanksKeepEverythingLowerRanksCan() {
		for (Permission p : Permission.values()) {
			for (Rank r : Rank.values()) {
				if (!Permissions.allows(r, p)) continue;
				for (Rank higher : Rank.values()) {
					if (higher.ordinal() < r.ordinal()) assertTrue(Permissions.allows(higher, p), higher + " / " + p);
				}
			}
		}
	}

	@Test void strangerIsVisitor() {
		UUID owner = UUID.randomUUID();
		VillageState v = VillageState.founded(new HeartLocation("minecraft:overworld", 0, 64, 0), owner, "O", 0);
		Rank stranger = v.rankOf(UUID.randomUUID());
		assertEquals(Rank.VISITOR, stranger);
		assertTrue(Permissions.allows(stranger, VIEW_OVERVIEW));
		assertFalse(Permissions.allows(stranger, OPEN_CONTAINERS));
		assertTrue(Permissions.allows(v.rankOf(owner), MANAGE_RANKS));
	}
}
