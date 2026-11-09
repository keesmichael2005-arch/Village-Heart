package dev.villageheart.core.hunt;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.mine.HaulRng;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** T-L1-6 */
class MeatWeightsTest {
	@Test
	void plainsSpawnsGiveTheFarmAnimalWeights() {
		MeatWeights w = MeatWeights.fromSpawns(Map.of("minecraft:sheep", 12, "minecraft:pig", 10, "minecraft:chicken", 10, "minecraft:cow", 8,
				"minecraft:horse", 5, "minecraft:donkey", 1));
		assertEquals(new MeatWeights(8, 10, 12, 10), w);
		assertEquals(MeatWeights.DEFAULT, w);
	}

	@Test
	void mooshroomIsBeefOnly() {
		assertEquals(new MeatWeights(8, 0, 0, 0), MeatWeights.fromSpawns(Map.of("minecraft:mooshroom", 8)));
	}

	@Test
	void noFarmAnimalsFallBackToTheDefault() {
		assertEquals(MeatWeights.DEFAULT, MeatWeights.fromSpawns(Map.of("minecraft:rabbit", 12, "minecraft:camel", 1)));
		assertEquals(MeatWeights.DEFAULT, MeatWeights.fromSpawns(Map.of()));
	}

	@Test
	void twoBiomesAreSummed() {
		assertEquals(new MeatWeights(16, 20, 24, 20 + 10), MeatWeights.fromSpawns(
				Map.of("minecraft:cow", 16, "minecraft:pig", 20, "minecraft:sheep", 24, "minecraft:chicken", 30)));
	}

	@Test
	void pickBoundaries() {
		MeatWeights w = MeatWeights.DEFAULT;
		assertEquals(MeatKind.BEEF, w.pick(0));
		assertEquals(MeatKind.BEEF, w.pick(7));
		assertEquals(MeatKind.PORKCHOP, w.pick(8));
		assertEquals(MeatKind.PORKCHOP, w.pick(17));
		assertEquals(MeatKind.MUTTON, w.pick(18));
		assertEquals(MeatKind.MUTTON, w.pick(29));
		assertEquals(MeatKind.CHICKEN, w.pick(30));
		assertEquals(MeatKind.CHICKEN, w.pick(w.total() - 1));
		assertThrows(IllegalArgumentException.class, () -> w.pick(w.total()));
		assertThrows(IllegalArgumentException.class, () -> w.pick(-1));
	}

	@Test
	void aZeroWeightMeatNeverAppearsAndTheOthersFollowTheWeights() {
		MeatWeights w = new MeatWeights(0, 0, 1, 3);
		UUID u = new UUID(7, 7);
		int mutton = 0, chicken = 0;
		for (int i = 0; i < 40_000; i++) {
			Map<String, Integer> r = HuntTable.roll(w, false, HaulRng.forHaul(1, u, i));
			assertNull(r.get("minecraft:beef"));
			assertNull(r.get("minecraft:porkchop"));
			mutton += r.getOrDefault("minecraft:mutton", 0);
			chicken += r.getOrDefault("minecraft:chicken", 0);
		}
		assertEquals(0.25, mutton / (double) (mutton + chicken), 0.015);
	}
}
