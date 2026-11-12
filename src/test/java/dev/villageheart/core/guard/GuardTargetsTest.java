package dev.villageheart.core.guard;

import static dev.villageheart.core.guard.GuardTargets.Kind.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.guard.GuardTargets.Candidate;
import dev.villageheart.core.guard.GuardTargets.Mode;
import java.util.List;
import org.junit.jupiter.api.Test;

class GuardTargetsTest {
	private static Candidate c(int id, GuardTargets.Kind kind, double dist, boolean inClaim, boolean visible, boolean targetsVillager, boolean hurtMe) {
		return new Candidate(id, kind, dist * dist, inClaim, visible, targetsVillager, hurtMe, false);
	}

	private static Candidate zombie(int id, double dist) { return c(id, HOSTILE, dist, true, true, false, false); }

	@Test
	void creepersAndFlyersNever() {
		assertTrue(GuardTargets.choose(List.of(c(1, CREEPER, 3, true, true, true, true)), Mode.DUTY).isEmpty());
		assertTrue(GuardTargets.choose(List.of(c(1, UNREACHABLE, 3, true, true, true, true)), Mode.OFF).isEmpty());
	}

	@Test
	void neutralOnlyWhenItTargetsAVillagerOrHurtMe() {
		assertTrue(GuardTargets.choose(List.of(c(1, NEUTRAL, 3, true, true, false, false)), Mode.DUTY).isEmpty());
		assertTrue(GuardTargets.choose(List.of(c(1, NEUTRAL, 3, true, true, true, false)), Mode.DUTY).isPresent());
		assertTrue(GuardTargets.choose(List.of(c(1, NEUTRAL, 3, true, true, false, true)), Mode.DUTY).isPresent());
	}

	@Test
	void dutyIgnoresOutOfClaimUnlessItHurtMe() {
		assertTrue(GuardTargets.choose(List.of(c(1, HOSTILE, 5, false, true, false, false)), Mode.DUTY).isEmpty());
		assertTrue(GuardTargets.choose(List.of(c(1, HOSTILE, 5, false, true, false, true)), Mode.DUTY).isPresent());
	}

	@Test
	void offDutyOnlyHurtMeOrNearbyHostileTargetingAVillager() {
		assertTrue(GuardTargets.choose(List.of(zombie(1, 3)), Mode.OFF).isEmpty(), "a zombie that targets nobody is left alone off shift");
		assertTrue(GuardTargets.choose(List.of(c(1, HOSTILE, 3, true, true, true, false)), Mode.OFF).isPresent());
		assertTrue(GuardTargets.choose(List.of(c(1, HOSTILE, 9, true, true, true, false)), Mode.OFF).isEmpty(), "beyond 8 blocks");
		assertTrue(GuardTargets.choose(List.of(c(1, HOSTILE, 20, true, true, false, true)), Mode.OFF).isPresent());
		assertTrue(GuardTargets.choose(List.of(c(1, RAIDER, 4, true, true, true, false)), Mode.OFF).isPresent());
	}

	@Test
	void invisibleIgnoredUnlessItHurtMe() {
		assertTrue(GuardTargets.choose(List.of(c(1, HOSTILE, 3, true, false, false, false)), Mode.DUTY).isEmpty());
		assertTrue(GuardTargets.choose(List.of(c(1, HOSTILE, 3, true, false, false, true)), Mode.DUTY).isPresent());
	}

	@Test
	void dutyChasesATrackedRaiderPastTheClaim() {
		Candidate outside = new Candidate(1, RAIDER, 100 * 100, false, false, false, false, true);
		assertTrue(GuardTargets.choose(List.of(outside), Mode.DUTY).isPresent(), "a tracked raider past the edge is a target without a sight line");
		assertTrue(GuardTargets.choose(List.of(c(1, RAIDER, 10, false, false, false, false)), Mode.DUTY).isEmpty());
		assertTrue(GuardTargets.choose(List.of(new Candidate(1, HOSTILE, 9 * 9, false, false, false, false, true)), Mode.DUTY).isEmpty(),
				"the chase flag does not pull a zombie out of the claim");
	}

	@Test
	void priority() {
		Candidate near = zombie(5, 2), far = zombie(6, 10), threat = c(7, HOSTILE, 12, true, true, true, false), attacker = c(8, HOSTILE, 20, true, true, false, true);
		assertEquals(5, GuardTargets.choose(List.of(far, near), Mode.DUTY).orElseThrow().id());
		assertEquals(7, GuardTargets.choose(List.of(near, threat), Mode.DUTY).orElseThrow().id(), "targets a villager beats nearest");
		assertEquals(8, GuardTargets.choose(List.of(near, threat, attacker), Mode.DUTY).orElseThrow().id(), "hurt me beats all");
		assertEquals(3, GuardTargets.choose(List.of(zombie(4, 2), zombie(3, 2)), Mode.DUTY).orElseThrow().id(), "lower id at equal distance");
	}
}
