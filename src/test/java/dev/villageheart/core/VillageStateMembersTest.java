package dev.villageheart.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VillageStateMembersTest {
	static final HeartLocation LOC = new HeartLocation("minecraft:overworld", 0, 64, 0);
	static final UUID OWNER = new UUID(1, 1);

	static UUID id(int n) { return new UUID(0, n); }

	static VillageState founded() { return VillageState.founded(LOC, OWNER, "Olga", 0); }

	@Test void seenAddsAndUpdates() {
		VillageState v = founded().seen(id(1), "Rae", 10);
		assertEquals("Rae", v.name(id(1)));
		assertEquals(10, v.knownPlayer(id(1)).orElseThrow().lastSeen());
		v = v.seen(id(1), "Rae2", 50);
		assertEquals("Rae2", v.name(id(1)));
		assertEquals(50, v.knownPlayer(id(1)).orElseThrow().lastSeen());
		assertEquals(1, v.known().size());
		assertEquals("Rae2", v.seen(id(1), "  ", 60).name(id(1)), "a blank name keeps the stored one");
		assertEquals(Rank.VISITOR, v.rankOf(id(1)));
	}

	@Test void ownerSeenUpdatesOwnerNameOnly() {
		VillageState v = founded().seen(OWNER, "Olga2", 10);
		assertEquals("Olga2", v.ownerName());
		assertTrue(v.known().isEmpty());
		assertEquals("Olga2", v.name(OWNER));
	}

	@Test void unknownNameIsEmpty() {
		assertEquals("", founded().name(id(9)));
	}

	@Test void evictionDropsOldestUnrankedNeverARankedOne() {
		VillageState v = founded().withRank(id(1), "Oldest", Rank.RESIDENT, 1);
		for (int i = 2; i <= Balance.MAX_KNOWN_PLAYERS; i++) v = v.seen(id(i), "p" + i, i);
		assertEquals(Balance.MAX_KNOWN_PLAYERS, v.known().size());
		v = v.seen(id(100), "New", 1000);
		assertEquals(Balance.MAX_KNOWN_PLAYERS, v.known().size());
		assertTrue(v.knownPlayer(id(1)).isPresent(), "the oldest entry is ranked, so it stays");
		assertTrue(v.knownPlayer(id(2)).isEmpty(), "the oldest unranked entry goes");
		assertTrue(v.knownPlayer(id(100)).isPresent());
	}

	@Test void fullOfRankedPlusOneVisitorEvictsThatVisitor() {
		VillageState v = founded();
		for (int i = 1; i <= Balance.MAX_KNOWN_PLAYERS - 1; i++) v = v.withRank(id(i), "m" + i, Rank.RESIDENT, i);
		v = v.seen(id(200), "Vis1", 500);
		assertEquals(Balance.MAX_KNOWN_PLAYERS, v.known().size());
		v = v.seen(id(201), "Vis2", 600);
		assertEquals(Balance.MAX_KNOWN_PLAYERS, v.known().size());
		assertTrue(v.knownPlayer(id(200)).isEmpty());
		assertTrue(v.knownPlayer(id(201)).isPresent());
		assertEquals(63, v.ranks().size());
	}

	@Test void demotingToVisitorKeepsTheName() {
		VillageState v = founded().withRank(id(1), "Rae", Rank.STEWARD, 5).withRank(id(1), Rank.VISITOR);
		assertEquals(Rank.VISITOR, v.rankOf(id(1)));
		assertEquals("Rae", v.name(id(1)));
		assertTrue(v.ranks().isEmpty());
	}

	@Test void rankWithoutNameGetsPlaceholder() {
		VillageState v = founded().withRank(id(1), Rank.RESIDENT);
		assertEquals(Rank.RESIDENT, v.rankOf(id(1)));
		assertEquals("", v.name(id(1)));
	}

	@Test void constructorInvariants() {
		Map<UUID, Rank> ranks = Map.of(id(1), Rank.RESIDENT);
		assertThrows(IllegalArgumentException.class, () -> new VillageState(LOC, OWNER, "O", 0, ranks, List.of(), 0, Tier.HAMLET, List.of(), List.of()),
				"ranked without a known entry");
		assertThrows(IllegalArgumentException.class, () -> new VillageState(LOC, OWNER, "O", 0, Map.of(),
				List.of(new KnownPlayer(OWNER, "O", 0)), 0, Tier.HAMLET, List.of(), List.of()), "owner in known");
		assertThrows(IllegalArgumentException.class, () -> new VillageState(LOC, OWNER, "O", 0, Map.of(),
				List.of(new KnownPlayer(id(1), "a", 0), new KnownPlayer(id(1), "b", 1)), 0, Tier.HAMLET, List.of(), List.of()), "duplicates");
		List<KnownPlayer> many = new ArrayList<>();
		for (int i = 1; i <= Balance.MAX_KNOWN_PLAYERS + 1; i++) many.add(new KnownPlayer(id(i), "p", i));
		assertThrows(IllegalArgumentException.class, () -> new VillageState(LOC, OWNER, "O", 0, Map.of(), many, 0, Tier.HAMLET, List.of(), List.of()),
				"more than the cap");
	}

	@Test void equalityIgnoresInputOrder() {
		KnownPlayer a = new KnownPlayer(id(1), "a", 5), b = new KnownPlayer(id(2), "b", 3), c = new KnownPlayer(id(3), "c", 5);
		VillageState x = new VillageState(LOC, OWNER, "O", 0, Map.of(), List.of(a, b, c), 0, Tier.HAMLET, List.of(), List.of());
		VillageState y = new VillageState(LOC, OWNER, "O", 0, Map.of(), List.of(c, a, b), 0, Tier.HAMLET, List.of(), List.of());
		assertEquals(x, y);
	}

	@Test void namesAreCleaned() {
		KnownPlayer k = new KnownPlayer(id(1), "§aGreen" + "x".repeat(60), 0);
		assertEquals(Balance.MAX_PLAYER_NAME, k.name().length());
		assertFalse(k.name().contains("§"));
	}

	@Test void transferToResident() {
		VillageState v = founded().depositTreasury(7).withBuilding(new BuildingRecord("storage", LOC))
				.withRank(id(1), "Rae", Rank.RESIDENT, 5);
		VillageState t = v.transferOwnership(id(1), 99);
		assertEquals(id(1), t.owner());
		assertEquals("Rae", t.ownerName());
		assertEquals(Rank.STEWARD, t.rankOf(OWNER));
		assertEquals("Olga", t.name(OWNER));
		assertFalse(t.ranks().containsKey(id(1)));
		assertTrue(t.knownPlayer(id(1)).isEmpty());
		assertEquals(v.treasury(), t.treasury());
		assertEquals(v.buildings(), t.buildings());
		assertEquals(v.workers(), t.workers());
		assertEquals(v.claimedAtGameTime(), t.claimedAtGameTime());
		assertEquals(v.heart(), t.heart());
		assertEquals(v.tier(), t.tier());
	}

	@Test void transferToSteward() {
		VillageState t = founded().withRank(id(1), "Rae", Rank.STEWARD, 5).transferOwnership(id(1), 9);
		assertEquals(id(1), t.owner());
		assertEquals(Rank.STEWARD, t.rankOf(OWNER));
	}

	@Test void transferRefusals() {
		VillageState v = founded().seen(id(1), "Vis", 5);
		assertThrows(IllegalArgumentException.class, () -> v.transferOwnership(id(1), 9));
		assertThrows(IllegalArgumentException.class, () -> v.transferOwnership(OWNER, 9));
		assertThrows(IllegalArgumentException.class, () -> v.transferOwnership(id(77), 9));
	}

	@Test void canTransferBackAndForth() {
		VillageState v = founded().withRank(id(1), "Rae", Rank.RESIDENT, 5);
		VillageState back = v.transferOwnership(id(1), 9).transferOwnership(OWNER, 10);
		assertEquals(OWNER, back.owner());
		assertEquals(Rank.STEWARD, back.rankOf(id(1)));
		assertEquals("Rae", back.name(id(1)));
	}
}
