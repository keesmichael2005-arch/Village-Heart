package dev.villageheart.core.heart;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Rank;
import dev.villageheart.core.heart.VillageFacts.VillagerFact;
import dev.villageheart.core.stock.StorageAlert;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** M12 T-L1-12: the Overview shows the wage bill, the strike and the short treasury to every rank, and the cap never hides the strike. */
class TreasurySnapshotTest {
	private static VillageFacts facts(int treasury, int bill, int strikers, List<AlertLine> extra, StorageAlert storage, List<VillagerFact> villagers) {
		VillageFacts f = HeartSnapshotBuilderTest.facts(villagers, List.of(), HeartSnapshotBuilderTest.stock(storage, 0), 1, 0, extra);
		return new VillageFacts(f.ownerName(), f.tier(), treasury, f.adults(), f.babies(), f.beds(), f.workerSlots(), f.villagers(), f.jobs(),
				f.buildings(), f.stock(), f.extraAlerts(), f.members(), f.builds(), bill, strikers);
	}

	@Test void theWageBillIsTheFactsBillNotMinusOne() {
		Overview o = HeartSnapshotBuilder.build(facts(5, 4, 0, List.of(), StorageAlert.OK, List.of()), Rank.OWNER).overview();
		assertEquals(4, o.wageBill());
		assertEquals(5, o.treasury());
	}

	@Test void everyRankSeesTreasuryBillAndTheStrike() {
		VillageFacts f = facts(0, 3, 1, List.of(), StorageAlert.OK, List.of());
		for (Rank r : Rank.values()) {
			Overview o = HeartSnapshotBuilder.build(f, r).overview();
			assertEquals(3, o.wageBill(), r.name());
			assertEquals(List.of(AlertCode.STRIKE, AlertCode.TREASURY_SHORT), o.alerts().stream().map(AlertLine::code).toList(), r.name());
		}
	}

	@Test void aStrikerRowShowsOnStrikeAndUnpaid() {
		VillagerFact striker = new VillagerFact(HeartSnapshotBuilderTest.id(1), "A", 0, "farmer", "x", 1, 0,
				NeedsFlags.of(true, true, false), ActivityKind.ON_STRIKE, false, false, false, true);
		VillagerRow row = HeartSnapshotBuilder.build(facts(0, 1, 1, List.of(), StorageAlert.OK, List.of(striker)), Rank.OWNER).villagers().get(0);
		assertEquals(ActivityKind.ON_STRIKE, row.activity());
		assertFalse(row.needs().paid());
	}

	@Test void severityFirstSoTheCapNeverCutsTheStrike() {
		List<AlertLine> warnings = new ArrayList<>();
		for (int i = 0; i < 4; i++) warnings.add(new AlertLine(AlertCode.INVALID_BUILDINGS, Severity.WARNING, new TextLine("w" + i, "w")));
		// NO_STORAGE (warning) + 4 extra warnings + TREASURY_SHORT (warning) + STRIKE (danger) = 7 alerts
		Overview o = HeartSnapshotBuilder.build(facts(0, 2, 1, warnings, StorageAlert.NO_STORAGE, List.of()), Rank.VISITOR).overview();
		assertEquals(5, o.alerts().size());
		assertEquals(2, o.moreAlerts());
		assertEquals(AlertCode.STRIKE, o.alerts().get(0).code(), "the strike is line 1");
		List<String> keys = o.alerts().stream().skip(1).map(a -> a.text().key()).toList();
		assertEquals(List.of("gui.villageheart.heart.alert.no_storage", "gui.villageheart.heart.alert.treasury_short", "w0", "w1"), keys,
				"the warnings keep their relative order: built in, wage, then later milestones'");
	}

	@Test void aWorkplaceDangerAndAStrikeBothLeadTheWarnings() {
		VillagerFact bad = new VillagerFact(HeartSnapshotBuilderTest.id(1), "A", 0, "farmer", "x", 1, 0,
				NeedsFlags.of(true, true, true), ActivityKind.WORKING, true, false, false, true);
		Overview o = HeartSnapshotBuilder.build(facts(0, 2, 1, List.of(), StorageAlert.NO_STORAGE, List.of(bad)), Rank.OWNER).overview();
		assertEquals(List.of(AlertCode.WORKPLACE_INVALID, AlertCode.STRIKE, AlertCode.NO_STORAGE, AlertCode.TREASURY_SHORT),
				o.alerts().stream().map(AlertLine::code).toList());
	}
}
