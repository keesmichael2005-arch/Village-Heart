package dev.villageheart.core.raid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.room.Pos;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** T-L1-3. A raid latches, and its outcome is the one vanilla actually reached. */
class RaidTrackerTest {
	private static final long NOW = 1_000L;

	private static RaidFacts going(int id, boolean centreInClaim, int raidersInClaim) {
		return new RaidFacts(true, id, false, false, false, false, centreInClaim, raidersInClaim, 4, 0, 5, false, false);
	}

	private static RaidFacts ended(int id, boolean victory, boolean loss, boolean stopped) {
		return new RaidFacts(true, id, true, stopped, victory, loss, true, 0, 0, 3, 5, false, true);
	}

	private RaidTracker.Step step(Optional<RaidRecord> current, RaidFacts facts, AtomicInteger reads) {
		return RaidTracker.step(current, facts, NOW, 3, () -> {
			reads.incrementAndGet();
			return 8;
		});
	}

	@Test void centreInTheClaimStartsAndVillagersAreReadOnce() {
		AtomicInteger reads = new AtomicInteger();
		RaidTracker.Step started = step(Optional.empty(), going(1, true, 0), reads);
		assertTrue(started.started());
		assertTrue(started.ended().isEmpty());
		assertEquals(1, started.next().orElseThrow().vanillaId());
		assertEquals(8, started.next().orElseThrow().villagersAtStart());
		assertEquals(1, reads.get());
		RaidFacts later = new RaidFacts(true, 1, false, false, false, false, true, 0, 2, 1, 5, false, true);
		RaidTracker.Step updated = step(started.next(), later, reads);
		assertFalse(updated.started());
		assertEquals(1, reads.get(), "an update does not read the census again");
		assertEquals(8, updated.next().orElseThrow().villagersAtStart());
		assertEquals(1, updated.next().orElseThrow().wavesSpawned());
		assertEquals(2, updated.next().orElseThrow().raidersAlive());
	}

	@Test void outsideDoesNotStartUnlessARaiderIsInside() {
		AtomicInteger reads = new AtomicInteger();
		RaidTracker.Step out = step(Optional.empty(), going(1, false, 0), reads);
		assertFalse(out.started());
		assertTrue(out.next().isEmpty());
		assertEquals(0, reads.get());
		RaidTracker.Step in = step(Optional.empty(), going(1, false, 1), reads);
		assertTrue(in.started());
		assertEquals(1, reads.get());
	}

	@Test void raidersLeavingDoesNotDropTheLatch() {
		AtomicInteger reads = new AtomicInteger();
		RaidRecord started = step(Optional.empty(), going(7, true, 3), reads).next().orElseThrow();
		RaidFacts left = new RaidFacts(true, 7, false, false, false, false, true, 0, 3, 1, 5, false, true);
		RaidTracker.Step still = step(Optional.of(started), left, reads);
		assertEquals(7, still.next().orElseThrow().vanillaId());
		assertTrue(still.ended().isEmpty());
	}

	@Test void aNearerIdDoesNotReplaceTheTrackedOne() {
		AtomicInteger reads = new AtomicInteger();
		RaidRecord tracked = step(Optional.empty(), going(4, true, 1), reads).next().orElseThrow();
		RaidTracker.Step nearer = step(Optional.of(tracked), going(2, true, 9), reads);
		assertEquals(4, nearer.next().orElseThrow().vanillaId());
		assertTrue(nearer.ended().isEmpty());
		assertEquals(tracked.raidersAlive(), nearer.next().orElseThrow().raidersAlive(), "the other raid's facts are not copied");
	}

	@Test void stopIsStoppedAndVictoryBeatsALaterStop() {
		AtomicInteger reads = new AtomicInteger();
		RaidRecord tracked = step(Optional.empty(), going(1, true, 1), reads).next().orElseThrow();
		RaidTracker.Step stopped = step(Optional.of(tracked), ended(1, false, false, true), reads);
		assertEquals(RaidOutcome.STOPPED, stopped.ended().orElseThrow().outcome());
		assertTrue(stopped.next().isEmpty());
		assertEquals(8, stopped.ended().orElseThrow().villagersAtEnd());

		RaidRecord again = step(Optional.empty(), going(1, true, 1), reads).next().orElseThrow();
		RaidTracker.Step victory = step(Optional.of(again), ended(1, true, false, true), reads);
		assertEquals(RaidOutcome.VICTORY, victory.ended().orElseThrow().outcome());
		assertEquals(3, victory.ended().orElseThrow().wavesSpawned(), "the closing facts supply the wave count");
		RaidTracker.Step laterStop = step(victory.next(), ended(1, false, false, true), reads);
		assertTrue(laterStop.ended().isEmpty(), "a stop after the record closed does not overwrite the victory");
		assertTrue(laterStop.next().isEmpty());
	}

