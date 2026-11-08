package dev.villageheart.core.mine;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.HeartLocation;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ShaftChoiceTest {
	private static final String OW = "minecraft:overworld";
	private static final HeartLocation MINE = new HeartLocation(OW, 0, 64, 0);

	@Test
	void theNearestShaftWins() {
		HeartLocation far = new HeartLocation(OW, 30, 64, 0), near = new HeartLocation(OW, 12, 64, 5);
		assertEquals(Optional.of(near), ShaftChoice.pick(MINE, List.of(far, near)));
		assertEquals(Optional.of(near), ShaftChoice.pick(MINE, List.of(near, far)));
	}

	@Test
	void aTieGoesToTheLowestLocationWhateverTheInputOrder() {
		HeartLocation a = new HeartLocation(OW, 10, 64, 0), b = new HeartLocation(OW, -10, 64, 0);
		Optional<HeartLocation> first = ShaftChoice.pick(MINE, List.of(a, b));
		assertEquals(first, ShaftChoice.pick(MINE, List.of(b, a)));
		assertTrue(first.isPresent());
	}

	@Test
	void noneGivesEmptyAndAShaftInAnotherDimensionDoesNotCount() {
		assertEquals(Optional.empty(), ShaftChoice.pick(MINE, List.of()));
		assertEquals(Optional.empty(), ShaftChoice.pick(MINE, List.of(new HeartLocation("minecraft:the_nether", 1, 64, 1))));
	}
}
