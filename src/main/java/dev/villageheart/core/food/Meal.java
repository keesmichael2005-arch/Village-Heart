package dev.villageheart.core.food;

import java.util.Map;
import java.util.UUID;

/** What one villager ate: {@code fed} when it reached its need. */
public record Meal<K>(UUID eater, boolean fed, int nutrition, Map<K, Integer> items) {}
