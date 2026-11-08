package dev.villageheart.gametest;

import dev.villageheart.block.JobBlocks;
import dev.villageheart.block.MarkerStatus;
import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.building.JobBlockType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The stacked scenes of the side-by-side lumberjack test (M15, docs/plans/M15.md 3.8): three identical lumber yards one above the other, 28
 * blocks apart (more than the lumberjack's 16-block vertical search range plus a dark oak's height, less than the claim radius), each a glass
 * platform with a 2-high glass rim, a Lumber Camp, a Storage and a grove of six dark oaks grown from the same seeds. The levels are
 * numbered 0 (the test's own yard), 1 and 2. The test clears the volumes first and last (a failed earlier run may have left them).
 */
public final class SideBySide {
	private SideBySide() {}

	/** Vertical distance between two levels. */
	public static final int DY = 28;
	public static final int LEVELS = 3;

	/** Dark oak trees of a grove: x, z (relative to the yard) and the seed; probed: 47-60 logs each. */
	public static final int[][] TREES = {{7, 16, 4}, {16, 16, 18}, {25, 16, 2}, {7, 26, 17}, {16, 26, 20}, {25, 26, 5}};

	public static int dy(int level) { return level * DY; }

	public static BlockPos camp(int level) { return LumberTestSupport.CAMP.above(dy(level)); }

	public static BlockPos store(int level) { return LumberTestSupport.STORE.above(dy(level)); }

	public static BlockPos campMarker(int level) { return BuildingTestSupport.markerPos(camp(level), 5); }

	public static BlockPos storeMarker(int level) { return BuildingTestSupport.markerPos(store(level), 5); }

	public static BlockPos chop(int level) { return BuildingTestSupport.inside(camp(level), 0, 0, 0); }

	public static BlockPos chest(int level) { return BuildingTestSupport.inside(camp(level), 4, 0, 3); }

	public static BlockPos spawn(int level) { return BuildingTestSupport.inside(camp(level), 1, 0, 1); }

	public static BlockPos storeChest1(int level) { return BuildingTestSupport.inside(store(level), 0, 0, 3); }

	public static BlockPos storeChest2(int level) { return BuildingTestSupport.inside(store(level), 4, 0, 3); }

	/** Empties the volume of a level above the yard (the yard itself is emptied by {@code sweepStrayTrees}) and discards villagers in it. */
	public static void clearUpper(GameTestHelper h) {
		for (int k = 1; k < LEVELS; k++) clearVolume(h, dy(k) - 1, dy(k) + 18);
	}

	/** Clears the platforms of the levels 1 and 2 again (the end of the test) so later tests find an empty sky. */
	public static void clearAll(GameTestHelper h) {
		for (int k = 1; k < LEVELS; k++) clearVolume(h, dy(k) - 1, dy(k) + 18);
	}

	private static void clearVolume(GameTestHelper h, int y0, int y1) {
		var level = h.getLevel();
		for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = y0; y <= y1; y++) {
			BlockPos abs = h.absolutePos(new BlockPos(x, y, z));
			if (level.getChunkSource().getChunkNow(abs.getX() >> 4, abs.getZ() >> 4) == null) continue;
			if (!level.getBlockState(abs).isAir()) level.setBlock(abs, Blocks.AIR.defaultBlockState(), 2 | 16);
		}
		var box = new net.minecraft.world.phys.AABB(h.absolutePos(new BlockPos(0, y0, 0)).getCenter(), h.absolutePos(new BlockPos(32, y1 + 1, 32)).getCenter());
		for (var e : level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, box, e -> !(e instanceof net.minecraft.world.entity.player.Player))) e.discard();
	}

	/** The glass ground (and the 2-high glass rim) of a level; at level 0 it replaces the yard's ground. */
	public static void platform(GameTestHelper h, int k) {
		int y = dy(k);
		for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) {
			h.setBlock(new BlockPos(x, y - 1, z), Blocks.GLASS);
			if (x == 0 || x == 31 || z == 0 || z == 31) {
				h.setBlock(new BlockPos(x, y, z), Blocks.GLASS);
				h.setBlock(new BlockPos(x, y + 1, z), Blocks.GLASS);
			}
		}
	}

	/** The camp room (Chopping Block, chest, torch), the Storage room (two chests) and the two markers of a level. */
	public static void buildBuildings(GameTestHelper h, int k) {
		BlockPos camp = camp(k);
		BuildingTestSupport.room(h, camp, 5, 4);
		h.setBlock(chop(k), JobBlocks.BLOCKS.get(JobBlockType.CHOPPING_BLOCK));
		h.setBlock(chest(k), Blocks.CHEST);
		h.setBlock(BuildingTestSupport.inside(camp, 2, 0, 3), Blocks.TORCH);
		BuildingTestSupport.room(h, store(k), 5, 4);
		h.setBlock(storeChest1(k), Blocks.CHEST);
		h.setBlock(storeChest2(k), Blocks.CHEST);
		BuildingTestSupport.placeMarker(h, BuildingType.LUMBER_CAMP, campMarker(k), Direction.NORTH);
		BuildingTestSupport.placeMarker(h, BuildingType.STORAGE, storeMarker(k), Direction.NORTH);
	}

	public static void assertValid(GameTestHelper h) {
		for (int k = 0; k < LEVELS; k++) {
			BuildingTestSupport.assertStatus(h, campMarker(k), MarkerStatus.VALID);
			BuildingTestSupport.assertStatus(h, storeMarker(k), MarkerStatus.VALID);
		}
	}

	/** A grove: the logs (absolute positions) of each tree, in the order of {@link #TREES}. */
	public record Grove(int level, List<BlockPos> bases, List<Set<BlockPos>> trees) {
		public Set<BlockPos> all() {
			Set<BlockPos> all = new HashSet<>();
			for (Set<BlockPos> t : trees) all.addAll(t);
			return all;
		}

		public int logs() { return all().size(); }
	}

	public static Grove grow(GameTestHelper h, int k) {
		List<BlockPos> bases = new ArrayList<>();
		List<Set<BlockPos>> trees = new ArrayList<>();
		for (int[] t : TREES) {
			BlockPos base = LumberTestSupport.grow(h, TreeFeatures.DARK_OAK, new BlockPos(t[0], dy(k), t[1]), t[2]);
			bases.add(base);
			trees.add(LumberTestSupport.logsOf(h, base));
		}
		return new Grove(k, bases, trees);
	}

	/** Positions of a grove translated down to level 0 (relative to the level's own origin), for the identity check. */
	public static Set<BlockPos> normalised(Grove g) {
		Set<BlockPos> out = new HashSet<>();
		for (BlockPos p : g.all()) out.add(p.below(dy(g.level())));
		return out;
	}

	public static int count(net.minecraft.world.Container c, Item item) {
		int n = 0;
		for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).is(item)) n += c.getItem(i).getCount();
		return n;
	}

	/** Damage summed over a camp chest's axes, plus {@code maxDamage} for each axe that is gone (it broke), minus the damage they started with. */
	public static int wear(GameTestHelper h, int k, int[] startDamage, int maxDamage) {
		var c = StockpileTestSupport.container(h, chest(k));
		int total = 0;
		for (int slot = 0; slot < 2; slot++) {
			ItemStack s = c.getItem(slot);
			total += (s.isEmpty() ? maxDamage : s.getDamageValue()) - startDamage[slot];
		}
		return total;
	}

	public static Villager hireAt(GameTestHelper h, int k) {
		Villager v = WorkerTestSupport.spawnVillager(h, spawn(k));
		var pref = BuildingTestSupport.record(h, campMarker(k)).orElseThrow().marker();
		var r = dev.villageheart.worker.Workers.hire(h.getLevel(), v, dev.villageheart.core.worker.JobType.LUMBERJACK, java.util.Optional.of(pref));
		h.assertTrue(r instanceof dev.villageheart.worker.HireResult.Hired, "the lumberjack of level " + k + " was hired: " + r);
		h.assertValueEqual(pref, ((dev.villageheart.worker.HireResult.Hired) r).record().building(), "hired into the camp of level " + k);
		return v;
	}
}
