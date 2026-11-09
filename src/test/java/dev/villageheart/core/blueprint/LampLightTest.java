package dev.villageheart.core.blueprint;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** T-L1-3 */
class LampLightTest {
	@Test
	void radiusIsTwelve() {
		assertEquals(12, LampLight.radius(15, 2));
		assertEquals(7, LampLight.radius(10, 2));
		assertEquals(15, LampLight.blockLight(0, 0, 0));
		assertEquals(1, LampLight.blockLight(12, -2, 0));
		assertEquals(1, LampLight.blockLight(6, -2, 6));
		assertEquals(0, LampLight.blockLight(13, -2, 0));
		assertEquals(0, LampLight.blockLight(7, -2, 6));
	}
}
