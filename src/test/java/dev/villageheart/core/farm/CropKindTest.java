package dev.villageheart.core.farm;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class CropKindTest {
	@Test
	void theTableOfThePlan() {
		assertEquals("minecraft:wheat", CropKind.WHEAT.blockId());
		assertEquals("minecraft:wheat_seeds", CropKind.WHEAT.seedId());
		assertEquals("minecraft:wheat", CropKind.WHEAT.productId());
		assertEquals("minecraft:carrots", CropKind.CARROTS.blockId());
		assertEquals("minecraft:carrot", CropKind.CARROTS.seedId());
		assertEquals("minecraft:potatoes", CropKind.POTATOES.blockId());
		assertEquals("minecraft:potato", CropKind.POTATOES.seedId());
		assertEquals("minecraft:beetroots", CropKind.BEETROOTS.blockId());
		assertEquals("minecraft:beetroot_seeds", CropKind.BEETROOTS.seedId());
		assertEquals("minecraft:beetroot", CropKind.BEETROOTS.productId());
		assertEquals(7, CropKind.WHEAT.maxAge());
		assertEquals(7, CropKind.CARROTS.maxAge());
		assertEquals(7, CropKind.POTATOES.maxAge());
		assertEquals(3, CropKind.BEETROOTS.maxAge());
	}

	@Test
	void otherPlantsAreNotCrops() {
		for (String id : new String[] {"minecraft:torchflower_crop", "minecraft:pitcher_crop", "minecraft:melon_stem", "minecraft:pumpkin_stem",
				"minecraft:sweet_berry_bush", "minecraft:nether_wart", "minecraft:sugar_cane", "minecraft:air"}) {
			assertEquals(Optional.empty(), CropKind.byBlockId(id), id);
		}
		for (CropKind k : CropKind.values()) assertEquals(Optional.of(k), CropKind.byBlockId(k.blockId()));
	}

	@Test
	void matureAtTheMaximumOnly() {
		for (CropKind k : CropKind.values()) {
			assertTrue(k.isMature(k.maxAge()), k + " at max");
			assertFalse(k.isMature(k.maxAge() - 1), k + " one below max");
			assertTrue(FieldCell.crop(k, k.maxAge()).isMature());
			assertTrue(FieldCell.crop(k, k.maxAge() - 1).isYoung());
		}
		assertTrue(FieldCell.crop(CropKind.BEETROOTS, 3).isMature(), "a beetroot at 3 is mature, not young");
		assertFalse(FieldCell.crop(CropKind.BEETROOTS, 2).isMature());
		assertFalse(FieldCell.crop(CropKind.WHEAT, 6).isMature());
		assertFalse(FieldCell.EMPTY.isMature());
		assertFalse(FieldCell.OTHER.isYoung());
	}
}
