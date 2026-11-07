package dev.villageheart.stock;

import dev.villageheart.core.stock.Inventory;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** One vanilla {@link Container} (a single chest half or a barrel) as a stockpile inventory. Never the combined double-chest container. */
final class ContainerInventory implements Inventory<StackKey> {
	private final Container container;

	ContainerInventory(Container container) { this.container = container; }

	@Override public int size() { return container.getContainerSize(); }

	@Override
	public @Nullable StackKey key(int slot) { return StackKey.of(container.getItem(slot)); }

	@Override public int count(int slot) { return container.getItem(slot).getCount(); }

	@Override
	public int limit(int slot, StackKey key) {
		ItemStack sample = key.sample();
		return container.canPlaceItem(slot, sample) ? container.getMaxStackSize(sample) : 0;
	}

	@Override
	public void add(int slot, StackKey key, int n) {
		ItemStack have = container.getItem(slot);
		if (have.isEmpty()) {
			container.setItem(slot, key.toStack(n));
		} else {
			have.grow(n);
		}
	}

	@Override
	public void remove(int slot, int n) {
		ItemStack have = container.getItem(slot);
		have.shrink(n);
		if (have.isEmpty()) container.setItem(slot, ItemStack.EMPTY);
	}

	@Override public void changed() { container.setChanged(); }
}
