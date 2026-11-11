package dev.villageheart.defence;

import dev.villageheart.core.ClaimGeometry;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.defence.GateRules;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.worker.WorkerAttachments;
import dev.villageheart.worker.guard.GuardCombat;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Which closed fence gates may a walker pass (human decision 2026-11-09: hired workers and guards open and close the village's fence gates
 * like doors, mobs still cannot). Used by the path-node evaluator tweak ({@code WalkNodeEvaluatorMixin}), by {@link GateTraffic} (opens) and
 * {@link GateKeeper} (closes). Server thread only.
 */
public final class GatePaths {
	private GatePaths() {}

	public static boolean isClosedGate(BlockState s) {
		return s.getBlock() instanceof FenceGateBlock && !s.getValue(FenceGateBlock.OPEN);
	}

	/** An alive {@link Enemy} mob within {@link dev.villageheart.core.Balance#GATE_HOSTILE_RANGE} blocks of the gate. */
	public static boolean hostileNear(ServerLevel level, BlockPos gate) {
		AABB box = new AABB(gate).inflate(dev.villageheart.core.Balance.GATE_HOSTILE_RANGE);
		return !level.getEntitiesOfClass(Mob.class, box, m -> m instanceof Enemy && m.isAlive()).isEmpty();
	}

	/** True when {@code gate} is inside the claim of the village. */
	public static boolean inClaim(ServerLevel level, BlockPos gate) {
		Optional<VillageState> village = VillageClaims.current(level.getServer());
		return village.isPresent() && ClaimGeometry.inClaim(village.get().heart(), VillageClaims.locate(level, gate));
	}

	/** Is {@code mob} a hired worker or guard (the only walkers that open gates)? */
	public static boolean isHired(Mob mob) {
		return mob instanceof Villager v && !v.isBaby() && WorkerAttachments.isWorker(v);
	}

	/** May this walker pass the closed gate at {@code pos} (plan a path through it, and open it when it gets there)? */
	public static boolean mayPass(Mob mob, BlockPos pos, BlockState state) {
		if (!isClosedGate(state) || !isHired(mob) || !(mob.level() instanceof ServerLevel level)) return false;
		boolean engaged = mob instanceof Villager v && GuardCombat.engaged(v);
		return GateRules.mayOpen(inClaim(level, pos), state.getValue(FenceGateBlock.POWERED),
				engaged ? false : hostileNear(level, pos), engaged);
	}
}
