package dev.villageheart.siege;

import java.util.EnumSet;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Walks a creeper to a wall and lights it (docs/plans/M25.md 3.11). Installed in step 12 so a reload cannot add a
 * second one. Seeking and the explosion mixin are step 14; until then {@link #canUse} stays false.
 */
public final class CreeperBreachGoal extends Goal {
	@SuppressWarnings("unused")
	private final Creeper creeper;

	public CreeperBreachGoal(Creeper creeper) {
		this.creeper = creeper;
		setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() { return false; }
}
