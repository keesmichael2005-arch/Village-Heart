package dev.villageheart.raid;

import dev.villageheart.VillageHeart;
import dev.villageheart.core.Balance;
import dev.villageheart.core.ClaimGeometry;
import dev.villageheart.core.VillageState;
import dev.villageheart.world.VillageClaims;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Rings the village bell when a raid or a zombie siege starts (owner, 2026-11-11). The real bell is hit, so vanilla gives
 * every villager within 32 blocks the "heard the bell" memory and they hide. The claim is wider than that, so the rest
 * of the village's villagers get the same memory: the sound carries across the claim.
 */
public final class VillageBell {
	/** How many times the bell was rung since the last test reset, and the last bell it found. For tests and the sim. */
	public static int rings;
	public static BlockPos lastBell;

	private VillageBell() {}

	public static void clearForTests() {
		rings = 0;
		lastBell = null;
	}

	/** The village bell nearest the Heart, within the claim. */
	public static Optional<BlockPos> find(ServerLevel level, VillageState village) {
		BlockPos heart = new BlockPos(village.heart().x(), village.heart().y(), village.heart().z());
		return level.getPoiManager().findClosest(type -> type.is(PoiTypes.MEETING), heart, Balance.CLAIM_RADIUS, PoiManager.Occupancy.ANY);
	}

	/** Rings the bell, if the village has one, and lets every villager in the claim hear it. Returns true when a bell was hit. */
	public static boolean ring(ServerLevel level, VillageState village) {
		rings++;
		Optional<BlockPos> bell = find(level, village);
		boolean hit = false;
		if (bell.isPresent()) {
			BlockPos pos = bell.get();
			lastBell = pos;
			BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof BellBlock block) hit = block.attemptToRing(level, pos, state.getValue(BellBlock.FACING));
		} else {
			VillageHeart.LOG.warn("[threats] the village has no bell to ring");
		}
		long now = level.getGameTime();
		BlockPos heart = new BlockPos(village.heart().x(), village.heart().y(), village.heart().z());
		AABB box = new AABB(heart).inflate(Balance.CLAIM_RADIUS);
		for (Villager v : level.getEntities(EntityType.VILLAGER, box, e -> e.isAlive() && !e.isBaby()
				&& ClaimGeometry.inClaim(village.heart(), VillageClaims.locate(level, e.blockPosition())))) {
			v.getBrain().setMemory(MemoryModuleType.HEARD_BELL_TIME, now);
		}
		return hit;
	}
}
