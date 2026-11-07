package dev.villageheart.core;

import dev.villageheart.core.ClaimRules.Result;
import dev.villageheart.core.WorldClaim.BreakDecision;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WorldClaimTest {
	static final HeartLocation A = new HeartLocation("minecraft:overworld", 1, 64, 2);
	static final HeartLocation B = new HeartLocation("minecraft:overworld", 50, 64, 60);
	final UUID alice = UUID.randomUUID(), bob = UUID.randomUUID(), carol = UUID.randomUUID();

	WorldClaim claimedByAlice() {
		WorldClaim w = new WorldClaim();
		assertEquals(Result.CLAIMED, w.claim(A, alice, "alice", 3, 100));
		return w;
	}

	@Test void firstClaimStoresVillage() {
		WorldClaim w = claimedByAlice();
		VillageState v = w.village().orElseThrow();
		assertEquals(A, v.heart());
		assertEquals(alice, v.owner());
		assertEquals(100, v.claimedAtGameTime());
	}

	@Test void secondClaimRefusedAndFirstUntouched() {
		WorldClaim w = claimedByAlice();
		VillageState before = w.village().orElseThrow();
		assertEquals(Result.ALREADY_HAVE_VILLAGE, w.claim(B, bob, "bob", 3, 200));
		assertEquals(before, w.village().orElseThrow());
	}

	@Test void sameOwnerSecondClaimAlsoRefused() {
		WorldClaim w = claimedByAlice();
		assertEquals(Result.ALREADY_HAVE_VILLAGE, w.claim(B, alice, "alice", 3, 200));
	}

	@Test void refusedClaimStoresNothing() {
		WorldClaim w = new WorldClaim();
		assertEquals(Result.NO_BELL_NEARBY, w.claim(A, alice, "alice", -1, 1));
		assertTrue(w.village().isEmpty());
	}

	@Test void releaseOnlyAtClaimedLocation() {
		WorldClaim w = claimedByAlice();
		assertTrue(w.releaseAt(B).isEmpty());
		assertTrue(w.village().isPresent());
		assertEquals(A, w.releaseAt(A).orElseThrow().heart());
		assertTrue(w.village().isEmpty());
	}

	@Test void reclaimAfterReleaseIsFresh() {
		WorldClaim w = claimedByAlice();
		w.update(v -> v.depositTreasury(9).withRank(carol, Rank.STEWARD));
		w.releaseAt(A);
		assertEquals(Result.CLAIMED, w.claim(B, bob, "bob", 2, 300));
		VillageState v = w.village().orElseThrow();
		assertEquals(0, v.treasury());
		assertTrue(v.ranks().isEmpty());
		assertEquals(bob, v.owner());
	}

	@Test void breakDecisions() {
		WorldClaim w = claimedByAlice();
		assertEquals(BreakDecision.ALLOWED_UNCLAIMED, w.mayBreakHeart(B, bob));
		assertEquals(BreakDecision.ALLOWED_OWNER, w.mayBreakHeart(A, alice));
		assertEquals(BreakDecision.DENIED_NOT_OWNER, w.mayBreakHeart(A, bob));
		w.update(v -> v.withRank(carol, Rank.STEWARD).withRank(bob, Rank.RESIDENT));
		assertEquals(BreakDecision.DENIED_NOT_OWNER, w.mayBreakHeart(A, carol));
		assertEquals(BreakDecision.DENIED_NOT_OWNER, w.mayBreakHeart(A, bob));
	}

	@Test void dimensionIsPartOfLocation() {
		WorldClaim w = claimedByAlice();
		HeartLocation nether = new HeartLocation("minecraft:the_nether", A.x(), A.y(), A.z());
		assertTrue(w.releaseAt(nether).isEmpty());
		assertEquals(BreakDecision.ALLOWED_UNCLAIMED, w.mayBreakHeart(nether, bob));
	}

	@Test void updateWithoutVillageThrows() {
		assertThrows(IllegalStateException.class, () -> new WorldClaim().update(v -> v));
	}
}
