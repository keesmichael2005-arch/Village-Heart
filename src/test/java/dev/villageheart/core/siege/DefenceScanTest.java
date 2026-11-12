package dev.villageheart.core.siege;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.siege.DefenceScan.Occupancy;
import org.junit.jupiter.api.Test;

/** T-L1-26. A cell the scan has never seen holding its material does not become a breach. */
class DefenceScanTest {
	@Test
	void onlyASeenMissingCellIsBreached() {
		assertFalse(DefenceScan.breached(false, false, Occupancy.MISSING));
		assertTrue(DefenceScan.breached(true, false, Occupancy.MISSING));
		assertFalse(DefenceScan.breached(true, true, Occupancy.MISSING));
		assertFalse(DefenceScan.breached(true, false, Occupancy.HOLDING));
		assertFalse(DefenceScan.breached(true, false, Occupancy.OTHER));
	}
}
