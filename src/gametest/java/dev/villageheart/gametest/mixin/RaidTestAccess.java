package dev.villageheart.gametest.mixin;

import net.minecraft.world.entity.raid.Raid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Gametest-only reads and writes of a raid's private counters (M25). {@code numGroups} stays final. */
@Mixin(Raid.class)
public interface RaidTestAccess {
	@Accessor("raidCooldownTicks")
	int villageheart$raidCooldownTicks();

	@Accessor("raidCooldownTicks")
	void villageheart$setRaidCooldownTicks(int ticks);

	@Accessor("groupsSpawned")
	int villageheart$groupsSpawned();

	@Accessor("groupsSpawned")
	void villageheart$setGroupsSpawned(int groups);

	@Accessor("started")
	boolean villageheart$started();

	@Accessor("started")
	void villageheart$setStarted(boolean started);
}
