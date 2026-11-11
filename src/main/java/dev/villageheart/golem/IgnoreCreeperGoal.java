package dev.villageheart.golem;

import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Creeper;

/** Iron golems never keep a creeper as a target. The owner decision of 2026-11-11: nobody attacks creepers. */
public final class IgnoreCreeperGoal extends Goal {
	private final IronGolem golem;

	public IgnoreCreeperGoal(IronGolem golem) {
		this.golem = golem;
		setFlags(EnumSet.of(Flag.TARGET));
	}

	@Override
	public boolean canUse() { return golem.getTarget() instanceof Creeper; }

	@Override
	public boolean canContinueToUse() { return golem.getTarget() instanceof Creeper; }

	@Override
	public void start() { golem.setTarget(null); }

	@Override
	public void tick() { golem.setTarget(null); }

	@Override
	public boolean requiresUpdateEveryTick() { return true; }
}
