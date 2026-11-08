package dev.villageheart.worker;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.core.worker.WorkerData;
import dev.villageheart.worker.build.BuilderKeep;
import dev.villageheart.worker.build.BuilderWork;
import dev.villageheart.worker.lumber.LumberKeep;
import dev.villageheart.worker.lumber.LumberjackWork;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * The job-specific part of a worker's brain. M7 has only the shared idle work (walk to the job block and stand there); the real jobs
 * (M9 and later) add their behaviours here, keyed by the worker's job.
 */
final class JobBehaviours {
	private JobBehaviours() {}

	/** The behaviours of the WORK activity. */
	static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> work(Villager villager) {
		if (jobOf(villager) == JobType.LUMBERJACK) {
			// the lumberjack walks where its trees are: no GoToJobBlock (it would fight for the walk target between logs)
			return ImmutableList.of(
					Pair.of(1, new DepositTrip()),
					Pair.of(3, new LumberjackWork()),
					Pair.of(4, new StandAtJobBlock()));
		}
		if (jobOf(villager) == JobType.BUILDER) {
			// the builder walks to its site and to Storage: no GoToJobBlock, as for the lumberjack
			return ImmutableList.of(
					Pair.of(1, new DepositTrip()),
					Pair.of(3, new BuilderWork()),
					Pair.of(4, new StandAtJobBlock()));
		}
		return ImmutableList.of(
				Pair.of(1, new DepositTrip()),
				Pair.of(2, new GoToJobBlock()),
				Pair.of(4, new StandAtJobBlock()));
	}

	private static JobType jobOf(Villager villager) {
		return WorkerAttachments.get(villager).map(WorkerData::job).orElse(null);
	}

	/**
	 * How many of {@code item} this worker never hands in to Storage (besides the food every villager keeps): what its job needs for its
	 * own work. {@code 0} = everything is handed in.
	 */
	static int keep(Villager villager, Item item) {
		if (jobOf(villager) == JobType.LUMBERJACK) return LumberKeep.keep(villager, item);
		if (jobOf(villager) == JobType.BUILDER) return BuilderKeep.keep(villager, item);
		return 0;
	}

	/** The behaviours of the go_home activity that come before walking to the bed (M9+: deposit trips). */
	static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> goHome(Villager villager) {
		return ImmutableList.of(Pair.of(1, new DepositTrip()));
	}
}
