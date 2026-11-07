package dev.villageheart.core.worker;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.Tier;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.WorkerRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JobSlotsTest {
	static HeartLocation at(int x, int y, int z) { return new HeartLocation("minecraft:overworld", x, y, z); }

	static final HeartLocation MA = at(0, 64, 0), MB = at(50, 64, 0);
	static final HeartLocation A1 = at(1, 65, 1), A2 = at(2, 65, 1), B1 = at(51, 65, 1), B2 = at(52, 65, 1);
	static final SiteView SA = new SiteView(MA, List.of(A2, A1), 2);
	static final SiteView SB = new SiteView(MB, List.of(B1, B2), 2);

	static UUID id(int n) { return new UUID(0, n); }
	static WorkerRecord worker(int n, HeartLocation building, HeartLocation block) { return new WorkerRecord(id(n), "farmer", building, block); }

	static HireDecision decide(List<SiteView> sites, List<WorkerRecord> workers, Tier tier, UUID who, JobType job, Optional<HeartLocation> pref) {
		return JobSlots.decide(sites, workers, tier, who, job, pref);
	}

	@Test
	void freeSlotsAreSiteOrderThenPositionOrder() {
		assertEquals(List.of(A1, A2, B1, B2), JobSlots.freeSlots(List.of(SA, SB), List.of()));
		assertEquals(List.of(A2, B1, B2), JobSlots.freeSlots(List.of(SA, SB), List.of(worker(1, MA, A1))));
	}

	@Test
	void decideTakesTheFirstFreeSlot() {
		HireDecision d = decide(List.of(SA, SB), List.of(worker(1, MA, A1)), Tier.HAMLET, id(2), JobType.FARMER, Optional.empty());
		assertEquals(new HireDecision.Hired(SA, A2), d);
	}

	@Test
	void allSlotsTakenRefusesAndFiringFreesTheSlot() {
		VillageState v = VillageState.founded(MA, id(99), "o", 0);
		v = v.withWorker(worker(1, MA, A1)).withWorker(worker(2, MA, A2)).withWorker(worker(3, MB, B1)).withWorker(worker(4, MB, B2));
		assertEquals(new HireDecision.Refused(HireRefusal.NO_FREE_SLOT),
				decide(List.of(SA, SB), v.workers(), Tier.HAMLET, id(5), JobType.FARMER, Optional.empty()));
		v = v.withoutWorker(id(3));
		assertEquals(new HireDecision.Hired(SB, B1), decide(List.of(SA, SB), v.workers(), Tier.HAMLET, id(5), JobType.FARMER, Optional.empty()));
	}

	@Test
	void sameVillagerTwiceIsAlreadyEmployed() {
		assertEquals(new HireDecision.Refused(HireRefusal.ALREADY_EMPLOYED),
				decide(List.of(SA), List.of(worker(1, MA, A1)), Tier.HAMLET, id(1), JobType.FARMER, Optional.empty()));
	}

	@Test
	void aWorkerWhoseBlockIsInNoSiteDoesNotEatSlots() {
		List<WorkerRecord> ws = List.of(worker(1, MA, at(77, 65, 77)));
		assertEquals(List.of(A1, A2), JobSlots.freeSlots(List.of(SA), ws));
	}

	@Test
	void preferredBuildingWithoutFreeSlotIsRefusedEvenWhenAnotherHasOne() {
		List<WorkerRecord> ws = List.of(worker(1, MA, A1), worker(2, MA, A2));
		assertEquals(new HireDecision.Refused(HireRefusal.NOT_IN_PREFERRED_BUILDING),
				decide(List.of(SA, SB), ws, Tier.HAMLET, id(3), JobType.FARMER, Optional.of(MA)));
		assertEquals(new HireDecision.Hired(SB, B1), decide(List.of(SA, SB), ws, Tier.HAMLET, id(3), JobType.FARMER, Optional.of(MB)));
	}

	@Test
	void preferredBuildingWithNothingFreeAnywhereIsNoFreeSlot() {
		List<WorkerRecord> ws = List.of(worker(1, MA, A1), worker(2, MA, A2));
		assertEquals(new HireDecision.Refused(HireRefusal.NO_FREE_SLOT), decide(List.of(SA), ws, Tier.HAMLET, id(3), JobType.FARMER, Optional.of(MA)));
	}

	@Test
	void tierLockedAndRefusalOrder() {
		assertEquals(new HireDecision.Refused(HireRefusal.TIER_LOCKED),
				decide(List.of(SA), List.of(), Tier.HAMLET, id(1), JobType.MINER, Optional.empty()));
		assertEquals(new HireDecision.Hired(SA, A1), decide(List.of(SA), List.of(), Tier.VILLAGE, id(1), JobType.MINER, Optional.empty()));
		// an employed villager asking for a tier-locked job: ALREADY_EMPLOYED comes first
		assertEquals(new HireDecision.Refused(HireRefusal.ALREADY_EMPLOYED),
				decide(List.of(), List.of(worker(1, MA, A1)), Tier.HAMLET, id(1), JobType.MINER, Optional.empty()));
		// tier-locked with no slots at all: TIER_LOCKED beats NO_FREE_SLOT
		assertEquals(new HireDecision.Refused(HireRefusal.TIER_LOCKED),
				decide(List.of(), List.of(), Tier.HAMLET, id(1), JobType.BLACKSMITH, Optional.empty()));
	}

	@Test
	void anOccupiedSlotIsNeverDisplacedByALowerBlock() {
		HeartLocation b = at(10, 65, 0), c = at(11, 65, 0), a = at(9, 65, 0);
		List<WorkerRecord> ws = List.of(worker(1, MA, c));
		SiteView before = new SiteView(MA, List.of(b, c), 1);
		assertEquals(List.of(c), JobSlots.slotBlocks(before, ws));
		SiteView after = new SiteView(MA, List.of(a, b, c), 1);
		assertEquals(List.of(c), JobSlots.slotBlocks(after, ws));
		assertTrue(JobSlots.freeSlots(List.of(after), ws).isEmpty());
	}

	@Test
	void slotsCutTheBlockList() {
		SiteView three = new SiteView(MA, List.of(A1, A2, at(3, 65, 1)), 2);
		assertEquals(List.of(A1, A2), JobSlots.slotBlocks(three, List.of()));
		assertEquals(List.of(), JobSlots.slotBlocks(new SiteView(MA, List.of(A1), 0), List.of()));
		List<WorkerRecord> none = new ArrayList<>();
		assertEquals(List.of(A1), JobSlots.slotBlocks(new SiteView(MA, List.of(A1), 5), none));
	}
}
