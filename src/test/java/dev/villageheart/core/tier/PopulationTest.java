package dev.villageheart.core.tier;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.WorkerRecord;

class PopulationTest {
	private static final HeartLocation L = new HeartLocation("minecraft:overworld", 1, 64, 2);

	private static WorkerRecord w(UUID id) { return new WorkerRecord(id, "farmer", L, L); }

	@Test void unloadedWorkersCountOnceLoadedOnesNotTwice() {
		UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID(), d = UUID.randomUUID();
		assertEquals(4, Population.count(Set.of(a, b, c), List.of(w(a), w(d))));
	}

	@Test void empty() {
		assertEquals(0, Population.count(Set.of(), List.of()));
	}

	@Test void aWorkerOutsideTheSphereCounts() {
		assertEquals(1, Population.count(Set.of(), List.of(w(UUID.randomUUID()))));
	}
}
