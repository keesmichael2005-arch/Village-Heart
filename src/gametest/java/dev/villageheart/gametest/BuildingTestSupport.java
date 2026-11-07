package dev.villageheart.gametest;

import com.mojang.authlib.GameProfile;
import dev.villageheart.block.MarkerBlock;
import dev.villageheart.block.MarkerBlocks;
import dev.villageheart.block.MarkerStatus;
import dev.villageheart.building.BuildingSystem;
import dev.villageheart.building.Buildings;
import dev.villageheart.core.BuildingRecord;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.building.Reason;
import dev.villageheart.core.building.Verdict;
import dev.villageheart.world.VillageClaims;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Helpers for the building GameTests (M5). Rules: each test declares its own environment {@code buildings_<name>}, uses
 * {@link #YARD} with {@code skyAccess = true} (the room scanner needs a real sky), resets first and calls
 * {@code VillageTestSupport.done} last. Waits are measured: {@link #awaitWithin}.
 */
public final class BuildingTestSupport {
	public static final String YARD = "villageheart-gametest:yard";
	public static final BlockPos BELL = new BlockPos(1, 1, 1);

	private BuildingTestSupport() {}

	public static String env(String name) { return "villageheart-gametest:buildings_" + name; }

	/** Bound for a change that triggers no hook: {@code n} buildings in the round-robin, {@code hops} dependent re-checks, slack. */
	public static int bound(int buildings, int hops) { return buildings * 20 + hops + 4; }

	// ---- claim -----------------------------------------------------------------------------------

	/** Reset, then (after the bell's POI exists) claim a village with a survival owner. The owner is in {@link #owner}. */
	public static GameTestSequence begin(GameTestHelper h) {
		VillageTestSupport.reset(h);
		BuildingSystem.resetRuntimeForTests();
		sweepStrayMarkers(h);
		h.setBlock(BELL, Blocks.BELL);
		return h.startSequence().thenIdle(3).thenExecute(() -> claim(h));
	}

	/**
	 * Remove every marker in the loaded chunks around the test. Test structures sit only a few dozen blocks apart, inside each
	 * other's claim radius, so markers left behind by other tests would be adopted by this test's claim and change its building
	 * count (and with it the round-robin timing the tests measure). Runs before this test builds anything, so nothing of its own
	 * can be hit; only call it with no claim active.
	 */
	public static void sweepStrayMarkers(GameTestHelper h) {
		ServerLevel level = h.getLevel();
		BlockPos centre = h.absolutePos(BlockPos.ZERO);
		for (int cx = (centre.getX() - 160) >> 4; cx <= (centre.getX() + 160) >> 4; cx++) {
			for (int cz = (centre.getZ() - 160) >> 4; cz <= (centre.getZ() + 160) >> 4; cz++) {
				var chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null) continue;
				var sections = chunk.getSections();
				for (int i = 0; i < sections.length; i++) {
					if (sections[i].hasOnlyAir() || !sections[i].maybeHas(st -> st.getBlock() instanceof MarkerBlock)) continue;
					int baseY = (level.getMinSectionY() + i) << 4;
					for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) {
						if (sections[i].getBlockState(x, y, z).getBlock() instanceof MarkerBlock) {
							level.setBlock(new BlockPos((cx << 4) + x, baseY + y, (cz << 4) + z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
						}
					}
				}
			}
		}
	}

	private static ServerPlayer owner;

	public static ServerPlayer owner() { return owner; }

	/** Place the Heart next to the bell (the bell must be placed 3 ticks earlier). */
	public static void claim(GameTestHelper h) {
		owner = VillageTestSupport.player(h, "owner", GameType.SURVIVAL);
		VillageTestSupport.place(h, owner, new BlockPos(3, 0, 1));
		h.assertTrue(VillageClaims.current(h.getLevel().getServer()).isPresent(), "the Heart claimed the village");
	}

	public static void setTier(GameTestHelper h, dev.villageheart.core.Tier tier) {
		VillageClaims.data(h.getLevel().getServer()).update(v -> v.withTier(tier));
	}

	// ---- building rooms --------------------------------------------------------------------------

	private static void door(GameTestHelper h, BlockPos lower, Direction facing) {
		BlockState base = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, facing);
		h.setBlock(lower, base.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		h.setBlock(lower.above(), base.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
	}

	/**
	 * An oak-planks room: shell x {ox..ox+w+1}, y {oy..oy+4}, z {oz..oz+d+1}; interior w x d, 3 high; a door in the north wall
	 * at x = ox + (w + 1) / 2. Returns the marker position: above the door, one block outside, hanging on the north wall
	 * (place it with {@link #placeMarker} facing NORTH). Leave at least 2 free blocks in front of the door wall.
	 */
	public static BlockPos room(GameTestHelper h, BlockPos origin, int w, int d) {
		for (int x = 0; x <= w + 1; x++) for (int y = 0; y <= 4; y++) for (int z = 0; z <= d + 1; z++) {
			boolean edge = x == 0 || x == w + 1 || y == 0 || y == 4 || z == 0 || z == d + 1;
			h.setBlock(origin.offset(x, y, z), edge ? Blocks.OAK_PLANKS.defaultBlockState() : Blocks.AIR.defaultBlockState());
		}
		int doorX = origin.getX() + (w + 1) / 2;
		door(h, new BlockPos(doorX, origin.getY() + 1, origin.getZ()), Direction.NORTH);
		return new BlockPos(doorX, origin.getY() + 3, origin.getZ() - 1);
	}

	/** Where {@link #room}'s marker goes for a room of interior width {@code w} at {@code origin}. */
	public static BlockPos markerPos(BlockPos origin, int w) {
		return new BlockPos(origin.getX() + (w + 1) / 2, origin.getY() + 3, origin.getZ() - 1);
	}

	/** An interior position: (i, j, k) counted from the first interior cell (1, 1, 1 of the shell). */
	public static BlockPos inside(BlockPos origin, int i, int j, int k) { return origin.offset(1 + i, 1 + j, 1 + k); }

	/** A second door, in the east wall, with a marker position outside it (facing EAST). */
	public static BlockPos eastDoorMarker(GameTestHelper h, BlockPos origin, int w, int d) {
		int doorZ = origin.getZ() + (d + 1) / 2;
		int wallX = origin.getX() + w + 1;
		door(h, new BlockPos(wallX, origin.getY() + 1, doorZ), Direction.EAST);
		return new BlockPos(wallX + 1, origin.getY() + 3, doorZ);
	}

	// ---- markers ---------------------------------------------------------------------------------

	/** Place a marker the way a player does: right-click the wall block behind it, from outside, with the item in hand. */
	public static void placeMarker(GameTestHelper h, BuildingType type, BlockPos markerRel, Direction outward) {
		ServerPlayer placer = MarkerTestSupport.player(h, "placer", GameType.CREATIVE, markerRel.relative(outward, 3));
		try {
			BlockPos support = markerRel.relative(outward.getOpposite());
			InteractionResult r = MarkerTestSupport.clickFace(h, placer, support, outward, new ItemStack(MarkerBlocks.ITEMS.get(type)));
			h.assertTrue(r.consumesAction(), "the marker was placed: " + r);
			h.assertTrue(h.getBlockState(markerRel).getBlock() == MarkerBlocks.BLOCKS.get(type), "a " + type.id() + " marker stands at " + markerRel);
		} finally {
			MarkerTestSupport.removePlayer(h, placer);
		}
	}

	public static HeartLocation loc(GameTestHelper h, BlockPos rel) { return VillageClaims.locate(h.getLevel(), h.absolutePos(rel)); }

	public static MarkerStatus status(GameTestHelper h, BlockPos rel) { return h.getBlockState(rel).getValue(MarkerBlock.STATUS); }

	public static Optional<BuildingRecord> record(GameTestHelper h, BlockPos rel) {
		return VillageClaims.current(h.getLevel().getServer()).flatMap(v -> v.building(loc(h, rel)));
	}

	public static Optional<Verdict> verdict(GameTestHelper h, BlockPos rel) {
		return Buildings.report(h.getLevel().getServer(), loc(h, rel));
	}

	public static List<Reason> reasons(GameTestHelper h, BlockPos rel) {
		return verdict(h, rel).map(Verdict::reasons).orElse(List.of());
	}

	/** Assert the plaque colour, the saved status and the stored verdict agree on {@code expected}. */
	public static void assertStatus(GameTestHelper h, BlockPos rel, MarkerStatus expected) {
		h.assertValueEqual(expected, status(h, rel), "plaque at " + rel);
		var rec = record(h, rel);
		h.assertTrue(rec.isPresent(), "a building is registered at " + rel);
		h.assertValueEqual(expected.name(), rec.get().status().name(), "saved status at " + rel);
	}

	/**
	 * Run {@code check} every tick until it passes, then assert it took at most {@code bound} ticks from now: a measured
	 * bound on top of the test's timeout.
	 */
	public static GameTestSequence awaitWithin(GameTestHelper h, GameTestSequence seq, int bound, String what, Runnable check) {
		long[] t0 = new long[1];
		return seq.thenExecute(() -> t0[0] = h.getTick())
				.thenWaitUntil(check)
				.thenExecute(() -> {
					h.assertTrue(h.getTick() - t0[0] <= bound, what + " took " + (h.getTick() - t0[0]) + " ticks, bound " + bound);
				});
	}

	// ---- right-click and chat --------------------------------------------------------------------

	/** A real ServerPlayer that records the system messages sent to it. */
	public static final class RecordingPlayer extends ServerPlayer {
		public final List<Component> messages = new ArrayList<>();

		RecordingPlayer(MinecraftServer server, ServerLevel level, GameProfile profile) {
			super(server, level, profile, ClientInformation.createDefault());
		}

		@Override
		public void sendSystemMessage(Component message) { messages.add(message); }
	}

	public static RecordingPlayer recordingPlayer(GameTestHelper h, String name) {
		return (RecordingPlayer) VillageTestSupport.player(h, name, GameType.CREATIVE, RecordingPlayer::new);
	}

	/** Right-click the marker at {@code markerRel} (placed facing {@code outward}) with {@code stack} in the main hand. */
	public static InteractionResult rightClick(GameTestHelper h, ServerPlayer player, BlockPos markerRel, Direction outward, ItemStack stack) {
		return rightClickAbs(h.getLevel(), player, h.absolutePos(markerRel), outward, stack);
	}

	public static InteractionResult rightClickAbs(ServerLevel level, ServerPlayer player, BlockPos abs, Direction outward, ItemStack stack) {
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		Vec3 at = Vec3.atCenterOf(abs).add(outward.getStepX() * 0.4, outward.getStepY() * 0.4, outward.getStepZ() * 0.4);
		BlockHitResult hit = new BlockHitResult(at, outward, abs, false);
		return player.gameMode.useItemOn(player, level, stack, InteractionHand.MAIN_HAND, hit);
	}

	public static TranslatableContents translatable(Component c) {
		if (!(c.getContents() instanceof TranslatableContents t)) throw new AssertionError("not a translatable component: " + c);
		return t;
	}

	/** The reason line of a report: " - " plus a translatable child. */
	public static TranslatableContents lineBody(Component line) {
		return translatable(line.getSiblings().get(0));
	}

	public static void clearBlock(GameTestHelper h, BlockPos rel) { h.setBlock(rel, Blocks.AIR); }
}
