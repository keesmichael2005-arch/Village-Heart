package dev.villageheart.gametest;

import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.job.ToolTier;
import dev.villageheart.core.mine.DepthBand;
import dev.villageheart.core.mine.HaulRng;
import dev.villageheart.core.mine.HaulTable;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.worker.HireResult;
import dev.villageheart.worker.Workers;
import dev.villageheart.core.mine.AwayState;
import dev.villageheart.worker.job.AwayTrips;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Helpers for the miner GameTests (M16). Rules (docs/TESTING.md, "Miner GameTests"): one environment per test ({@code mine_<name>}),
 * the {@code yard} structure with {@code skyAccess}, {@code begin} first, {@code VillageTestSupport.done} + {@code WorkerTestSupport.end} last.
 *
 * <p>Two scenes. The GameTest world is a flat world and tests stand at y = -59, so a Mineshaft built in the yard is in the DEEP band
 * (cobbled deepslate, below Y 0). <b>Low scene</b> ({@code dy = 0}): a 13-high stone tower with the shaft in it (the same shaft as the
 * Building tests' {@code buildShaft}), reached by a staircase; deepest Y = -58. <b>High scene</b> ({@code dy = 64 - absY}): the
 * whole village stands on a 13-thick stone platform in the air whose top block is at absolute Y 63, the shaft is dug into it (deepest
 * Y = 52, the HIGH band); it lies inside the structure's own x/z footprint, 110 blocks above every other test.
 */
public final class MineTestSupport {
	private MineTestSupport() {}

	public static String env(String name) { return "villageheart-gametest:mine_" + name; }

	public static final BlockPos MINE = WorkerTestSupport.ORIGIN_A;
	public static final BlockPos STORE = WorkerTestSupport.ORIGIN_B;
	/** Shaft centres (x, z): the first is the nearer one. */
	public static final int[] SHAFT1 = {4, 18};
	public static final int[] SHAFT2 = {28, 24};
	/** Tower height of the low scene (top block). */
	public static final int TOWER_TOP = 12;

	/** The height of the high scene's platform surface above this test's origin: the walking plane is at absolute Y 64. */
	public static int high(GameTestHelper h) { return 64 - h.absolutePos(BlockPos.ZERO).getY(); }

	/** The top block of the stone the shaft is dug into, relative. */
	public static int top(int dy) { return dy > 0 ? dy - 1 : TOWER_TOP; }

	public static BlockPos mineMarker(int dy) { return BuildingTestSupport.markerPos(MINE.above(dy), 5); }

	public static BlockPos storeMarker(int dy) { return BuildingTestSupport.markerPos(STORE.above(dy), 5); }

	public static BlockPos stonecutter(int dy) { return BuildingTestSupport.inside(MINE.above(dy), 0, 0, 0); }

	public static BlockPos stonecutter2(int dy) { return BuildingTestSupport.inside(MINE.above(dy), 4, 0, 0); }

	public static BlockPos chest(int dy) { return BuildingTestSupport.inside(MINE.above(dy), 4, 0, 3); }

	public static BlockPos storeChest1(int dy) { return BuildingTestSupport.inside(STORE.above(dy), 0, 0, 3); }

	public static BlockPos storeChest2(int dy) { return BuildingTestSupport.inside(STORE.above(dy), 4, 0, 3); }

	public static BlockPos spawnSpot(int dy) { return BuildingTestSupport.inside(MINE.above(dy), 1, 0, 1); }

	public static BlockPos shaftMarker(int dy, int[] shaft) { return new BlockPos(shaft[0], top(dy) + 1, shaft[1]); }

	/** The rail cell (relative) removed to make a shaft invalid: two of the eight go below the minimum. */
	public static BlockPos rail(int dy, int[] shaft, int i) { return new BlockPos(shaft[0] - 1 + i / 3, top(dy) - 11, shaft[1] - 1 + i % 3); }

	// ---- building -----------------------------------------------------------------------------------

	private static void fill(GameTestHelper h, int x0, int y0, int z0, int x1, int y1, int z1, Block block) {
		ServerLevel level = h.getLevel();
		BlockState state = block.defaultBlockState();
		for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) {
			level.setBlock(h.absolutePos(new BlockPos(x, y, z)), state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		}
	}

	/** The ground the village stands on: grass in the low scene, a stone platform (13 thick, top block at dy - 1) in the high one. */
	public static void ground(GameTestHelper h, int dy) {
		if (dy > 0) {
			fill(h, 0, dy - 13, 0, 31, dy - 1, 31, Blocks.STONE);
			// whatever an earlier failed run left above the platform, except the bell and the Heart in the corner
			ServerLevel level = h.getLevel();
			for (int x = 0; x < 32; x++) for (int y = dy; y <= dy + 14; y++) for (int z = 0; z < 32; z++) {
				if (x <= 4 && y <= dy + 3 && z <= 3) continue;
				BlockPos p = h.absolutePos(new BlockPos(x, y, z));
				if (!level.getBlockState(p).isAir()) level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			}
		} else {
			LumberTestSupport.sweepStrayTrees(h, 48);
			LumberTestSupport.grass(h, 0, 0, 31, 31);
		}
	}

	/** Empties the high scene again (the platform and everything on it). */
	public static void clearHigh(GameTestHelper h, int dy) {
		if (dy > 0) fill(h, 0, dy - 13, 0, 31, dy + 12, 31, Blocks.AIR);
	}

	/** A shaft exactly 12 deep (8 rails, one torch) in the stone whose top block is {@code top(dy)}; the low scene adds the tower and its stairs. */
	public static void buildShaft(GameTestHelper h, int dy, int[] shaft) {
		int sx = shaft[0], sz = shaft[1], top = top(dy);
		if (dy == 0) {
			fill(h, sx - 2, 0, sz - 2, sx + 2, top, sz + 2, Blocks.STONE);
			// the stairs: from the ground 15 blocks north of the tower up to its top face, in line with the marker (a path search that has to
			// go around a tower first runs out of nodes: the miner would stand at the foot of the tower)
			for (int k = 0; k <= 12; k++) {
				BlockPos p = new BlockPos(sx, top - k, sz - 3 - k);
				h.setBlock(p, Blocks.STONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
			}
		}
		fill(h, sx - 1, top - 11, sz - 1, sx + 1, top - 10, sz + 1, Blocks.AIR);
		fill(h, sx, top - 9, sz, sx, top, sz, Blocks.AIR);
		int n = 0;
		for (int x = sx - 1; x <= sx + 1; x++) for (int z = sz - 1; z <= sz + 1; z++) {
			h.setBlock(new BlockPos(x, top - 11, z), n < 8 ? Blocks.RAIL : Blocks.TORCH);
			n++;
		}
		h.setBlock(new BlockPos(sx, top + 1, sz - 1), Blocks.OAK_FENCE);
	}

	public static void buildMine(GameTestHelper h, int dy, boolean secondStonecutter) {
		BuildingTestSupport.room(h, MINE.above(dy), 5, 4);
		h.setBlock(stonecutter(dy), Blocks.STONECUTTER);
		if (secondStonecutter) h.setBlock(stonecutter2(dy), Blocks.STONECUTTER);
		h.setBlock(chest(dy), Blocks.CHEST);
		h.setBlock(BuildingTestSupport.inside(MINE.above(dy), 2, 0, 3), Blocks.TORCH);
	}

	public static void buildStorage(GameTestHelper h, int dy) {
		BuildingTestSupport.room(h, STORE.above(dy), 5, 4);
		h.setBlock(storeChest1(dy), Blocks.CHEST);
		h.setBlock(storeChest2(dy), Blocks.CHEST);
	}

	/** Torches on the ground every 4 blocks outside the rooms, the tower and the staircase. */
	public static void lightYard(GameTestHelper h, int dy) {
		for (int x = 0; x <= 31; x += 4) for (int z = 0; z <= 31; z += 4) {
			boolean inRooms = x >= 7 && x <= 26 && z >= 2 && z <= 10;
			boolean inTower = inShaftArea(SHAFT1, x, z) || inShaftArea(SHAFT2, x, z);
			if (!inRooms && !inTower) h.setBlock(new BlockPos(x, dy, z), Blocks.TORCH);
		}
	}

	/** The tower and its staircase (low scene), or just the tower (high scene): no torch there. */
	private static boolean inShaftArea(int[] s, int x, int z) { return Math.abs(x - s[0]) <= 2 && z >= 2 && z <= s[1] + 2; }

	public static void placeMarkers(GameTestHelper h, int dy, int[]... shafts) {
		BuildingTestSupport.placeMarker(h, BuildingType.MINE, mineMarker(dy), Direction.NORTH);
		BuildingTestSupport.placeMarker(h, BuildingType.STORAGE, storeMarker(dy), Direction.NORTH);
		for (int[] s : shafts) BuildingTestSupport.placeMarker(h, BuildingType.MINESHAFT, shaftMarker(dy, s), Direction.SOUTH);
	}

	public static void assertValid(GameTestHelper h, int dy, int[]... shafts) {
		BuildingTestSupport.assertStatus(h, mineMarker(dy), dev.villageheart.block.MarkerStatus.VALID);
		BuildingTestSupport.assertStatus(h, storeMarker(dy), dev.villageheart.block.MarkerStatus.VALID);
		for (int[] s : shafts) BuildingTestSupport.assertStatus(h, shaftMarker(dy, s), dev.villageheart.block.MarkerStatus.VALID);
		for (int[] s : shafts) {
			HeartLocation loc = BuildingTestSupport.loc(h, shaftMarker(dy, s));
			h.assertTrue(dev.villageheart.building.Buildings.shaftDepth(h.getLevel().getServer(), loc).isPresent(), "the shaft depth is known");
		}
	}

	/**
	 * Claim, sweep, ground, a Mine with its Mineshaft(s) and a Storage; the sequence ends when all are valid and the shafts' depth is known.
	 * {@code dy} 0 = low scene, {@code high(h)} = high scene. Starts at day time 1000 (the caller sets it after).
	 */
	public static GameTestSequence scene(GameTestHelper h, int dy, boolean secondStonecutter, int[]... shafts) {
		return BuildingTestSupport.begin(h, dy).thenExecute(() -> {
			ground(h, dy);
			buildMine(h, dy, secondStonecutter);
			buildStorage(h, dy);
			for (int[] s : shafts) buildShaft(h, dy, s);
			BuildingTestSupport.setTier(h, dev.villageheart.core.Tier.VILLAGE);
			placeMarkers(h, dy, shafts);
		}).thenWaitUntil(() -> assertValid(h, dy, shafts));
	}

	// ---- villagers and items ------------------------------------------------------------------------

	public static Villager hire(GameTestHelper h, int dy) {
		Villager v = WorkerTestSupport.spawnVillager(h, spawnSpot(dy));
		HireResult r = Workers.hire(h.getLevel(), v, JobType.MINER, Optional.empty());
		h.assertTrue(r instanceof HireResult.Hired, "the miner was hired: " + r);
		return v;
	}

	public static Villager hireAt(GameTestHelper h, BlockPos spawnRel) {
		Villager v = WorkerTestSupport.spawnVillager(h, spawnRel);
		HireResult r = Workers.hire(h.getLevel(), v, JobType.MINER, Optional.empty());
		h.assertTrue(r instanceof HireResult.Hired, "the miner was hired: " + r);
		return v;
	}

	public static ItemStack pickaxe(ToolTier t) {
		Item item = switch (t) {
			case WOOD -> Items.WOODEN_PICKAXE;
			case STONE -> Items.STONE_PICKAXE;
			case COPPER -> Items.COPPER_PICKAXE;
			case GOLD -> Items.GOLDEN_PICKAXE;
			case IRON -> Items.IRON_PICKAXE;
			case DIAMOND -> Items.DIAMOND_PICKAXE;
			case NETHERITE -> Items.NETHERITE_PICKAXE;
		};
		return new ItemStack(item);
	}

	public static void putInChest(GameTestHelper h, BlockPos chestRel, int slot, ItemStack stack) {
		LumberTestSupport.putInChest(h, chestRel, slot, stack);
	}

	public static ItemStack chestSlot(GameTestHelper h, BlockPos chestRel, int slot) { return StockpileTestSupport.container(h, chestRel).getItem(slot); }

	public static dev.villageheart.core.worker.WorkerStatus status(GameTestHelper h, Villager v) { return LumberTestSupport.status(h, v); }

	public static long count(GameTestHelper h, Item item) {
		return dev.villageheart.stock.Stockpiles.count(h.getLevel().getServer(), item);
	}

	/** Sum of the first {@code n} hauls this villager rolls in this world (seed, uuid, index 0..n-1) for a band and tier. */
	public static Map<String, Integer> predicted(ServerLevel level, UUID villager, DepthBand band, ToolTier tier, long n) {
		Map<String, Integer> sum = new LinkedHashMap<>();
		for (long i = 0; i < n; i++) HaulTable.roll(band, tier, HaulRng.forHaul(level.getSeed(), villager, i)).forEach((k, c) -> sum.merge(k, c, Integer::sum));
		return sum;
	}

	public static Optional<dev.villageheart.core.building.ShaftScanner.ShaftDepth> depth(GameTestHelper h, int dy, int[] shaft) {
		return dev.villageheart.building.Buildings.shaftDepth(h.getLevel().getServer(), BuildingTestSupport.loc(h, shaftMarker(dy, shaft)));
	}

	public record Descent(long tick, long since, BlockPos surface, long dayTime) {}

	public record Ascent(long tick, Map<String, Integer> inventory, Vec3 pos, AwayTrips.SurfaceRecord record, boolean invisible, boolean noAi,
			boolean invulnerable, long dayTime, double progress) {}

	/**
	 * Watches one miner every tick (register {@code h.onEachTick(() -> t.sample(h))} from the test body, never inside a callback) and
	 * records each descent and ascent with the state at that very tick: where it hid, when, and what it carried when it came up.
	 */
	public static final class Tracker {
		private final java.util.function.Supplier<Villager> villager;
		private boolean active, wasAway;
		private AwayState.AwayTrip trip;
		public final java.util.List<Descent> descents = new java.util.ArrayList<>();
		public final java.util.List<Ascent> ascents = new java.util.ArrayList<>();

		public Tracker(java.util.function.Supplier<Villager> villager) { this.villager = villager; }

		public void start() { active = true; }

		public void sample(GameTestHelper h) {
			if (!active) return;
			Villager v = villager.get();
			if (v == null || v.isRemoved()) return;
			boolean away = AwayTrips.isAway(v);
			if (away && !wasAway) {
				trip = AwayTrips.state(v).trip().orElseThrow();
				descents.add(new Descent(h.getTick(), trip.since(), new BlockPos(trip.surface().x(), trip.surface().y(), trip.surface().z()),
						h.getLevel().getDayTime() % 24000));
			} else if (!away && wasAway) {
				Map<String, Integer> inv = new LinkedHashMap<>();
				var c = v.getInventory();
				for (int i = 0; i < c.getContainerSize(); i++) {
					ItemStack s = c.getItem(i);
					if (!s.isEmpty()) inv.merge(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).toString(), s.getCount(), Integer::sum);
				}
				ascents.add(new Ascent(h.getTick(), inv, v.position(), AwayTrips.lastSurface(v.getUUID()).orElse(null), v.isInvisible(), v.isNoAi(), v.isInvulnerable(),
						h.getLevel().getDayTime() % 24000, AwayTrips.state(v).progress().units()));
			}
			wasAway = away;
		}

		public boolean away() { return wasAway; }
	}

	/** Absolute position of a relative one, as a block. */
	public static BlockPos abs(GameTestHelper h, BlockPos rel) { return h.absolutePos(rel); }
}
