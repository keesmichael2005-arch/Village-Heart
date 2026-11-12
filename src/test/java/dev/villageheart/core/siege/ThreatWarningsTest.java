package dev.villageheart.core.siege;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.villageheart.core.Balance;
import dev.villageheart.core.siege.ThreatWarnings.Kind;
import org.junit.jupiter.api.Test;

/** The lead-up schedule (owner, 2026-11-11): lines spread over the hours before a raid or siege, each sent once. */
class ThreatWarningsTest {
	private static final int LEAD = Balance.WARNING_LEAD_TICKS;

	@Test
	void raidHasThreeLinesAndSiegeTwo() {
		assertEquals(3, Kind.RAID.lines().size());
		assertEquals(2, Kind.SIEGE.lines().size());
		assertEquals("A raid is starting!", Kind.RAID.title().orElseThrow().text());
	}

	/** Owner, 2026-11-12: lead-up lines are gold chat, a raid start keeps its title, a siege start has none, both starts are a red chat line. */
	@Test
	void warningsAreGoldAndStartsAreRedWithTheTitleOnlyForARaid() {
		for (Kind kind : Kind.values()) {
			assertEquals(ThreatWarnings.Tone.WARNING, kind.warningTone(), kind + " lead-up lines are gold");
			assertEquals(ThreatWarnings.Tone.ALERT, kind.startTone(), kind + " start line is bold red");
			assertTrue(kind.start().key().startsWith("message.villageheart.start."), kind.start().key());
			assertTrue(!kind.start().text().isBlank());
		}
		assertTrue(Kind.RAID.title().isPresent(), "a raid start keeps the big title");
		assertTrue(Kind.SIEGE.title().isEmpty(), "a siege start shows no title");
		assertEquals("The dead are coming!", Kind.SIEGE.start().text());
		assertTrue(!Kind.RAID.start().equals(Kind.SIEGE.start()));
	}

	@Test
	void siegeWindowIsTheWholeNightBeforeTheSpawn() {
		// rolled at 13000, spawns at 18000: the 5000-tick gap is shorter than the 6000-tick lead, so it opens at the roll
		assertEquals(13000, ThreatWarnings.windowStart(Balance.SIEGE_ROLL_TICK, 18000, LEAD));
		assertEquals(13000, ThreatWarnings.lineTick(Kind.SIEGE, 13000, 18000, LEAD, 0));
		assertEquals(15500, ThreatWarnings.lineTick(Kind.SIEGE, 13000, 18000, LEAD, 1));
	}

	@Test
	void raidWindowOpensAtTheLeadWhenThereIsRoom() {
		// start 11000, roll 1000: the lead (6000) is shorter than the room, so it opens at 5000
		assertEquals(5000, ThreatWarnings.windowStart(1000, 11000, LEAD));
		assertEquals(5000, ThreatWarnings.lineTick(Kind.RAID, 1000, 11000, LEAD, 0));
		assertEquals(7000, ThreatWarnings.lineTick(Kind.RAID, 1000, 11000, LEAD, 1));
		assertEquals(9000, ThreatWarnings.lineTick(Kind.RAID, 1000, 11000, LEAD, 2));
	}

	@Test
	void raidWindowNeverOpensBeforeTheRoll() {
		assertEquals(1000, ThreatWarnings.windowStart(1000, 2000, LEAD));
		assertEquals(1000, ThreatWarnings.lineTick(Kind.RAID, 1000, 2000, LEAD, 0));
		assertEquals(1333, ThreatWarnings.lineTick(Kind.RAID, 1000, 2000, LEAD, 1));
		assertEquals(1666, ThreatWarnings.lineTick(Kind.RAID, 1000, 2000, LEAD, 2));
	}

	@Test
	void everyLineIsBeforeTheStartAndInOrder() {
		for (Kind kind : Kind.values()) {
			for (int start = 2000; start <= 18000; start += 500) {
				int roll = kind == Kind.RAID ? 1000 : 13000;
				if (start <= roll) continue;
				int last = -1;
				for (int i = 0; i < kind.lines().size(); i++) {
					int t = ThreatWarnings.lineTick(kind, roll, start, LEAD, i);
					assertTrue(t >= roll && t < start, kind + " start " + start + " line " + i + " at " + t);
					assertTrue(t >= last, "in order");
					last = t;
				}
			}
		}
	}

	@Test
	void nextSendsEachLineOnceAsTheClockPasses() {
		int sent = 0;
		int[] seen = new int[3];
		int count = 0;
		for (int t = 0; t < 11000; t++) {
			int i = ThreatWarnings.next(Kind.RAID, 1000, 11000, LEAD, t, sent);
			if (i < 0) continue;
			seen[count++] = i;
			sent = i + 1;
		}
		assertEquals(3, count);
		assertEquals(0, seen[0]);
		assertEquals(1, seen[1]);
		assertEquals(2, seen[2]);
	}

	@Test
	void nothingIsDueOnceTheStartHasCome() {
		assertEquals(0, ThreatWarnings.due(Kind.RAID, 1000, 5000, LEAD, 5000));
		assertEquals(-1, ThreatWarnings.next(Kind.RAID, 1000, 5000, LEAD, 9000, 0));
	}

	@Test
	void aClockJumpSendsOnlyTheLatestDueLine() {
		// slept from the roll to just before the start: lines 0..2 are all due, only the last is sent
		assertEquals(2, ThreatWarnings.next(Kind.RAID, 1000, 11000, LEAD, 10900, 0));
		assertEquals(-1, ThreatWarnings.next(Kind.RAID, 1000, 11000, LEAD, 10900, 3));
	}

	@Test
	void beforeTheWindowNothingIsDue() {
		assertEquals(-1, ThreatWarnings.next(Kind.RAID, 1000, 11000, LEAD, 4999, 0));
		assertEquals(-1, ThreatWarnings.next(Kind.SIEGE, 13000, 18000, LEAD, 12999, 0));
	}

	@Test
	void aZeroLeadOpensAtTheStart() {
		assertEquals(0, ThreatWarnings.due(Kind.RAID, 1000, 5000, 0, 4999));
	}
}
