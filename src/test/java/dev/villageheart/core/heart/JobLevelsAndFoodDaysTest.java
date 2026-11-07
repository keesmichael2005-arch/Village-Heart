package dev.villageheart.core.heart;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class JobLevelsAndFoodDaysTest {
	@Test void xpForNext() {
		assertEquals(100, JobLevels.xpForNext(1));
		assertEquals(300, JobLevels.xpForNext(2));
		assertEquals(0, JobLevels.xpForNext(3));
		assertThrows(IllegalArgumentException.class, () -> JobLevels.xpForNext(0));
	}

	@Test void foodDays() {
		assertEquals(33, FoodDays.tenths(100, 3, 0));
		assertEquals(10, FoodDays.tenths(30, 2, 2));
		assertEquals(FoodDays.NO_VILLAGERS, FoodDays.tenths(100, 0, 0));
		assertEquals(0, FoodDays.tenths(0, 3, 1));
		assertEquals(Integer.MAX_VALUE, FoodDays.tenths(Long.MAX_VALUE, 1, 0));
		assertEquals(Integer.MAX_VALUE, FoodDays.tenths(Long.MAX_VALUE, 1, 1));
	}
}
