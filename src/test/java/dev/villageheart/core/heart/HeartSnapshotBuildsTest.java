package dev.villageheart.core.heart;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Rank;
import dev.villageheart.core.Tier;
import dev.villageheart.core.blueprint.BuildStatus;
import dev.villageheart.core.heart.VillageFacts.JobFact;
import dev.villageheart.core.stock.StorageAlert;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** T-L1-12: build rows reach Residents and above only, at most 8, with at most 2 missing lines each. */
class HeartSnapshotBuildsTest {
	private static TextLine line(String s) { return new TextLine("k", s); }

	private static BuildRow row(int i, int missing) {
		List<TextLine> m = new ArrayList<>();
		for (int k = 0; k < missing; k++) m.add(line("Needs " + k));
		return new BuildRow("small_house", i, 64, 0, BuildStatus.BUILDING, i, 172, line("Building: " + i + "%"), m, "x".repeat(40));
	}

	private static VillageFacts facts(List<BuildRow> builds) {
		return new VillageFacts("Owner", Tier.VILLAGE, 0, 1, 0, 0, 0, List.of(), List.of(new JobFact("builder", 1, false)), List.of(),
				new VillageFacts.StockFacts(List.of(), 0, 0, 0, 0, 0, StorageAlert.OK), List.of(), List.of(), builds);
	}

	@Test
	void aVisitorSeesNoBuilds() {
		assertTrue(HeartSnapshotBuilder.build(facts(List.of(row(1, 1))), Rank.VISITOR).builds().isEmpty());
	}

	@Test
	void aResidentAndAboveSeeThem() {
		for (Rank r : new Rank[] {Rank.RESIDENT, Rank.STEWARD, Rank.OWNER}) {
			HeartSnapshot s = HeartSnapshotBuilder.build(facts(List.of(row(1, 1), row(2, 0))), r);
			assertEquals(2, s.builds().size(), r.name());
			assertEquals(1, s.builds().get(0).x());
		}
	}

	@Test
	void capsRowsLinesAndNames() {
		List<BuildRow> many = new ArrayList<>();
		for (int i = 0; i < 12; i++) many.add(row(i, 5));
		HeartSnapshot s = HeartSnapshotBuilder.build(facts(many), Rank.OWNER);
		assertEquals(HeartLimits.MAX_BUILDS, s.builds().size());
		assertEquals(0, s.builds().get(0).x(), "oldest first, the rest is cut");
		for (BuildRow r : s.builds()) {
			assertEquals(HeartLimits.MAX_BUILD_MISSING, r.missing().size());
			assertEquals(HeartLimits.MAX_NAME, r.builderName().length());
		}
	}

	@Test
	void anOldFactsConstructorMeansNoBuilds() {
		VillageFacts old = new VillageFacts("Owner", Tier.HAMLET, 0, 1, 0, 0, 0, List.of(), List.of(), List.of(),
				new VillageFacts.StockFacts(List.of(), 0, 0, 0, 0, 0, StorageAlert.OK), List.of(), List.of());
		assertTrue(old.builds().isEmpty());
		assertTrue(HeartSnapshotBuilder.build(old, Rank.OWNER).builds().isEmpty());
	}
}
