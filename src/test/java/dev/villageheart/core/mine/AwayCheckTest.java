package dev.villageheart.core.mine;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Balance;
import dev.villageheart.core.mine.AwayCheck.Inputs;
import dev.villageheart.core.mine.AwayCheck.Lease;
import dev.villageheart.core.mine.AwayCheck.Outcome;
import org.junit.jupiter.api.Test;

class AwayCheckTest {
	private static final AwayProgress SOME = new AwayProgress(700);

	private static Inputs ok() { return new Inputs(true, true, true, true, Lease.HELD, 1.25, true, SOME, 0); }

	private static Inputs with(boolean miner, boolean work, boolean site, boolean slot, Lease lease, double mult, boolean depth, int unknown) {
		return new Inputs(miner, work, site, slot, lease, mult, depth, SOME, unknown);
	}

	private static void assertSurface(SurfaceReason reason, Outcome o) {
		assertEquals(reason, o.surface().orElse(null), "reason");
		assertEquals(reason == SurfaceReason.HAUL, o.haul());
	}

	@Test
	void aHealthyStepStaysDownAndAddsProgress() {
		Outcome o = AwayCheck.check(ok());
		assertTrue(o.stays());
		assertEquals(725.0, o.progress().units(), 1e-9);
	}

	@Test
	void eachCheckAlone() {
		assertSurface(SurfaceReason.NOT_A_MINER, AwayCheck.check(with(false, true, true, true, Lease.HELD, 1.25, true, 0)));
		assertSurface(SurfaceReason.DAY_END, AwayCheck.check(with(true, false, true, true, Lease.HELD, 1.25, true, 0)));
		assertSurface(SurfaceReason.SITE_INVALID, AwayCheck.check(with(true, true, false, true, Lease.HELD, 1.25, true, 0)));
		assertSurface(SurfaceReason.SITE_INVALID, AwayCheck.check(with(true, true, true, false, Lease.HELD, 1.25, true, 0)));
		assertSurface(SurfaceReason.NO_TOOL, AwayCheck.check(with(true, true, true, true, Lease.NONE, 1.25, true, 0)));
		assertSurface(SurfaceReason.STOPPED_WORKING, AwayCheck.check(with(true, true, true, true, Lease.HELD, 0, true, 0)));
	}

	@Test
	void precedence() {
		// not a miner beats day end beats site invalid beats no tool beats strike
		assertSurface(SurfaceReason.NOT_A_MINER, AwayCheck.check(with(false, false, false, false, Lease.NONE, 0, false, 0)));
		assertSurface(SurfaceReason.DAY_END, AwayCheck.check(with(true, false, false, false, Lease.NONE, 0, false, 0)));
		assertSurface(SurfaceReason.SITE_INVALID, AwayCheck.check(with(true, true, false, true, Lease.NONE, 0, true, 0)));
		assertSurface(SurfaceReason.NO_TOOL, AwayCheck.check(with(true, true, true, true, Lease.NONE, 0, true, 0)));
	}

	@Test
	void siteInvalidResetsProgressTheOthersKeepIt() {
		assertEquals(0.0, AwayCheck.check(with(true, true, false, true, Lease.HELD, 1.25, true, 0)).progress().units());
		assertEquals(700.0, AwayCheck.check(with(true, false, true, true, Lease.HELD, 1.25, true, 0)).progress().units());
		assertEquals(700.0, AwayCheck.check(with(true, true, true, true, Lease.NONE, 1.25, true, 0)).progress().units());
		assertEquals(700.0, AwayCheck.check(with(true, true, true, true, Lease.HELD, 0, true, 0)).progress().units());
	}

