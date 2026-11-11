package dev.villageheart.core.siege;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.defence.DefenceCell;
import dev.villageheart.core.defence.DefenceKind;
import dev.villageheart.core.room.Pos;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * T-L1-17. The second column of a wide body is the positive perpendicular: +x when the mob faces along z.
 */
class BreachChoiceTest {
	@Test
	void behindAndFarCellsAreIgnoredAndTheNearestWins() {
		List<DefenceCell> cells = List.of(
				cell(0, 64, -1, DefenceKind.STONE),
				cell(0, 64, 3, DefenceKind.STONE),
				cell(0, 65, 1, DefenceKind.STONE),
				cell(0, 64, 1, DefenceKind.STONE));
		List<DefenceCell> pick = BreachChoice.pick(0, 64, 0, 1, 2, 0, 64, 10, cells);
		assertEquals(List.of(cell(0, 64, 1, DefenceKind.STONE), cell(0, 65, 1, DefenceKind.STONE)), pick);
	}

	@Test
	void aGateWinsAnEqualDistanceTie() {
		DefenceCell gate = cell(0, 65, 1, DefenceKind.GATE);
		DefenceCell stone = cell(1, 64, 1, DefenceKind.STONE);
		List<DefenceCell> pick = BreachChoice.pick(0, 64, 0, 2, 3, 0, 64, 10, List.of(stone, gate));
		assertEquals(gate, pick.get(0));
		assertEquals(stone, pick.get(1));
	}

	@Test
	void aZombiePicksTwoCellsAndARavagerPicksSix() {
		List<DefenceCell> extras = new ArrayList<>();
		extras.add(cell(1, 64, 1, DefenceKind.STONE));
		extras.add(cell(0, 66, 1, DefenceKind.STONE));
		extras.add(cell(0, 64, -1, DefenceKind.GATE));
		List<DefenceCell> zombie = new ArrayList<>(List.of(cell(0, 64, 1, DefenceKind.STONE), cell(0, 65, 1, DefenceKind.GATE)));
		zombie.addAll(extras);
		assertEquals(2, BreachChoice.pick(0, 64, 0, 1, 2, 0, 64, 8, zombie).size());

		List<DefenceCell> box = new ArrayList<>();
		for (int z = 0; z < 2; z++) for (int y = 64; y < 67; y++) box.add(cell(1, y, z, z == 0 && y == 64 ? DefenceKind.GATE : DefenceKind.STONE));
		box.add(cell(1, 64, 2, DefenceKind.STONE));
		box.add(cell(1, 67, 0, DefenceKind.STONE));
		box.add(cell(-1, 64, 0, DefenceKind.STONE));
		box.add(cell(4, 64, 0, DefenceKind.STONE));
		List<DefenceCell> ravager = BreachChoice.pick(0, 64, 0, 2, 3, 10, 64, 0, box);
		assertEquals(6, ravager.size());
		assertEquals(DefenceKind.GATE, ravager.get(0).kind());
	}

	private static DefenceCell cell(int x, int y, int z, DefenceKind kind) {
		return new DefenceCell(new UUID(x + 8L, ((long) y << 32) ^ z), Pos.pack(x, y, z), kind, "state");
	}
}
