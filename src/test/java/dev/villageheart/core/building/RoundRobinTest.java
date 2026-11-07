package dev.villageheart.core.building;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class RoundRobinTest {
	static RoundRobin<String> rr() { return new RoundRobin<String>(Comparator.<String>naturalOrder(), 20, 1); }

	static TreeSet<String> keys(String... k) { return new TreeSet<>(List.of(k)); }

	@Test void cycleIsInOrderWithExactGaps() {
		RoundRobin<String> r = rr();
		TreeSet<String> cur = keys("a", "b", "c");
		Map<String, List<Integer>> at = new HashMap<>();
		List<String> order = new ArrayList<>();
		for (int t = 1; t <= 120; t++) {
			for (String k : r.tick(cur)) {
				at.computeIfAbsent(k, x -> new ArrayList<>()).add(t);
				order.add(k);
			}
		}
		assertEquals(List.of("a", "b", "c", "a", "b", "c"), order);
		for (List<Integer> ticks : at.values()) {
			assertEquals(2, ticks.size());
			assertEquals(60, ticks.get(1) - ticks.get(0));
		}
	}

	@Test void priorityKeyIsCheckedNextTickAndCycleContinues() {
		RoundRobin<String> r = rr();
		TreeSet<String> cur = keys("a", "b", "c");
		List<String> order = new ArrayList<>();
		for (int t = 1; t <= 60; t++) {
			List<String> now = r.tick(cur);
			if (t == 5) {
				assertEquals(List.of(), now);
				r.prioritise("c");
			}
			if (t == 6) assertEquals(List.of("c"), now);
			if (!now.isEmpty() && t != 6) order.add(now.get(0));
		}
		assertEquals(List.of("a", "b", "c"), order, "the cycle is unchanged by the priority check");
	}

	@Test void priorityIsDeduplicatedAndLimitedToOnePerTick() {
		RoundRobin<String> r = rr();
		TreeSet<String> cur = keys("a", "b", "c");
		r.prioritise("b");
		r.prioritise("b");
		assertEquals(List.of("b"), r.tick(cur));
		assertEquals(List.of(), r.tick(cur), "queued once only");
		r.prioritise("a");
		r.prioritise("b");
		r.prioritise("c");
		assertEquals(List.of("a"), r.tick(cur));
		assertEquals(List.of("b"), r.tick(cur));
		assertEquals(List.of("c"), r.tick(cur));
	}

	@Test void removedPriorityKeyIsDropped() {
		RoundRobin<String> r = rr();
		r.prioritise("x");
		assertEquals(List.of(), r.tick(keys("a")));
		assertEquals(0, r.pendingPriority());
	}

	@Test void addingAndRemovingNeverSkips() {
		RoundRobin<String> r = rr();
		TreeSet<String> cur = keys("a", "b", "c");
		for (int t = 1; t < 20; t++) assertEquals(List.of(), r.tick(cur));
		assertEquals(List.of("a"), r.tick(cur));
		cur.add("d");
		for (int t = 1; t < 20; t++) r.tick(cur);
		assertEquals(List.of("b"), r.tick(cur), "d sorts after c, so b is still next");
		cur.remove("b"); // remove the cursor key
		for (int t = 1; t < 20; t++) r.tick(cur);
		assertEquals(List.of("c"), r.tick(cur), "the next larger key, nothing skipped");
		for (int t = 1; t < 20; t++) r.tick(cur);
		assertEquals(List.of("d"), r.tick(cur));
		for (int t = 1; t < 20; t++) r.tick(cur);
		assertEquals(List.of("a"), r.tick(cur), "wraps around");
	}

	@Test void emptyRegistryDoesNothingAndFirstKeyChecksAtOnceAfterwards() {
		RoundRobin<String> r = rr();
		for (int t = 0; t < 50; t++) assertEquals(List.of(), r.tick(keys()));
		assertEquals(List.of("a"), r.tick(keys("a")));
	}

	/** After the last add/remove, every key is checked within (size + 1) intervals, whatever priority traffic there is. */
	@Test void everyKeyIsCheckedWithinTheBoundOfAStableSet() {
		for (int n = 1; n <= 12; n++) {
			Random rnd = new Random(n);
			RoundRobin<String> r = rr();
			TreeSet<String> cur = new TreeSet<>();
			for (int i = 0; i < n; i++) cur.add(String.format("k%02d", i));
			Map<String, Integer> since = new HashMap<>();
			for (int t = 0; t < 4000; t++) {
				boolean changed = false;
				if (rnd.nextInt(150) == 0 && cur.size() > 1) {
					cur.remove(cur.stream().skip(rnd.nextInt(cur.size())).findFirst().get());
					changed = true;
				}
				if (rnd.nextInt(150) == 0 && cur.size() < 12) changed |= cur.add(String.format("k%02d", rnd.nextInt(30)));
				if (changed) since.clear();
				if (rnd.nextInt(7) == 0 && !cur.isEmpty()) r.prioritise(cur.first());
				for (String k : cur) since.merge(k, 1, Integer::sum);
				for (String k : r.tick(cur)) since.put(k, 0);
				for (String k : cur) {
					assertTrue(since.get(k) <= (cur.size() + 1) * 20, "n=" + n + " t=" + t + " key " + k + " starved " + since.get(k));
				}
			}
		}
	}

	@Test void neverMoreThanTwoChecksAndNoDuplicates() {
		RoundRobin<String> r = rr();
		TreeSet<String> cur = keys("a", "b", "c", "d");
		for (int t = 0; t < 1000; t++) {
			for (String k : cur) r.prioritise(k);
			List<String> now = r.tick(cur);
			assertTrue(now.size() <= 2, "tick " + t + ": " + now);
			assertEquals(now.size(), now.stream().distinct().count());
		}
	}
}