	@Test void lossAndGoneThenANewRaid() {
		AtomicInteger reads = new AtomicInteger();
		RaidRecord tracked = step(Optional.empty(), going(3, true, 1), reads).next().orElseThrow();
		RaidTracker.Step loss = step(Optional.of(tracked), ended(3, false, true, false), reads);
		assertEquals(RaidOutcome.LOSS, loss.ended().orElseThrow().outcome());

		RaidRecord live = step(Optional.empty(), going(3, true, 1), reads).next().orElseThrow();
		RaidTracker.Step gone = step(Optional.of(live), RaidFacts.NONE, reads);
		assertEquals(RaidOutcome.GONE, gone.ended().orElseThrow().outcome());
		assertTrue(gone.next().isEmpty());
		RaidTracker.Step next = step(gone.next(), going(9, true, 0), reads);
		assertTrue(next.started());
		assertEquals(9, next.next().orElseThrow().vanillaId());
	}

	@Test void anOverRaidThatWasNeverTrackedDoesNotStart() {
		AtomicInteger reads = new AtomicInteger();
		RaidTracker.Step over = step(Optional.empty(), ended(5, true, false, false), reads);
		assertFalse(over.started());
		assertTrue(over.next().isEmpty());
		assertTrue(over.ended().isEmpty());
		assertEquals(0, reads.get());
	}

	@Test void deathsAndKillsCountOnlyWhileTracked() {
		AtomicInteger reads = new AtomicInteger();
		assertTrue(RaidTracker.onVillagerDeath(Optional.empty()).isEmpty());
		assertTrue(RaidTracker.onRaiderKilled(Optional.empty(), false).isEmpty());
		RaidRecord tracked = step(Optional.empty(), going(1, true, 1), reads).next().orElseThrow();
		RaidRecord dead = RaidTracker.onVillagerDeath(Optional.of(tracked)).orElseThrow();
		assertEquals(1, dead.villagerDeaths());
		RaidRecord killed = RaidTracker.onRaiderKilled(Optional.of(dead), true).orElseThrow();
		assertEquals(1, killed.raidersKilled());
		assertEquals(1, killed.extraRaidersKilled());
		assertTrue(RaidTracker.onVillagerDeath(Optional.empty()).isEmpty());
	}

	@Test void onAllGuardsOnDutyKeepsTheFirstTime() {
		AtomicInteger reads = new AtomicInteger();
		RaidRecord tracked = step(Optional.empty(), going(1, true, 1), reads).next().orElseThrow();
		assertEquals(-1L, tracked.allGuardsOnDutyAt());
		RaidRecord first = RaidTracker.onAllGuardsOnDuty(tracked, 50L);
		RaidRecord second = RaidTracker.onAllGuardsOnDuty(first, 80L);
		assertEquals(50L, second.allGuardsOnDutyAt());
	}

	@Test void releasingTheClaimDropsTheRecordWithoutASummary() {
		AtomicInteger reads = new AtomicInteger();
		RaidRecord tracked = step(Optional.empty(), going(1, true, 1), reads).next().orElseThrow();
		RaidTracker.Step released = RaidTracker.release(Optional.of(tracked));
		assertTrue(released.next().isEmpty());
		assertTrue(released.ended().isEmpty());
		assertFalse(released.started());
	}

	/** The 80-block edge of the claim (the facts half of T-L1-6b). */
	@Test void claimEdgeCountsARaiderAt79AndNotAt81() {
		HeartLocation heart = new HeartLocation("minecraft:overworld", 0, 64, 0);
		RaidFacts inside = RaidFacts.from(1, false, false, false, false, new Pos(85, 64, 0), List.of(new Pos(79, 64, 0)), heart,
				1, 0, 1, false, false);
		assertEquals(1, inside.raidersInClaim());
		assertFalse(inside.centreInClaim());
		assertTrue(inside.relevant());
		RaidFacts edge = RaidFacts.from(1, false, false, false, false, new Pos(80, 64, 0), List.of(), heart, 0, 0, 1, false, false);
		assertTrue(edge.centreInClaim(), "80 blocks is still inside the sphere");
		RaidFacts outside = RaidFacts.from(1, false, false, false, false, new Pos(85, 64, 0), List.of(new Pos(81, 64, 0)), heart,
				1, 0, 1, false, false);
		assertEquals(0, outside.raidersInClaim());
		assertFalse(outside.relevant());
		assertEquals(6, RaidFacts.wavesTotal(5, 2));
		assertEquals(2, RaidFacts.wavesTotal(2, 1));
	}
}
