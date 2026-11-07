package dev.villageheart.gametest;

import static dev.villageheart.gametest.BuildingTestSupport.*;

import dev.villageheart.block.MarkerBlock;
import dev.villageheart.block.MarkerBlocks;
import dev.villageheart.block.MarkerStatus;
import dev.villageheart.building.BuildingSystem;
import dev.villageheart.building.Buildings;
import dev.villageheart.core.BuildingRecord;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.Tier;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.building.AreaCounter;
import dev.villageheart.core.building.BuildingStatus;
import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.building.Reason;
import dev.villageheart.core.building.ShaftScanner;
import dev.villageheart.core.building.Thing;
import dev.villageheart.core.building.Verdict;
import dev.villageheart.core.room.Pos;
import dev.villageheart.room.LevelBlockView;
import dev.villageheart.room.RoomCells;
import dev.villageheart.room.RoomScans;
import dev.villageheart.world.VillageClaims;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

/**
 * Layer 2: the building registry and validation (M5). Rules in {@link BuildingTestSupport}: own environment per test, the
 * 32x28x32 {@code yard} structure, {@code skyAccess = true}, measured waits.
 */
public class BuildingGameTests {
	private static final String Y = YARD;
	private static final BlockPos ORIGIN_A = new BlockPos(8, 0, 4);
	private static final BlockPos ORIGIN_B = new BlockPos(18, 0, 4);

	private static void chest(GameTestHelper h, BlockPos rel) { h.setBlock(rel, Blocks.CHEST); }

