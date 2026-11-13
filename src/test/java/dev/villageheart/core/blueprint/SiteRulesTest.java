package dev.villageheart.core.blueprint;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.Rank;
import dev.villageheart.core.Tier;
import dev.villageheart.core.blueprint.SiteRules.Box;
import dev.villageheart.core.blueprint.SiteRules.Code;
import dev.villageheart.core.blueprint.SiteRules.Protected;
import dev.villageheart.core.blueprint.SiteRules.SiteCell;
import dev.villageheart.core.blueprint.SiteRules.SiteFacts;
import dev.villageheart.core.blueprint.SiteRules.SiteProblem;
import dev.villageheart.core.room.Dir;
import dev.villageheart.core.room.FakeGrid;
import dev.villageheart.core.room.Pos;
import dev.villageheart.core.room.RoomScan;
import dev.villageheart.core.room.RoomScanner;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** T-L1-9 */
class SiteRulesTest {
	private static final HeartLocation HEART = new HeartLocation("minecraft:overworld", 0, 0, 0);
	private static final String DIM = "minecraft:overworld";
	private static final Blueprint HOUSE = Blueprints.SMALL_HOUSE;

	/** Everything is air unless overridden. */
	private static final class World implements SiteRules.SiteView {
		final Map<Pos, SiteCell> cells = new HashMap<>();
		final Map<Pos, String> ids = new HashMap<>();
		/** Cleared cell to what rests on it. */
		final Map<Pos, SiteRules.Dependent> dependents = new HashMap<>();

		@Override
		public java.util.Optional<SiteRules.Dependent> dependent(Pos cleared, java.util.Set<Long> touched) {
			return java.util.Optional.ofNullable(dependents.get(cleared)).filter(d -> !touched.contains(d.at().pack()));
		}

		@Override
		public SiteCell cell(Pos p) { return cells.getOrDefault(p, SiteCell.AIR); }

		@Override
		public String blockId(Pos p) { return ids.getOrDefault(p, "minecraft:stone"); }
	}

	private static SiteFacts facts(Pos ground, World w, List<Protected> prot, List<Box> others, int active, StandPoints.Standable stand) {
		return new SiteFacts(HOUSE, new Placement(ground, Rotation4.NONE), HEART, DIM, Tier.VILLAGE, Rank.STEWARD, -64, 320, w, prot, others,
				active, stand);
	}

	private static SiteFacts ok(Pos ground) {
		return facts(ground, new World(), List.of(), List.of(), 0, (x, z) -> OptionalInt.of(ground.y() + 1));
	}

	private static SiteFacts with(SiteFacts f, java.util.function.UnaryOperator<SiteFacts> c) { return c.apply(f); }

	private static Code code(SiteFacts f) { return SiteRules.check(f).map(SiteProblem::code).orElse(null); }

	private static Protected roomArea() {
		RoomScan scan = RoomScanner.scanFromMarker(FakeGrid.room5x5x3(), FakeGrid.MARKER, Dir.NORTH);
		assertTrue(scan.enclosed());
		Set<Long> cells = new HashSet<>(scan.interior());
		cells.addAll(scan.shell());
		cells.add(FakeGrid.MARKER.pack());
		cells.add(FakeGrid.MARKER.offset(Dir.SOUTH).pack());
		return new Protected("House", FakeGrid.MARKER, cells);
	}

	@Test
	void aFreeSiteIsOk() {
		assertTrue(SiteRules.check(ok(new Pos(10, 0, 30))).isEmpty());
	}

	/** Roadmap: a site overlap with a player room is refused. */
	@Test
	void overlapWithARoomIsRefused() {
		List<Protected> prot = List.of(roomArea());
		StandPoints.Standable stand = (x, z) -> OptionalInt.of(1);
		// the House's wall cells touch the room's shell (x = 6): the box is x 6..12
		SiteFacts shell = facts(new Pos(9, 0, 3), new World(), prot, List.of(), 0, stand);
		SiteProblem p = SiteRules.check(shell).orElseThrow();
		assertEquals(Code.OVERLAP, p.code());
		assertTrue(p.english().startsWith("Overlaps the House at (3, 3, -1)"), p.english());
		// only the interior (x 4..10 covers interior x 4 and 5, and the shell at 6)
		SiteFacts inside = facts(new Pos(5, 0, 3), new World(), prot, List.of(), 0, stand);
		assertEquals(Code.OVERLAP, code(inside));
		// one block away from the shell: the box starts at x = 8 (the shell is x = 6, x = 7 is free)
		SiteFacts away = facts(new Pos(11, 0, 3), new World(), prot, List.of(), 0, stand);
		assertNull(code(away));
		// a site cell next to a registered marker is refused too, even when the marker's own cells are not in the set
		Placement pl = new Placement(new Pos(10, 0, 30), Rotation4.NONE);
		Pos roofCorner = pl.world(HOUSE, 0, 4, 1);
		SiteFacts adj = facts(pl.ground(), new World(), List.of(new Protected("House", roofCorner.above(), Set.of())), List.of(), 0, stand);
		assertEquals(Code.OVERLAP, code(adj));
		// a marker two cells away is not
		SiteFacts far = facts(pl.ground(), new World(), List.of(new Protected("House", roofCorner.above().above(), Set.of())), List.of(), 0, stand);
		assertNull(code(far));
	}

