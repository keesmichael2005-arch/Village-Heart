package dev.villageheart.worker;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import dev.villageheart.VillageHeart;
import dev.villageheart.core.worker.DayPhase;
import dev.villageheart.core.worker.DaySchedule;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.AcquirePoi;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.DoNothing;
import net.minecraft.world.entity.ai.behavior.GateBehavior;
import net.minecraft.world.entity.ai.behavior.InsideBrownianWalk;
import net.minecraft.world.entity.ai.behavior.InteractWith;
import net.minecraft.world.entity.ai.behavior.InteractWithDoor;
import net.minecraft.world.entity.ai.behavior.LookAtTargetSink;
import net.minecraft.world.entity.ai.behavior.MoveToTargetSink;
import net.minecraft.world.entity.ai.behavior.ReactToBell;
import net.minecraft.world.entity.ai.behavior.RunOne;
import net.minecraft.world.entity.ai.behavior.SetClosestHomeAsWalkTarget;
import net.minecraft.world.entity.ai.behavior.SetEntityLookTarget;
import net.minecraft.world.entity.ai.behavior.SetLookAndInteract;
import net.minecraft.world.entity.ai.behavior.SetRaidStatus;
import net.minecraft.world.entity.ai.behavior.SetWalkTargetFromBlockMemory;
import net.minecraft.world.entity.ai.behavior.SetWalkTargetFromLookTarget;
import net.minecraft.world.entity.ai.behavior.SleepInBed;
import net.minecraft.world.entity.ai.behavior.Swim;
import net.minecraft.world.entity.ai.behavior.ValidateNearbyPoi;
import net.minecraft.world.entity.ai.behavior.VillageBoundRandomStroll;
import net.minecraft.world.entity.ai.behavior.VillagerGoalPackages;
import net.minecraft.world.entity.ai.behavior.VillagerMakeLove;
import net.minecraft.world.entity.ai.behavior.VillagerPanicTrigger;
import net.minecraft.world.entity.ai.behavior.WakeUp;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The brain of a hired villager (DESIGN section 12: brain mixin). Built from vanilla behaviours by their public factories, minus every
 * profession behaviour (job-site acquisition, validation, {@code ResetProfession}, ...), so the mod profession sticks and vanilla never
 * takes the villager's job away. The day shape comes from core's {@code DaySchedule}, applied by {@link UpdateWorkerActivity}; no vanilla
 * schedule is set.
 */
public final class WorkerBrain {
	private static final float SPEED = 0.5F;

	/** Worker has handed in its tools and is walking home; no sleeping yet (SleepInBed has no darkness check). */
	public static final Activity GO_HOME = Registry.register(BuiltInRegistries.ACTIVITY,
			Identifier.fromNamespaceAndPath(VillageHeart.MOD_ID, "go_home"), new Activity("go_home"));

	/** Brains installed by {@link #install}, so the worker system can notice a worker running a vanilla brain. */
	private static final Set<Brain<?>> INSTALLED = Collections.newSetFromMap(new WeakHashMap<>());

	private WorkerBrain() {}

	/** Touch the class during mod init so the activity is registered before any brain is built. */
	public static void register() {}

	public static boolean isWorkerBrain(Villager v) {
		synchronized (INSTALLED) {
			return INSTALLED.contains(v.getBrain());
		}
	}

	/** The activity a worker should be in at this day time. */
	public static Activity activityFor(long dayTime) {
		DayPhase phase = DaySchedule.phaseAt(dayTime);
		return switch (phase) {
			case WAKE -> Activity.IDLE;
			case WORK -> Activity.WORK;
			case GO_HOME -> GO_HOME;
			case SLEEP -> Activity.REST;
		};
	}

	/** Vanilla's bed rule ({@code VillagerGoalPackages.validateBedPoi} is private): a bed that nobody is lying in. */
	static boolean bedIsFree(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.is(BlockTags.BEDS) && !state.getValue(BedBlock.OCCUPIED);
	}

	private static Pair<Integer, BehaviorControl<? super Villager>> p(int priority, BehaviorControl<? super Villager> b) {
		return Pair.of(priority, b);
	}

	private static Pair<Integer, BehaviorControl<? super Villager>> look() {
		return p(5, new RunOne<>(ImmutableList.of(
				Pair.of(SetEntityLookTarget.create(EntityType.VILLAGER, 8.0F), 2),
				Pair.of(SetEntityLookTarget.create(EntityType.PLAYER, 8.0F), 2),
				Pair.of(new DoNothing(30, 60), 8))));
	}

