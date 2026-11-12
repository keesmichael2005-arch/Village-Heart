package dev.villageheart.core.smith;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RepairRulesTest {
	@Test
	void aQuarterOfTheMaxFloored() {
		assertEquals(62, RepairRules.unitSize(250));
		assertEquals(3, RepairRules.units(200, 250));
		assertEquals(14, RepairRules.after(200, 250, 3));
		assertEquals(0, RepairRules.units(61, 250));
		assertEquals(60, RepairRules.unitSize(240));
		assertEquals(3, RepairRules.units(200, 240));
		assertEquals(20, RepairRules.after(200, 240, 3));
		assertEquals(16, RepairRules.unitSize(65));
	}
}
