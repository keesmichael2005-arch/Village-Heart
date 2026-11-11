package dev.villageheart.core.defence;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.blueprint.Blueprint;
import dev.villageheart.core.blueprint.DefenceBlueprints;
import dev.villageheart.core.blueprint.Rotation4;
import dev.villageheart.core.room.Pos;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** T-L1-15. Defence cells are the cobblestone and fence-gate PLACE steps, and a rotation does not change the count. */
class DefenceCellsTest {
	private static final HeartLocation GROUND = new HeartLocation("minecraft:overworld", 0, 64, 0);

	@Test
	void countsMatchTheBlueprintsAndTheRotations() {
		assertEquals(96, stone(site("wall_segment", 32, Rotation4.NONE)));
		assertEquals(45, stone(site("wall_segment", 15, Rotation4.NONE)));
		assertEquals(30, stone(site("wall_segment", 10, Rotation4.NONE)));

		List<DefenceCells.Placed> gate5 = DefenceCells.of(site("gate", 5, Rotation4.NONE));
		assertEquals(9, DefenceCells.count(gate5, DefenceKind.STONE));
		assertEquals(3, DefenceCells.count(gate5, DefenceKind.GATE));
		assertEquals(List.of(new Pos(1, 65, 0), new Pos(2, 65, 0), new Pos(3, 65, 0)),
				gate5.stream().filter(c -> c.kind() == DefenceKind.GATE).map(DefenceCells.Placed::pos).toList());

		List<DefenceCells.Placed> gate32 = DefenceCells.of(site("gate", 32, Rotation4.NONE));
		assertEquals(90, DefenceCells.count(gate32, DefenceKind.STONE));
		assertEquals(3, DefenceCells.count(gate32, DefenceKind.GATE));

		List<DefenceCells.Placed> tower = DefenceCells.of(site("watchtower", 0, Rotation4.NONE));
		assertEquals(38, tower.size());
		assertEquals(38, DefenceCells.count(tower, DefenceKind.STONE));
		for (DefenceCells.Placed c : tower) {
			String s = c.expectedState();
			assertFalse(s.contains("ladder") || s.contains("plank") || s.contains("fence"), s);
		}
		assertEquals(0, DefenceCells.of(site("lamp_post", 0, Rotation4.NONE)).size());

		for (String id : new String[] {"wall_segment", "gate", "watchtower"}) {
			int length = id.equals("gate") ? 5 : id.equals("wall_segment") ? 10 : 0;
			Blueprint bp = id.equals("watchtower") ? DefenceBlueprints.WATCHTOWER : id.equals("gate") ? DefenceBlueprints.gate(length) : DefenceBlueprints.wall(length);
			int places = defencePlaces(bp);
			if (!id.equals("watchtower")) assertEquals(bp.placeCount(), places, id);
			else assertTrue(bp.placeCount() > places, "the watchtower's planks, fences and ladder are PLACE steps and not defence cells");
			for (Rotation4 r : Rotation4.values()) assertEquals(places, DefenceCells.of(site(id, length, r)).size(), id + " " + r);
		}
		assertNotEquals(positions(site("wall_segment", 10, Rotation4.NONE)), positions(site("wall_segment", 10, Rotation4.CLOCKWISE_90)));
	}

	private static int stone(DefenceSite site) { return DefenceCells.count(DefenceCells.of(site), DefenceKind.STONE); }

	private static List<Pos> positions(DefenceSite site) { return DefenceCells.of(site).stream().map(DefenceCells.Placed::pos).toList(); }

	private static int defencePlaces(Blueprint bp) {
		int n = 0;
		for (int y = 0; y < bp.height(); y++) for (int z = 0; z < bp.depth(); z++) for (int x = 0; x < bp.width(); x++)
			if (DefenceCells.kindOf(bp.entry(x, y, z)) != null) n++;
		return n;
	}

	private static DefenceSite site(String id, int length, Rotation4 rotation) {
		return new DefenceSite(new UUID(1, length + rotation.ordinal()), id, GROUND, rotation, 0, length, java.util.Set.of(), java.util.Set.of());
	}
}
