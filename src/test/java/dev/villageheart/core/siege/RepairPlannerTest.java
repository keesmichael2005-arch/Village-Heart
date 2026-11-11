package dev.villageheart.core.siege;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.defence.DefenceKind;
import dev.villageheart.core.room.Pos;
import dev.villageheart.core.siege.RepairPlanner.Cell;
import dev.villageheart.core.siege.RepairPlanner.Plan;
import dev.villageheart.core.siege.RepairPlanner.Repair;
import dev.villageheart.core.siege.RepairPlanner.Wait;
import dev.villageheart.core.worker.DayPhase;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** T-L1-22. Repairs wait for material, and they are not planned during a threat or off the work shift. */
class RepairPlannerTest {
	private static final HeartLocation HEART = new HeartLocation("minecraft:overworld", 0, 64, 0);
	private static final Map<String, Integer> STOCKED = Map.of(RepairPlanner.COBBLESTONE, 64, RepairPlanner.FENCE_GATE, 4);

	@Test
	void orderNearestAndCosts() {
		Cell gate = cell(10, 64, 0, DefenceKind.GATE, 0, true, false);
		Cell stone = cell(1, 64, 0, DefenceKind.STONE, 0, true, false);
		Cell heavy = cell(30, 64, 0, DefenceKind.STONE, 6, false, false);
		Cell light = cell(2, 64, 0, DefenceKind.STONE, 1, false, false);
		assertEquals(gate.packed(), repair(next(false, List.of(light, heavy, stone, gate), STOCKED)).packed());

		Cell low = cell(0, 63, 2, DefenceKind.STONE, 0, true, false);
		Cell high = cell(0, 65, 2, DefenceKind.STONE, 0, true, false);
		assertEquals(low.packed(), repair(next(false, List.of(high, low), STOCKED)).packed());

		assertEquals(heavy.packed(), repair(next(false, List.of(light, heavy), STOCKED)).packed());
		assertEquals(1, repair(next(false, List.of(stone), STOCKED)).cost());
		assertEquals(1, repair(next(false, List.of(heavy), STOCKED)).cost());
		assertEquals(0, repair(next(false, List.of(light), Map.of())).cost());
		assertEquals(RepairPlanner.FENCE_GATE, repair(next(false, List.of(gate), STOCKED)).materialId());
		assertEquals(0, repair(next(false, List.of(cell(1, 64, 0, DefenceKind.STONE, 5, false, false)), STOCKED)).cost());
		assertEquals(1, repair(next(false, List.of(cell(1, 64, 0, DefenceKind.GATE, 4, false, false)), STOCKED)).cost());
	}

	@Test
	void noMaterialWaitsAndAThreatPlansNothing() {
		Cell gate = cell(3, 64, 0, DefenceKind.GATE, 0, true, false);
		Plan wait = next(false, List.of(gate), Map.of(RepairPlanner.COBBLESTONE, 64)).orElseThrow();
		assertEquals(new Wait("fence gate", 1), wait);
		Cell heavy = cell(3, 64, 0, DefenceKind.STONE, 6, false, false);
		assertEquals(new Wait("cobblestone", 1), next(false, List.of(heavy), Map.of()).orElseThrow());

		assertTrue(next(true, List.of(gate), STOCKED).isEmpty());
		assertTrue(RepairPlanner.next(false, DayPhase.SLEEP, List.of(gate), HEART, STOCKED).isEmpty());
		assertTrue(RepairPlanner.next(false, DayPhase.WAKE, List.of(gate), HEART, STOCKED).isEmpty());
		Cell opened = cell(1, 64, 0, DefenceKind.GATE, 0, true, true);
		Cell stone = cell(4, 64, 0, DefenceKind.STONE, 0, true, false);
		assertEquals(stone.packed(), repair(next(false, List.of(opened, stone), STOCKED)).packed());
		assertTrue(next(false, List.of(opened), STOCKED).isEmpty());
	}

	private static java.util.Optional<Plan> next(boolean threat, List<Cell> cells, Map<String, Integer> stock) {
		return RepairPlanner.next(threat, DayPhase.WORK, cells, HEART, stock);
	}

	private static Repair repair(java.util.Optional<Plan> plan) { return (Repair) plan.orElseThrow(); }

	private static Cell cell(int x, int y, int z, DefenceKind kind, int damage, boolean breached, boolean opened) {
		return new Cell(Pos.pack(x, y, z), kind, damage, breached, opened);
	}
}
