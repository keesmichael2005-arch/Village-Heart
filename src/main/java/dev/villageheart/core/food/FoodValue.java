package dev.villageheart.core.food;

/** What one item feeds: nutrition and saturation points, and whether it is raw (a Cook improves it, M13). */
public record FoodValue(int nutrition, float saturation, boolean raw) {}
