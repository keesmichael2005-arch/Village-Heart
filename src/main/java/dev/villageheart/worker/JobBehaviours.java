package dev.villageheart.worker;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.core.worker.WorkerData;
import dev.villageheart.worker.cook.CookJob;
import dev.villageheart.worker.cook.CookWork;
import dev.villageheart.worker.build.BuilderKeep;
import dev.villageheart.worker.build.BuilderWork;
import dev.villageheart.worker.farm.FarmJob;
import dev.villageheart.worker.farm.FarmerWork;
import dev.villageheart.worker.lumber.LumberKeep;
import dev.villageheart.worker.lumber.LumberjackWork;
import dev.villageheart.worker.hunt.HuntGear;
import dev.villageheart.worker.hunt.HunterWork;
import dev.villageheart.worker.mine.MinerKeep;
import dev.villageheart.worker.mine.MinerWork;
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
		if (jobOf(villager) == JobType.FARMER) {
			// the farmer walks to its fields: no GoToJobBlock either
			return ImmutableList.of(
					Pair.of(1, new DepositTrip()),
					Pair.of(3, new FarmerWork()),
					Pair.of(4, new StandAtJobBlock()));
		}
		if (jobOf(villager) == JobType.MINER) {
			// the miner walks to its Mineshaft: no GoToJobBlock either
			return ImmutableList.of(
					Pair.of(1, new DepositTrip()),
					Pair.of(3, new MinerWork()),
					Pair.of(4, new StandAtJobBlock()));
		}
		if (jobOf(villager) == JobType.COOK) {
			// the cook walks to its Storage, crafting table and smoker itself: no GoToJobBlock
			return ImmutableList.of(
					Pair.of(1, new DepositTrip()),
					Pair.of(3, new CookWork()),
					Pair.of(4, new StandAtJobBlock()));
		}
		if (jobOf(villager) == JobType.HUNTER) {
			// the hunter walks to its Hunting Ground: no GoToJobBlock either
			return ImmutableList.of(
					Pair.of(1, new DepositTrip()),
					Pair.of(3, new HunterWork()),
					Pair.of(4, new StandAtJobBlock()));
		}
		if (jobOf(villager) == JobType.MERCHANT) {
			// the merchant walks to its stall like the default, and sells while it stands there (M22)
			return ImmutableList.of(
					Pair.of(1, new DepositTrip()),
					Pair.of(2, new GoToJobBlock()),
					Pair.of(3, new dev.villageheart.market.MerchantWork()),
					Pair.of(4, new StandAtJobBlock()));
		}
		return ImmutableList.of(
				Pair.of(1, new DepositTrip()),
				Pair.of(2, new GoToJobBlock()),
				Pair.of(4, new StandAtJobBlock()));
	}

	/** The shared food top-up (FoodKeep) does not apply to a cook: its food is its product (M13). */
	static boolean foodKeepApplies(Villager villager) { return jobOf(villager) != JobType.COOK; }

	private static JobType jobOf(Villager villager) {
		return WorkerAttachments.get(villager).map(WorkerData::job).orElse(null);
	}

	/**
	 * How many of {@code item} this worker never hands in to Storage (besides the food every villager keeps, {@link dev.villageheart.core.job.FoodKeep}): what its job needs for its
	 * own work. {@code 0} = everything is handed in.
	 */
	static int keep(Villager villager, Item item) {
		if (jobOf(villager) == JobType.LUMBERJACK) return LumberKeep.keep(villager, item);
		if (jobOf(villager) == JobType.BUILDER) return BuilderKeep.keep(villager, item);
		if (jobOf(villager) == JobType.FARMER) return FarmJob.keep(villager, item);
		if (jobOf(villager) == JobType.COOK) return CookJob.keep(villager, item);
		return 0;
	}

	/**
	 * True when this worker's job wants a deposit trip now, whatever the load (M16: the miner hands in its haul as soon as it is back
	 * on the surface, however small). The trip still needs something to hand in.
	 */
	static boolean depositNow(Villager villager) {
		JobType job = jobOf(villager);
		if (job == JobType.COOK) return CookJob.depositNow(villager);
		if (job == JobType.MINER) return MinerKeep.carriesNonFood(villager);
		// the hunter's rule is by item id, not by the food component: raw meat has one, and it is handed in (M17)
		return job == JobType.HUNTER && HuntGear.carriesYield(villager);
	}

	/** The behaviours of the go_home activity that come before walking to the bed (M9+: deposit trips). */
	static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> goHome(Villager villager) {
		return ImmutableList.of(Pair.of(1, new DepositTrip()));
	}
}
