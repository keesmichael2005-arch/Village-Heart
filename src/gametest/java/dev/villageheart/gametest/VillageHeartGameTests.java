package dev.villageheart.gametest;

import dev.villageheart.block.ModBlocks;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.core.ClaimRules;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import dev.villageheart.core.Rank;
import dev.villageheart.world.VillageSavedData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelResource;
import java.util.UUID;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/** Layer 2: real (headless) Minecraft server, real blocks, real POI system. */
public class VillageHeartGameTests {
	@GameTest(environment = "villageheart-gametest:heart_needs_bell")
	public void heartNeedsBellToClaim(GameTestHelper helper) {
		VillageTestSupport.reset(helper);
		BlockPos heart = new BlockPos(2, 1, 2);
		helper.setBlock(heart, ModBlocks.VILLAGE_HEART);
		BlockPos abs = helper.absolutePos(heart);

		helper.assertValueEqual(ClaimRules.Result.NO_BELL_NEARBY,
				VillageClaims.evaluate(helper.getLevel(), abs), "claim without bell");

		helper.setBlock(new BlockPos(4, 1, 2), Blocks.BELL);
		helper.runAfterDelay(2, () -> {
			helper.assertValueEqual(ClaimRules.Result.CLAIMED,
					VillageClaims.evaluate(helper.getLevel(), abs), "claim next to bell");
			VillageTestSupport.done(helper);
		});
	}

	/** T-L2-10: two tests that each hold a claim for 20 ticks. Fails if their batches overlap. */
	private void isolationProbe(GameTestHelper h, String name) {
		VillageTestSupport.reset(h);
		ServerPlayer owner = VillageTestSupport.player(h, name, GameType.SURVIVAL);
		h.setBlock(new BlockPos(4, 1, 2), Blocks.BELL);
		h.runAfterDelay(2, () -> {
			VillageTestSupport.place(h, owner, new BlockPos(2, 0, 2));
			h.runAfterDelay(20, () -> {
				var v = VillageClaims.current(h.getLevel().getServer());
				h.assertTrue(v.isPresent(), "claim still present after 20 ticks (" + name + ")");
				h.assertValueEqual(owner.getUUID(), v.get().owner(), "owner is still " + name);
				h.assertValueEqual(VillageClaims.locate(h.getLevel(), h.absolutePos(new BlockPos(2, 1, 2))), v.get().heart(), "heart location");
				VillageTestSupport.done(h);
			});
		});
	}

	@GameTest(environment = "villageheart-gametest:probe_a", maxTicks = 200)
	public void isolationProbeA(GameTestHelper h) { isolationProbe(h, "probe_a"); }

	@GameTest(environment = "villageheart-gametest:probe_b", maxTicks = 200)
	public void isolationProbeB(GameTestHelper h) { isolationProbe(h, "probe_b"); }

	/** T-L2-6: reload from disk through a brand-new DimensionDataStorage after each mutator. */
	private static VillageSavedData reload(MinecraftServer server) throws java.io.IOException {
		server.overworld().getDataStorage().saveAndJoin();
		java.nio.file.Path dir = DimensionType.getStorageFolder(Level.OVERWORLD, server.getWorldPath(LevelResource.ROOT)).resolve("data");
		try (DimensionDataStorage fresh = new DimensionDataStorage(dir,
				server.getFixerUpper(), server.registryAccess())) {
			return fresh.get(VillageSavedData.TYPE);
		}
	}

	@GameTest(environment = "villageheart-gametest:save_reload", maxTicks = 200)
	public void claimSurvivesSaveAndReload(GameTestHelper h) {
		MinecraftServer server = h.getLevel().getServer();
		VillageTestSupport.reset(h);
		try {
			VillageSavedData r = reload(server);
			h.assertTrue(r != null && r.village().isEmpty(), "after clear(): file exists and is empty");
			java.nio.file.Path file = DimensionType.getStorageFolder(Level.OVERWORLD, server.getWorldPath(LevelResource.ROOT))
					.resolve("data").resolve("villageheart_village.dat");
			h.assertTrue(java.nio.file.Files.exists(file), "villageheart_village.dat exists");

			ServerPlayer alice = VillageTestSupport.player(h, "alice", GameType.SURVIVAL);
			h.setBlock(new BlockPos(4, 1, 2), Blocks.BELL);
			h.runAfterDelay(2, () -> {
				try {
					VillageTestSupport.place(h, alice, new BlockPos(2, 0, 2));
					BlockPos abs = h.absolutePos(new BlockPos(2, 1, 2));
					VillageSavedData r2 = reload(server);
					h.assertTrue(r2 != null && r2.village().isPresent(), "claim persisted");
					h.assertValueEqual(alice.getUUID(), r2.village().get().owner(), "persisted owner");
					h.assertValueEqual(VillageClaims.locate(h.getLevel(), abs), r2.village().get().heart(), "persisted heart");

					VillageClaims.depositTreasury(server, 42);
					h.assertValueEqual(42, reload(server).village().orElseThrow().treasury(), "persisted treasury");

					UUID steward = UUID.randomUUID();
					VillageClaims.setRank(server, steward, Rank.STEWARD);
					VillageSavedData r4 = reload(server);
					h.assertValueEqual(Rank.STEWARD, r4.village().orElseThrow().rankOf(steward), "persisted rank");
					h.assertValueEqual(VillageClaims.current(server).orElseThrow(), r4.village().orElseThrow(), "whole record equal");

					alice.gameMode.destroyBlock(abs);
					h.assertTrue(reload(server).village().isEmpty(), "release persisted");
					VillageTestSupport.done(h);
				} catch (java.io.IOException e) {
					throw new RuntimeException(e);
				}
			});
		} catch (java.io.IOException e) {
			throw new RuntimeException(e);
		}
	}
}
