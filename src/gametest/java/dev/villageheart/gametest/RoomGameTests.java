package dev.villageheart.gametest;

import dev.villageheart.VillageHeart;
import dev.villageheart.core.room.Cell;
import dev.villageheart.core.room.DoorPart;
import dev.villageheart.core.room.Dir;
import dev.villageheart.core.room.Pos;
import dev.villageheart.core.room.RoomProblem;
import dev.villageheart.core.room.RoomProblem.Kind;
import dev.villageheart.core.room.RoomScan;
import dev.villageheart.core.room.SharedRooms;
import dev.villageheart.room.LevelBlockView;
import dev.villageheart.room.RoomCells;
import dev.villageheart.room.RoomScans;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Layer 2: room detection against real blocks (M4). Every test uses {@code skyAccess = true}: without it the test framework
 * encases the structure in barriers on top too, a roof hole would be "enclosed" by the barrier ceiling and every leak test would
 * pass for the wrong reason.
 *
 * <p>Fixture {@link #buildRoom}: oak planks shell x 1..7, y 0..4, z 2..7 (interior x 2..6, y 1..3, z 3..6 = 60 cells, floor area 20).
 * The strip z 0..1 in front of the door wall is two deep (barriers sit at z = -1; a one-deep strip would be an "alley").
 * Door at (4,1,2), marker position (4,3,1) facing NORTH (the marker block itself is not needed: the cell stays air).
 */
public class RoomGameTests {
	private static final BlockPos MARKER = new BlockPos(4, 3, 1);
	private static final BlockPos DOOR = new BlockPos(4, 1, 2);
	private static final BlockPos GAP = new BlockPos(3, 2, 2);
	private static final BlockPos ROOF = new BlockPos(4, 4, 4);

	private static BlockState planks() { return Blocks.OAK_PLANKS.defaultBlockState(); }

	private static void door(GameTestHelper h, Block block, BlockPos lower, Direction facing) {
		BlockState base = block.defaultBlockState().setValue(DoorBlock.FACING, facing);
		h.setBlock(lower, base.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		h.setBlock(lower.above(), base.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
	}

	/** The standard room with a door, a bed, a chest and a torch. */
	private static void buildRoom(GameTestHelper h) {
		for (int x = 1; x <= 7; x++) for (int y = 0; y <= 4; y++) for (int z = 2; z <= 7; z++) {
			boolean edge = x == 1 || x == 7 || y == 0 || y == 4 || z == 2 || z == 7;
			h.setBlock(new BlockPos(x, y, z), edge ? planks() : Blocks.AIR.defaultBlockState());
		}
		door(h, Blocks.OAK_DOOR, DOOR, Direction.NORTH);
		BlockState bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH);
		h.setBlock(new BlockPos(3, 1, 5), bed.setValue(BedBlock.PART, BedPart.FOOT));
		h.setBlock(new BlockPos(3, 1, 6), bed.setValue(BedBlock.PART, BedPart.HEAD));
		h.setBlock(new BlockPos(5, 1, 6), Blocks.CHEST);
		h.setBlock(new BlockPos(2, 1, 6), Blocks.TORCH);
	}

	private static RoomScan scan(GameTestHelper h) { return scan(h, MARKER, Direction.NORTH); }

	private static RoomScan scan(GameTestHelper h, BlockPos marker, Direction outward) {
		return RoomScans.scan(h.getLevel(), h.absolutePos(marker), outward);
	}

	private static Pos abs(GameTestHelper h, BlockPos rel) { return RoomScans.pos(h.absolutePos(rel)); }

	private static void assertProblem(GameTestHelper h, RoomScan s, Kind kind, BlockPos rel, String what) {
		h.assertTrue(!s.enclosed() && !s.valid(), what + ": expected a failing scan, got " + s.problems());
		h.assertValueEqual(List.of(new RoomProblem(kind, abs(h, rel))), s.problems(), what);
	}

	/** T-L2-1 (roadmap) */
	@GameTest(skyAccess = true, maxTicks = 100)
	public void realRoomEnclosed(GameTestHelper h) {
		buildRoom(h);
		long t0 = System.nanoTime();
		RoomScan s = scan(h);
		long micros = (System.nanoTime() - t0) / 1000;
		VillageHeart.LOG.info("room scan took {} us", micros);
		h.assertTrue(s.valid(), "valid: " + s.problems());
		h.assertValueEqual(57, s.volume(), "volume (60 - bed 2 - chest 1; the torch is air)");
		h.assertValueEqual(20, s.floorArea(), "floor area");
		h.assertValueEqual(abs(h, DOOR), s.door(), "door");
		h.assertValueEqual(1, s.count("#bed"), "#bed");
		h.assertValueEqual(List.of(abs(h, new BlockPos(3, 1, 6))), s.positions("#bed"), "bed head position");
		h.assertValueEqual(1, s.count("minecraft:red_bed"), "red_bed ids");
		h.assertValueEqual(List.of(abs(h, new BlockPos(5, 1, 6))), s.positions("#chest"), "#chest");
		h.assertValueEqual(1, s.count("#light"), "#light (the torch)");
		h.assertValueEqual(1, s.count("#door"), "#door");
		h.succeed();
	}

	/** T-L2-2 (roadmap) */
	@GameTest(skyAccess = true, maxTicks = 100)
	public void brokenRoofBlockReported(GameTestHelper h) {
		buildRoom(h);
		h.assertTrue(scan(h).valid(), "control: valid before breaking");
		h.setBlock(ROOF, Blocks.AIR);
		RoomScan s = scan(h);
		assertProblem(h, s, Kind.ROOF_MISSING, ROOF, "roof hole");
		h.assertValueEqual("Roof missing at " + abs(h, ROOF), s.problems().get(0).message(), "message");
		h.setBlock(ROOF, planks());
		h.assertTrue(scan(h).valid(), "valid again after the repair");

		BlockPos corner = new BlockPos(2, 4, 6);
		h.setBlock(corner, Blocks.AIR);
		assertProblem(h, scan(h), Kind.ROOF_MISSING, corner, "corner roof hole");
		h.succeed();
	}

	private static void expectValid(GameTestHelper h, BlockState gap, String what) {
		h.setBlock(GAP, gap);
		RoomScan s = scan(h);
		h.assertTrue(s.valid(), what + " should count as wall, got " + s.problems());
		h.setBlock(GAP, Blocks.AIR);
	}

	private static void expectHole(GameTestHelper h, BlockState gap, String what) {
		h.setBlock(GAP, gap);
		assertProblem(h, scan(h), Kind.WALL_GAP, GAP, what + " should be a hole");
		h.setBlock(GAP, Blocks.AIR);
	}

	/** T-L2-3 (roadmap: fences, glass and doors count as wall) */
	@GameTest(skyAccess = true, maxTicks = 200)
	public void wallMaterialsCountAsWall(GameTestHelper h) {
		buildRoom(h);
		expectHole(h, Blocks.AIR.defaultBlockState(), "air (control)");

		expectValid(h, Blocks.GLASS.defaultBlockState(), "glass");
		expectValid(h, Blocks.GLASS_PANE.defaultBlockState(), "glass pane");
		expectValid(h, Blocks.WHITE_STAINED_GLASS_PANE.defaultBlockState(), "stained glass pane");
		expectValid(h, Blocks.OAK_FENCE.defaultBlockState(), "oak fence");
		expectValid(h, Blocks.COBBLESTONE_WALL.defaultBlockState(), "cobblestone wall");
		expectValid(h, Blocks.IRON_BARS.defaultBlockState(), "iron bars");
		expectValid(h, Blocks.OAK_FENCE_GATE.defaultBlockState(), "closed fence gate");
		expectValid(h, Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.OPEN, true), "open fence gate");
		expectValid(h, Blocks.OAK_TRAPDOOR.defaultBlockState(), "closed trapdoor");
		expectValid(h, Blocks.OAK_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.OPEN, true), "open trapdoor");
		expectValid(h, Blocks.OAK_SLAB.defaultBlockState(), "bottom slab");
		expectValid(h, Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP), "top slab");
		expectValid(h, Blocks.OAK_STAIRS.defaultBlockState(), "stairs");
		expectValid(h, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true), "leaves");
		expectValid(h, Blocks.WHITE_CARPET.defaultBlockState(), "carpet");
		expectValid(h, Blocks.CHEST.defaultBlockState(), "chest");

		// a carpet in a roof hole: the sky test agrees with the wall rule
		h.setBlock(GAP, planks());
		h.assertTrue(scan(h).valid(), "control: the repaired wall is valid again");
		h.setBlock(ROOF, Blocks.AIR);
		assertProblem(h, scan(h), Kind.ROOF_MISSING, ROOF, "roof hole (control)");
		h.setBlock(ROOF, Blocks.WHITE_CARPET);
		RoomScan carpet = scan(h);
		h.assertTrue(carpet.valid(), "carpet in the roof hole should close it, got " + carpet.problems());
		h.setBlock(ROOF, planks());

		// a second door
		door(h, Blocks.SPRUCE_DOOR, new BlockPos(6, 1, 2), Direction.NORTH);
		RoomScan two = scan(h);
		h.assertTrue(two.valid(), "second door: " + two.problems());
		h.assertValueEqual(2, two.count("#door"), "#door with a second door");
		h.setBlock(new BlockPos(6, 1, 2), planks());
		h.setBlock(new BlockPos(6, 2, 2), planks());

		// no-collision blocks are holes
		expectHole(h, Blocks.TORCH.defaultBlockState(), "torch");
		expectHole(h, Blocks.STONE_BUTTON.defaultBlockState(), "stone button");
		expectHole(h, Blocks.OAK_PRESSURE_PLATE.defaultBlockState(), "pressure plate");
		expectHole(h, Blocks.COBWEB.defaultBlockState(), "cobweb");
		expectHole(h, Blocks.VINE.defaultBlockState(), "vine");
		expectHole(h, Blocks.SNOW.defaultBlockState(), "one layer of snow");
		expectHole(h, Blocks.POWDER_SNOW.defaultBlockState(), "powder snow");

		// water outside a gap does not read as a roof
		for (int z = 0; z <= 2; z++) h.setBlock(new BlockPos(3, 2, z), Blocks.WATER);
		assertProblem(h, scan(h), Kind.WALL_GAP, GAP, "water in and outside the gap");
		for (int z = 0; z <= 2; z++) h.setBlock(new BlockPos(3, 2, z), Blocks.AIR);
		h.succeed();
	}

	/** T-L2-4 */
	@GameTest(skyAccess = true, maxTicks = 100)
	public void sharedRealRoom(GameTestHelper h) {
		buildRoom(h);
		door(h, Blocks.OAK_DOOR, new BlockPos(1, 1, 4), Direction.WEST);
		RoomScan north = scan(h);
		RoomScan west = scan(h, new BlockPos(0, 3, 4), Direction.WEST);
		h.assertTrue(north.enclosed() && west.enclosed(), "both enclosed: " + north.problems() + " " + west.problems());
		h.assertValueEqual(north.roomKey(), west.roomKey(), "room key");
		Pos m1 = abs(h, MARKER), m2 = abs(h, new BlockPos(0, 3, 4));
		Map<Pos, RoomScan> out = SharedRooms.apply(Map.of(m1, north, m2, west));
		h.assertValueEqual(List.of(new RoomProblem(Kind.SHARED, m2)), out.get(m1).problems(), "north scan");
		h.assertValueEqual(List.of(new RoomProblem(Kind.SHARED, m1)), out.get(m2).problems(), "west scan");
		h.succeed();
	}

	/** T-L2-5 */
	@GameTest
	public void unloadedChunkNotLoaded(GameTestHelper h) {
		LevelBlockView view = new LevelBlockView(h.getLevel());
		h.assertValueEqual(Cell.UNLOADED, view.cell(100_000, 64, 100_000), "cell far away");
		h.assertTrue(!view.openToSky(100_000, 64, 100_000), "openToSky far away");
		h.assertTrue(!h.getLevel().getChunkSource().hasChunk(100_000 >> 4, 100_000 >> 4), "the chunk must not have been loaded");
		h.succeed();
	}

	/** T-L2-7 */
	@GameTest(skyAccess = true, maxTicks = 100)
	public void openToSkyMatchesCells(GameTestHelper h) {
		buildRoom(h);
		LevelBlockView view = new LevelBlockView(h.getLevel());
		for (int x = 2; x <= 6; x++) for (int y = 1; y <= 3; y++) for (int z = 3; z <= 6; z++) {
			BlockPos p = h.absolutePos(new BlockPos(x, y, z));
			h.assertTrue(!view.openToSky(p.getX(), p.getY(), p.getZ()), "interior " + p + " is covered");
		}
		for (int x = 2; x <= 6; x++) for (int z = 0; z <= 1; z++) {
			BlockPos p = h.absolutePos(new BlockPos(x, 1, z));
			h.assertTrue(view.openToSky(p.getX(), p.getY(), p.getZ()), "strip " + p + " is open");
		}
		h.setBlock(ROOF, Blocks.WHITE_CARPET);
		LevelBlockView fresh = new LevelBlockView(h.getLevel());
		BlockPos under = h.absolutePos(new BlockPos(4, 3, 4));
		h.assertTrue(!fresh.openToSky(under.getX(), under.getY(), under.getZ()), "still covered under a carpet");
		h.succeed();
	}

	/** T-L2-8 */
	@GameTest
	public void offThreadScanRefused(GameTestHelper h) throws InterruptedException {
		AtomicReference<Throwable> thrown = new AtomicReference<>();
		BlockPos marker = h.absolutePos(MARKER);
		Thread t = new Thread(() -> {
			try {
				RoomScans.scan(h.getLevel(), marker, Direction.NORTH);
			} catch (Throwable e) {
				thrown.set(e);
			}
		});
		t.start();
		t.join();
		h.assertTrue(thrown.get() instanceof IllegalStateException, "expected IllegalStateException, got " + thrown.get());
		h.succeed();
	}

	/** T-L2-6 */
	@GameTest
	public void classifierSmoke(GameTestHelper h) {
		h.assertValueEqual(Cell.OPEN, RoomCells.of(Blocks.AIR.defaultBlockState()), "air");

		BlockState bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.EAST);
		Cell foot = RoomCells.of(bed.setValue(BedBlock.PART, BedPart.FOOT));
		Cell head = RoomCells.of(bed.setValue(BedBlock.PART, BedPart.HEAD));
		h.assertTrue(foot.contents().isEmpty(), "bed foot carries no contents: " + foot.contents());
		h.assertValueEqual(Dir.EAST, foot.partner(), "bed foot points at the head");
		h.assertTrue(head.contents().contains("minecraft:red_bed") && head.contents().contains("#bed"), "bed head: " + head.contents());
		h.assertTrue(head.partner() == null, "bed head has no partner");

		BlockState door = Blocks.OAK_DOOR.defaultBlockState();
		Cell upper = RoomCells.of(door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		Cell lower = RoomCells.of(door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		h.assertValueEqual(DoorPart.UPPER, upper.door(), "upper half");
		h.assertTrue(upper.contents().isEmpty(), "upper half carries no contents");
		h.assertValueEqual(Dir.DOWN, upper.partner(), "upper half points down");
		h.assertValueEqual(DoorPart.LOWER, lower.door(), "lower half");
		h.assertValueEqual(List.of("minecraft:oak_door", "#door"), lower.contents(), "lower half contents");

		h.assertTrue(RoomCells.of(Blocks.TRAPPED_CHEST.defaultBlockState()).contents().contains("#chest"), "trapped chest");
		h.assertTrue(RoomCells.of(Blocks.COPPER_CHEST.defaultBlockState()).contents().contains("#chest"), "copper chest");
		h.assertTrue(!RoomCells.of(Blocks.ENDER_CHEST.defaultBlockState()).contents().contains("#chest"), "ender chest");
		h.assertTrue(RoomCells.of(Blocks.LANTERN.defaultBlockState()).contents().contains("#light"), "lantern");
		h.assertTrue(!RoomCells.of(Blocks.FURNACE.defaultBlockState()).contents().contains("#light"), "unlit furnace");
		h.assertValueEqual(dev.villageheart.core.room.CellKind.OPEN, RoomCells.of(Blocks.OAK_WALL_SIGN.defaultBlockState()).kind(), "wall sign");
		h.assertValueEqual(dev.villageheart.core.room.CellKind.SOLID, RoomCells.of(Blocks.OAK_PLANKS.defaultBlockState()).kind(), "planks");
		BlockState chest = Blocks.CHEST.defaultBlockState();
		h.assertTrue(RoomCells.of(chest) == RoomCells.of(chest), "cached instance");
		h.succeed();
	}
}
