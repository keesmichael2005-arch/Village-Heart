package dev.villageheart.core.worker;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.WorkerRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VillageStateWorkersTest {
	static final HeartLocation H = new HeartLocation("minecraft:overworld", 0, 64, 0);
	static final HeartLocation M2 = new HeartLocation("minecraft:overworld", 50, 64, 0);
	static final HeartLocation J1 = new HeartLocation("minecraft:overworld", 1, 65, 1);
	static final HeartLocation J2 = new HeartLocation("minecraft:overworld", 2, 65, 1);
	static final HeartLocation J3 = new HeartLocation("minecraft:overworld", 51, 65, 1);

	static UUID id(int n) { return new UUID(0, n); }
	static WorkerRecord w(int n, HeartLocation building, HeartLocation block) { return new WorkerRecord(id(n), "farmer", building, block); }
	static VillageState base() { return VillageState.founded(H, id(100), "o", 0); }

	@Test
	void withWorkerAppendsInHiringOrder() {
		VillageState v = base().withWorker(w(3, H, J2)).withWorker(w(1, H, J1)).withWorker(w(2, M2, J3));
		assertEquals(List.of(id(3), id(1), id(2)), v.workers().stream().map(WorkerRecord::villager).toList());
		assertEquals(Optional.of(w(1, H, J1)), v.worker(id(1)));
		assertEquals(List.of(w(3, H, J2), w(1, H, J1)), v.workersAt(H));
	}

	@Test
	void withoutWorkerRemovesAndUnknownIsANoOp() {
		VillageState v = base().withWorker(w(1, H, J1)).withWorker(w(2, H, J2));
		VillageState gone = v.withoutWorker(id(1));
		assertEquals(List.of(w(2, H, J2)), gone.workers());
		assertSame(gone, gone.withoutWorker(id(9)));
	}

	@Test
	void duplicateVillagerOrJobBlockThrows() {
		VillageState v = base().withWorker(w(1, H, J1));
		assertThrows(IllegalArgumentException.class, () -> v.withWorker(w(1, H, J2)));
		assertThrows(IllegalArgumentException.class, () -> v.withWorker(w(2, H, J1)));
	}

	@Test
	void reHireGoesLast() {
		VillageState v = base().withWorker(w(1, H, J1)).withWorker(w(2, H, J2)).withoutWorker(id(1)).withWorker(w(1, H, J1));
		assertEquals(List.of(id(2), id(1)), v.workers().stream().map(WorkerRecord::villager).toList());
	}
}
