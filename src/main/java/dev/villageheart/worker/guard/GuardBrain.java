package dev.villageheart.worker.guard;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import dev.villageheart.worker.UpdateWorkerActivity;
import dev.villageheart.worker.WorkerBrain;
import java.util.Optional;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.AcquirePoi;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.InteractWithDoor;
import net.minecraft.world.entity.ai.behavior.LookAtTargetSink;
import net.minecraft.world.entity.ai.behavior.MoveToTargetSink;
import net.minecraft.world.entity.ai.behavior.SetWalkTargetFromBlockMemory;
import net.minecraft.world.entity.ai.behavior.SleepInBed;
import net.minecraft.world.entity.ai.behavior.Swim;
import net.minecraft.world.entity.ai.behavior.WakeUp;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;

/**
 * The brain of a guard (M18 3.2 #2, #3). Built from vanilla behaviours minus everything that makes a villager run from a fight:
 * no {@code VillagerPanicTrigger}, {@code ReactToBell}, {@code SetRaidStatus}, no PANIC / RAID / HIDE packages, and no HOME
 * {@code AcquirePoi} (a guard's Barracks bed is assigned by {@code GuardBedSystem}). CORE: swimming, doors and gates, looking, waking,
 * {@code UpdateWorkerActivity}, combat, walking. Activities: {@link GuardActivities#DUTY}, {@link GuardActivities#OFF} (the default, so a
 * brain dropped to its default never freezes) and vanilla REST.
 */
public final class GuardBrain {
	private static final float SPEED = 0.5F;

	private GuardBrain() {}

	public static void install(Villager villager, Brain<Villager> brain) {
		brain.addActivity(Activity.CORE, ImmutableList.of(
				WorkerBrain.p(0, new Swim<>(0.8F)),
				WorkerBrain.p(0, InteractWithDoor.create()),
				WorkerBrain.p(0, new dev.villageheart.defence.GateTraffic()),
				WorkerBrain.p(0, new LookAtTargetSink(45, 90)),
				WorkerBrain.p(0, WakeUp.create()),
				WorkerBrain.p(0, new GuardCombatBehaviour()),
				WorkerBrain.p(1, new MoveToTargetSink()),
				WorkerBrain.p(10, AcquirePoi.create(h -> h.is(PoiTypes.MEETING), MemoryModuleType.MEETING_POINT, true, Optional.of((byte) 14))),
				WorkerBrain.p(99, new UpdateWorkerActivity())));

		brain.addActivity(GuardActivities.DUTY, ImmutableList.<Pair<Integer, ? extends BehaviorControl<? super Villager>>>of(
				WorkerBrain.p(2, new GuardDutyBehaviour()),
				WorkerBrain.look()));

		brain.addActivity(GuardActivities.OFF, ImmutableList.<Pair<Integer, ? extends BehaviorControl<? super Villager>>>of(
				WorkerBrain.p(2, new GuardOffBehaviour()),
				WorkerBrain.look()));

		// vanilla's rest package minus ValidateNearbyPoi: it would erase HOME when someone else lies in the bed; the mod owns a guard's HOME
		brain.addActivity(Activity.REST, ImmutableList.<Pair<Integer, ? extends BehaviorControl<? super Villager>>>of(
				WorkerBrain.p(2, SetWalkTargetFromBlockMemory.create(MemoryModuleType.HOME, SPEED, 1, 150, 1200)),
				WorkerBrain.p(3, new SleepInBed()),
				WorkerBrain.look()));

		brain.setCoreActivities(ImmutableSet.of(Activity.CORE));
		brain.setDefaultActivity(GuardActivities.OFF);
		if (villager.level() instanceof net.minecraft.server.level.ServerLevel level) {
			brain.setActiveActivityIfPossible(Guards.activityFor(villager, level));
		} else {
			brain.setActiveActivityIfPossible(GuardActivities.OFF);
		}
	}
}
