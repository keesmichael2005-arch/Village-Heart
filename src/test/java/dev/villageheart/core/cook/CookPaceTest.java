package dev.villageheart.core.cook;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Balance;
import dev.villageheart.core.job.WorkClock;
import dev.villageheart.core.job.WorkSpeed;
import dev.villageheart.core.worker.Needs;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CookPaceTest {
	private static List<Integer> itemTicks(double speed, int ticks) {
		WorkClock clock = new WorkClock(Balance.COOK_TICKS_PER_ITEM);
		List<Integer> out = new ArrayList<>();
		for (int t = 1; t <= ticks; t++) for (int i = clock.tick(speed); i > 0; i--) out.add(t);
		return out;
	}

	@Test
	void eightItemsPerHourAtLevelOne() {
		double speed = WorkSpeed.untooled(1, Needs.DEFAULT);
		assertEquals(1.0, speed);
		List<Integer> items = itemTicks(speed, 1000);
		assertEquals(8, items.size(), "DESIGN 5.5: 8 items per in-game hour");
		assertEquals(List.of(125, 250, 375), items.subList(0, 3));
	}

	@Test
	void hungerAndLevelsChangeThePace() {
		double hungry = WorkSpeed.untooled(1, Needs.of(1, true, true));
		assertEquals(0.5, hungry);
		assertEquals(List.of(250, 500), itemTicks(hungry, 600));
		double level2 = WorkSpeed.untooled(2, Needs.DEFAULT);
		assertEquals(List.of(100, 200, 300), itemTicks(level2, 300));
	}

	@Test
	void tooHungryOrOnStrikeNeverCooks() {
		assertTrue(itemTicks(WorkSpeed.untooled(1, Needs.of(2, true, true)), 10_000).isEmpty());
		assertTrue(itemTicks(WorkSpeed.untooled(1, Needs.of(0, true, false)), 10_000).isEmpty());
	}
}
