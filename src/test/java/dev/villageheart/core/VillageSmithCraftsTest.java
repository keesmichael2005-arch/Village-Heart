package dev.villageheart.core;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.gear.GearCraft;
import dev.villageheart.core.gear.GearNeed;
import dev.villageheart.core.gear.ItemCount;
import dev.villageheart.core.gear.OrderKey;
import dev.villageheart.core.smith.SmithCraft;
import java.util.List;
import org.junit.jupiter.api.Test;

/** M19 review r1 item 2: a furnace holds one craft, and a different order never replaces it. */
class VillageSmithCraftsTest {
	private static final HeartLocation FURNACE = new HeartLocation("minecraft:overworld", 1, 64, 2);
	private static final HeartLocation MINE = new HeartLocation("minecraft:overworld", 10, 64, 10);
	private static final HeartLocation BARRACKS = new HeartLocation("minecraft:overworld", 20, 64, 10);

	private static SmithCraft craft(HeartLocation building, GearNeed need, int progress) {
		return new SmithCraft(new GearCraft("minecraft:stone_pickaxe", new OrderKey(building, need, 0), progress,
				List.of(new ItemCount("minecraft:cobblestone", 3), new ItemCount("minecraft:stick", 2))), MINE);
	}

	@Test
	void aDifferentOrderDoesNotOverwriteTheSavedCraft() {
		VillageState base = VillageState.founded(new HeartLocation("minecraft:overworld", 0, 64, 0), new java.util.UUID(0, 1), "o", 0);
		SmithCraft pick = craft(MINE, GearNeed.PICKAXE, 40);
		SmithCraft sword = craft(BARRACKS, GearNeed.SWORD, 0);
		VillageState with = base.withSmithCraft(FURNACE, pick);
		assertTrue(with.canStoreSmithCraft(FURNACE, pick));
		assertFalse(with.canStoreSmithCraft(FURNACE, sword));
		VillageState after = with.withSmithCraft(FURNACE, sword);
		assertEquals(pick, after.smithCraft(FURNACE).orElseThrow(), "the pickaxe record and its committed items were replaced");
	}

	@Test
	void theSameOrderIsUpdatedInPlaceAndAFreeFurnaceTakesAny() {
		VillageState base = VillageState.founded(new HeartLocation("minecraft:overworld", 0, 64, 0), new java.util.UUID(0, 1), "o", 0);
		assertTrue(base.canStoreSmithCraft(FURNACE, craft(BARRACKS, GearNeed.SWORD, 0)));
		VillageState with = base.withSmithCraft(FURNACE, craft(MINE, GearNeed.PICKAXE, 0));
		VillageState moved = with.withSmithCraft(FURNACE, craft(MINE, GearNeed.PICKAXE, 600));
		assertEquals(600, moved.smithCraft(FURNACE).orElseThrow().craft().progress());
		assertTrue(moved.withoutSmithCraft(FURNACE).canStoreSmithCraft(FURNACE, craft(BARRACKS, GearNeed.SWORD, 0)));
	}
}
