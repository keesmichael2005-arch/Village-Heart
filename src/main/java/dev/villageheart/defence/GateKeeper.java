package dev.villageheart.defence;

import dev.villageheart.core.Balance;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.defence.GateRules;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.world.VillageSystem;
import dev.villageheart.world.VillageTicker;
import dev.villageheart.worker.Workers;
import dev.villageheart.worker.guard.GuardCombat;
import dev.villageheart.worker.guard.Guards;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;

/**
 * Opens and closes the village's fence gates for hired workers and guards (human decision 2026-11-09). {@link #open} is called by
 * {@link GateTraffic} when a path reaches a closed gate; every {@link Balance#GATE_KEEPER_TICKS} ticks the keeper looks at each gate in the
 * {@link GateLedger} and closes it when nobody needs it ({@link GateRules#shouldClose}). A gate is never left open: the ledger is saved, so
 * a reload, a fired worker or an interrupted walk still ends with the gate closed (a safety limit closes it after
 * {@link Balance#GATE_MAX_OPEN_TICKS} whatever happens).
 */
public final class GateKeeper implements VillageSystem {
	public static final GateKeeper INSTANCE = new GateKeeper();

	/** Gate -> game time it was opened (or first seen open after a reload). Not saved. */
	private static final Map<HeartLocation, Long> OPENED_AT = new HashMap<>();

	private GateKeeper() {}

	/** Opens the closed gate at {@code pos} for {@code who} and remembers to close it. */
	public static void open(ServerLevel level, BlockPos pos, BlockState state, LivingEntity who) {
		level.setBlock(pos, state.setValue(FenceGateBlock.OPEN, true), 10);
		level.playSound(null, pos, SoundEvents.FENCE_GATE_OPEN, SoundSource.BLOCKS, 1.0F, level.getRandom().nextFloat() * 0.1F + 0.9F);
		level.gameEvent(who, GameEvent.BLOCK_OPEN, pos);
		HeartLocation loc = VillageClaims.locate(level, pos);
		GateLedger.of(level.getServer()).add(loc);
		OPENED_AT.put(loc, level.getGameTime());
	}

	/** The gates currently in the ledger (they were opened by a villager and not closed yet). */
	public static int openGates(MinecraftServer server) { return GateLedger.of(server).gates().size(); }

	/** Test: forget when each gate was opened, as a restart does (the saved ledger stays). */
	public static void forgetAgesForTests() { OPENED_AT.clear(); }

	/** Test reset: forget every gate without touching blocks. */
	public static void resetForTests(MinecraftServer server) {
		OPENED_AT.clear();
		GateLedger.of(server).clear();
	}

	@Override
	public void tick(MinecraftServer server, VillageState village) {
		if (server.getTickCount() % Balance.GATE_KEEPER_TICKS != 0) return;
		var ledger = GateLedger.of(server);
		if (ledger.gates().isEmpty()) return;
		try (var t = VillageTicker.section()) {
			for (HeartLocation g : ledger.gates()) {
				ServerLevel level = Workers.levelOf(server, g);
				BlockPos pos = new BlockPos(g.x(), g.y(), g.z());
				if (level == null || !level.isLoaded(pos)) continue;
				BlockState state = level.getBlockState(pos);
				if (!(state.getBlock() instanceof FenceGateBlock) || !state.getValue(FenceGateBlock.OPEN)) {
					ledger.remove(g);
					OPENED_AT.remove(g);
					continue;
				}
				long now = level.getGameTime();
				long age = now - OPENED_AT.computeIfAbsent(g, k -> now);
				GateRules.OpenGate facts = look(level, pos, age);
				if (GateRules.shouldClose(facts)) {
					level.setBlock(pos, state.setValue(FenceGateBlock.OPEN, false), 10);
					level.playSound(null, pos, SoundEvents.FENCE_GATE_CLOSE, SoundSource.BLOCKS, 1.0F, level.getRandom().nextFloat() * 0.1F + 0.9F);
					level.gameEvent((net.minecraft.world.entity.Entity) null, GameEvent.BLOCK_CLOSE, pos);
					ledger.remove(g);
					OPENED_AT.remove(g);
				}
			}
		}
	}

	private static GateRules.OpenGate look(ServerLevel level, BlockPos pos, long age) {
		AABB cell = new AABB(pos).expandTowards(0, 0.5, 0).inflate(0.1);
		boolean occupied = !level.getEntitiesOfClass(LivingEntity.class, cell, e -> e.isAlive() && !e.isSpectator()).isEmpty();
		boolean userNear = false, engaged = false;
		for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(pos).inflate(6.0), GatePaths::isHired)) {
			if (usesGate(v, pos)) {
				userNear = true;
				if (Guards.isGuard(v) && GuardCombat.engaged(v)) engaged = true;
			}
		}
		return new GateRules.OpenGate(age, occupied, userNear, GatePaths.hostileNear(level, pos), engaged);
	}

	/** Is the villager at the gate or about to pass it: within 1.5 blocks, or the gate is among the next nodes of its path. */
	static boolean usesGate(Villager v, BlockPos gate) {
		if (v.position().distanceToSqr(gate.getX() + 0.5, gate.getY(), gate.getZ() + 0.5) < 1.5 * 1.5) return true;
		Path path = v.getNavigation().getPath();
		if (path == null) path = v.getBrain().getMemory(MemoryModuleType.PATH).orElse(null);
		if (path == null || path.isDone()) return false;
		int from = Math.max(0, path.getNextNodeIndex() - 1), to = Math.min(path.getNodeCount() - 1, path.getNextNodeIndex() + 3);
		for (int i = from; i <= to; i++) if (path.getNode(i).asBlockPos().equals(gate)) return true;
		return false;
	}
}