	@Test
	void ruleOrder1Rank() {
		SiteFacts f = with(ok(new Pos(10, 0, 30)), x -> new SiteFacts(x.bp(), x.p(), x.heart(), x.dimension(), x.tier(), Rank.RESIDENT, x.minY(),
				x.maxY(), x.view(), x.protectedAreas(), x.otherBuilds(), x.activeBuilds(), x.stand()));
		SiteProblem p = SiteRules.check(f).orElseThrow();
		assertEquals(Code.RANK, p.code());
		assertEquals("Only a Steward or the Owner can start a blueprint", p.english());
		// a Steward and the Owner may
		for (Rank r : new Rank[] {Rank.STEWARD, Rank.OWNER}) {
			SiteFacts g = with(ok(new Pos(10, 0, 30)), x -> new SiteFacts(x.bp(), x.p(), x.heart(), x.dimension(), x.tier(), r, x.minY(),
					x.maxY(), x.view(), x.protectedAreas(), x.otherBuilds(), x.activeBuilds(), x.stand()));
			assertNull(code(g));
		}
	}

	@Test
	void ruleOrder2Tier() {
		SiteFacts f = with(ok(new Pos(10, 0, 30)), x -> new SiteFacts(x.bp(), x.p(), x.heart(), x.dimension(), Tier.HAMLET, x.rank(), x.minY(),
				x.maxY(), x.view(), x.protectedAreas(), x.otherBuilds(), x.activeBuilds(), x.stand()));
		assertEquals(Code.TIER, code(f));
		assertEquals("Requires Village tier", SiteRules.check(f).orElseThrow().english());
	}

	@Test
	void ruleOrder3DimensionAndHeight() {
		SiteFacts d = with(ok(new Pos(10, 0, 30)), x -> new SiteFacts(x.bp(), x.p(), x.heart(), "minecraft:the_nether", x.tier(), x.rank(), x.minY(),
				x.maxY(), x.view(), x.protectedAreas(), x.otherBuilds(), x.activeBuilds(), x.stand()));
		assertEquals(Code.DIMENSION, code(d));
		SiteFacts h = ok(new Pos(10, 318, 30)); // the roof is at 323
		assertEquals(Code.HEIGHT, code(h));
		SiteFacts low = ok(new Pos(10, -70, 30));
		assertEquals(Code.HEIGHT, code(low));
	}

	@Test
	void ruleOrder4Claim() {
		assertNull(code(ok(new Pos(70, 0, 0))));
		SiteFacts f = ok(new Pos(78, 0, 0)); // box x 75..81
		SiteProblem p = SiteRules.check(f).orElseThrow();
		assertEquals(Code.OUTSIDE, p.code());
		assertTrue(p.english().startsWith("Outside the village: 8"), p.english());
		assertTrue(p.english().endsWith("blocks from the Heart (max 80)"), p.english());
	}

	@Test
	void ruleOrder5AnyNumberOfBuildsMayBePlaced() {
		StandPoints.Standable stand = (x, z) -> OptionalInt.of(1);
		assertNull(code(facts(new Pos(10, 0, 30), new World(), List.of(), List.of(), 8, stand)));
		assertNull(code(facts(new Pos(10, 0, 30), new World(), List.of(), List.of(), 40, stand)));
	}

	@Test
	void ruleOrder6Loaded() {
		World w = new World();
		w.cells.put(new Pos(10, 4, 30), SiteCell.UNLOADED);
		assertEquals(Code.NOT_LOADED, code(facts(new Pos(10, 0, 30), w, List.of(), List.of(), 0, (x, z) -> OptionalInt.of(1))));
	}

	@Test
	void ruleOrder8OtherBuilds() {
		Placement other = new Placement(new Pos(10, 0, 30), Rotation4.NONE);
		Box ob = SiteRules.box(HOUSE, other); // x 7..13
		StandPoints.Standable stand = (x, z) -> OptionalInt.of(1);
		// one free column between the boxes: the stand rings touch, refused
		SiteFacts near = facts(new Pos(10 + 8, 0, 30), new World(), List.of(), List.of(ob), 0, stand); // box x 15..21, free column x = 14
		assertEquals(Code.BUILD_OVERLAP, code(near));
		// two free columns: ok
		SiteFacts far = facts(new Pos(10 + 9, 0, 30), new World(), List.of(), List.of(ob), 0, stand); // two free columns
		assertNull(code(far));
	}

	@Test
	void ruleOrder9Fluid() {
		World w = new World();
		Pos p = new Placement(new Pos(10, 0, 30), Rotation4.NONE).world(HOUSE, 0, 1, 3);
		w.cells.put(p.offset(Dir.WEST), SiteCell.FLUID); // next to a touched cell
		SiteProblem pr = SiteRules.check(facts(new Pos(10, 0, 30), w, List.of(), List.of(), 0, (x, z) -> OptionalInt.of(1))).orElseThrow();
		assertEquals(Code.FLUID, pr.code());
		assertTrue(pr.english().startsWith("Water or lava at ("), pr.english());
	}

