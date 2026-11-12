package dev.villageheart.worker;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.VillagerPanicTrigger;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;

/**
 * Vanilla {@link VillagerPanicTrigger} holds {@code PANIC} for as long as a hostile is remembered, which is the whole raid when a
 * raider stands in sight. A sheltering worker would never get back indoors. While {@link WorkerBrain#activityFor} is {@code SHELTER}
 * this puts them back in {@code SHELTER} instead, and again on later ticks if something else has switched them away. A worker who is
 * not sheltering still panics.
 */
public final class WorkerPanicTrigger extends VillagerPanicTrigger {
	@Override
	protected void start(ServerLevel level, Villager villager, long gameTime) {
		if (shelter(level, villager)) return;
		super.start(level, villager, gameTime);
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		super.tick(level, villager, gameTime);
		if (WorkerBrain.activityFor(villager, level) != WorkerBrain.SHELTER) {
			if ((isHurt(villager) || hasHostile(villager)) && !villager.getBrain().isActive(Activity.PANIC)) super.start(level, villager, gameTime);
			return;
		}
		shelter(level, villager);
	}

	/** @return true when the worker should be sheltering, and the activity was set */
	private static boolean shelter(ServerLevel level, Villager villager) {
		if (WorkerBrain.activityFor(villager, level) != WorkerBrain.SHELTER) return false;
		if (!villager.getBrain().isActive(WorkerBrain.SHELTER)) villager.getBrain().setActiveActivityIfPossible(WorkerBrain.SHELTER);
		return true;
	}
}
