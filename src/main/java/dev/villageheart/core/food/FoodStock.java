package dev.villageheart.core.food;

/** One kind of food in the stockpile that dinner may eat: {@code available} already excludes build reservations. */
public record FoodStock<K>(K key, String id, FoodValue value, long available) {}
