package dev.villageheart.core.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.WorkerRecord;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** A missing workplace keeps the job. A red building does not give its workers away. */
class WorkplaceRebindTest {
	static HeartLocation at(int x, int y, int z) { return new HeartLocation("minecraft:overworld", x, y, z); }

	static final HeartLocation RED = at(0, 64, 0), GONE = at(20, 64, 0), FREE = at(40, 64, 0);
	static final HeartLocation RED_BLOCK = at(1, 65, 1), GONE_A = at(21, 65, 1), GONE_B = at(22, 65, 1), FREE_BLOCK = at(41, 65, 1);
	static final SiteView FREE_SITE = new SiteView(FREE, List.of(FREE_BLOCK), 1);

	static UUID id(int n) { return new UUID(0, n); }

	static WorkerRecord farmer(int n, HeartLocation building, HeartLocation block, int level) {
		return new WorkerRecord(id(n), "farmer", building, block, level);
	}

	@Test void aRegisteredBuildingKeepsItsWorkerEvenWhenAnotherSlotIsFree() {
		WorkerRecord stays = farmer(1, RED, RED_BLOCK, 2);
		List<WorkerRecord> out = WorkplaceRebind.apply(List.of(stays), Set.of(RED, FREE), Map.of("farmer", List.of(FREE_SITE)));
		assertEquals(List.of(stays), out, "a red building does not give its worker away");
	}

	@Test void aGoneBuildingRebindsInHiringOrderAndTheSecondWaiterStays() {
		WorkerRecord earlier = farmer(1, GONE, GONE_A, 3);
		WorkerRecord later = farmer(2, GONE, GONE_B, 2);
		List<WorkerRecord> out = WorkplaceRebind.apply(List.of(earlier, later), Set.of(FREE), Map.of("farmer", List.of(FREE_SITE)));
		assertEquals(earlier.withPlace(FREE, FREE_BLOCK), out.get(0), "the earlier hire takes the free block and keeps level and job");
		assertEquals(later, out.get(1), "the second waiter keeps the old building");
	}

	@Test void aWorkerOfAnotherJobDoesNotTakeTheFreeBlock() {
		WorkerRecord jack = new WorkerRecord(id(3), "lumberjack", GONE, GONE_A, 2);
		List<WorkerRecord> out = WorkplaceRebind.apply(List.of(jack), Set.of(FREE), Map.of("farmer", List.of(FREE_SITE)));
		assertEquals(List.of(jack), out, "a lumberjack does not take a Farmhouse");
	}

	@Test void aValidWorkplaceDoesNotMoveItsWorkerOntoAnotherFreeBlock() {
		HeartLocation home = at(50, 64, 0), block = at(51, 65, 1), other = at(60, 65, 1);
		WorkerRecord stays = farmer(4, home, block, 1);
		SiteView own = new SiteView(home, List.of(block, other), 2);
		List<WorkerRecord> out = WorkplaceRebind.apply(List.of(stays), Set.of(home), Map.of("farmer", List.of(own)));
		assertEquals(List.of(stays), out);
	}
}
