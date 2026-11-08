package dev.villageheart.core.blueprint;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Tier;
import dev.villageheart.core.room.CellKind;
import dev.villageheart.core.room.Dir;
import org.junit.jupiter.api.Test;

/** T-L1-1 */
class BlueprintParserTest {
	private static final String OK = """
			id tiny
			name Tiny
			tier VILLAGE
			kind BUILDING
			builds none
			base 1
			anchor 1 1
			front NORTH
			size 3 3 3
			palette
			. any
			_ air
			P minecraft:oak_planks <- #minecraft:planks "plank" "planks"
			D minecraft:oak_door[facing=south,half=lower] <- #minecraft:wooden_doors "door" cell=solid +#door
			d minecraft:oak_door[facing=south,half=upper] part=DOWN
			T minecraft:wall_torch[facing=north] <- minecraft:torch "torch" "torches" pass=attached cell=open +#light
			layer 0
			PPP
			PPP
			...
			layer 1
			.D.
			._.
			..T
			layer 2
			.d.
			...
			...
			end
			""";

	private static BlueprintSyntaxException fails(String text) {
		return assertThrows(BlueprintSyntaxException.class, () -> BlueprintParser.parse(text));
	}

	private static String with(String from, String to) {
		assertTrue(OK.contains(from), from);
		return OK.replace(from, to);
	}

	@Test
	void parsesAFixture() {
		Blueprint bp = BlueprintParser.parse(OK);
		assertEquals("tiny", bp.id());
		assertEquals(Tier.VILLAGE, bp.tier());
		assertEquals(Blueprint.Kind.BUILDING, bp.kind());
		assertNull(bp.buildsTypeId());
		assertEquals(1, bp.base());
		assertEquals(1, bp.anchorX());
		assertEquals(Dir.NORTH, bp.front());
		assertEquals(3, bp.width());
		assertEquals(3, bp.height());
		assertEquals(3, bp.depth());
		PaletteEntry d = bp.entry(1, 1, 0);
		assertEquals(PaletteEntry.Kind.BLOCK, d.kind());
		assertEquals("minecraft:oak_door", d.blockId());
		assertEquals("lower", d.property("half"));
		assertEquals("#minecraft:wooden_doors", d.material().id());
		assertEquals("doors", d.material().plural());
		assertEquals(CellKind.SOLID, d.cell());
		assertEquals(java.util.List.of("#door"), d.contents());
		PaletteEntry up = bp.entry(1, 2, 0);
		assertEquals(Dir.DOWN, up.partOf());
		assertNull(up.material());
		assertFalse(up.primary());
		PaletteEntry torch = bp.entry(2, 1, 2);
		assertEquals(PaletteEntry.Pass.ATTACHED, torch.pass());
		assertEquals(CellKind.OPEN, torch.cell());
		assertEquals("torches", torch.material().plural());
		assertEquals("planks", bp.entry(0, 0, 0).material().plural());
		assertEquals(PaletteEntry.Kind.ANY, bp.entry(0, 0, 2).kind());
		assertEquals(PaletteEntry.Kind.AIR, bp.entry(1, 1, 1).kind());
		assertEquals(10, bp.touchedCount());
		assertEquals(1, bp.materials().count("minecraft:torch"));
		assertEquals(6, bp.materials().count("#minecraft:planks"));
	}

	@Test
	void everyKindOfSyntaxErrorHasTheRightLine() {
		BlueprintSyntaxException dupKey = fails(with("kind BUILDING\n", "kind BUILDING\nkind BUILDING\n"));
		assertEquals(5, dupKey.line());
		BlueprintSyntaxException unknown = fails(with("tier VILLAGE\n", "tier VILLAGE\nflavour sweet\n"));
		assertEquals(4, unknown.line());
		assertTrue(unknown.getMessage().contains("unknown key flavour"));
		BlueprintSyntaxException dup = fails(with("T minecraft:wall_torch", "P minecraft:stone <- minecraft:stone \"stone\"\nT minecraft:wall_torch"));
		assertEquals(16, dup.line());
		assertTrue(dup.getMessage().contains("duplicate symbol"));
		assertTrue(fails(with("..T\n", "..T.\n")).getMessage().contains("ragged"));
		assertTrue(fails(with("layer 2\n.d.\n...\n...\n", "")).getMessage().contains("size"));
		assertTrue(fails(with("layer 1\n", "layer 1\n...\n")).getMessage().contains("rows"));
		BlueprintSyntaxException noMat = fails(with("P minecraft:oak_planks <- #minecraft:planks \"plank\" \"planks\"", "P minecraft:oak_planks"));
		assertEquals(13, noMat.line());
		assertTrue(noMat.getMessage().contains("no material"));
		assertTrue(fails(with("part=DOWN", "part=EAST")).getMessage().contains("does not point at a primary"));
		assertTrue(fails(with("d minecraft:oak_door[facing=south,half=upper]", "d minecraft:spruce_door[facing=south,half=upper]"))
				.getMessage().contains("does not point at a primary"));
		assertTrue(fails(with("anchor 1 1", "anchor 3 1")).getMessage().contains("outside the footprint"));
		assertTrue(fails(with("builds none", "builds house")).getMessage().contains("exactly one marker"));
		BlueprintSyntaxException pass = fails(with("pass=attached", "pass=sideways"));
		assertTrue(pass.getMessage().contains("unknown pass"));
		assertEquals(16, pass.line());
		assertTrue(fails(with("layer 0", "layer 1")).getMessage().contains("in order"));
		assertTrue(fails(with("PPP\nPPP\n...\n", "PPX\nPPP\n...\n")).getMessage().contains("unknown symbol"));
		assertTrue(fails(OK.replace("end\n", "")).getMessage().contains("missing end"));
	}

	@Test
	void conversionsAreWhitelisted() {
		assertTrue(fails(with("T minecraft:wall_torch[facing=north] <- minecraft:torch \"torch\" \"torches\" pass=attached cell=open +#light",
				"T minecraft:wall_torch[facing=north] <- minecraft:torch \"torch\" converts")).getMessage().contains("conversion not allowed"));
		assertEquals(2, Blueprints.FARM_PLOT.palette().values().stream().filter(PaletteEntry::converts).count());
	}
}
