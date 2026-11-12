package dev.villageheart.gametest.mixin;

import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** GameTest-only write of a furnace's burn time, so a scene can hold a furnace really lit. Production code does not use this. */
@Mixin(AbstractFurnaceBlockEntity.class)
public interface FurnaceBurnSetter {
	@Accessor("litTimeRemaining")
	void villageheart$setLitTimeRemaining(int ticks);

	@Accessor("litTotalTime")
	void villageheart$setLitTotalTime(int ticks);
}