	@Test
	void ruleOrder10And11LogsAndBlocks() {
		StandPoints.Standable stand = (x, z) -> OptionalInt.of(1);
		Placement pl = new Placement(new Pos(10, 0, 30), Rotation4.NONE);
		Pos cell = pl.world(HOUSE, 2, 2, 3);
		World log = new World();
		log.cells.put(cell, SiteCell.LOG);
		SiteProblem t = SiteRules.check(facts(pl.ground(), log, List.of(), List.of(), 0, stand)).orElseThrow();
		assertEquals(Code.TREE, t.code());
		assertEquals("Tree in the way at " + cell + ": fell it first", t.english());
		World planks = new World();
		planks.cells.put(cell, SiteCell.BLOCKED);
		planks.ids.put(cell, "minecraft:oak_planks");
		SiteProblem b = SiteRules.check(facts(pl.ground(), planks, List.of(), List.of(), 0, stand)).orElseThrow();
		assertEquals(Code.BLOCKED, b.code());
		assertEquals("oak_planks in the way at " + cell, b.english());
		// a log wins over a block
		planks.cells.put(pl.world(HOUSE, 1, 2, 3), SiteCell.LOG);
		assertEquals(Code.TREE, code(facts(pl.ground(), planks, List.of(), List.of(), 0, stand)));
	}

	@Test
	void ruleOrder11bABlockThatRestsOnAClearedCellRefuses() {
		StandPoints.Standable stand = (x, z) -> OptionalInt.of(1);
		Placement pl = new Placement(new Pos(10, 0, 30), Rotation4.NONE);
		Pos dirt = pl.world(HOUSE, 3, 4, 3);
		Pos torch = new Pos(dirt.x(), dirt.y() + 1, dirt.z()); // above the box
		World w = new World();
		w.cells.put(dirt, SiteCell.CLEARABLE);
		w.dependents.put(dirt, new SiteRules.Dependent("minecraft:torch", torch));
		SiteProblem p = SiteRules.check(facts(pl.ground(), w, List.of(), List.of(), 0, stand)).orElseThrow();
		assertEquals(Code.SUPPORT, p.code());
		assertEquals("torch at " + torch + " would fall: it rests on the site", p.english());
		assertEquals(torch, p.at());
		assertEquals("build.villageheart.site.support", p.key());
		// the same dependent in a cell the build touches is the build's own business, not a refusal
		w.dependents.put(dirt, new SiteRules.Dependent("minecraft:torch", pl.world(HOUSE, 3, 3, 3)));
		assertNull(code(facts(pl.ground(), w, List.of(), List.of(), 0, stand)));
		// an air cell (nothing to clear) never asks
		World air = new World();
		air.dependents.put(dirt, new SiteRules.Dependent("minecraft:torch", torch));
		assertNull(code(facts(pl.ground(), air, List.of(), List.of(), 0, stand)));
		// a block of a player in the way (BLOCKED) is reported before a dependent; a dependent before the stand points
		w.dependents.put(dirt, new SiteRules.Dependent("minecraft:torch", torch));
		w.cells.put(pl.world(HOUSE, 1, 1, 1), SiteCell.BLOCKED);
		assertEquals(Code.BLOCKED, code(facts(pl.ground(), w, List.of(), List.of(), 0, stand)));
		w.cells.remove(pl.world(HOUSE, 1, 1, 1));
		assertEquals(Code.SUPPORT, code(facts(pl.ground(), w, List.of(), List.of(), 0, (x, z) -> OptionalInt.empty())));
	}

	@Test
	void ruleOrder12StandPoints() {
		// a 3-deep pit: every ring column is solid, so nobody can stand anywhere
		SiteFacts pit = facts(new Pos(10, 0, 30), new World(), List.of(), List.of(), 0, (x, z) -> OptionalInt.empty());
		SiteProblem p = SiteRules.check(pit).orElseThrow();
		assertEquals(Code.NO_STAND, p.code());
		assertTrue(p.english().startsWith("No place to stand near ("), p.english());
		// a lone wall on the west side is not enough to fail
		SiteFacts oneWall = facts(new Pos(10, 0, 30), new World(), List.of(), List.of(), 0, (x, z) -> x < 7 ? OptionalInt.empty() : OptionalInt.of(1));
		assertNull(code(oneWall));
	}

	@Test
	void uncheckedRoomText() {
		SiteProblem p = SiteRules.unchecked("House", new Pos(1, 2, 3));
		assertEquals("The House at (1, 2, 3) has not been checked yet; try again", p.english());
		assertEquals("build.villageheart.site.unchecked", p.key());
	}

	@Test
	void problemTextsArePinned() {
		Optional<SiteProblem> none = SiteRules.check(ok(new Pos(10, 0, 30)));
		assertTrue(none.isEmpty());
		for (Code c : Code.values()) assertFalse(new SiteProblem(c, "x", List.of(), null).key().isEmpty());
	}
}
