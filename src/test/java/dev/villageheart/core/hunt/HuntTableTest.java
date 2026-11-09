package dev.villageheart.core.hunt;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.mine.HaulRng;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

/** Roadmap L1: the trip yield table, with and without a bow (M17 plan T-L1-3 .. T-L1-5). */
class HuntTableTest {
	static final UUID U = new UUID(0x1234L, 0x5678L);
	static final int N = 10_000;
	static final MeatWeights W = MeatWeights.DEFAULT;

	static RandomGenerator g(int i) { return HaulRng.forHaul(42, U, i); }

	static int meat(Map<String, Integer> m) {
		int n = 0;
		for (MeatKind k : MeatKind.values()) n += m.getOrDefault(k.itemId(), 0);
		return n;
	}

	private static void check(boolean arrow) {
		int bonus = arrow ? 1 : 0;
		int[] base = new int[5], leather = new int[3], feathers = new int[3], hide = new int[2];
		double meatSum = 0, leatherSum = 0, featherSum = 0, hideSum = 0;
		Set<String> allowed = Set.copyOf(HuntTable.allItems());
		for (int i = 0; i < N; i++) {
			Map<String, Integer> r = HuntTable.roll(W, arrow, g(i));
			assertTrue(allowed.containsAll(r.keySet()), "only the seven ids: " + r);
			r.values().forEach(v -> assertTrue(v > 0, "zero counts are left out"));
			int m = meat(r);
			assertTrue(m >= 2 + bonus && m <= 4 + bonus, "meat " + m);
			int l = r.getOrDefault(HuntTable.LEATHER, 0), f = r.getOrDefault(HuntTable.FEATHER, 0), h = r.getOrDefault(HuntTable.RABBIT_HIDE, 0);
			assertTrue(l >= 1 && l <= 2, "leather " + l);
			assertTrue(f >= 1 && f <= 2, "feathers " + f);
			assertTrue(h >= 0 && h <= 1, "hide " + h);
			base[m - bonus]++;
			leather[l]++;
			feathers[f]++;
			hide[h]++;
			meatSum += m;
			leatherSum += l;
			featherSum += f;
			hideSum += h;
		}
		for (int b = 2; b <= 4; b++) assertEquals(1.0 / 3, base[b] / (double) N, 0.02, "base meat " + b);
		for (int v = 1; v <= 2; v++) {
			assertEquals(0.5, leather[v] / (double) N, 0.02, "leather " + v);
			assertEquals(0.5, feathers[v] / (double) N, 0.02, "feathers " + v);
		}
		assertEquals(0.5, hide[1] / (double) N, 0.02, "hide");
		// literal means, not taken from expected()
		assertEquals(arrow ? 4.0 : 3.0, meatSum / N, (arrow ? 4.0 : 3.0) * 0.03, "mean meat");
		assertEquals(1.5, leatherSum / N, 1.5 * 0.03, "mean leather");
		assertEquals(1.5, featherSum / N, 1.5 * 0.03, "mean feathers");
		assertEquals(0.5, hideSum / N, 0.5 * 0.03, "mean hide");
		Map<String, Double> exp = HuntTable.expected(W, arrow);
		assertEquals(arrow ? 4.0 : 3.0, exp.get("minecraft:beef") + exp.get("minecraft:porkchop") + exp.get("minecraft:mutton") + exp.get("minecraft:chicken"), 1e-9);
		assertEquals(1.5, exp.get(HuntTable.LEATHER), 1e-9);
		assertEquals(1.5, exp.get(HuntTable.FEATHER), 1e-9);
		assertEquals(0.5, exp.get(HuntTable.RABBIT_HIDE), 1e-9);
	}

	@Test
	void sword_orBowWithoutArrow_yieldTableRangesAndMeans() { check(false); }

	@Test
	void bowWithArrow_yieldTableRangesAndMeans() { check(true); }

	@Test
	void allItemsAreTheSevenIds() {
		assertEquals(List.of("minecraft:beef", "minecraft:porkchop", "minecraft:mutton", "minecraft:chicken", "minecraft:leather",
				"minecraft:feather", "minecraft:rabbit_hide"), HuntTable.allItems());
	}

	/** T-L1-4: the arrow adds exactly one meat, of the kind of draw number {@code base}, and changes nothing else. */
	@Test
	void anArrowAddsExactlyOneMeatOfTheNextKindDraw() {
		for (int i = 0; i < N; i++) {
			Map<String, Integer> plain = HuntTable.roll(W, false, g(i));
			Map<String, Integer> bow = HuntTable.roll(W, true, g(i));
			// replay the documented draw order on a fresh generator
			RandomGenerator r = g(i);
			int base = 2 + r.nextInt(3);
			r.nextInt(2);
			r.nextInt(2);
			r.nextDouble();
			MeatKind[] kinds = new MeatKind[5];
			for (int k = 0; k < 5; k++) kinds[k] = W.pick(r.nextInt(W.total()));
			assertEquals(base, meat(plain), "base " + i);
			for (MeatKind k : MeatKind.values()) {
				int expect = plain.getOrDefault(k.itemId(), 0) + (k == kinds[base] ? 1 : 0);
				assertEquals(expect, bow.getOrDefault(k.itemId(), 0), "kind " + k + " trip " + i);
			}
			for (String id : List.of(HuntTable.LEATHER, HuntTable.FEATHER, HuntTable.RABBIT_HIDE)) {
				assertEquals(plain.getOrDefault(id, 0), bow.getOrDefault(id, 0), id + " trip " + i);
			}
		}
	}

	/** T-L1-5 */
	@Test
	void sameIndexSameMapAndAPinnedLiteral() {
		for (int i = 0; i < 50; i++) assertEquals(HuntTable.roll(W, true, g(i)), HuntTable.roll(W, true, g(i)));
		// regression pin, recorded on the first run (M17): a changed draw order or table shows up here
		assertEquals(PIN, HuntTable.roll(W, false, HaulRng.forHaul(0, new UUID(1, 2), 0)).toString());
	}

	static final String PIN = "{minecraft:beef=2, minecraft:chicken=2, minecraft:leather=2, minecraft:feather=1}";
}