	@Test
	void anUnreadableChestMakesNoProgressAndTheSixtiethStepSurfaces() {
		Outcome o = AwayCheck.check(with(true, true, true, true, Lease.UNKNOWN, 1.25, true, 0));
		assertTrue(o.stays());
		assertEquals(700.0, o.progress().units());
		assertEquals(1, o.unknownSteps());
		assertTrue(AwayCheck.check(with(true, true, true, true, Lease.UNKNOWN, 1.25, true, Balance.MINER_UNKNOWN_LIMIT - 2)).stays());
		Outcome last = AwayCheck.check(with(true, true, true, true, Lease.UNKNOWN, 1.25, true, Balance.MINER_UNKNOWN_LIMIT - 1));
		assertSurface(SurfaceReason.STOPPED_WORKING, last);
		assertEquals(700.0, last.progress().units());
	}

	@Test
	void aKnownLeaseResetsTheUnknownCount() {
		assertEquals(0, AwayCheck.check(with(true, true, true, true, Lease.HELD, 1.25, true, 40)).unknownSteps());
	}

	@Test
	void aDueHaulIsTakenWhenTheDepthIsKnown() {
		Inputs due = new Inputs(true, true, true, true, Lease.HELD, 2.0, true, new AwayProgress(3960), 0);
		Outcome o = AwayCheck.check(due);
		assertSurface(SurfaceReason.HAUL, o);
		assertEquals(0.0, o.progress().units(), 1e-9);
	}

	@Test
	void aDueHaulWaitsForTheDepthAndGivesUpAfterSixtySteps() {
		Inputs due = new Inputs(true, true, true, true, Lease.HELD, 2.0, false, new AwayProgress(3960), 0);
		Outcome o = AwayCheck.check(due);
		assertTrue(o.stays());
		assertEquals(Balance.MINER_HAUL_UNITS, o.progress().units());
		assertEquals(1, o.unknownSteps());
		Inputs late = new Inputs(true, true, true, true, Lease.HELD, 1.25, false, new AwayProgress(Balance.MINER_HAUL_UNITS), Balance.MINER_UNKNOWN_LIMIT - 1);
		Outcome up = AwayCheck.check(late);
		assertSurface(SurfaceReason.STOPPED_WORKING, up);
		assertEquals(Balance.MINER_HAUL_UNITS, up.progress().units());
	}

	@Test
	void theHunterNumbersMakeATripDueAt3000AndTheMinersAt4000() {
		double u = Balance.HUNTER_TRIP_UNITS;
		int step = Balance.HUNTER_STEP_TICKS, lim = Balance.HUNTER_UNKNOWN_LIMIT;
		Inputs at3980 = new Inputs(true, true, true, true, Lease.HELD, 1.0, true, new AwayProgress(3980), 0);
		assertSurface(SurfaceReason.HAUL, AwayCheck.check(at3980)); // the miner's number (4000): due
		Inputs at2980 = new Inputs(true, true, true, true, Lease.HELD, 1.0, true, new AwayProgress(2980), 0);
		assertTrue(AwayCheck.check(at2980).stays()); // the miner's: not yet
		assertSurface(SurfaceReason.HAUL, AwayCheck.check(at2980, u, step, lim)); // the hunter's 3000: due
		Inputs at3000 = new Inputs(true, true, true, true, Lease.HELD, 1.0, true, new AwayProgress(2980), 0);
		assertSurface(SurfaceReason.HAUL, AwayCheck.check(at3000, u, step, lim));
		assertEquals(0.0, AwayCheck.check(at3000, u, step, lim).progress().units(), 1e-9);
		// unknown limit is the caller's
		assertTrue(AwayCheck.check(with(true, true, true, true, Lease.UNKNOWN, 1.0, true, 58), u, step, 60).stays());
		assertSurface(SurfaceReason.STOPPED_WORKING, AwayCheck.check(with(true, true, true, true, Lease.UNKNOWN, 1.0, true, 58), u, step, 59));
	}

	@Test
	void anUnknownDepthWithNoHaulDueStillMakesProgress() {
		Outcome o = AwayCheck.check(with(true, true, true, true, Lease.HELD, 1.25, false, 0));
		assertTrue(o.stays());
		assertEquals(725.0, o.progress().units(), 1e-9);
		assertEquals(0, o.unknownSteps());
	}
}
