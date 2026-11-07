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
		workers.add(new WorkerRecord(UUID.randomUUID(), "farmer", LOC, LOC));
		assertTrue(v.ranks().isEmpty());
		assertTrue(v.buildings().isEmpty());
		assertTrue(v.workers().isEmpty());
		assertThrows(UnsupportedOperationException.class, () -> v.ranks().put(UUID.randomUUID(), Rank.STEWARD));
	}

	static HeartLocation at(int x, int y, int z) { return new HeartLocation("minecraft:overworld", x, y, z); }

	@Test void buildingRegistryOperations() {
		VillageState v = founded();
		BuildingRecord a = new BuildingRecord("storage", at(5, 64, 5));
		BuildingRecord b = new BuildingRecord("house", at(1, 70, 0));
		BuildingRecord c = new BuildingRecord("mine", at(1, 64, 9));
		VillageState s = v.withBuilding(a).withBuilding(b).withBuilding(c);
		assertEquals(List.of(c.marker(), b.marker(), a.marker()), s.buildings().stream().map(BuildingRecord::marker).toList(),
				"sorted by (dimension, x, y, z)");
		VillageState replaced = s.withBuilding(a.withStatus(dev.villageheart.core.building.BuildingStatus.VALID));
		assertEquals(3, replaced.buildings().size());
		assertEquals(dev.villageheart.core.building.BuildingStatus.VALID, replaced.building(a.marker()).orElseThrow().status());
		assertEquals(2, s.withoutBuilding(a.marker()).buildings().size());
		assertSame(s, s.withoutBuilding(at(99, 99, 99)));
		assertTrue(s.building(at(99, 99, 99)).isEmpty());
	}

	@Test void duplicateMarkersThrow() {
		assertThrows(IllegalArgumentException.class, () -> new VillageState(LOC, owner, "a", 0, Map.of(), 0, Tier.HAMLET,
				List.of(new BuildingRecord("storage", LOC), new BuildingRecord("house", LOC)), List.of()));
	}

	@Test void withTierChangesOnlyTier() {
		VillageState v = founded().withBuilding(new BuildingRecord("storage", LOC)).depositTreasury(5);
		VillageState t = v.withTier(Tier.TOWN);
		assertEquals(Tier.TOWN, t.tier());
		assertEquals(v, t.withTier(Tier.HAMLET));
	}

	@Test void twoArgRecordIsUncheckedWithoutLink() {
		BuildingRecord r = new BuildingRecord("storage", LOC);
		assertEquals(dev.villageheart.core.building.BuildingStatus.UNCHECKED, r.status());
		assertNull(r.link());
		assertEquals(dev.villageheart.core.building.BuildingType.STORAGE, r.type().orElseThrow());
		assertEquals(LOC, r.withLink(LOC).link());
	}
}
