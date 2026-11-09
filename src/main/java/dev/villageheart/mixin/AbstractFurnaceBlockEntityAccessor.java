package dev.villageheart.mixin;

import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** M13: the Kitchen sweep must not unlight a smoker that burns by itself (a player's), and {@code isLit()} is private. */
@Mixin(AbstractFurnaceBlockEntity.class)
public interface AbstractFurnaceBlockEntityAccessor {
	@Accessor("litTimeRemaining")
	int villageheart$litTimeRemaining();
}
