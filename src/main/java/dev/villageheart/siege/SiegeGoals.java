package dev.villageheart.siege;

import dev.villageheart.mixin.MobGoalsAccessor;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.level.Level;

/** Adds the siege goals once per mob. {@code ENTITY_LOAD} fires again every time a chunk loads. */
public final class SiegeGoals {
	private SiegeGoals() {}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (level.getServer() == null || !level.dimension().equals(Level.OVERWORLD)) return;
			if (entity instanceof Mob mob) install(mob);
		});
	}

	/** Adds the goals this mob should carry, skipping any that are already there. */
	public static void install(Mob mob) {
		if (!mob.level().dimension().equals(Level.OVERWORLD)) return;
		if (mob instanceof Zombie && !(mob instanceof Drowned) && !(mob instanceof ZombifiedPiglin)) {
			add(mob, 5, new VillageLureGoal(mob));
			add(mob, 4, new BreachGoal(mob));
		} else if (mob instanceof Creeper creeper) {
			add(mob, 4, new CreeperBreachGoal(creeper));
		} else if (mob instanceof Ravager || mob instanceof Vindicator) {
			add(mob, 2, new BreachGoal(mob));
		}
	}

	private static void add(Mob mob, int priority, Goal goal) {
		GoalSelector selector = ((MobGoalsAccessor) mob).villageheart$goalSelector();
		for (WrappedGoal wrapped : selector.getAvailableGoals()) {
			if (wrapped.getGoal().getClass() == goal.getClass()) return;
		}
		selector.addGoal(priority, goal);
	}
}
