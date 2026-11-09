package dev.villageheart.core.mine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DepthBandTest {
	@Test
	void boundariesBelongToTheUpperBand() {
		assertEquals(DepthBand.HIGH, DepthBand.of(320));
		assertEquals(DepthBand.HIGH, DepthBand.of(40));
		assertEquals(DepthBand.MID, DepthBand.of(39));
		assertEquals(DepthBand.MID, DepthBand.of(0));
		assertEquals(DepthBand.DEEP, DepthBand.of(-1));
		assertEquals(DepthBand.DEEP, DepthBand.of(-64));
	}
}
