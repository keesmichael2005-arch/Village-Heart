package dev.villageheart.core.sim;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ActionScheduleTest {
	private static ScenarioAction a(int day, int tick) {
		return new ScenarioAction("command", day, tick, Map.of("command", "d" + day + "t" + tick));
	}

	private static ActionSchedule schedule() {
		// deliberately unsorted
		return new ActionSchedule(List.of(a(3, 0), a(1, 100), a(0, 0), a(1, 0)));
	}

	private static List<String> ids(List<ScenarioAction> l) {
		return l.stream().map(x -> "d" + x.day() + "t" + x.tick()).toList();
	}

	@Test
	void dueOrderAndOnce() {
		ActionSchedule s = schedule();
		assertEquals(List.of("d0t0"), ids(s.setupActions()));
		assertEquals(List.of("d1t0"), ids(s.due(0)));
		assertEquals(List.of(), ids(s.due(99)));
		assertEquals(List.of("d1t100"), ids(s.due(150)));
		assertEquals(List.of(), ids(s.due(150)));
		assertEquals(List.of("d3t0"), ids(s.due(48000)));
		assertEquals(4, s.actionsDone());
	}

	@Test
	void jumpReturnsAllInOrder() {
		ActionSchedule s = schedule();
		s.due(50);
		assertEquals(List.of("d1t100", "d3t0"), ids(s.due(49000)));
	}

	@Test
	void restore() {
		ActionSchedule s = schedule();
		s.restore(2);
		assertEquals(List.of("d1t100", "d3t0"), ids(s.due(49000)));
		ActionSchedule t = schedule();
		t.restore(3);
		assertEquals(List.of("d3t0"), ids(t.due(49000)));
		assertThrows(IllegalArgumentException.class, () -> schedule().restore(5));
	}

	@Test
	void markSetupDone() {
		ActionSchedule s = schedule();
		s.markSetupDone();
		assertEquals(1, s.actionsDone());
	}
}
