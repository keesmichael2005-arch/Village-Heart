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
import java.util.List;
import java.util.UUID;
import dev.villageheart.core.Tier;
import dev.villageheart.core.VillageState;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.material.PushReaction;
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

					// clear(): needs a non-empty claim on disk first, or an empty file would mask a missing setDirty
					VillageTestSupport.place(h, alice, new BlockPos(2, 0, 2));
					h.assertTrue(reload(server).village().isPresent(), "re-claim persisted");
					VillageClaims.data(server).clear();
					h.assertTrue(reload(server).village().isEmpty(), "clear() persisted (non-empty file became empty)");
					VillageTestSupport.done(h);
				} catch (java.io.IOException e) {
					throw new RuntimeException(e);
				}
			});
		} catch (java.io.IOException e) {
			throw new RuntimeException(e);
		}
	}

	// ---- helpers -------------------------------------------------------------------------------

	/** Places bells at the given relative positions, waits for the (deferred) POI registration, then runs next. */
	private static void withBells(GameTestHelper h, Runnable next, BlockPos... bells) {
		for (BlockPos b : bells) h.setBlock(b, Blocks.BELL);
		h.runAfterDelay(2, next);
	}

	private static VillageState village(GameTestHelper h) {
		return VillageClaims.current(h.getLevel().getServer()).orElseThrow(() -> h.assertionException("expected a claimed village"));
	}

	// ---- T-L2-2 --------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:placing_claims", maxTicks = 100)
	public void placingHeartClaimsVillage(GameTestHelper h) {
		VillageTestSupport.reset(h);
		ServerPlayer owner = VillageTestSupport.player(h, "alice", GameType.SURVIVAL);
		withBells(h, () -> {
			VillageTestSupport.place(h, owner, new BlockPos(2, 0, 2));
			h.assertBlockPresent(ModBlocks.VILLAGE_HEART, new BlockPos(2, 1, 2));
			VillageState v = village(h);
			h.assertValueEqual(owner.getUUID(), v.owner(), "owner");
			h.assertValueEqual(VillageClaims.locate(h.getLevel(), h.absolutePos(new BlockPos(2, 1, 2))), v.heart(), "heart location is absolute");
			h.assertValueEqual(Tier.HAMLET, v.tier(), "tier");
			h.assertValueEqual(0, v.treasury(), "treasury");
			h.assertTrue(owner.getMainHandItem().isEmpty(), "Heart item consumed on success");
			VillageTestSupport.done(h);
		}, new BlockPos(4, 1, 2));
	}

	// ---- T-L2-3 --------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:second_heart", maxTicks = 200)
	public void secondHeartRefusedThenReleaseAllowsNewClaim(GameTestHelper h) {
		VillageTestSupport.reset(h);
		ServerPlayer alice = VillageTestSupport.player(h, "alice", GameType.SURVIVAL);
		ServerPlayer bob = VillageTestSupport.player(h, "bob", GameType.SURVIVAL);
		BlockPos aHeart = new BlockPos(1, 1, 2), bHeart = new BlockPos(6, 1, 5);
		withBells(h, () -> {
			VillageTestSupport.place(h, alice, aHeart.below());
			h.assertValueEqual(alice.getUUID(), village(h).owner(), "alice claimed first");

			BlockPos absB = h.absolutePos(bHeart);
			double d = VillageClaims.bellDistance(h.getLevel(), absB);
			h.assertTrue(d >= 0 && d < 2, "second site is next to bell B, distance " + d);
			h.assertValueEqual(ClaimRules.Result.ALREADY_HAVE_VILLAGE, VillageClaims.evaluate(h.getLevel(), absB), "evaluate at B");
			h.assertValueEqual(ClaimRules.Result.ALREADY_HAVE_VILLAGE, VillageClaims.checkPlacement(h.getLevel(), absB, bob), "checkPlacement at B");

			VillageTestSupport.place(h, bob, bHeart.below());
			h.assertBlockNotPresent(ModBlocks.VILLAGE_HEART, bHeart);
			h.assertValueEqual(1, bob.getMainHandItem().getCount(), "Bob keeps his Heart item");
			h.assertValueEqual(alice.getUUID(), village(h).owner(), "claim still Alice's");
			h.assertValueEqual(VillageClaims.locate(h.getLevel(), h.absolutePos(aHeart)), village(h).heart(), "claim still at A");

			// (b) owner breaks, new claim possible
			h.assertTrue(alice.gameMode.destroyBlock(h.absolutePos(aHeart)), "owner may break the Heart");
			h.assertBlockNotPresent(ModBlocks.VILLAGE_HEART, aHeart);
			h.assertTrue(VillageClaims.current(h.getLevel().getServer()).isEmpty(), "claim released");
			VillageTestSupport.place(h, bob, bHeart.below());
			h.assertBlockPresent(ModBlocks.VILLAGE_HEART, bHeart);
			h.assertValueEqual(bob.getUUID(), village(h).owner(), "Bob now owns the village");
			VillageTestSupport.done(h);
		}, new BlockPos(1, 1, 1), new BlockPos(6, 1, 6));
	}

	// ---- T-L2-4 --------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:break_not_owner", maxTicks = 100)
	public void nonOwnerCannotBreakHeart(GameTestHelper h) {
		VillageTestSupport.reset(h);
		ServerPlayer alice = VillageTestSupport.player(h, "alice", GameType.SURVIVAL);
		ServerPlayer bobSurvival = VillageTestSupport.player(h, "bob", GameType.SURVIVAL);
		ServerPlayer bobCreative = VillageTestSupport.player(h, "bobc", GameType.CREATIVE);
		BlockPos heart = new BlockPos(2, 1, 2);
		withBells(h, () -> {
			VillageTestSupport.place(h, alice, heart.below());
			VillageClaims.depositTreasury(h.getLevel().getServer(), 5);
			int before = VillageTestSupport.emeralds(h, heart, 3);
			for (ServerPlayer bob : List.of(bobSurvival, bobCreative)) {
				h.assertTrue(!bob.gameMode.destroyBlock(h.absolutePos(heart)), "non-owner break refused (creative=" + bob.isCreative() + ")");
				h.assertBlockPresent(ModBlocks.VILLAGE_HEART, heart);
				h.assertValueEqual(alice.getUUID(), village(h).owner(), "claim owner unchanged");
				h.assertValueEqual(5, village(h).treasury(), "treasury unchanged");
				h.assertValueEqual(before, VillageTestSupport.emeralds(h, heart, 3), "no emerald drop");
			}
			VillageTestSupport.done(h);
		}, new BlockPos(4, 1, 2));
	}

	// ---- T-L2-5 / 5b ---------------------------------------------------------------------------

	private void ownerBreakDrops(GameTestHelper h, boolean blockDrops) {
		VillageTestSupport.reset(h);
		MinecraftServer server = h.getLevel().getServer();
		ServerPlayer alice = VillageTestSupport.player(h, "alice", GameType.SURVIVAL);
		BlockPos heart = new BlockPos(2, 1, 2);
		withBells(h, () -> {
			VillageTestSupport.place(h, alice, heart.below());
			VillageClaims.depositTreasury(server, 70);
			int before = VillageTestSupport.emeralds(h, heart, 3);
			h.getLevel().getGameRules().set(GameRules.BLOCK_DROPS, blockDrops, server);
			boolean ok = alice.gameMode.destroyBlock(h.absolutePos(heart));
			h.getLevel().getGameRules().set(GameRules.BLOCK_DROPS, true, server);
			h.assertTrue(ok, "owner break succeeds");
			h.assertValueEqual(70, VillageTestSupport.emeralds(h, heart, 3) - before, "emeralds dropped");
			h.assertTrue(VillageClaims.current(server).isEmpty(), "claim released");
			boolean heartItem = !h.getEntities(EntityType.ITEM, heart, 3).stream().filter(e -> e.getItem().is(ModBlocks.VILLAGE_HEART_ITEM)).toList().isEmpty();
			h.assertValueEqual(blockDrops, heartItem, "Heart item drop follows the block-drops gamerule");
			VillageTestSupport.done(h);
		}, new BlockPos(4, 1, 2));
	}

	@GameTest(environment = "villageheart-gametest:owner_break_drops", maxTicks = 100)
	public void ownerBreakDropsTreasury(GameTestHelper h) { ownerBreakDrops(h, true); }

	@GameTest(environment = "villageheart-gametest:block_drops_off", maxTicks = 100)
	public void treasuryDropsEvenWithBlockDropsOff(GameTestHelper h) { ownerBreakDrops(h, false); }

	// ---- T-L2-7 --------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:piston", maxTicks = 100)
	public void pistonCannotMoveHeart(GameTestHelper h) {
		VillageTestSupport.reset(h);
		ServerPlayer alice = VillageTestSupport.player(h, "alice", GameType.SURVIVAL);
		BlockPos heart = new BlockPos(3, 1, 3);
		withBells(h, () -> {
			VillageTestSupport.place(h, alice, heart.below());
			h.assertBlockPresent(ModBlocks.VILLAGE_HEART, heart);
			h.setBlock(new BlockPos(2, 1, 3), Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));
			h.setBlock(new BlockPos(1, 1, 3), Blocks.REDSTONE_BLOCK);
			h.runAfterDelay(6, () -> {
				h.assertBlockPresent(ModBlocks.VILLAGE_HEART, heart);
				h.assertBlockNotPresent(ModBlocks.VILLAGE_HEART, new BlockPos(4, 1, 3));
				h.assertValueEqual(VillageClaims.locate(h.getLevel(), h.absolutePos(heart)), village(h).heart(), "claim unchanged");
				VillageTestSupport.done(h);
			});
		}, new BlockPos(3, 1, 6));
	}

	// ---- T-L2-8 --------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:stale_heal", maxTicks = 100)
	public void staleClaimSelfHeals(GameTestHelper h) {
		VillageTestSupport.reset(h);
		MinecraftServer server = h.getLevel().getServer();
		ServerPlayer alice = VillageTestSupport.player(h, "alice", GameType.SURVIVAL);
		ServerPlayer bob = VillageTestSupport.player(h, "bob", GameType.SURVIVAL);
		BlockPos a = new BlockPos(1, 1, 2), b = new BlockPos(6, 1, 5);
		withBells(h, () -> {
			VillageTestSupport.place(h, alice, a.below());
			VillageClaims.depositTreasury(server, 7);
			// no UPDATE_NEIGHBORS: skips affectNeighborsAfterRemoval
			h.getLevel().setBlock(h.absolutePos(a), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			h.assertTrue(VillageClaims.current(server).isPresent(),
					"precondition: removal without neighbour updates leaves the claim (the removal hook was skipped); if this fails the vanilla behaviour changed");
			int nearABefore = VillageTestSupport.emeralds(h, a, 2), nearBBefore = VillageTestSupport.emeralds(h, b, 2);
			VillageTestSupport.place(h, bob, b.below());
			h.assertBlockPresent(ModBlocks.VILLAGE_HEART, b);
			h.assertValueEqual(bob.getUUID(), village(h).owner(), "Bob claimed after the heal");
			h.assertValueEqual(7, VillageTestSupport.emeralds(h, a, 2) - nearABefore, "treasury dropped at the stale Heart");
			h.assertValueEqual(0, VillageTestSupport.emeralds(h, b, 2) - nearBBefore, "nothing dropped at the new Heart");
			h.assertValueEqual(ClaimRules.Result.ALREADY_HAVE_VILLAGE,
					VillageClaims.checkPlacement(h.getLevel(), h.absolutePos(new BlockPos(5, 1, 5)), alice), "Bob's village now blocks Alice");
			h.assertValueEqual(7, VillageTestSupport.emeralds(h, a, 2) - nearABefore, "heal is idempotent");
			VillageTestSupport.done(h);
		}, new BlockPos(1, 1, 1), new BlockPos(6, 1, 6));
	}

	// ---- T-L2-9 --------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:unclaimed_inert", maxTicks = 100)
	public void unclaimedHeartIsInert(GameTestHelper h) {
		VillageTestSupport.reset(h);
		ServerPlayer alice = VillageTestSupport.player(h, "alice", GameType.SURVIVAL);
		ServerPlayer bob = VillageTestSupport.player(h, "bob", GameType.SURVIVAL);
		BlockPos a = new BlockPos(1, 1, 2), c = new BlockPos(6, 1, 6);
		withBells(h, () -> {
			VillageTestSupport.place(h, alice, a.below());
			VillageClaims.depositTreasury(h.getLevel().getServer(), 3);
			h.setBlock(c, ModBlocks.VILLAGE_HEART);
			int before = VillageTestSupport.emeralds(h, c, 3);
			h.assertTrue(bob.gameMode.destroyBlock(h.absolutePos(c)), "anyone may break an unclaimed Heart");
			h.assertBlockNotPresent(ModBlocks.VILLAGE_HEART, c);
			h.assertBlockPresent(ModBlocks.VILLAGE_HEART, a);
			h.assertValueEqual(alice.getUUID(), village(h).owner(), "claim intact");
			h.assertValueEqual(before, VillageTestSupport.emeralds(h, c, 3), "no emerald drop");
			VillageTestSupport.done(h);
		}, new BlockPos(1, 1, 1));
	}

	// ---- T-L2-11 -------------------------------------------------------------------------------

	@GameTest
	public void heartIsImmuneToWitherAndDragon(GameTestHelper h) {
		BlockState state = ModBlocks.VILLAGE_HEART.defaultBlockState();
		h.assertTrue(state.is(BlockTags.WITHER_IMMUNE), "wither_immune tag");
		h.assertTrue(state.is(BlockTags.DRAGON_IMMUNE), "dragon_immune tag");
		h.assertValueEqual(PushReaction.BLOCK, state.getPistonPushReaction(), "push reaction");
		h.succeed();
	}

	/** Regression: reset must not drop POIs of non-bell blocks (that made vanilla log "POI data mismatch" later). */
	@GameTest(environment = "villageheart-gametest:composter_poi", maxTicks = 100)
	public void resetLeavesOtherPoisAlone(GameTestHelper h) {
		VillageTestSupport.reset(h);
		BlockPos composter = new BlockPos(3, 1, 3);
		h.setBlock(composter, Blocks.COMPOSTER);
		h.runAfterDelay(3, () -> {
			var poi = h.getLevel().getPoiManager();
			BlockPos abs = h.absolutePos(composter);
			h.assertTrue(poi.getType(abs).isPresent(), "precondition: composter POI registered");
			VillageTestSupport.reset(h);
			h.assertTrue(poi.getType(abs).isPresent(), "composter POI still registered after reset");
			h.setBlock(composter, Blocks.AIR);
			h.runAfterDelay(2, () -> {
				h.assertTrue(poi.getType(abs).isEmpty(), "composter POI removed by vanilla with its block");
				VillageTestSupport.done(h);
			});
		});
	}

	// ---- T-L2-1 / 1b: the village tick timer ------------------------------------------------------

	private static void spinMs(long ms) {
		long end = System.nanoTime() + ms * 1_000_000L;
		while (System.nanoTime() < end) { /* busy wait */ }
	}

	@GameTest(environment = "villageheart-gametest:village_ticker", maxTicks = 100)
	public void villageTickerTimesSystems(GameTestHelper h) {
		VillageTestSupport.reset(h);
		ServerPlayer owner = VillageTestSupport.player(h, "alice", GameType.SURVIVAL);
		withBells(h, () -> {
			VillageTestSupport.place(h, owner, new BlockPos(2, 0, 2));
			village(h);
			dev.villageheart.world.VillageTicker.TIMER.snapshotAndReset();
			var reg = dev.villageheart.world.VillageSystems.register("test_spin", (server, v) -> spinMs(2));
			boolean threw = false;
			try {
				dev.villageheart.world.VillageSystems.register("test_spin", (server, v) -> { });
			} catch (IllegalArgumentException e) {
				threw = true;
			}
			if (!threw) {
				reg.close();
				throw h.assertionException("duplicate system id must throw IllegalArgumentException");
			}
			h.runAfterDelay(20, () -> {
				var stats = dev.villageheart.world.VillageTicker.TIMER.snapshot();
				reg.close();
				h.assertTrue(stats.samples() >= 20, "samples >= 20, got " + stats.samples());
				h.assertTrue(stats.meanMs() >= 1.5, "mean >= 1.5 ms, got " + stats.meanMs());
				dev.villageheart.world.VillageTicker.TIMER.snapshotAndReset();
				h.runAfterDelay(10, () -> {
					var after = dev.villageheart.world.VillageTicker.TIMER.snapshot();
					h.assertTrue(after.samples() >= 9, "still sampling, got " + after.samples());
					h.assertTrue(after.meanMs() < 0.5, "unregistered system costs nothing, mean " + after.meanMs());
					VillageTestSupport.done(h);
				});
			});
		}, new BlockPos(4, 1, 2));
	}

	@GameTest(environment = "villageheart-gametest:village_ticker_section", maxTicks = 100)
	public void villageTickerCountsSections(GameTestHelper h) {
		VillageTestSupport.reset(h);
		dev.villageheart.world.VillageTicker.TIMER.snapshotAndReset();
		int[] ticks = {0};
		h.onEachTick(() -> {
			if (ticks[0] >= 20) return;
			ticks[0]++;
			// two sections per tick: they must add up into one sample, not two
			try (var t = dev.villageheart.world.VillageTicker.section()) { spinMs(1); }
			try (var t = dev.villageheart.world.VillageTicker.section()) { spinMs(1); }
			if (ticks[0] == 20) {
				var stats = dev.villageheart.world.VillageTicker.TIMER.snapshot();
				h.assertTrue(stats.meanMs() >= 1.5, "mean >= 1.5 ms (sections of 2x1 ms per tick), got " + stats.meanMs());
				h.assertTrue(stats.samples() >= 19 && stats.samples() <= 21,
						"one sample per tick (20 ticks), got " + stats.samples());
				VillageTestSupport.done(h);
			}
		});
	}

	// ---- T-L2-2..4: the simulation metrics collector --------------------------------------------

	private static net.minecraft.world.phys.Vec3 centreOf(GameTestHelper h, BlockPos rel) {
		return net.minecraft.world.phys.Vec3.atCenterOf(h.absolutePos(rel));
	}

	@GameTest(environment = "villageheart-gametest:sim_collector", maxTicks = 100)
	public void simCollectorCountsVillagersInRadius(GameTestHelper h) {
		VillageTestSupport.reset(h);
		BlockPos heart = new BlockPos(2, 1, 2);
		for (int i = 0; i < 2; i++) h.spawn(net.minecraft.world.entity.EntityType.VILLAGER, new BlockPos(3 + i, 1, 3));
		net.minecraft.world.entity.npc.villager.Villager baby = h.spawn(net.minecraft.world.entity.EntityType.VILLAGER, new BlockPos(4, 1, 4));
		baby.setAge(-24000);
		h.spawn(net.minecraft.world.entity.EntityType.VILLAGER, new BlockPos(14, 1, 2)); // 12 blocks away
		h.runAfterDelay(2, () -> {
			var c = dev.villageheart.sim.SimMetricsCollector.census(h.getLevel(), centreOf(h, heart), 10);
			h.assertValueEqual(3, c.population(), "population within 10 blocks");
			h.assertValueEqual(2, c.adults(), "adults");
			h.assertValueEqual(1, c.babies(), "babies");
			var wide = dev.villageheart.sim.SimMetricsCollector.census(h.getLevel(), centreOf(h, heart), 20);
			h.assertTrue(wide.population() >= 4, "radius 20 also sees the far villager, got " + wide.population());
			VillageTestSupport.done(h);
		});
	}

	@GameTest(environment = "villageheart-gametest:sim_collector_death", maxTicks = 100)
	public void simCollectorRecordsDeathCause(GameTestHelper h) {
		VillageTestSupport.reset(h);
		BlockPos heart = new BlockPos(2, 1, 2);
		var collector = new dev.villageheart.sim.SimMetricsCollector(h.getLevel(), centreOf(h, heart), 10);
		collector.register();
		var near = h.spawn(net.minecraft.world.entity.EntityType.VILLAGER, new BlockPos(3, 1, 3));
		var far = h.spawn(net.minecraft.world.entity.EntityType.VILLAGER, new BlockPos(14, 1, 2));
		near.hurtServer(h.getLevel(), h.getLevel().damageSources().fall(), 1000f);
		far.hurtServer(h.getLevel(), h.getLevel().damageSources().fall(), 1000f);
		h.runAfterDelay(1, () -> {
			try {
				h.assertValueEqual(1, collector.pendingDeaths(), "one death inside the radius, none outside");
				var deaths = collector.drainDeaths();
				h.assertValueEqual("minecraft:fall", deaths.get(0).cause(), "cause is the damage type id");
				h.assertTrue(!deaths.get(0).byMob(), "a fall is not a mob death");
				h.assertValueEqual(0, collector.pendingDeaths(), "drain empties the list");
				collector.onDeath(near, h.getLevel().damageSources().fall());
				h.assertValueEqual(0, collector.pendingDeaths(), "the same villager is never counted twice");
			} finally {
				collector.setEnabled(false);
			}
			VillageTestSupport.done(h);
		});
	}

	@GameTest(environment = "villageheart-gametest:sim_collector_zombie", maxTicks = 100)
	public void simCollectorZombieConversionCountsOnce(GameTestHelper h) {
		VillageTestSupport.reset(h);
		BlockPos heart = new BlockPos(2, 1, 2);
		var collector = new dev.villageheart.sim.SimMetricsCollector(h.getLevel(), centreOf(h, heart), 10);
		var v1 = h.spawn(net.minecraft.world.entity.EntityType.VILLAGER, new BlockPos(3, 1, 3));
		var v2 = h.spawn(net.minecraft.world.entity.EntityType.VILLAGER, new BlockPos(4, 1, 3));
		var zombie = h.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, new BlockPos(2, 1, 4));
		var zv = h.spawn(net.minecraft.world.entity.EntityType.ZOMBIE_VILLAGER, new BlockPos(1, 1, 4));
		zombie.setNoAi(true);
		zv.setNoAi(true);
		// vanilla order for a villager killed by a zombie: death, then conversion in the same tick
		collector.onDeath(v1, h.getLevel().damageSources().mobAttack(zombie));
		collector.onConversion(v1, zv);
		collector.onConversion(v2, zv); // converted without a recorded death
		var deaths = collector.drainDeaths();
		h.assertValueEqual(2, deaths.size(), "two villagers lost, not three");
		for (var d : deaths) {
			h.assertValueEqual("zombified", d.cause(), "cause");
			h.assertTrue(d.byMob(), "counted as a mob death");
		}
		h.assertValueEqual(2L, deaths.stream().filter(dev.villageheart.core.sim.DeathRecord::byMob).count(), "deaths.mobs");
		VillageTestSupport.done(h);
	}

	/** The scenario parser's known action ids must match the harness implementation (they live in different source sets). */
	@GameTest(maxTicks = 20)
	public void simActionTypesMatchParser(GameTestHelper h) {
		h.assertValueEqual(dev.villageheart.core.sim.ScenarioParser.ACTION_TYPES, dev.villageheart.sim.SimActions.actionTypes(),
				"action ids: parser vs harness");
		h.succeed();
	}
}
