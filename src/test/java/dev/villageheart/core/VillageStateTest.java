package dev.villageheart.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class VillageStateTest {
	static final HeartLocation LOC = new HeartLocation("minecraft:overworld", 1, 64, 2);
	final UUID owner = UUID.randomUUID();

	VillageState founded() { return VillageState.founded(LOC, owner, "alice", 10); }

	@Test void foundedDefaults() {
		VillageState v = founded();
		assertEquals(Tier.HAMLET, v.tier());
		assertEquals(0, v.treasury());
		assertTrue(v.ranks().isEmpty());
		assertTrue(v.buildings().isEmpty());
		assertTrue(v.workers().isEmpty());
		assertEquals(Rank.OWNER, v.rankOf(owner));
		assertEquals(Rank.VISITOR, v.rankOf(UUID.randomUUID()));
	}

	@Test void withRankSetsAndVisitorRemoves() {
		UUID p = UUID.randomUUID();
		VillageState v = founded();
		VillageState s = v.withRank(p, Rank.STEWARD);
		assertEquals(Rank.STEWARD, s.rankOf(p));
		VillageState r = s.withRank(p, Rank.RESIDENT);
		assertEquals(Rank.RESIDENT, r.rankOf(p));
		VillageState gone = r.withRank(p, Rank.VISITOR);
		assertTrue(gone.ranks().isEmpty());
		assertTrue(v.ranks().isEmpty());
		assertEquals(Rank.STEWARD, s.rankOf(p));
	}

	@Test void cannotRankOwnerOrGrantOwner() {
		assertThrows(IllegalArgumentException.class, () -> founded().withRank(owner, Rank.STEWARD));
		assertThrows(IllegalArgumentException.class, () -> founded().withRank(UUID.randomUUID(), Rank.OWNER));
	}

	@Test void treasuryRules() {
		assertThrows(IllegalArgumentException.class, () -> new VillageState(LOC, owner, "a", 0, Map.of(), -1,
				Tier.HAMLET, List.of(), List.of()));
		assertThrows(IllegalArgumentException.class, () -> founded().depositTreasury(-1));
		VillageState max = founded().depositTreasury(Integer.MAX_VALUE);
		assertThrows(ArithmeticException.class, () -> max.depositTreasury(1));
		assertEquals(10, founded().depositTreasury(5).depositTreasury(5).treasury());
	}

	@Test void defensiveCopies() {
		Map<UUID, Rank> ranks = new HashMap<>();
		List<BuildingRecord> buildings = new ArrayList<>();
		List<WorkerRecord> workers = new ArrayList<>();
		VillageState v = new VillageState(LOC, owner, "a", 0, ranks, 0, Tier.HAMLET, buildings, workers);
		ranks.put(UUID.randomUUID(), Rank.STEWARD);
		buildings.add(new BuildingRecord("farm", LOC));
		workers.add(new WorkerRecord(UUID.randomUUID(), "farmer"));
		assertTrue(v.ranks().isEmpty());
		assertTrue(v.buildings().isEmpty());
		assertTrue(v.workers().isEmpty());
		assertThrows(UnsupportedOperationException.class, () -> v.ranks().put(UUID.randomUUID(), Rank.STEWARD));
	}
}
