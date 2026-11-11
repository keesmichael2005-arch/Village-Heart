package dev.villageheart.worker.shelter;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import dev.villageheart.core.Balance;
import dev.villageheart.core.ClaimGeometry;
import dev.villageheart.core.VillageState;
import dev.villageheart.raid.VillageThreats;
import dev.villageheart.worker.WorkerAttachments;
import dev.villageheart.worker.WorkerBrain;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.world.VillageSystem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.phys.AABB;

/**
 * Every villager in the claim who is not hired goes indoors while a raid or a zombie siege lasts (owner, 2026-11-11), to a valid
 * House bed or the nearest valid room: the shelter a hired worker uses ({@link ShelterRuntime}). Hired workers have their own brain
 * and guards stay out to fight, so neither is touched here. The bell ({@code VillageBell}) is rung when the threat starts; during
 * a raid vanilla ignores it, so this system also puts the unhired villagers into the {@code villageheart:shelter} activity.
 */
public final class UnhiredShelter implements VillageSystem {
	public static final UnhiredShelter INSTANCE = new UnhiredShelter();

	/** Brains that already have the shelter package. {@code addActivity} would add a second copy of the behaviour. */
	private static final Set<Brain<?>> EQUIPPED = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
	private static final List<Villager> SHELTERING = new ArrayList<>();
	private static long lastScan = Long.MIN_VALUE;
	private static List<Villager> cache = List.of();

	private UnhiredShelter() {}

	public static void clearForTests() {
		EQUIPPED.clear();
		SHELTERING.clear();
		ShelterHold.clearForTests();
		lastScan = Long.MIN_VALUE;
		cache = List.of();
	}

	/** How many unhired villagers are under the shelter hold now. */
	public static int sheltering() { return SHELTERING.size(); }

	@Override
	public void tick(MinecraftServer server, VillageState village) {
		ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(village.heart().dimension())));
		if (level == null) return;
		long now = level.getGameTime();
		if (now % Balance.SHELTER_ENFORCE_TICKS != 0) return;
		boolean siege = village.threats().siege().isPresent();
		boolean raid = village.threats().raid().isPresent();
		if (!siege && !raid) {
			releaseAll(level);
			return;
		}
		if (lastScan == Long.MIN_VALUE || now - lastScan >= Balance.RAID_CHECK_TICKS) {
			lastScan = now;
			cache = unhired(level, village);
		}
		for (Villager v : cache) {
			if (!v.isAlive() || v.isRemoved()) continue;
			if (!siege && !VillageThreats.nearRaid(level, v.blockPosition())) {
				release(level, v);
				continue;
			}
			hold(level, v);
		}
	}

	private static List<Villager> unhired(ServerLevel level, VillageState village) {
		BlockPos heart = new BlockPos(village.heart().x(), village.heart().y(), village.heart().z());
		AABB box = new AABB(heart).inflate(Balance.CLAIM_RADIUS);
		return level.getEntities(EntityType.VILLAGER, box, e -> e.isAlive() && !WorkerAttachments.isWorker(e)
				&& ClaimGeometry.inClaim(village.heart(), VillageClaims.locate(level, e.blockPosition())));
	}

	private static void hold(ServerLevel level, Villager v) {
		if (v.isSleeping()) return;
		Brain<Villager> brain = v.getBrain();
		if (EQUIPPED.add(brain)) {
			brain.addActivity(WorkerBrain.SHELTER, ImmutableList.of(Pair.of(0, new ShelterBehaviour())));
		}
		ShelterHold.hold(brain);
		if (!SHELTERING.contains(v)) SHELTERING.add(v);
		if (!brain.isActive(WorkerBrain.SHELTER)) brain.setActiveActivityIfPossible(WorkerBrain.SHELTER);
	}

	private static void release(ServerLevel level, Villager v) {
		Brain<Villager> brain = v.getBrain();
		SHELTERING.remove(v);
		if (!ShelterHold.holds(brain)) return;
		ShelterHold.release(brain);
		if (brain.isActive(WorkerBrain.SHELTER)) {
			ShelterRuntime.release(v);
			brain.setDefaultActivity(Activity.IDLE);
			brain.eraseMemory(MemoryModuleType.HEARD_BELL_TIME);
			brain.updateActivityFromSchedule(level.environmentAttributes(), level.getGameTime(), v.position());
			if (brain.isActive(WorkerBrain.SHELTER)) brain.useDefaultActivity();
		}
	}

	private static void releaseAll(ServerLevel level) {
		if (SHELTERING.isEmpty() && ShelterHold.count() == 0) return;
		for (Villager v : List.copyOf(SHELTERING)) release(level, v);
		SHELTERING.clear();
		ShelterHold.clear();
	}
}
