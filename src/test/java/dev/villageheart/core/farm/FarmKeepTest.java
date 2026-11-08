package dev.villageheart.core.farm;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.job.FoodKeep;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FarmKeepTest {
	@Test
	void farmerKeepRules() {
		assertEquals(50, FarmKeep.keep("minecraft:wheat_seeds", 50));
		assertEquals(64, FarmKeep.keep("minecraft:wheat_seeds", 200), "one stack of seeds at most");
		assertEquals(64, FarmKeep.keep("minecraft:beetroot_seeds", 200));
		assertEquals(16, FarmKeep.keep("minecraft:carrot", 40));
		assertEquals(5, FarmKeep.keep("minecraft:potato", 5));
		assertEquals(5, FarmKeep.keep("minecraft:bone_meal", 5));
		assertEquals(0, FarmKeep.keep("minecraft:wheat", 64));
		assertEquals(0, FarmKeep.keep("minecraft:beetroot", 64));
		assertEquals(0, FarmKeep.keep("minecraft:poisonous_potato", 3));
		assertEquals(0, FarmKeep.keep("minecraft:oak_log", 3));
	}

	@Test
	void surplusIsWhatIsAboveTheReserve() {
		assertEquals(0, FarmKeep.surplus(16));
		assertEquals(34, FarmKeep.surplus(50));
		assertEquals(0, FarmKeep.surplus(3));
		assertTrue(FarmKeep.isCompostSeed("minecraft:wheat_seeds"));
		assertFalse(FarmKeep.isCompostSeed("minecraft:carrot"));
	}

	@Test
	void foodTopUpKeepsUpToTwelvePoints() {
		// a farmer that already keeps 16 carrots + 16 potatoes needs no bread
		Map<String, Integer> inv = Map.of("minecraft:carrot", 40, "minecraft:potato", 30, "minecraft:bread", 2);
		Map<String, Integer> job = Map.of("minecraft:carrot", 16, "minecraft:potato", 16);
		assertEquals(Map.of(), FoodKeep.topUp(inv, job));
		// a non-farmer with 5 bread keeps 3 (12 points)
		assertEquals(Map.of("minecraft:bread", 3), FoodKeep.topUp(Map.of("minecraft:bread", 5), Map.of()));
		// 2 bread + 10 carrots: 2 bread (8) + 4 carrots (4)
		assertEquals(Map.of("minecraft:bread", 2, "minecraft:carrot", 4), FoodKeep.topUp(Map.of("minecraft:bread", 2, "minecraft:carrot", 10), Map.of()));
		// no food: nothing
		assertEquals(Map.of(), FoodKeep.topUp(Map.of(), Map.of()));
		// 12 points exactly with bread; the carrots go to Storage
		assertEquals(Map.of("minecraft:bread", 3), FoodKeep.topUp(Map.of("minecraft:bread", 3, "minecraft:carrot", 9), Map.of()));
		// without bread: potato, then carrot, then beetroot
		assertEquals(Map.of("minecraft:potato", 2, "minecraft:carrot", 3, "minecraft:beetroot", 7),
				FoodKeep.topUp(Map.of("minecraft:potato", 2, "minecraft:carrot", 3, "minecraft:beetroot", 9), Map.of()));
	}

	@Test
	void jobKeptFoodCountsAndIsNotKeptTwice() {
		// 5 carrots all job-kept = 5 points; 2 bread (8) tops up to 13 -> bread: ceil(7/4) = 2
		Map<String, Integer> inv = Map.of("minecraft:carrot", 5, "minecraft:bread", 3);
		Map<String, Integer> extra = FoodKeep.topUp(inv, Map.of("minecraft:carrot", 5));
		assertEquals(Map.of("minecraft:bread", 2), extra);
		// the job keeps fewer carrots than carried: the rest of the carrots can be topped up
		Map<String, Integer> extra2 = FoodKeep.topUp(Map.of("minecraft:carrot", 30), Map.of("minecraft:carrot", 4));
		assertEquals(Map.of("minecraft:carrot", 8), extra2);
	}
}
