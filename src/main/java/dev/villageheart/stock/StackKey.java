package dev.villageheart.stock;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The stockpile's item identity: the item plus its component patch. Equals {@code ItemStack.isSameItemSameComponents}
 * (vanilla compares the item and the patched component maps, whose prototype is the item's own default map, so for one item
 * that is patch equality; proven pairwise by the GameTest {@code componentsAreRespected}).
 */
public final class StackKey {
	private final Item item;
	private final DataComponentPatch patch;
	private final int hash;
	private int maxStack = -1;
	@Nullable
	private ItemStack one;

	private StackKey(Item item, DataComponentPatch patch) {
		this.item = item;
		this.patch = patch;
		this.hash = 31 * item.hashCode() + patch.hashCode();
	}

	/** Null for an empty stack. */
	@Nullable
	public static StackKey of(ItemStack s) {
		return s.isEmpty() ? null : new StackKey(s.getItem(), s.getComponentsPatch());
	}

	public static StackKey of(Item item) { return new StackKey(item, DataComponentPatch.EMPTY); }

	public Item item() { return item; }

	public DataComponentPatch patch() { return patch; }

	public ItemStack toStack(int n) { return new ItemStack(item.builtInRegistryHolder(), n, patch); }

	/** A cached one-item stack for limit checks. Never mutate it. */
	ItemStack sample() {
		ItemStack s = one;
		if (s == null) one = s = toStack(1);
		return s;
	}

	public int maxStackSize() {
		if (maxStack < 0) maxStack = sample().getMaxStackSize();
		return maxStack;
	}

	/** E.g. "minecraft:paper". */
	public String id() { return BuiltInRegistries.ITEM.getKey(item).toString(); }

	public boolean is(TagKey<Item> tag) { return item.builtInRegistryHolder().is(tag); }

	@Override
	public boolean equals(Object o) {
		return this == o || o instanceof StackKey k && k.item == item && k.patch.equals(patch);
	}

	@Override
	public int hashCode() { return hash; }

	@Override
	public String toString() { return id() + (patch.isEmpty() ? "" : "{" + patch + "}"); }
}
