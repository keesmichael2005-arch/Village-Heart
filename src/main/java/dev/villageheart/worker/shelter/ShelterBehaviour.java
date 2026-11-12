package dev.villageheart.worker.shelter;

import com.google.common.collect.ImmutableMap;
import dev.villageheart.worker.WorkerBrain;
import dev.villageheart.world.VillageTicker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.npc.villager.Villager;

/** Walks a hired worker to the shelter {@link ShelterRuntime} chose, and stays there while the raid lasts. */
public final class ShelterBehaviour extends Behavior<Villager> {
	public ShelterBehaviour() {
		super(ImmutableMap.of(), Integer.MAX_VALUE / 2);
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) { return true; }

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		return villager.getBrain().isActive(WorkerBrain.SHELTER);
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		try (var t = VillageTicker.section()) {
			ShelterRuntime.tick(level, villager, gameTime);
		}
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long gameTime) {
		ShelterRuntime.release(villager);
	}
}
