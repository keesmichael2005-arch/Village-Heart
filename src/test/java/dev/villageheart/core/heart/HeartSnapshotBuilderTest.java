package dev.villageheart.core.heart;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Rank;
import dev.villageheart.core.Tier;
import dev.villageheart.core.heart.VillageFacts.BuildingFact;
import dev.villageheart.core.heart.VillageFacts.JobFact;
import dev.villageheart.core.heart.VillageFacts.StockFacts;
import dev.villageheart.core.heart.VillageFacts.StockItemFact;
import dev.villageheart.core.heart.VillageFacts.VillagerFact;
import dev.villageheart.core.stock.StorageAlert;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HeartSnapshotBuilderTest {
	static UUID id(int n) { return new UUID(0, n); }

	static VillagerFact worker(int n, String name, int hiring) {
		return new VillagerFact(id(n), name, hiring, "farmer", "villageheart:farmer", 1, 0, NeedsFlags.of(true, true, true),
				ActivityKind.WORKING, false, false, false, true);
	}

	static VillagerFact unemployed(int n, String name) {
		return new VillagerFact(id(n), name, -1, "", "minecraft:none", 0, 0, NeedsFlags.UNKNOWN, ActivityKind.IDLE, false,
				false, false, true);
	}

	static VillagerFact nitwit(int n, String name) {
		return new VillagerFact(id(n), name, -1, "", "minecraft:nitwit", 0, 0, NeedsFlags.UNKNOWN, ActivityKind.IDLE, false,
				true, false, true);
	}

	static BuildingFact building(String type, RowStatus st, int x, List<TextLine> reasons) {
		return new BuildingFact(type, x, 64, 0, st, reasons, DetailKind.CONTAINERS, 2, 0);
	}

	static StockFacts stock(StorageAlert alert, int unknown, StockItemFact... items) {
		return new StockFacts(List.of(items), 1, unknown, 2, 54, 40, alert);
	}

	/** Placeholder; M24 member tests build their own facts. */
	static final List<VillageFacts.MemberFact> MEMBERS = List.of();

	static VillageFacts facts(List<VillagerFact> v, List<BuildingFact> b, StockFacts s, int adults, int babies,
			List<AlertLine> extra) {
		return new VillageFacts("Owner", Tier.HAMLET, 7, adults, babies, 2, 1, v, List.of(new JobFact("farmer", 0, false)), b, s,
				extra, MEMBERS);
	}

	static VillageFacts basic() {
		return facts(List.of(worker(1, "", 0), unemployed(2, "Ada")),
				List.of(building("storage", RowStatus.VALID, 0, List.of()), building("house", RowStatus.VALID, 5, List.of())),
				stock(StorageAlert.OK, 0, new StockItemFact("minecraft:bread", 20, 5, true),
						new StockItemFact("minecraft:oak_log", 64, 0, true)), 2, 0, List.of());
	}

	@Test void rankFilter() {
		VillageFacts f = basic();
		HeartSnapshot vis = HeartSnapshotBuilder.build(f, Rank.VISITOR);
		assertEquals(Rank.VISITOR, vis.viewerRank());
		assertTrue(vis.villagers().isEmpty());
		assertTrue(vis.buildings().isEmpty());
		assertTrue(vis.stock().items().isEmpty());
		assertTrue(vis.jobChoices().isEmpty());
		assertEquals(Stock.EMPTY, vis.stock());
		assertEquals(0, vis.moreVillagers());
		assertEquals(0, vis.moreBuildings());
		assertEquals(2, vis.overview().population());
		assertEquals(7, vis.overview().treasury());
		assertEquals(1, vis.overview().workers());
		assertEquals(Tier.VILLAGE, vis.overview().nextTier().orElseThrow());

		HeartSnapshot res = HeartSnapshotBuilder.build(f, Rank.RESIDENT);
		assertEquals(2, res.villagers().size());
		assertEquals(2, res.buildings().size());
		assertEquals(2, res.stock().items().size());
		assertTrue(res.jobChoices().isEmpty());
		assertEquals(vis.overview(), res.overview(), "the overview is the same for every rank");

		for (Rank r : new Rank[] {Rank.STEWARD, Rank.OWNER}) {
			HeartSnapshot s = HeartSnapshotBuilder.build(f, r);
			assertEquals(1, s.jobChoices().size(), r.name());
			assertEquals(2, s.villagers().size());
		}
	}

	// ---- M24: members (the Ranks tab) ----------------------------------------------------------------------------

	static VillageFacts.MemberFact member(int n, String name, Rank rank, boolean online) {
		return new VillageFacts.MemberFact(id(n), name, rank, online);
	}

	static VillageFacts withMembers(List<VillageFacts.MemberFact> members) {
		VillageFacts f = basic();
		return new VillageFacts(f.ownerName(), f.tier(), f.treasury(), f.adults(), f.babies(), f.beds(), f.workerSlots(),
				f.villagers(), f.jobs(), f.buildings(), f.stock(), f.extraAlerts(), members);
	}

	@Test void ownerSeesMembersInDocumentedOrder() {
		VillageFacts f = withMembers(List.of(
				member(1, "zed", Rank.VISITOR, true), member(2, "Bea", Rank.STEWARD, false), member(3, "olga", Rank.OWNER, true),
				member(4, "amy", Rank.RESIDENT, false), member(5, "Cal", Rank.RESIDENT, true), member(6, "adam", Rank.STEWARD, true),
				member(7, "Dee", Rank.RESIDENT, false), member(8, "ann", Rank.VISITOR, false)));
		HeartSnapshot s = HeartSnapshotBuilder.build(f, Rank.OWNER);
		assertEquals(List.of("olga", "adam", "Bea", "Cal", "amy", "Dee", "zed", "ann"), s.members().stream().map(MemberRow::name).toList(),
				"owner; stewards (online first); residents (online first, then case-insensitive name); visitors");
		assertEquals(0, s.moreMembers());
	}

	@Test void othersNeverReceiveTheMembers() {
		VillageFacts f = withMembers(List.of(member(3, "olga", Rank.OWNER, true), member(1, "zed", Rank.VISITOR, true)));
		for (Rank r : new Rank[] {Rank.STEWARD, Rank.RESIDENT, Rank.VISITOR}) {
			HeartSnapshot s = HeartSnapshotBuilder.build(f, r);
			assertTrue(s.members().isEmpty(), r + " gets no member list");
			assertEquals(0, s.moreMembers());
		}
	}

	@Test void hundredAndTwentyMembersAreCapped() {
		List<VillageFacts.MemberFact> many = new ArrayList<>();
		many.add(member(0, "o", Rank.OWNER, true));
		for (int i = 1; i < 120; i++) many.add(member(i, "p" + i, Rank.VISITOR, false));
		HeartSnapshot s = HeartSnapshotBuilder.build(withMembers(many), Rank.OWNER);
		assertEquals(HeartLimits.MAX_MEMBERS, s.members().size());
		assertEquals(20, s.moreMembers());
		assertEquals(Rank.OWNER, s.members().get(0).rank());
	}

	@Test void memberNamesAreCut() {
		HeartSnapshot s = HeartSnapshotBuilder.build(withMembers(List.of(member(0, "n".repeat(80), Rank.OWNER, true))), Rank.OWNER);
		assertEquals(HeartLimits.MAX_NAME, s.members().get(0).name().length());
	}

	@Test void tierTownHasNoNext() {
		VillageFacts f = new VillageFacts("O", Tier.TOWN, 0, 0, 0, 0, 0, List.of(), List.of(), List.of(),
				stock(StorageAlert.OK, 0), List.of(), List.of());
		assertTrue(HeartSnapshotBuilder.build(f, Rank.OWNER).overview().nextTier().isEmpty());
	}

	@Test void order() {
		List<VillagerFact> v = List.of(nitwit(9, "Aaa"), unemployed(5, "Zed"), worker(3, "Bee", 0), unemployed(4, "Ann"),
				worker(2, "Cat", 1)); // hired: Bee first, then Cat
		// The task's example: B hired before A.
		List<VillagerFact> swap = List.of(worker(1, "Alf", 1), worker(2, "Bob", 0), unemployed(3, "Dan"), unemployed(4, "Cid"),
				nitwit(5, "Abe"));
		HeartSnapshot s = HeartSnapshotBuilder.build(facts(swap, List.of(), stock(StorageAlert.OK, 0), 5, 1, List.of()),
				Rank.OWNER);
		assertEquals(List.of("Bob", "Alf", "Cid", "Dan", "Abe"), s.villagers().stream().map(VillagerRow::name).toList());
		assertEquals(6, s.overview().population());
		assertEquals(1, s.overview().babies());
		assertEquals(2, s.overview().workers());

		HeartSnapshot t = HeartSnapshotBuilder.build(facts(v, List.of(), stock(StorageAlert.OK, 0), 5, 0, List.of()), Rank.OWNER);
		assertEquals(List.of("Bee", "Cat", "Ann", "Zed", "Aaa"), t.villagers().stream().map(VillagerRow::name).toList());

		HeartSnapshot b = HeartSnapshotBuilder.build(facts(List.of(), List.of(building("storage", RowStatus.VALID, 0, List.of()),
				building("house", RowStatus.INVALID, 1, List.of(new TextLine("k", "m"))),
				building("farmhouse", RowStatus.DEFERRED, 2, List.of(new TextLine("k", "m")))),
				stock(StorageAlert.OK, 0), 0, 0, List.of()), Rank.OWNER);
		assertEquals(List.of("house", "farmhouse", "storage"), b.buildings().stream().map(BuildingRow::typeId).toList());
	}

	@Test void buildingsOfOneTypeSortByPosition() {
		HeartSnapshot b = HeartSnapshotBuilder.build(facts(List.of(), List.of(building("house", RowStatus.VALID, 9, List.of()),
				building("house", RowStatus.VALID, -3, List.of())), stock(StorageAlert.OK, 0), 0, 0, List.of()), Rank.OWNER);
		assertEquals(List.of(-3, 9), b.buildings().stream().map(BuildingRow::x).toList());
	}

	@Test void namesAndWorkerFields() {
		HeartSnapshot s = HeartSnapshotBuilder.build(basic(), Rank.OWNER);
		VillagerRow w = s.villagers().get(0);
		assertEquals(VillagerNames.of(id(1)), w.name(), "no name tag -> generated name");
		assertEquals("farmer", w.jobId());
		assertEquals(1, w.level());
		assertEquals(100, w.xpForNext());
		assertTrue(w.needs().known());
		VillagerRow u = s.villagers().get(1);
		assertEquals("Ada", u.name());
		assertFalse(u.needs().known());
		assertEquals(0, u.xpForNext());
		assertEquals("", u.jobId());
	}

	@Test void caps() {
		List<VillagerFact> v = new ArrayList<>();
		for (int i = 0; i < 150; i++) v.add(unemployed(i, "N" + i));
		List<TextLine> nine = new ArrayList<>();
		for (int i = 0; i < 9; i++) nine.add(new TextLine("k" + i, "x".repeat(500)));
		List<BuildingFact> b = new ArrayList<>();
		b.add(building("house", RowStatus.INVALID, 0, nine));
		for (int i = 1; i < 130; i++) b.add(building("storage", RowStatus.VALID, i, List.of()));
		List<StockItemFact> items = new ArrayList<>();
		for (int i = 0; i < 300; i++) items.add(new StockItemFact("minecraft:item_" + i, 1, 2, true));
		List<AlertLine> extra = new ArrayList<>();
		for (int i = 0; i < 4; i++) extra.add(new AlertLine(AlertCode.NO_STORAGE, Severity.WARNING, new TextLine("e" + i, "e")));
		VillageFacts f = facts(v, b, new StockFacts(items, 1, 0, 2, 54, 40, StorageAlert.NO_STORAGE), 150, 0, extra);

		HeartSnapshot s = HeartSnapshotBuilder.build(f, Rank.OWNER);
		assertEquals(100, s.villagers().size());
		assertEquals(50, s.moreVillagers());
		assertEquals(128, s.buildings().size());
		assertEquals(130 - 128, s.moreBuildings());
		BuildingRow first = s.buildings().get(0);
		assertEquals("house", first.typeId());
		assertEquals(4, first.reasons().size());
		assertEquals(5, first.moreReasons());
		assertEquals(120, first.reasons().get(0).fallback().length(), "a 500-char reason is cut");
		assertEquals(256, s.stock().items().size());
		assertEquals(44, s.stock().moreKinds());
		assertEquals(600, s.stock().nutrition(), "nutrition counts the hidden kinds too");
		assertEquals(300, s.stock().totalItems());
		assertEquals(5, s.overview().alerts().size());
		assertEquals(1, s.overview().moreAlerts(), "NO_STORAGE + INVALID_BUILDINGS + 4 extra = 6, one hidden");
	}

	@Test void eightAlertsShowFivePlusThree() {
		List<AlertLine> extra = new ArrayList<>();
		for (int i = 0; i < 5; i++) extra.add(new AlertLine(AlertCode.NO_STORAGE, Severity.WARNING, new TextLine("e" + i, "e")));
		// NO_STORAGE + INVALID_BUILDINGS + WORKPLACE_INVALID + 5 extra = 8
		List<VillagerFact> v =List.of(new VillagerFact(id(1), "A", 0, "farmer", "x", 1, 0, NeedsFlags.of(true, true, true),
				ActivityKind.WORKING, true, false, false, true));
		VillageFacts f = facts(v, List.of(building("house", RowStatus.INVALID, 0, List.of())),
				stock(StorageAlert.NO_STORAGE, 0), 1, 0, extra);
		Overview o = HeartSnapshotBuilder.build(f, Rank.VISITOR).overview();
		assertEquals(5, o.alerts().size());
		assertEquals(3, o.moreAlerts());
	}

	@Test void notVillageFoodIsNotNutrition() {
		VillageFacts f = facts(List.of(), List.of(), stock(StorageAlert.OK, 0, new StockItemFact("minecraft:rotten_flesh", 10, 4, false),
				new StockItemFact("minecraft:bread", 2, 5, true)), 1, 0, List.of());
		Stock s = HeartSnapshotBuilder.build(f, Rank.OWNER).stock();
		assertEquals(10, s.nutrition());
		assertEquals(0, s.items().stream().filter(i -> i.itemId().endsWith("rotten_flesh")).findFirst().orElseThrow().nutritionEach());
		assertEquals(5, s.items().stream().filter(i -> i.itemId().endsWith("bread")).findFirst().orElseThrow().nutritionEach());
		assertEquals("minecraft:rotten_flesh", s.items().get(0).itemId(), "sorted by count desc");
	}

	@Test void alerts() {
		TextLine none = HeartSnapshotBuilder.build(facts(List.of(), List.of(), stock(StorageAlert.OK, 0), 1, 0, List.of()),
				Rank.VISITOR).overview().alerts().stream().findAny().map(AlertLine::text).orElse(null);
		assertNull(none, "no alerts");

		Overview ns = HeartSnapshotBuilder.build(facts(List.of(), List.of(), stock(StorageAlert.NO_STORAGE, 0), 1, 0, List.of()),
				Rank.VISITOR).overview();
		assertEquals(List.of(AlertCode.NO_STORAGE), ns.alerts().stream().map(AlertLine::code).toList());

		Overview full = HeartSnapshotBuilder.build(facts(List.of(), List.of(), stock(StorageAlert.FULL, 0), 1, 0, List.of()),
				Rank.VISITOR).overview();
		assertEquals("Storage full: no empty slot in any readable Storage", full.alerts().get(0).text().english());

		Overview unk = HeartSnapshotBuilder.build(facts(List.of(), List.of(), stock(StorageAlert.FULL, 1), 1, 0, List.of()),
				Rank.VISITOR).overview();
		assertEquals("Storage full: no empty slot in any readable Storage (1 Storage not checked yet)",
				unk.alerts().get(0).text().english());

		VillageFacts many = facts(List.of(worker(1, "A", 0)), List.of(building("house", RowStatus.INVALID, 0, List.of()),
				building("storage", RowStatus.INVALID, 1, List.of()), building("kitchen", RowStatus.VALID, 2, List.of())),
				stock(StorageAlert.FULL, 0), 1, 0, List.of());
		VillageFacts broken = new VillageFacts(many.ownerName(), many.tier(), many.treasury(), many.adults(), many.babies(),
				many.beds(), many.workerSlots(), List.of(new VillagerFact(id(1), "A", 0, "farmer", "x", 1, 0,
						NeedsFlags.of(true, true, true), ActivityKind.WORKING, true, false, false, true)),
				many.jobs(), many.buildings(), many.stock(), List.of(), List.of());
		Overview o = HeartSnapshotBuilder.build(broken, Rank.OWNER).overview();
		assertEquals(List.of(AlertCode.STORAGE_FULL, AlertCode.INVALID_BUILDINGS, AlertCode.WORKPLACE_INVALID),
				o.alerts().stream().map(AlertLine::code).toList());
		assertEquals("2 buildings need attention", o.alerts().get(1).text().english());
		assertEquals("1 worker has no valid workplace", o.alerts().get(2).text().english());
		assertEquals(Severity.DANGER, o.alerts().get(2).severity());
	}

	@Test void equalFactsGiveEqualSnapshots() {
		assertEquals(HeartSnapshotBuilder.build(basic(), Rank.OWNER), HeartSnapshotBuilder.build(basic(), Rank.OWNER));
		VillageFacts f = basic();
		VillageFacts changed = new VillageFacts(f.ownerName(), f.tier(), f.treasury() + 1, f.adults(), f.babies(), f.beds(),
				f.workerSlots(), f.villagers(), f.jobs(), f.buildings(), f.stock(), f.extraAlerts(), f.members());
		assertNotEquals(HeartSnapshotBuilder.build(f, Rank.OWNER), HeartSnapshotBuilder.build(changed, Rank.OWNER));
	}

	@Test void foodDaysOnTheOverview() {
		VillageFacts f = facts(List.of(), List.of(), stock(StorageAlert.OK, 0, new StockItemFact("minecraft:bread", 20, 5, true)),
				3, 0, List.of());
		assertEquals(33, HeartSnapshotBuilder.build(f, Rank.VISITOR).overview().foodDaysTenths());
		VillageFacts nobody = facts(List.of(), List.of(), stock(StorageAlert.OK, 0), 0, 0, List.of());
		assertEquals(-1, HeartSnapshotBuilder.build(nobody, Rank.VISITOR).overview().foodDaysTenths());
	}
}
