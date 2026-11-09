package dev.villageheart.core.food;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DinnerPlannerTest {
	static final FoodValue BREAD = new FoodValue(5, 6.0f, false), BEEF = new FoodValue(8, 12.8f, false), CARROT = new FoodValue(3, 3.6f, false),
			RAW_BEEF = new FoodValue(3, 1.8f, true), SOUP = new FoodValue(6, 7.2f, false);

	static FoodStock<String> s(String k, FoodValue v, long n) { return new FoodStock<>(k, "minecraft:" + k, v, n); }
	static UUID id(int n) { return new UUID(0, n); }
	static Eater adult(int n) { return new Eater(id(n), false, 0, -1); }
	static Eater child(int n) { return new Eater(id(n), true, 0, -1); }
	static Meal<String> meal(DinnerPlan<String> p, UUID who) { return p.meals().stream().filter(m -> m.eater().equals(who)).findFirst().orElseThrow(); }

	@Test void fourAdultsTwentyBreadEatTwoEach() {
		DinnerPlan<String> p = DinnerPlanner.plan(List.of(s("bread", BREAD, 20)), List.of(adult(1), adult(2), adult(3), adult(4)));
		assertEquals(Map.of("bread", 8L), p.consumed());
		assertEquals(4, p.fed());
		assertEquals(0, p.hungry());
		assertEquals(40, p.eatenNutrition());
		assertEquals(8, p.eatenItems());
		for (Meal<String> m : p.meals()) assertEquals(Map.of("bread", 2), m.items());
	}

	@Test void preparedBeforeRaw() {
		DinnerPlan<String> p = DinnerPlanner.plan(List.of(s("bread", BREAD, 2), s("beef", RAW_BEEF, 5)), List.of(adult(1)));
		assertEquals(Map.of("bread", 2L), p.consumed());
	}

	@Test void preparedFoodIsEatenBeforeRichRawFood() {
		FoodValue cookie = new FoodValue(2, 0.4f, false);
		// 5 cookies feed the adult exactly; the raw beef (3 each, more than a cookie) stays untouched
		DinnerPlan<String> p = DinnerPlanner.plan(List.of(s("beef", RAW_BEEF, 10), s("cookie", cookie, 5)), List.of(adult(1)));
		assertEquals(Map.of("cookie", 5L), p.consumed());
		// with too few cookies the raw food tops it up after them
		DinnerPlan<String> q = DinnerPlanner.plan(List.of(s("beef", RAW_BEEF, 10), s("cookie", cookie, 2)), List.of(adult(1)));
		assertEquals(Map.of("cookie", 2L, "beef", 2L), q.consumed());
	}

	@Test void bestFirstThenNext() {
		DinnerPlan<String> p = DinnerPlanner.plan(List.of(s("cooked_beef", BEEF, 1), s("carrot", CARROT, 1), s("bread", BREAD, 1)), List.of(adult(1)));
		assertEquals(Map.of("cooked_beef", 1, "bread", 1), meal(p, id(1)).items());
		assertEquals(13, meal(p, id(1)).nutrition());
		assertEquals(Map.of("cooked_beef", 1L, "bread", 1L), p.consumed());
	}

	@Test void childEatsHalf() {
		DinnerPlan<String> p = DinnerPlanner.plan(List.of(s("bread", BREAD, 1)), List.of(child(1)));
		assertTrue(meal(p, id(1)).fed());
		assertEquals(5, meal(p, id(1)).nutrition());
	}

	@Test void twoAdultsAndAChild() {
		List<Eater> eaters = List.of(adult(1), adult(2), child(3));
		DinnerPlan<String> p = DinnerPlanner.plan(List.of(s("bread", BREAD, 5)), eaters);
		assertEquals(3, p.fed());
		assertEquals(Map.of("bread", 2), meal(p, id(1)).items());
		assertEquals(Map.of("bread", 1), meal(p, id(3)).items());
		DinnerPlan<String> shortOf = DinnerPlanner.plan(List.of(s("bread", BREAD, 4)), eaters);
		assertFalse(meal(shortOf, id(3)).fed());
		assertEquals(Map.of("bread", 4L), shortOf.consumed());
		assertEquals(2, shortOf.fed());
	}

	@Test void adultTakesTheBestChildTheRest() {
		Random r = new Random(7);
		for (int i = 0; i < 20; i++) {
			UUID a = new UUID(r.nextLong(), r.nextLong()), c = new UUID(r.nextLong(), r.nextLong());
			DinnerPlan<String> p = DinnerPlanner.plan(List.of(s("cooked_beef", BEEF, 1), s("bread", BREAD, 1), s("soup", SOUP, 1)),
					List.of(new Eater(c, true, 0, -1), new Eater(a, false, 0, -1)));
			assertEquals(Map.of("cooked_beef", 1, "soup", 1), meal(p, a).items());
			assertEquals(Map.of("bread", 1), meal(p, c).items());
		}
	}

	@Test void notEnoughMeansNobodyEatsAPartialMeal() {
		DinnerPlan<String> p = DinnerPlanner.plan(List.of(s("carrot", CARROT, 3)), List.of(adult(1), adult(2)));
		assertEquals(0, p.fed());
		assertEquals(2, p.hungry());
		assertTrue(p.consumed().isEmpty());
		assertEquals(0, p.eatenNutrition());
	}

	@Test void hungriestEatFirst() {
		Eater a = new Eater(id(1), false, 1, -1), b = new Eater(id(2), false, 0, -1);
		DinnerPlan<String> p = DinnerPlanner.plan(List.of(s("bread", BREAD, 2)), List.of(b, a));
		assertTrue(meal(p, id(1)).fed());
		assertFalse(meal(p, id(2)).fed());
	}

	@Test void workersInHiringOrderThenAdultsThenChildren() {
		Eater w1 = new Eater(id(9), false, 0, 1), w0 = new Eater(id(8), false, 0, 0), a = adult(1), c = child(2);
		DinnerPlan<String> p = DinnerPlanner.plan(List.of(s("bread", BREAD, 4)), List.of(c, a, w1, w0));
		assertTrue(meal(p, id(8)).fed());
		assertTrue(meal(p, id(9)).fed());
		assertFalse(meal(p, id(1)).fed());
		assertFalse(meal(p, id(2)).fed());
		assertEquals(id(8), p.meals().get(0).eater());
	}

	@Test void tiesBreakByUuid() {
		DinnerPlan<String> p = DinnerPlanner.plan(List.of(s("bread", BREAD, 2)), List.of(adult(5), adult(3)));
		assertTrue(meal(p, id(3)).fed());
		assertFalse(meal(p, id(5)).fed());
	}

	@Test void totalsMatchMeals() {
		DinnerPlan<String> p = DinnerPlanner.plan(List.of(s("bread", BREAD, 7), s("carrot", CARROT, 2)), List.of(adult(1), adult(2), child(3)));
		long nutrition = 0, items = 0;
		int fed = 0;
		for (Meal<String> m : p.meals()) {
			nutrition += m.nutrition();
			for (int n : m.items().values()) items += n;
			if (m.fed()) fed++;
		}
		assertEquals(nutrition, p.eatenNutrition());
		assertEquals(items, p.eatenItems());
		assertEquals(fed, p.fed());
		assertEquals(p.diners() - p.fed(), p.hungry());
		assertEquals(3, p.diners());
	}

	@Test void emptyAndZeroEntriesAreIgnored() {
		DinnerPlan<String> p = DinnerPlanner.plan(List.of(s("bread", BREAD, 0), s("air", new FoodValue(0, 0, false), 50)), List.of(adult(1)));
		assertEquals(1, p.hungry());
		assertTrue(p.consumed().isEmpty());
	}

	@Test void shuffledInputGivesTheSamePlan() {
		List<FoodStock<String>> stock = new ArrayList<>(List.of(s("bread", BREAD, 3), s("cooked_beef", BEEF, 2), s("carrot", CARROT, 4), s("beef", RAW_BEEF, 5)));
		List<Eater> eaters = new ArrayList<>(List.of(adult(1), adult(2), child(3), new Eater(id(4), false, 1, 0), new Eater(id(5), false, 0, 1)));
		DinnerPlan<String> base = DinnerPlanner.plan(stock, eaters);
		for (int seed = 0; seed < 20; seed++) {
			Collections.shuffle(stock, new Random(seed));
			Collections.shuffle(eaters, new Random(seed));
			DinnerPlan<String> p = DinnerPlanner.plan(stock, eaters);
			assertEquals(base.meals(), p.meals());
			assertEquals(base.consumed(), p.consumed());
		}
	}
}