	private static void bed(GameTestHelper h, BlockPos foot) {
		BlockState bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH);
		h.setBlock(foot, bed.setValue(BedBlock.PART, BedPart.FOOT));
		h.setBlock(foot.south(), bed.setValue(BedBlock.PART, BedPart.HEAD));
	}

	private static Pos pos(GameTestHelper h, BlockPos rel) { return RoomScans.pos(h.absolutePos(rel)); }

	private static void fill(GameTestHelper h, int x0, int y0, int z0, int x1, int y1, int z1, Block block) {
		for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) h.setBlock(new BlockPos(x, y, z), block);
	}

	/** A Storage room of 5x4 (floor 20) with two chests at opposite ends; returns the marker position. */
	private static BlockPos storageRoom(GameTestHelper h, BlockPos origin, int chests) {
		BlockPos marker = room(h, origin, 5, 4);
		if (chests >= 1) chest(h, inside(origin, 0, 0, 3));
		if (chests >= 2) chest(h, inside(origin, 4, 0, 3));
		return marker;
	}

	// ---- T-L2-1 (roadmap) ------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:buildings_storage", structure = Y, skyAccess = true, maxTicks = 400)
	public void storageGreenThenRed(GameTestHelper h) {
		BlockPos marker = markerPos(ORIGIN_A, 5);
		GameTestSequence seq = begin(h).thenExecute(() -> {
			storageRoom(h, ORIGIN_A, 2);
			placeMarker(h, BuildingType.STORAGE, marker, Direction.NORTH);
		});
		seq = awaitWithin(h, seq, 40, "green", () -> {
			assertStatus(h, marker, MarkerStatus.VALID);
			h.assertTrue(verdict(h, marker).isPresent() && reasons(h, marker).isEmpty(), "no reasons: " + reasons(h, marker));
		});
		seq = seq.thenExecute(() -> h.destroyBlock(inside(ORIGIN_A, 0, 0, 3)));
		seq = awaitWithin(h, seq, 40, "red", () -> assertStatus(h, marker, MarkerStatus.INVALID));
		seq.thenExecute(() -> {
			h.assertValueEqual(List.of(Reason.needsMore(1, Thing.STORAGE_CONTAINER)), reasons(h, marker), "reasons");
			h.assertValueEqual("Needs 1 more chest", reasons(h, marker).get(0).message(), "message");
			VillageTestSupport.done(h);
		});
	}

	// ---- T-L2-2 ----------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:buildings_break", structure = Y, skyAccess = true, maxTicks = 400)
	public void breakUnregisters(GameTestHelper h) {
		BlockPos marker = markerPos(ORIGIN_A, 5);
		GameTestSequence seq = begin(h).thenExecute(() -> {
			storageRoom(h, ORIGIN_A, 2);
			placeMarker(h, BuildingType.STORAGE, marker, Direction.NORTH);
		});
		seq = awaitWithin(h, seq, 40, "green", () -> assertStatus(h, marker, MarkerStatus.VALID));
		seq = seq.thenExecute(() -> {
			ServerPlayer breaker = VillageTestSupport.player(h, "breaker", GameType.SURVIVAL);
			breaker.gameMode.destroyBlock(h.absolutePos(marker));
			h.assertTrue(record(h, marker).isEmpty(), "unregistered in the same tick");
			boolean dropped = h.getEntities(EntityType.ITEM, marker, 4.0).stream()
					.anyMatch(e -> e.getItem().is(MarkerBlocks.ITEMS.get(BuildingType.STORAGE)));
			h.assertTrue(dropped, "the marker item dropped");
			placeMarker(h, BuildingType.STORAGE, marker, Direction.NORTH);
		});
		seq = awaitWithin(h, seq, 40, "registered again", () -> assertStatus(h, marker, MarkerStatus.VALID));
		seq = seq.thenExecute(() -> h.getLevel().setBlock(h.absolutePos(marker), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS));
		awaitWithin(h, seq, 40, "orphan swept", () -> h.assertTrue(record(h, marker).isEmpty(), "ghost building"))
				.thenExecute(() -> VillageTestSupport.done(h));
	}

	// ---- T-L2-3 ----------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:buildings_shared", structure = Y, skyAccess = true, maxTicks = 500)
	public void sharedRoomBothRed(GameTestHelper h) {
		BlockPos north = markerPos(ORIGIN_A, 5);
		BlockPos east = new BlockPos(ORIGIN_A.getX() + 5 + 2, ORIGIN_A.getY() + 3, ORIGIN_A.getZ() + (4 + 1) / 2);
		GameTestSequence seq = begin(h).thenExecute(() -> {
			storageRoom(h, ORIGIN_A, 2);
			h.assertValueEqual(east, eastDoorMarker(h, ORIGIN_A, 5, 4), "east marker position");
			placeMarker(h, BuildingType.STORAGE, north, Direction.NORTH);
			placeMarker(h, BuildingType.STORAGE, east, Direction.EAST);
		});
		seq = awaitWithin(h, seq, bound(2, 0), "both red", () -> {
			assertStatus(h, north, MarkerStatus.INVALID);
			assertStatus(h, east, MarkerStatus.INVALID);
			h.assertValueEqual("Room is shared with the marker at " + pos(h, east), reasons(h, north).get(0).message(), "north reason");
			h.assertValueEqual("Room is shared with the marker at " + pos(h, north), reasons(h, east).get(0).message(), "east reason");
		});
		seq = seq.thenIdle(40).thenExecute(() ->
				h.assertValueEqual(0, BuildingSystem.priorityQueuedForTests(h.getLevel().getServer()), "no ping-pong: the queue is empty"));
		seq = seq.thenExecute(() -> VillageTestSupport.player(h, "breaker", GameType.SURVIVAL).gameMode.destroyBlock(h.absolutePos(east)));
		awaitWithin(h, seq, 5, "survivor green", () -> {
			assertStatus(h, north, MarkerStatus.VALID);
			h.assertTrue(reasons(h, north).isEmpty(), "no SHARED left: " + reasons(h, north));
		}).thenExecute(() -> VillageTestSupport.done(h));
	}

	// ---- T-L2-4 ----------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:buildings_tier", structure = Y, skyAccess = true, maxTicks = 500)
	public void tierGate(GameTestHelper h) {
		BlockPos mine = markerPos(ORIGIN_A, 5);
		BlockPos smithy = markerPos(ORIGIN_B, 5);
		GameTestSequence seq = begin(h).thenExecute(() -> {
			room(h, ORIGIN_A, 5, 4);
			h.setBlock(inside(ORIGIN_A, 0, 0, 0), Blocks.STONECUTTER);
			chest(h, inside(ORIGIN_A, 4, 0, 3));
			room(h, ORIGIN_B, 5, 4);
			h.setBlock(inside(ORIGIN_B, 0, 0, 0), Blocks.BLAST_FURNACE);
			h.setBlock(inside(ORIGIN_B, 2, 0, 0), Blocks.ANVIL);
			chest(h, inside(ORIGIN_B, 4, 0, 3));
			placeMarker(h, BuildingType.MINE, mine, Direction.NORTH);
			placeMarker(h, BuildingType.SMITHY, smithy, Direction.NORTH);
		});
		seq = awaitWithin(h, seq, bound(2, 0), "gated", () -> {
			assertStatus(h, mine, MarkerStatus.INVALID);
			assertStatus(h, smithy, MarkerStatus.INVALID);
			h.assertValueEqual(List.of(Reason.requiresTier(Tier.VILLAGE), Reason.needsLinkedSite(BuildingType.MINESHAFT)), reasons(h, mine), "mine");
			h.assertValueEqual(List.of(Reason.requiresTier(Tier.TOWN)), reasons(h, smithy), "smithy");
		});
		seq = seq.thenExecute(() -> {
			setTier(h, Tier.VILLAGE);
			Buildings.requestAll(h.getLevel().getServer());
		});
		awaitWithin(h, seq, bound(2, 0), "tier-up re-check", () -> {
			h.assertValueEqual(List.of(Reason.needsLinkedSite(BuildingType.MINESHAFT)), reasons(h, mine), "mine after tier-up");
			h.assertValueEqual("Requires Town tier", reasons(h, smithy).get(0).message(), "smithy still needs Town");
		}).thenExecute(() -> VillageTestSupport.done(h));
	}

	// ---- T-L2-5 ----------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:buildings_noclaim", structure = Y, skyAccess = true, maxTicks = 600)
	public void outsideClaimAndNoClaim(GameTestHelper h) {
		BlockPos m1 = markerPos(ORIGIN_A, 5);
		BlockPos m2 = markerPos(ORIGIN_B, 5);
		RecordingHolder rec = new RecordingHolder();
		VillageTestSupport.reset(h);
		BuildingSystem.resetRuntimeForTests();
		sweepStrayMarkers(h);
		GameTestSequence seq = h.startSequence().thenExecute(() -> {
			storageRoom(h, ORIGIN_A, 2);
			placeMarker(h, BuildingType.STORAGE, m1, Direction.NORTH);
			storageRoom(h, ORIGIN_B, 2);
		}).thenIdle(40).thenExecute(() -> {
			h.assertValueEqual(MarkerStatus.UNCHECKED, status(h, m1), "still grey without a claim");
			h.assertTrue(VillageClaims.current(h.getLevel().getServer()).isEmpty(), "no claim");
			rec.player = recordingPlayer(h, "clicker");
			rightClick(h, rec.player, m1, Direction.NORTH, ItemStack.EMPTY);
			h.assertValueEqual("message.villageheart.reason.no_village", lineKey(rec.player.messages.get(0)), "NO_VILLAGE key");
			h.setBlock(BELL, Blocks.BELL);
		}).thenIdle(3).thenExecute(() -> claim(h));
		seq = awaitWithin(h, seq, bound(1, 0), "adopted and green", () -> assertStatus(h, m1, MarkerStatus.VALID));
		seq = seq.thenExecute(() -> {
			// a marker that never ran onPlace (UPDATE_SKIP_ON_PLACE) is missed by everything but a right-click
			BlockState state = MarkerBlocks.BLOCKS.get(BuildingType.STORAGE).defaultBlockState().setValue(MarkerBlock.FACING, Direction.NORTH);
			h.getLevel().setBlock(h.absolutePos(m2), state, Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_ON_PLACE);
		}).thenIdle(30).thenExecute(() -> {
			h.assertTrue(record(h, m2).isEmpty(), "not registered without onPlace");
			rec.player.messages.clear();
			rightClick(h, rec.player, m2, Direction.NORTH, ItemStack.EMPTY);
			h.assertTrue(record(h, m2).isPresent(), "the right-click registered it");
			h.assertValueEqual("message.villageheart.marker.valid", translatable(rec.player.messages.get(0)).getKey(), "first report is the real verdict");
		});
		// (b) far outside the claim, in a chunk loaded and forced by the test
		seq = seq.thenExecute(() -> {
			ServerLevel level = h.getLevel();
			HeartLocation heart = VillageClaims.current(level.getServer()).orElseThrow().heart();
			BlockPos far = new BlockPos(heart.x(), heart.y(), heart.z() + 100_000);
			rec.far = far;
			level.getChunk(far.getX() >> 4, far.getZ() >> 4);
			level.setChunkForced(far.getX() >> 4, far.getZ() >> 4, true);
			level.setBlock(far.south(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
			level.setBlock(far, MarkerBlocks.BLOCKS.get(BuildingType.STORAGE).defaultBlockState().setValue(MarkerBlock.FACING, Direction.NORTH), Block.UPDATE_ALL);
		});
		seq = seq.thenWaitUntil(() -> h.assertValueEqual(MarkerStatus.INVALID,
				h.getLevel().getBlockState(rec.far).getValue(MarkerBlock.STATUS), "far marker is red"));
		seq.thenExecute(() -> {
			ServerLevel level = h.getLevel();
			h.assertTrue(VillageClaims.current(level.getServer()).orElseThrow().building(VillageClaims.locate(level, rec.far)).isEmpty(),
					"out-of-claim marker is not registered");
			rec.player.messages.clear();
			rightClickAbs(level, rec.player, rec.far, Direction.NORTH, ItemStack.EMPTY);
			h.assertValueEqual("Outside the village: 100000 blocks from the Heart (max 80)", lineBody(rec.player.messages.get(1)).getFallback(), "reason");
			level.setBlock(rec.far, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			level.setBlock(rec.far.south(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			level.setChunkForced(rec.far.getX() >> 4, rec.far.getZ() >> 4, false);
			VillageTestSupport.done(h);
		});
	}

	private static final class RecordingHolder {
		BuildingTestSupport.RecordingPlayer player;
		BlockPos far;
	}

	// ---- T-L2-6 ----------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:buildings_release", structure = Y, skyAccess = true, maxTicks = 400)
	public void releaseResetsMarkers(GameTestHelper h) {
		BlockPos marker = markerPos(ORIGIN_A, 5);
		GameTestSequence seq = begin(h).thenExecute(() -> {
			storageRoom(h, ORIGIN_A, 2);
			placeMarker(h, BuildingType.STORAGE, marker, Direction.NORTH);
		});
		seq = awaitWithin(h, seq, 40, "green", () -> assertStatus(h, marker, MarkerStatus.VALID));
		seq.thenExecute(() -> {
			owner().gameMode.destroyBlock(h.absolutePos(new BlockPos(3, 1, 1)));
			h.assertValueEqual(MarkerStatus.UNCHECKED, status(h, marker), "grey again in the same tick");
			h.assertTrue(VillageClaims.current(h.getLevel().getServer()).isEmpty(), "released");
			var rp = recordingPlayer(h, "clicker");
			rightClick(h, rp, marker, Direction.NORTH, ItemStack.EMPTY);
			h.assertValueEqual("message.villageheart.reason.no_village", lineKey(rp.messages.get(0)), "NO_VILLAGE");
			VillageTestSupport.done(h);
		});
	}

	/** The key of a one-line message that is either a bare translatable or " - " plus a translatable. */
	private static String lineKey(net.minecraft.network.chat.Component c) {
		return c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t ? t.getKey() : lineBody(c).getKey();
	}

	// ---- T-L2-7 ----------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:buildings_unloaded", structure = Y, skyAccess = true, maxTicks = 300)
	public void unloadedMarkerUntouched(GameTestHelper h) {
		GameTestSequence seq = begin(h);
		HeartLocation[] far = new HeartLocation[1];
		BuildingRecord[] fake = new BuildingRecord[1];
		seq = seq.thenExecute(() -> {
			MinecraftServer server = h.getLevel().getServer();
			HeartLocation heart = VillageClaims.current(server).orElseThrow().heart();
			far[0] = new HeartLocation(heart.dimension(), heart.x(), heart.y(), heart.z() + 100_000);
			fake[0] = new BuildingRecord("storage", far[0], BuildingStatus.VALID, null);
			VillageClaims.data(server).update(v -> v.withBuilding(fake[0]));
		}).thenIdle(100).thenExecute(() -> {
			MinecraftServer server = h.getLevel().getServer();
			h.assertValueEqual(fake[0], VillageClaims.current(server).orElseThrow().building(far[0]).orElse(null), "record unchanged");
			h.assertTrue(h.getLevel().getChunkSource().getChunkNow(far[0].x() >> 4, far[0].z() >> 4) == null, "the scheduler did not load the chunk");
			Buildings.requestAll(server);
		}).thenIdle(20).thenExecute(() -> {
			MinecraftServer server = h.getLevel().getServer();
			h.assertValueEqual(fake[0], VillageClaims.current(server).orElseThrow().building(far[0]).orElse(null), "record unchanged after a request");
			h.assertTrue(h.getLevel().getChunkSource().getChunkNow(far[0].x() >> 4, far[0].z() >> 4) == null, "still not loaded");
			VillageTestSupport.done(h);
		});
	}

	// ---- T-L2-8 ----------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:buildings_persist", structure = Y, skyAccess = true, maxTicks = 600)
	public void registryPersists(GameTestHelper h) {
		BlockPos storage = markerPos(ORIGIN_A, 5);
		BlockPos house = markerPos(ORIGIN_B, 5);
		GameTestSequence seq = begin(h).thenExecute(() -> {
			storageRoom(h, ORIGIN_A, 2);
			room(h, ORIGIN_B, 5, 4);
			placeMarker(h, BuildingType.STORAGE, storage, Direction.NORTH);
			placeMarker(h, BuildingType.HOUSE, house, Direction.NORTH);
		});
		seq = awaitWithin(h, seq, bound(2, 0), "verdicts", () -> {
			assertStatus(h, storage, MarkerStatus.VALID);
			assertStatus(h, house, MarkerStatus.INVALID);
		});
		seq = seq.thenExecute(() -> {
			try {
				VillageState onDisk = VillageTestSupport.reload(h.getLevel().getServer()).village().orElseThrow();
				h.assertValueEqual(BuildingStatus.VALID, onDisk.building(loc(h, storage)).orElseThrow().status(), "storage on disk");
				h.assertValueEqual(BuildingStatus.INVALID, onDisk.building(loc(h, house)).orElseThrow().status(), "house on disk");
			} catch (java.io.IOException e) {
				throw new RuntimeException(e);
			}
			BuildingSystem.resetRuntimeForTests();
			h.assertTrue(verdict(h, storage).isEmpty(), "the runtime really is empty");
		});
		awaitWithin(h, seq, bound(2, 0), "rebuilt", () -> {
			h.assertTrue(verdict(h, storage).isPresent() && verdict(h, house).isPresent(), "both re-checked");
			assertStatus(h, storage, MarkerStatus.VALID);
			assertStatus(h, house, MarkerStatus.INVALID);
		}).thenExecute(() -> VillageTestSupport.done(h));
	}

	// ---- T-L2-9 (roadmap) ------------------------------------------------------------------------

	private static final BlockPos SHAFT_MARKER = new BlockPos(3, 13, 8);
	private static final BlockPos MINE_ORIGIN = new BlockPos(20, 10, 9);

	private static void buildShaft(GameTestHelper h) {
		fill(h, 1, 0, 6, 5, 12, 10, Blocks.STONE);
		fill(h, 2, 1, 7, 4, 2, 9, Blocks.AIR);
		fill(h, 3, 3, 8, 3, 12, 8, Blocks.AIR);
		int n = 0;
		for (int x = 2; x <= 4; x++) for (int z = 7; z <= 9; z++) {
			h.setBlock(new BlockPos(x, 1, z), n < 8 ? Blocks.RAIL : Blocks.TORCH);
			n++;
		}
		h.setBlock(new BlockPos(3, 13, 7), Blocks.OAK_FENCE);
	}

	@GameTest(environment = "villageheart-gametest:buildings_mineshaft", structure = Y, skyAccess = true, maxTicks = 700)
	public void mineshaftLinksToMine(GameTestHelper h) {
		BlockPos mineMarker = markerPos(MINE_ORIGIN, 5);
		h.assertValueEqual(new BlockPos(23, 13, 8), mineMarker, "the Mine marker is 20 blocks from the shaft marker");
		GameTestSequence seq = begin(h).thenExecute(() -> {
			setTier(h, Tier.VILLAGE);
			buildShaft(h);
			ServerLevel level = h.getLevel();
			var survey = ShaftScanner.scan(new LevelBlockView(level), pos(h, SHAFT_MARKER));
			h.assertValueEqual(12, survey.depth(), "the shaft is exactly 12 deep");
			h.assertValueEqual(8, survey.rails(), "rails");
			h.assertValueEqual(1, survey.lights(), "lights");
			placeMarker(h, BuildingType.MINESHAFT, SHAFT_MARKER, Direction.SOUTH);
		});
		seq = awaitWithin(h, seq, bound(1, 0), "no mine yet", () -> {
			assertStatus(h, SHAFT_MARKER, MarkerStatus.INVALID);
			h.assertValueEqual(List.of(Reason.noParent(BuildingType.MINE, 48)), reasons(h, SHAFT_MARKER), "reasons");
			h.assertValueEqual("No Mine within 48 blocks", reasons(h, SHAFT_MARKER).get(0).message(), "message");
		});
		seq = seq.thenExecute(() -> {
			fill(h, 19, 0, 7, 27, 9, 15, Blocks.STONE);
			room(h, MINE_ORIGIN, 5, 4);
			h.setBlock(inside(MINE_ORIGIN, 0, 0, 0), Blocks.STONECUTTER);
			chest(h, inside(MINE_ORIGIN, 4, 0, 3));
			placeMarker(h, BuildingType.MINE, mineMarker, Direction.NORTH);
		});
		seq = awaitWithin(h, seq, bound(2, 1), "linked", () -> {
			assertStatus(h, SHAFT_MARKER, MarkerStatus.VALID);
			assertStatus(h, mineMarker, MarkerStatus.VALID);
			h.assertValueEqual(loc(h, mineMarker), record(h, SHAFT_MARKER).orElseThrow().link(), "link");
		});
		seq = seq.thenExecute(() -> {
			h.setBlock(new BlockPos(2, 1, 7), Blocks.AIR);
			h.setBlock(new BlockPos(2, 1, 8), Blocks.AIR);
		});
		awaitWithin(h, seq, bound(2, 1), "6 rails", () -> {
			assertStatus(h, SHAFT_MARKER, MarkerStatus.INVALID);
			h.assertValueEqual(List.of(Reason.needsMore(2, Thing.RAIL)), reasons(h, SHAFT_MARKER), "shaft reasons");
			h.assertValueEqual(loc(h, mineMarker), record(h, SHAFT_MARKER).orElseThrow().link(), "link is kept");
			assertStatus(h, mineMarker, MarkerStatus.INVALID);
			h.assertValueEqual(List.of(Reason.needsLinkedSite(BuildingType.MINESHAFT)), reasons(h, mineMarker), "mine reasons");
		}).thenExecute(() -> VillageTestSupport.done(h));
	}

	// ---- T-L2-10 ---------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:buildings_hunting", structure = Y, skyAccess = true, maxTicks = 900)
	public void huntingGround(GameTestHelper h) {
		BlockPos lodgeOrigin = new BlockPos(2, 1, 4);
		BlockPos houseOrigin = new BlockPos(2, 1, 14);
		BlockPos lodge = markerPos(lodgeOrigin, 5);
		BlockPos house = markerPos(houseOrigin, 5);
		BlockPos ground = new BlockPos(16, 13, 16);
		BlockPos leaf = new BlockPos(10, 14, 10);
		BlockPos homeFence = inside(houseOrigin, 2, 0, 2);
		BlockPos homeGround = inside(houseOrigin, 3, 0, 2);
		GameTestSequence seq = begin(h).thenExecute(() -> {
			setTier(h, Tier.VILLAGE);
			fill(h, 0, 0, 0, 31, 0, 31, Blocks.STONE);
			room(h, lodgeOrigin, 5, 4);
			h.setBlock(inside(lodgeOrigin, 0, 0, 0), Blocks.FLETCHING_TABLE);
			chest(h, inside(lodgeOrigin, 4, 0, 3));
			fill(h, 16, 1, 15, 16, 12, 15, Blocks.STONE);
			h.setBlock(new BlockPos(16, 13, 15), Blocks.OAK_FENCE);
			BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
			for (int x = 10; x <= 14; x++) for (int y = 14; y <= 17; y++) for (int z = 10; z <= 11; z++) h.setBlock(new BlockPos(x, y, z), leaves);
			var count = AreaCounter.count(new LevelBlockView(h.getLevel()), pos(h, ground), 12, "#natural");
			h.assertValueEqual(40, count.n(), "the survey sees exactly 40 natural blocks (a wrong environment fails here)");
			placeMarker(h, BuildingType.HUNTING_LODGE, lodge, Direction.NORTH);
			placeMarker(h, BuildingType.HUNTING_GROUND, ground, Direction.SOUTH);
		});
		seq = awaitWithin(h, seq, bound(2, 1), "ground valid", () -> {
			assertStatus(h, ground, MarkerStatus.VALID);
			h.assertValueEqual(loc(h, lodge), record(h, ground).orElseThrow().link(), "linked to the lodge");
		});
		seq = seq.thenExecute(() -> h.setBlock(leaf, Blocks.AIR));
		seq = awaitWithin(h, seq, bound(2, 0), "39 leaves", () -> {
			assertStatus(h, ground, MarkerStatus.INVALID);
			h.assertValueEqual("Needs 1 more grass, leaf or log block", reasons(h, ground).get(0).message(), "message");
		});
		seq = seq.thenExecute(() -> {
			room(h, houseOrigin, 5, 4);
			bed(h, inside(houseOrigin, 0, 0, 1));
			h.setBlock(inside(houseOrigin, 4, 0, 0), Blocks.TORCH);
			placeMarker(h, BuildingType.HOUSE, house, Direction.NORTH);
		});
		seq = awaitWithin(h, seq, bound(3, 0), "house valid (scan cached)", () -> assertStatus(h, house, MarkerStatus.VALID));
		seq = seq.thenExecute(() -> {
			h.setBlock(homeFence, Blocks.OAK_FENCE);
			placeMarker(h, BuildingType.HUNTING_GROUND, homeGround, Direction.EAST);
		});
		awaitWithin(h, seq, bound(4, 0), "inside a home", () -> {
			assertStatus(h, homeGround, MarkerStatus.INVALID);
			h.assertTrue(reasons(h, homeGround).contains(Reason.insideHomeRoom()), "INSIDE_HOME_ROOM in " + reasons(h, homeGround));
		}).thenExecute(() -> VillageTestSupport.done(h));
	}

	// ---- T-L2-11 ---------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:buildings_yard", structure = Y, skyAccess = true, maxTicks = 700)
	public void trainingYard(GameTestHelper h) {
		BlockPos barracksOrigin = new BlockPos(2, 0, 4);
		BlockPos barracks = markerPos(barracksOrigin, 5);
		BlockPos yard = new BlockPos(16, 4, 16);
		ArmorStand[] stand = new ArmorStand[1];
		GameTestSequence seq = begin(h).thenExecute(() -> {
			setTier(h, Tier.VILLAGE);
			room(h, barracksOrigin, 5, 4);
			h.setBlock(inside(barracksOrigin, 0, 0, 0), Blocks.GRINDSTONE);
			bed(h, inside(barracksOrigin, 2, 0, 0));
			chest(h, inside(barracksOrigin, 4, 0, 3));
			fill(h, 16, 0, 15, 16, 4, 15, Blocks.STONE);
			stand[0] = h.spawn(EntityType.ARMOR_STAND, new BlockPos(18, 1, 16));
			stand[0].setNoGravity(true);
			placeMarker(h, BuildingType.BARRACKS, barracks, Direction.NORTH);
			placeMarker(h, BuildingType.TRAINING_YARD, yard, Direction.SOUTH);
		});
		seq = awaitWithin(h, seq, bound(2, 0), "one dummy", () -> {
			assertStatus(h, yard, MarkerStatus.INVALID);
			h.assertValueEqual(List.of(Reason.needsMore(1, Thing.DUMMY)), reasons(h, yard), "reasons");
			h.assertValueEqual(loc(h, barracks), record(h, yard).orElseThrow().link(), "linked to the barracks");
		});
		seq = seq.thenExecute(() -> h.setBlock(new BlockPos(14, 2, 16), Blocks.TARGET));
		seq = awaitWithin(h, seq, bound(2, 0), "armor stand + target", () -> assertStatus(h, yard, MarkerStatus.VALID));
		seq = seq.thenExecute(() -> {
			var at = stand[0].position();
			stand[0].teleportTo(at.x + 8, at.y, at.z);
		});
		awaitWithin(h, seq, bound(2, 0), "stand moved away", () -> assertStatus(h, yard, MarkerStatus.INVALID))
				.thenExecute(() -> VillageTestSupport.done(h));
	}

	// ---- T-L2-13 ---------------------------------------------------------------------------------

	@GameTest(maxTicks = 20)
	public void roomCellsGroups(GameTestHelper h) {
		for (Block b : List.of(Blocks.RAIL, Blocks.POWERED_RAIL)) group(h, b, "#rail", true);
		for (Block b : List.of(Blocks.GRASS_BLOCK, Blocks.OAK_LEAVES, Blocks.BIRCH_LOG, Blocks.STRIPPED_OAK_LOG)) group(h, b, "#natural", true);
		for (Block b : List.of(Blocks.DIRT, Blocks.SHORT_GRASS)) group(h, b, "#natural", false);
		for (Block b : List.of(Blocks.ANVIL, Blocks.CHIPPED_ANVIL)) group(h, b, "#anvil", true);
		for (Block b : List.of(Blocks.TARGET, Blocks.BARREL, Blocks.COMPOSTER, Blocks.SMOKER, Blocks.STONECUTTER, Blocks.FLETCHING_TABLE,
				Blocks.GRINDSTONE, Blocks.BLAST_FURNACE, Blocks.CRAFTING_TABLE)) {
			String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b).toString();
			h.assertTrue(RoomCells.of(b.defaultBlockState()).contents().contains(id), id + " is in the cell contents");
		}
		for (dev.villageheart.core.building.JobBlockType t : dev.villageheart.core.building.JobBlockType.values()) {
			Block b = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(
					net.minecraft.resources.Identifier.fromNamespaceAndPath("villageheart", t.id()));
			h.assertTrue(RoomCells.of(b.defaultBlockState()).contents().contains("villageheart:" + t.id()), t.id() + " is in the cell contents");
		}
		h.succeed();
	}

	private static void group(GameTestHelper h, Block b, String key, boolean expected) {
		boolean has = RoomCells.of(b.defaultBlockState()).contents().contains(key);
		h.assertTrue(has == expected, b + (expected ? " should" : " must not") + " carry " + key);
	}

	// ---- T-L2-14 ---------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:buildings_report", structure = Y, skyAccess = true, maxTicks = 400)
	public void rightClickReport(GameTestHelper h) {
		BlockPos marker = markerPos(ORIGIN_A, 5);
		GameTestSequence seq = begin(h).thenExecute(() -> {
			storageRoom(h, ORIGIN_A, 1);
			placeMarker(h, BuildingType.STORAGE, marker, Direction.NORTH);
		});
		seq = awaitWithin(h, seq, 40, "red", () -> assertStatus(h, marker, MarkerStatus.INVALID));
		seq.thenExecute(() -> {
			var rp = recordingPlayer(h, "reader");
			InteractionResult r = rightClick(h, rp, marker, Direction.NORTH, ItemStack.EMPTY);
			h.assertTrue(r.consumesAction(), "the click was consumed: " + r);
			h.assertTrue(rp.messages.size() >= 3, "header, reason, detail: " + rp.messages);
			var header = translatable(rp.messages.get(0));
			h.assertValueEqual("message.villageheart.marker.invalid", header.getKey(), "header key");
			h.assertValueEqual("%s at %s: invalid", header.getFallback(), "header fallback");
			h.assertValueEqual("Storage", String.valueOf(header.getArgs()[0]), "header arg 0");
			h.assertValueEqual(pos(h, marker).toString(), String.valueOf(header.getArgs()[1]), "header arg 1");
			var line = lineBody(rp.messages.get(1));
			h.assertValueEqual("message.villageheart.reason.needs_more", line.getKey(), "reason key");
			h.assertValueEqual("Needs 1 more chest", line.getFallback(), "reason fallback");

			// a second chest, and a click in the very same tick: the check is synchronous
			chest(h, inside(ORIGIN_A, 4, 0, 3));
			rp.messages.clear();
			rightClick(h, rp, marker, Direction.NORTH, ItemStack.EMPTY);
			h.assertValueEqual("message.villageheart.marker.valid", translatable(rp.messages.get(0)).getKey(), "valid header in the same tick");
			var containers = translatable(rp.messages.get(rp.messages.size() - 1));
			h.assertValueEqual("message.villageheart.marker.containers", containers.getKey(), "detail key");
			h.assertValueEqual("2", String.valueOf(containers.getArgs()[0]), "containers");
			h.assertValueEqual(MarkerStatus.VALID, status(h, marker), "the plaque is green at once");

			// holding a marker: report, do not place a second one
			ItemStack held = new ItemStack(MarkerBlocks.ITEMS.get(BuildingType.STORAGE), 3);
			rp.messages.clear();
			rightClick(h, rp, marker, Direction.NORTH, held);
			h.assertTrue(!rp.messages.isEmpty(), "a report was sent");
			h.assertValueEqual(3, held.getCount(), "the item stack is untouched");
			h.assertTrue(h.getBlockState(marker.north()).isAir(), "no second marker was placed");
			VillageTestSupport.done(h);
		});
	}

	// ---- T-L2-15 ---------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:buildings_recolour", structure = Y, skyAccess = true, maxTicks = 400)
	public void statusChangeIsNotAPlacement(GameTestHelper h) {
		BlockPos marker = markerPos(ORIGIN_A, 5);
		int[] notes = new int[1];
		GameTestSequence seq = begin(h).thenExecute(() -> {
			storageRoom(h, ORIGIN_A, 2);
			placeMarker(h, BuildingType.STORAGE, marker, Direction.NORTH);
		});
		seq = awaitWithin(h, seq, 40, "green", () -> assertStatus(h, marker, MarkerStatus.VALID));
		seq = seq.thenExecute(() -> {
			notes[0] = BuildingSystem.placedNotesForTests(h.getLevel().getServer());
			h.assertValueEqual(1, notes[0], "one placement so far");
			h.destroyBlock(inside(ORIGIN_A, 0, 0, 3));
		});
		seq = awaitWithin(h, seq, 40, "recoloured", () -> assertStatus(h, marker, MarkerStatus.INVALID));
		seq = seq.thenExecute(() -> {
			MinecraftServer server = h.getLevel().getServer();
			h.assertValueEqual(notes[0], BuildingSystem.placedNotesForTests(server), "a recolour is not a placement");
			h.assertValueEqual(0, BuildingSystem.pendingForTests(server), "nothing pending");
			h.assertValueEqual(1, VillageClaims.current(server).orElseThrow().buildings().size(), "no duplicate record");
			BlockPos abs = h.absolutePos(marker);
			BlockState state = h.getLevel().getBlockState(abs);
			h.getLevel().setBlock(abs, state.setValue(MarkerBlock.FACING, Direction.WEST), Block.UPDATE_CLIENTS);
		}).thenIdle(5);
		seq.thenExecute(() -> {
			MinecraftServer server = h.getLevel().getServer();
			h.assertValueEqual(notes[0], BuildingSystem.placedNotesForTests(server), "a rotation is not a placement");
			h.assertValueEqual(1, VillageClaims.current(server).orElseThrow().buildings().size(), "still one record");
			VillageTestSupport.done(h);
		});
	}
}
