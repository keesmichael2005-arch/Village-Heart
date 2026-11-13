package dev.villageheart.core.worker;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.room.Pos;
import java.util.List;
import org.junit.jupiter.api.Test;

class StandChoiceTest {
	@Test
	void picksTheClosestOpenSideAndKeepsTiesInOrder() {
		Pos block = new Pos(0, 64, 0);
		Pos north = new Pos(0, 64, -1);
		Pos east = new Pos(1, 64, 0);
		assertEquals(east, StandChoice.nearest(List.of(north, east), new Pos(2, 64, 0)).orElseThrow());
		assertEquals(north, StandChoice.nearest(List.of(north, east), block).orElseThrow());
		assertTrue(StandChoice.nearest(List.of(), block).isEmpty());
	}
}
