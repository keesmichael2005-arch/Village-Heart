package dev.villageheart.core.stock;

import org.jetbrains.annotations.Nullable;

/** One container's slots. {@code K} is the item identity (equals/hashCode = "stacks together"). */
public interface Inventory<K> {
	int size();

	/** The item in the slot, null when empty. */
	@Nullable K key(int slot);

	/** Count in the slot, 0 when empty. */
	int count(int slot);

	/** Max count of {@code key} this slot can hold; 0 = the slot refuses the key. */
	int limit(int slot, K key);

	/** Slot empty or holding {@code key}; {@code n > 0}; {@code count + n <= limit} (else IllegalStateException). */
	void add(int slot, K key, int n);

	/** {@code 0 < n <= count}; the slot becomes empty at 0. */
	void remove(int slot, int n);

	/** Called once per touched inventory at the end of an operation. */
	void changed();
}
