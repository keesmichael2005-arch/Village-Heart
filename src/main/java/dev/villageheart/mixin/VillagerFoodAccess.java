package dev.villageheart.mixin;

import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Vanilla's private {@code foodLevel} (saved as {@code FoodLevel}): the courtship tops it up for a licensed pair and restores it (M23 plan 3.2 #5). */
@Mixin(Villager.class)
public interface VillagerFoodAccess {
	@Accessor("foodLevel")
	int villageheart$getFoodLevel();

	@Accessor("foodLevel")
	void villageheart$setFoodLevel(int level);
}
