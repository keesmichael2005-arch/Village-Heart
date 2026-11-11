package dev.villageheart.siege;

import java.util.EnumSet;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Walks a zombie toward the village with no line-of-sight test (docs/plans/M25.md 3.11). Installed in step 12 so a
 * reload cannot add a second one. The walk itself is step 13; until then {@link #canUse} stays false.
 */
public final class VillageLureGoal extends Goal {
	@SuppressWarnings("unused")
	private final Mob mob;

	public VillageLureGoal(Mob mob) {
		this.mob = mob;
		setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	@Override
	public boolean canUse() { return false; }
}
