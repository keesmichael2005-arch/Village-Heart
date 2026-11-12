package dev.villageheart.worker.gear;

import dev.villageheart.core.gear.GearNeed;
import dev.villageheart.core.gear.MaterialRole;
import dev.villageheart.core.gear.PlankTable;
import dev.villageheart.core.gear.ToolWear;
import dev.villageheart.core.gear.WearProfile;
import dev.villageheart.core.job.ToolKind;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.stock.StackKey;
import dev.villageheart.worker.job.CampTools;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Item-level helpers of the self-craft code (M19a): durability, material roles, which stored tools a worker may take. */
public final class ToolStacks {
	private ToolStacks() {}

	public static boolean isToolOf(GearNeed need, ItemStack s) { return CampTools.isTool(need.toolKind(), s); }

	/** Which of the four needs this stack is a tool for, if any. */
	public static Optional<GearNeed> needOf(ItemStack s) {
		for (ToolKind k : ToolKind.values()) if (CampTools.isTool(k, s)) return Optional.of(GearNeed.of(k));
		return Optional.empty();
	}

	public static int max(ItemStack s) { return s.getMaxDamage(); }

	public static int remaining(ItemStack s) { return Math.max(0, s.getMaxDamage() - s.getDamageValue()); }

	public static boolean worn(ItemStack s, JobType job) {
		WearProfile w = WearProfile.of(job);
		return ToolWear.worn(remaining(s), max(s), w.wearPerUnit(), w.unitTicks(), 0);
	}

	/**
	 * A tool in Storage a worker may fetch instead of crafting (plan 3.2 #6): of the need's kind, not worn, no enchantment and no custom
	 * name (the player's special items stay where they are).
	 */
	public static boolean storedUsable(ItemStack s, GearNeed need, JobType job) {
		if (s.isEmpty() || !isToolOf(need, s) || worn(s, job)) return false;
		if (s.has(DataComponents.CUSTOM_NAME)) return false;
		var enchants = s.get(DataComponents.ENCHANTMENTS);
		return enchants == null || enchants.isEmpty();
	}

	/** The role of a stockpile item in a tool recipe. Only component-free stacks count (no renamed planks). */
	public static Optional<MaterialRole> roleOf(StackKey key) {
		if (!key.patch().isEmpty()) return Optional.empty();
		if (key.item() == Items.STICK) return Optional.of(MaterialRole.STICK);
		if (key.is(ItemTags.PLANKS)) return Optional.of(MaterialRole.PLANKS);
		if (key.is(ItemTags.STONE_TOOL_MATERIALS)) return Optional.of(MaterialRole.STONE);
		if (key.is(ItemTags.LOGS) && PlankTable.planksOf(key.id()).isPresent()) return Optional.of(MaterialRole.LOGS);
		if (key.item() == Items.COPPER_INGOT) return Optional.of(MaterialRole.COPPER_INGOT);
		if (key.item() == Items.IRON_INGOT) return Optional.of(MaterialRole.IRON_INGOT);
		if (key.item() == Items.DIAMOND) return Optional.of(MaterialRole.DIAMOND);
		if (key.item() == Items.LEATHER) return Optional.of(MaterialRole.LEATHER);
		if (key.item() == Items.RAW_COPPER) return Optional.of(MaterialRole.RAW_COPPER);
		if (key.item() == Items.RAW_IRON) return Optional.of(MaterialRole.RAW_IRON);
		return Optional.empty();
	}

	/** A component-free, undamaged tool or armour piece in Storage. The smith may carry it to a camp. */
	public static boolean storedGear(ItemStack s) {
		if (s.isEmpty() || s.isDamaged() || !s.getComponentsPatch().isEmpty()) return false;
		if (s.has(DataComponents.CUSTOM_NAME)) return false;
		var enchants = s.get(DataComponents.ENCHANTMENTS);
		if (enchants != null && !enchants.isEmpty()) return false;
		return dev.villageheart.core.gear.GearInfo.of(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).toString()).isPresent();
	}
}
