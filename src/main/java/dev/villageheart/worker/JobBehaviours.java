package dev.villageheart.worker;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * The job-specific part of a worker's brain. M7 has only the shared idle work (walk to the job block and stand there); the real jobs
 * (M9 and later) add their behaviours here, keyed by the worker's job.
 */
final class JobBehaviours {
	private JobBehaviours() {}

	/** The behaviours of the WORK activity. */
	static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> work(Villager villager) {
		return ImmutableList.of(
				Pair.of(1, new DepositTrip()),
				Pair.of(2, new GoToJobBlock()),
				Pair.of(4, new StandAtJobBlock()));
	}

	/** The behaviours of the go_home activity that come before walking to the bed (M9+: deposit trips). */
	static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> goHome(Villager villager) {
		return ImmutableList.of(Pair.of(1, new DepositTrip()));
	}
}
