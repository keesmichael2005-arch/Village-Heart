package dev.villageheart.mixin;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Every raid on the level. {@code getRaidAt} returns one raid within 96 blocks, which can be a different raid. */
@Mixin(Raids.class)
public interface RaidsView {
	@Accessor("raidMap")
	Int2ObjectMap<Raid> villageheart$raidMap();
}
