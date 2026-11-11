package dev.villageheart.core.siege;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.defence.DefenceKind;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** T-L1-16. Crack stage stops at 9, and a crack id is negative and unique inside the claim. */
class WallHpTest {
	@Test
	void stageAtElevenTwelfthsIsNineAndNeverTen() {
		assertEquals(12, WallHp.hp(DefenceKind.STONE));
		assertEquals(8, WallHp.hp(DefenceKind.GATE));
		assertEquals(9, WallHp.stage(11, 12));
		assertEquals(9, WallHp.stage(12, 12));
		assertEquals(0, WallHp.stage(0, 12));
	}

	@Test
	void crackIdIsNegativeAndInjectiveOverARadius80Box() {
		Set<Long> ids = new HashSet<>();
		int minY = -64;
		for (int dx = -80; dx <= 80; dx++) for (int dz = -80; dz <= 80; dz++) {
			long id = WallHp.crackId(dx, dz, 64, minY);
			assertTrue(id < 0, id + " at " + dx + "," + dz);
			assertTrue(ids.add(id), "collision at " + dx + "," + dz);
		}
		assertEquals(161 * 161, ids.size());
	}
}
