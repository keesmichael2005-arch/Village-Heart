package dev.villageheart.mixin;

import net.minecraft.world.entity.raid.Raid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The raid's real group count. {@code getNumGroups(difficulty)} is the uncapped table, which is wrong once a raid is capped. */
@Mixin(Raid.class)
public interface RaidGroups {
	@Accessor("numGroups")
	int villageheart$numGroups();
}
