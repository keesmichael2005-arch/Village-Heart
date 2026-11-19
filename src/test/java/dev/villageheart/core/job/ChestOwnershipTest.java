package dev.villageheart.core.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.villageheart.core.job.ChestOwnership.Chest;
import dev.villageheart.core.job.ChestOwnership.Spot;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** One chest per job block: nearest free chest, a double chest once, beds limit a Barracks. */
class ChestOwnershipTest {
	static Spot at(int x, int y, int z) { return new Spot(x, y, z); }

	static Chest single(int x, int y, int z) { return new Chest(at(x, y, z), null); }

	@Test
	void slotsAreTheMinimumOfJobBlocksAndChests() {
		assertEquals(1, ChestOwnership.slots(2, 1, null));
		assertEquals(2, ChestOwnership.slots(2, 3, null));
		assertEquals(0, ChestOwnership.slots(2, 0, null));
		assertEquals(1, ChestOwnership.slots(2, 2, 1), "a Barracks is also limited by beds");
		assertEquals(2, ChestOwnership.slots(3, 2, 4));
	}

	@Test
	void aDoubleChestCountsOnce() {
		Spot left = at(1, 64, 1), right = at(2, 64, 1);
		List<Chest> halves = List.of(new Chest(left, right), new Chest(right, left), single(8, 64, 1));
		assertEquals(2, ChestOwnership.distinct(halves).size());
		assertEquals(1, ChestOwnership.slots(2, ChestOwnership.distinct(halves).size() - 1, null));
		assertEquals(2, ChestOwnership.slots(2, ChestOwnership.distinct(halves).size(), null));
	}

	@Test
	void theNearestFreeChestIsPairedAndATakenOneIsSkipped() {
		Spot job = at(0, 64, 0);
		Chest far = single(10, 64, 0), near = single(2, 64, 0), tied = single(0, 64, 2);
		assertEquals(Optional.of(near), ChestOwnership.nearestFree(job, List.of(far, near), Set.of()));
		assertEquals(Optional.of(far), ChestOwnership.nearestFree(job, List.of(far, near), Set.of(near.at())));
		// equal distance: the lower position wins
		assertEquals(Optional.of(tied), ChestOwnership.nearestFree(job, List.of(far, tied, single(0, 65, 2)), Set.of()));
	}

	@Test
	void twoJobBlocksAndOneChestHireOneWorker() {
		Spot chop = at(0, 65, 0), chop2 = at(4, 65, 0);
		Chest only = single(4, 64, 3);
		Map<Spot, Spot> paired = ChestOwnership.pair(List.of(chop, chop2), List.of(only), Set.of());
		assertEquals(1, paired.size());
		assertEquals(only.at(), paired.get(chop2), "the nearer job block owns the only chest");
		assertTrue(ChestOwnership.nearestFree(chop, List.of(only), Set.copyOf(paired.values())).isEmpty());
	}

	@Test
	void aSharedChestGoesToTheNearestJobBlockAndABrokenLinkStays() {
		UUID a = new UUID(0, 1), b = new UUID(0, 2);
		Spot chest = at(5, 64, 5);
		Spot near = at(5, 65, 6), far = at(0, 65, 0);
		Spot gone = at(9, 64, 9);
		Map<UUID, Spot> owned = ChestOwnership.resolve(List.of(
				new ChestOwnership.Held(a, far, chest),
				new ChestOwnership.Held(b, near, chest),
				new ChestOwnership.Held(new UUID(0, 3), at(1, 65, 1), gone)),
				List.of(single(chest.x(), chest.y(), chest.z())));
		assertEquals(chest, owned.get(b), "the nearer job block keeps the shared chest");
		assertEquals(null, owned.get(a), "the other waits on a new chest");
		assertEquals(gone, owned.get(new UUID(0, 3)), "a broken chest keeps its link");
	}

	@Test
	void anOldSaveWithNoChestLinksTheNearestFreeOne() {
		UUID a = new UUID(0, 1), b = new UUID(0, 2);
		Spot jobA = at(0, 65, 0), jobB = at(8, 65, 0);
		Chest left = single(1, 64, 0), right = single(9, 64, 0);
		Map<UUID, Spot> owned = ChestOwnership.resolve(List.of(
				new ChestOwnership.Held(a, jobA, null),
				new ChestOwnership.Held(b, jobB, null)),
				List.of(left, right));
		assertEquals(left.at(), owned.get(a));
		assertEquals(right.at(), owned.get(b));
	}
}
