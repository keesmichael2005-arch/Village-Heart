package dev.villageheart.core.worker;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.HeartLocation;
import org.junit.jupiter.api.Test;

class WorkerDataTest {
	static final HeartLocation B = new HeartLocation("minecraft:overworld", 0, 64, 0);
	static final HeartLocation J = new HeartLocation("minecraft:overworld", 1, 65, 1);

	@Test
	void hiredHasDefaults() {
		WorkerData d = WorkerData.hired(JobType.FARMER, B, J);
		assertEquals("farmer", d.jobId());
		assertEquals(1, d.level());
		assertEquals(0, d.xp());
		assertEquals(Needs.DEFAULT, d.needs());
		assertEquals(new Needs(true, 0, true, true), Needs.DEFAULT);
		assertEquals(JobType.FARMER, d.job());
	}

	@Test
	void boundsAreExact() {
		assertDoesNotThrow(() -> new WorkerData("farmer", B, J, 1, 0, Needs.DEFAULT));
		assertThrows(IllegalArgumentException.class, () -> new WorkerData("farmer", B, J, 0, 0, Needs.DEFAULT));
		assertThrows(IllegalArgumentException.class, () -> new WorkerData("farmer", B, J, 1, -1, Needs.DEFAULT));
		assertThrows(IllegalArgumentException.class, () -> new WorkerData("wizard", B, J, 1, 0, Needs.DEFAULT));
		assertDoesNotThrow(() -> new Needs(true, 0, true, true));
		assertThrows(IllegalArgumentException.class, () -> new Needs(true, -1, true, true));
	}
}
