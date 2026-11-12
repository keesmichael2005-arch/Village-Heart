package dev.villageheart.mixin;

import net.minecraft.world.entity.raid.Raid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The saved group count. It is {@code final} in vanilla and set from the difficulty, so a young-village cap writes it
 * before the first wave (docs/plans/M25.md 3.13a). Vanilla stores it as {@code group_count}.
 */
@Mixin(Raid.class)
public interface RaidAccessor {
	@Accessor("numGroups")
	int villageheart$numGroups();

	@Mutable
	@Accessor("numGroups")
	void villageheart$setNumGroups(int groups);
}
