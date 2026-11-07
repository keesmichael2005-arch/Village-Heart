package dev.villageheart.core.stock;

import dev.villageheart.core.room.Pos;

/** One container (a chest half or a barrel) at a position. */
public record ContainerRef<K>(Pos pos, Inventory<K> inventory) {}
