package dev.villageheart.core.food;

import java.util.List;
import java.util.Map;

public record DinnerPlan<K>(List<Meal<K>> meals, Map<K, Long> consumed, int diners, int fed, int hungry,
		long eatenNutrition, long eatenItems) {}
