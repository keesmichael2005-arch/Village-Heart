package dev.villageheart.core.siege;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Balance;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** T-L1-18 and T-L1-26. The lure needs an enclosure, and a freed slot is filled again. */
class LureTest {
	@Test
	void nightAndSiegeAndPeaceful() {
		assertTrue(Lure.active(13000, 150, false, false));
		assertTrue(Lure.active(22999, 150, false, false));
		assertFalse(Lure.active(23000, 150, false, false));
		assertFalse(Lure.active(12999, 150, false, false));
		assertTrue(Lure.active(6000, 150, false, true));
		assertFalse(Lure.active(18000, 0, false, false));
		assertFalse(Lure.active(18000, 150, true, true));
		assertFalse(Lure.active(18000, 149, false, false));
		assertTrue(Lure.active(18000, 150, false, false));
	}

	@Test
	void twentyFourSlotsPreferSiegeZombiesAndAFreedSlotIsReused() {
		List<LureBudget.Mob> mobs = new ArrayList<>();
		for (int i = 0; i < 25; i++) mobs.add(new LureBudget.Mob(id(i), false, i, true));
		mobs.add(new LureBudget.Mob(id(100), true, 500, true));
		List<UUID> admitted = LureBudget.admit(mobs);
		assertEquals(Balance.MAX_LURED, admitted.size());
		assertTrue(admitted.contains(id(100)));
		assertFalse(admitted.contains(id(23)), "a nearer natural zombie loses the last slot to the siege");
		assertFalse(admitted.contains(id(24)));

		mobs.set(0, new LureBudget.Mob(id(0), false, 0, false));
		List<UUID> again = LureBudget.admit(mobs);
		assertTrue(again.contains(id(23)));
		assertFalse(again.contains(id(0)));
		assertEquals(Balance.MAX_LURED, again.size());
	}

	private static UUID id(int n) { return new UUID(0, n); }
}
