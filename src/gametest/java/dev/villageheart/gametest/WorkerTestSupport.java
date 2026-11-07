package dev.villageheart.gametest;

import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.core.worker.WorkerData;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.worker.Workers;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;

/**
 * Helpers for the worker GameTests (M7). Rules (docs/TESTING.md): one environment per test, time set explicitly and checked to advance,
 * every callback scheduled from the test body (never {@code runAfterDelay} inside a callback), lit areas for night tests, and cleanup of
 * every villager the test spawned.
 */
public final class WorkerTestSupport {
	public static final String YARD = BuildingTestSupport.YARD;
	private static final List<UUID> SPAWNED = new ArrayList<>();
	private static long savedDayTime = 6000;

	private WorkerTestSupport() {}

	public static String env(String name) { return "villageheart-gametest:workers_" + name; }

	/** Start of a test: drop earlier villagers, remember the day time, let time advance. */
	public static void begin(GameTestHelper h) {
		discardSpawned(h.getLevel());
		ServerLevel level = h.getLevel();
		savedDayTime = level.getDayTime();
		level.getGameRules().set(GameRules.ADVANCE_TIME, true, level.getServer());
	}

	/** End of a test (also safe to call from {@code VillageTestSupport.reset}): villagers discarded, day time restored. */
	public static void end(GameTestHelper h) {
		discardSpawned(h.getLevel());
		h.getLevel().setDayTime(savedDayTime);
	}

	public static void discardSpawned(ServerLevel level) {
		for (UUID id : SPAWNED) {
			Entity e = level.getEntity(id);
			if (e != null) e.discard();
		}
		SPAWNED.clear();
	}

	public static Villager spawnVillager(GameTestHelper h, BlockPos rel) {
		Villager v = h.spawn(EntityType.VILLAGER, rel);
		SPAWNED.add(v.getUUID());
		return v;
	}

	/** Track a villager that a test created itself (a reloaded copy), so cleanup discards it. */
	public static void track(Villager v) { SPAWNED.add(v.getUUID()); }

	public static HeartLocation loc(GameTestHelper h, BlockPos rel) { return VillageClaims.locate(h.getLevel(), h.absolutePos(rel)); }

	/** Makes {@code v} a worker with no registry record and no slot checks (the framework tests of step 4). */
	public static void hireDirect(GameTestHelper h, Villager v, JobType job, BlockPos jobBlockRel) {
		HeartLocation block = loc(h, jobBlockRel);
		Workers.install(h.getLevel(), v, WorkerData.hired(job, block, block));
	}

	/** 3D distance from the villager to the bottom centre of the (relative) block, the metric of the plan. */
	public static double distance(GameTestHelper h, Villager v, BlockPos rel) {
		return v.position().distanceTo(Vec3.atBottomCenterOf(h.absolutePos(rel)));
	}

	public static void floor(GameTestHelper h, int x0, int z0, int x1, int z1) {
		for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.OAK_PLANKS);
	}

	/** Torches on the floor every 4 blocks, so a night test is not spawn-proofed by darkness. */
	public static void light(GameTestHelper h, int x0, int z0, int x1, int z1) {
		for (int x = x0; x <= x1; x += 4) for (int z = z0; z <= z1; z += 4) h.setBlock(new BlockPos(x, 1, z), Blocks.TORCH);
	}

	public static void door(GameTestHelper h, BlockPos lower, Direction facing) {
		BlockState base = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, facing);
		h.setBlock(lower, base.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		h.setBlock(lower.above(), base.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
	}

	/** A bed with its foot at {@code foot}, head to the south. */
	public static void bed(GameTestHelper h, BlockPos foot) {
		BlockState bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH);
		h.setBlock(foot, bed.setValue(BedBlock.PART, BedPart.FOOT));
		h.setBlock(foot.south(), bed.setValue(BedBlock.PART, BedPart.HEAD));
	}
}
