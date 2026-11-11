package dev.villageheart.core.siege;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Balance;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** T-L1-16. An unbreakable cell stops one short of a breach, a breached cell ignores hits, and a breach is never the one evicted. */
class DefenceDamageTest {
	@Test
	void unbreakableStopsAndABreachIgnoresTheNextHit() {
		long cell = 7;
		DefenceDamage d = DefenceDamage.EMPTY;
		for (int i = 0; i < 30; i++) d = d.struck(cell, 1, 12, false);
		assertEquals(11, d.damageAt(cell));
		assertFalse(d.isBreached(cell));

		DefenceDamage broken = DefenceDamage.EMPTY;
		for (int i = 0; i < 12; i++) broken = broken.struck(cell, 1, 12, true);
		assertTrue(broken.isBreached(cell));
		assertEquals(0, broken.damageAt(cell));
		assertEquals(broken, broken.struck(cell, 6, 12, true));
	}

	@Test
	void evictionDropsTheLeastDamagedCrackAndKeepsTheBreach() {
		Map<Long, Integer> cracks = new LinkedHashMap<>();
		cracks.put(2L, 1);
		cracks.put(3L, 11);
		List<Long> breaches = new java.util.ArrayList<>();
		for (long i = 10; i < 10 + Balance.MAX_DAMAGED_CELLS - 1; i++) breaches.add(i);
		DefenceDamage.Capped capped = DefenceDamage.cap(cracks, breaches);
		assertEquals(1, capped.trimmed());
		assertFalse(capped.damage().cracked().containsKey(2L));
		assertEquals(11, capped.damage().damageAt(3));
		assertEquals(Balance.MAX_DAMAGED_CELLS - 1, capped.damage().breached().size());
		assertTrue(capped.damage().breached().contains(10L));
	}
}
