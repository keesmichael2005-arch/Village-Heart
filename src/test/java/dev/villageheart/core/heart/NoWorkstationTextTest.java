package dev.villageheart.core.heart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.Rank;
import dev.villageheart.core.Tier;
import dev.villageheart.core.WorkerRecord;
import dev.villageheart.core.economy.PayRun;
import dev.villageheart.core.economy.Payee;
import dev.villageheart.core.economy.WageRoster;
import dev.villageheart.core.heart.VillageFacts.VillagerFact;
import dev.villageheart.core.stock.StorageAlert;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Paid and no workplace is "No workstation". Unpaid is "On strike", and that wins. Payroll ignores the workplace. */
class NoWorkstationTextTest {
	static UUID id(int n) { return new UUID(0, n); }

	static VillagerFact paidNoWorkplace(int n) {
		return new VillagerFact(id(n), "Ada", 0, "farmer", "villageheart:farmer", 2, 0, NeedsFlags.of(true, true, true),
				ActivityKind.WORKING, true, false, false, true);
	}

	static VillagerFact strikerNoWorkplace() {
		return new VillagerFact(id(2), "Cy", 1, "farmer", "villageheart:farmer", 1, 0, NeedsFlags.of(true, true, false),
				ActivityKind.ON_STRIKE, true, false, false, true);
	}

	private static VillageFacts facts(List<VillagerFact> villagers, int strikers) {
		return new VillageFacts("Owner", Tier.HAMLET, 7, villagers.size(), 0, 1, 1, villagers,
				List.of(new VillageFacts.JobFact("farmer", 0, false)), List.of(),
				new VillageFacts.StockFacts(List.of(), 1, 0, 1, 54, 40, StorageAlert.OK),
				List.of(), List.of(), List.of(), 1, strikers);
	}

	@Test void paidAndNoWorkplaceIsTheWorkstationLine() {
		assertEquals("No workstation", WorkstationCopy.rowEnglish(false, true));
		assertTrue(WorkstationCopy.countsOnOverview(ActivityKind.WORKING, true, NeedsFlags.of(true, true, true)));
		Overview one = HeartSnapshotBuilder.build(facts(List.of(paidNoWorkplace(1)), 0), Rank.OWNER).overview();
		assertEquals("1 worker has no workstation", one.alerts().get(0).text().english());
		assertEquals(AlertCode.WORKPLACE_INVALID, one.alerts().get(0).code());
		Overview two = HeartSnapshotBuilder.build(facts(List.of(paidNoWorkplace(1), paidNoWorkplace(3)), 0), Rank.OWNER).overview();
		assertEquals("2 workers have no workstation", two.alerts().get(0).text().english());
	}

	@Test void unpaidIsOnStrikeEvenWhenTheWorkplaceIsMissing() {
		assertEquals("On strike", WorkstationCopy.rowEnglish(true, true));
		assertFalse(WorkstationCopy.countsOnOverview(ActivityKind.ON_STRIKE, true, NeedsFlags.of(true, true, false)));
		Overview o = HeartSnapshotBuilder.build(facts(List.of(strikerNoWorkplace()), 1), Rank.OWNER).overview();
		assertEquals(List.of(AlertCode.STRIKE), o.alerts().stream().map(AlertLine::code).toList());
		assertEquals("1 worker is on strike: deposit emeralds at the Heart", o.alerts().get(0).text().english());
	}

	@Test void payrollDoesNotLookAtTheWorkplace() {
		HeartLocation gone = new HeartLocation("minecraft:overworld", 9, 9, 9);
		HeartLocation held = new HeartLocation("minecraft:overworld", 0, 64, 0);
		WorkerRecord missing = new WorkerRecord(id(1), "farmer", gone, new HeartLocation("minecraft:overworld", 1, 65, 1), 3);
		WorkerRecord working = new WorkerRecord(id(2), "farmer", held, new HeartLocation("minecraft:overworld", 2, 65, 1), 1);
		List<Payee> due = WageRoster.of(List.of(missing, working));
		assertEquals(List.of(new Payee(id(1), 2), new Payee(id(2), 1)), due, "a missing workplace stays on the payroll at its level");
		assertEquals(List.of(id(1), id(2)), PayRun.run(1, 5, due).paid());
	}
}
