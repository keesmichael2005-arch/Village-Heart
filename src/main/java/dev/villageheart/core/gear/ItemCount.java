package dev.villageheart.core.gear;

/** A namespaced item id and a count. */
public record ItemCount(String id, int count) {
	public ItemCount {
		if (count < 0) throw new IllegalArgumentException("count < 0");
	}
}
