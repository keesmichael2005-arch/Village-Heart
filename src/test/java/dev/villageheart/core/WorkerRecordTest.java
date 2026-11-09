package dev.villageheart.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkerRecordTest {
	private static final HeartLocation L = new HeartLocation("minecraft:overworld", 1, 64, 2);

	@Test void theFourArgumentConstructorIsLevelOne() {
		assertEquals(1, new WorkerRecord(UUID.randomUUID(), "farmer", L, L).level());
	}

	@Test void levelBelowOneThrows() {
		assertThrows(IllegalArgumentException.class, () -> new WorkerRecord(UUID.randomUUID(), "farmer", L, L, 0));
	}

	@Test void withLevelChangesOnlyTheLevel() {
		UUID id = UUID.randomUUID();
		WorkerRecord r = new WorkerRecord(id, "farmer", L, L).withLevel(3);
		assertEquals(new WorkerRecord(id, "farmer", L, L, 3), r);
	}
}
