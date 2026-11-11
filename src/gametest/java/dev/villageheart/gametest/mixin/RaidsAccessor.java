package dev.villageheart.gametest.mixin;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Gametest-only view of every raid, so a test can stop the ones near its structure. */
@Mixin(Raids.class)
public interface RaidsAccessor {
	@Accessor("raidMap")
	Int2ObjectMap<Raid> villageheart$raidMap();
}