	public static void install(Villager villager, Brain<Villager> brain) {
		Holder<VillagerProfession> profession = villager.getVillagerData().profession();
		brain.addActivity(Activity.CORE, ImmutableList.of(
				p(0, new Swim<>(0.8F)),
				p(0, InteractWithDoor.create()),
				p(0, new LookAtTargetSink(45, 90)),
				p(0, new VillagerPanicTrigger()),
				p(0, WakeUp.create()),
				p(0, ReactToBell.create()),
				p(0, SetRaidStatus.create()),
				p(1, new MoveToTargetSink()),
				p(10, AcquirePoi.create(h -> h.is(PoiTypes.HOME), MemoryModuleType.HOME, false, Optional.of((byte) 14), WorkerBrain::bedIsFree)),
				p(10, AcquirePoi.create(h -> h.is(PoiTypes.MEETING), MemoryModuleType.MEETING_POINT, true, Optional.of((byte) 14)))));

		brain.addActivity(Activity.IDLE, ImmutableList.of(
				p(2, new RunOne<>(ImmutableList.of(
						Pair.of(InteractWith.of(EntityType.VILLAGER, 8, AgeableMob::canBreed, AgeableMob::canBreed, MemoryModuleType.BREED_TARGET, SPEED, 2), 1),
						Pair.of(VillageBoundRandomStroll.create(SPEED), 1),
						Pair.of(SetWalkTargetFromLookTarget.create(SPEED, 2), 1),
						Pair.of(new DoNothing(30, 60), 1)))),
				p(3, SetLookAndInteract.create(EntityType.PLAYER, 4)),
				p(3, new GateBehavior<>(ImmutableMap.of(), ImmutableSet.of(MemoryModuleType.BREED_TARGET),
						GateBehavior.OrderPolicy.ORDERED, GateBehavior.RunningPolicy.RUN_ONE,
						ImmutableList.of(Pair.of(new VillagerMakeLove(), 1)))),
				look(),
				p(99, new UpdateWorkerActivity())));

		brain.addActivity(Activity.WORK, ImmutableList.<Pair<Integer, ? extends BehaviorControl<? super Villager>>>builder()
				.addAll(JobBehaviours.work(villager))
				.add(look())
				.add(p(99, new UpdateWorkerActivity()))
				.build());

		brain.addActivity(GO_HOME, ImmutableList.<Pair<Integer, ? extends BehaviorControl<? super Villager>>>builder()
				.addAll(JobBehaviours.goHome(villager))
				.add(p(2, SetWalkTargetFromBlockMemory.create(MemoryModuleType.HOME, SPEED, 1, 150, 1200)))
				.add(p(3, ValidateNearbyPoi.create(h -> h.is(PoiTypes.HOME), MemoryModuleType.HOME)))
				.add(homeless())
				.add(look())
				.add(p(99, new UpdateWorkerActivity()))
				.build());

		brain.addActivity(Activity.REST, ImmutableList.of(
				p(2, SetWalkTargetFromBlockMemory.create(MemoryModuleType.HOME, SPEED, 1, 150, 1200)),
				p(3, ValidateNearbyPoi.create(h -> h.is(PoiTypes.HOME), MemoryModuleType.HOME)),
				p(3, new SleepInBed()),
				homeless(),
				look(),
				p(99, new UpdateWorkerActivity())));

		brain.addActivity(Activity.PANIC, VillagerGoalPackages.getPanicPackage(profession, SPEED));
		brain.addActivity(Activity.PRE_RAID, VillagerGoalPackages.getPreRaidPackage(profession, SPEED));
		brain.addActivity(Activity.RAID, VillagerGoalPackages.getRaidPackage(profession, SPEED));
		brain.addActivity(Activity.HIDE, VillagerGoalPackages.getHidePackage(profession, SPEED));
		brain.setCoreActivities(ImmutableSet.of(Activity.CORE));
		brain.setDefaultActivity(Activity.IDLE);
		brain.setActiveActivityIfPossible(activityFor(villager.level().getDayTime()));
		synchronized (INSTALLED) {
			INSTALLED.add(brain);
		}
	}

	/** Without a bed: wander inside, as vanilla does. */
	private static Pair<Integer, BehaviorControl<? super Villager>> homeless() {
		return p(5, new RunOne<>(ImmutableMap.of(MemoryModuleType.HOME, MemoryStatus.VALUE_ABSENT), ImmutableList.of(
				Pair.of(SetClosestHomeAsWalkTarget.create(SPEED), 1),
				Pair.of(InsideBrownianWalk.create(SPEED), 4),
				Pair.of(new DoNothing(20, 40), 2))));
	}
}
