package dev.villageheart.gametest;

import dev.villageheart.block.JobBlocks;
import dev.villageheart.core.Balance;
import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.building.JobBlockType;
import dev.villageheart.core.job.ToolRules;
import dev.villageheart.core.job.ToolTier;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.worker.HireResult;
import dev.villageheart.worker.Workers;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

/**
 * Helpers for the lumberjack GameTests (M9). Rules (docs/TESTING.md, "Lumberjack GameTests"): one environment per test
 * ({@code lumber_<name>}), {@code skyAccess = true}, {@code BuildingTestSupport.begin} first and {@code VillageTestSupport.done} last, trees
 * grown from a configured feature with a fixed seed (the same tree every run), stray trees of earlier tests swept away first, measured bounds.
 */
public final class LumberTestSupport {
	private LumberTestSupport() {}

	public static String env(String name) { return "villageheart-gametest:lumber_" + name; }

	/** Lumber Camp at {@link WorkerTestSupport#ORIGIN_A}, Storage at {@link WorkerTestSupport#ORIGIN_B}. */
	public static final BlockPos CAMP = WorkerTestSupport.ORIGIN_A;
	public static final BlockPos STORE = WorkerTestSupport.ORIGIN_B;

	public static BlockPos campMarker() { return BuildingTestSupport.markerPos(CAMP, 5); }

	public static BlockPos storeMarker() { return BuildingTestSupport.markerPos(STORE, 5); }

	/** The Chopping Block (relative). */
	public static BlockPos chop() { return BuildingTestSupport.inside(CAMP, 0, 0, 0); }

	/** The camp chest (relative). */
	public static BlockPos chest() { return BuildingTestSupport.inside(CAMP, 4, 0, 3); }

	public static BlockPos storeChest1() { return BuildingTestSupport.inside(STORE, 0, 0, 3); }

	public static BlockPos storeChest2() { return BuildingTestSupport.inside(STORE, 4, 0, 3); }

	/** Builds the camp room (Chopping Block, chest, torch) without its marker. */
	public static void buildCamp(GameTestHelper h) {
		BuildingTestSupport.room(h, CAMP, 5, 4);
		h.setBlock(chop(), JobBlocks.BLOCKS.get(JobBlockType.CHOPPING_BLOCK));
		h.setBlock(chest(), Blocks.CHEST);
		h.setBlock(BuildingTestSupport.inside(CAMP, 2, 0, 3), Blocks.TORCH);
	}

	/** A second Chopping Block (a second lumberjack slot) at the other end of the north wall. */
	public static BlockPos chop2() { return BuildingTestSupport.inside(CAMP, 4, 0, 0); }

	public static void addSecondChop(GameTestHelper h) { h.setBlock(chop2(), JobBlocks.BLOCKS.get(JobBlockType.CHOPPING_BLOCK)); }

	public static void buildStorage(GameTestHelper h) {
		BuildingTestSupport.room(h, STORE, 5, 4);
		h.setBlock(storeChest1(), Blocks.CHEST);
		h.setBlock(storeChest2(), Blocks.CHEST);
	}

	public static void placeMarkers(GameTestHelper h, boolean storage) {
		BuildingTestSupport.placeMarker(h, BuildingType.LUMBER_CAMP, campMarker(), Direction.NORTH);
		if (storage) BuildingTestSupport.placeMarker(h, BuildingType.STORAGE, storeMarker(), Direction.NORTH);
	}

	/** Assertion for {@code thenWaitUntil}: the camp (and Storage when asked) are valid. */
	public static void assertValid(GameTestHelper h, boolean storage) {
		BuildingTestSupport.assertStatus(h, campMarker(), dev.villageheart.block.MarkerStatus.VALID);
		if (storage) BuildingTestSupport.assertStatus(h, storeMarker(), dev.villageheart.block.MarkerStatus.VALID);
	}

	public static void putInChest(GameTestHelper h, BlockPos chestRel, int slot, ItemStack stack) {
		var c = StockpileTestSupport.container(h, chestRel);
		c.setItem(slot, stack);
		c.setChanged();
	}

	public static Villager hire(GameTestHelper h, BlockPos spawnRel) {
		Villager v = WorkerTestSupport.spawnVillager(h, spawnRel);
		HireResult r = Workers.hire(h.getLevel(), v, JobType.LUMBERJACK, Optional.empty());
		h.assertTrue(r instanceof HireResult.Hired, "the lumberjack was hired: " + r);
		return v;
	}

	/** Claim, sweep, grass, a valid Lumber Camp (and Storage): the sequence ends when both are valid. */
	public static net.minecraft.gametest.framework.GameTestSequence scene(GameTestHelper h, boolean twoChops, boolean storage) {
		return BuildingTestSupport.begin(h).thenExecute(() -> {
			sweepStrayTrees(h, 48);
			grass(h, 0, 0, 31, 31);
			buildCamp(h);
			if (twoChops) addSecondChop(h);
			if (storage) buildStorage(h);
			placeMarkers(h, storage);
		}).thenWaitUntil(() -> assertValid(h, storage));
	}

	/** Where a lumberjack is spawned: next to the Chopping Block. */
	public static BlockPos spawnSpot() { return BuildingTestSupport.inside(CAMP, 1, 0, 1); }

	// ---- trees ---------------------------------------------------------------------------------

	/** Grass under the whole area, so a tree can grow anywhere (relative coordinates, y = -1). */
	public static void grass(GameTestHelper h, int x0, int z0, int x1, int z1) {
		for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
			BlockPos below = new BlockPos(x, -1, z);
			if (!h.getBlockState(below).is(Blocks.GRASS_BLOCK)) h.setBlock(below, Blocks.GRASS_BLOCK);
		}
	}

	/**
	 * Grows the configured feature {@code key} at the relative position (a tree whose trunk starts there) with a fixed seed, on a grass
	 * block, and returns the absolute base position. Fails the test when the feature did not place.
	 */
	public static BlockPos grow(GameTestHelper h, ResourceKey<ConfiguredFeature<?, ?>> key, BlockPos baseRel, long seed) {
		ServerLevel level = h.getLevel();
		BlockPos abs = h.absolutePos(baseRel);
		if (!level.getBlockState(abs.below()).is(Blocks.GRASS_BLOCK)) level.setBlock(abs.below(), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
		level.setBlock(abs, Blocks.AIR.defaultBlockState(), 3);
		ConfiguredFeature<?, ?> feature = level.registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE).getOrThrow(key).value();
		boolean ok = feature.place(level, level.getChunkSource().getGenerator(), RandomSource.create(seed), abs);
		h.assertTrue(ok, "the tree " + key.identifier() + " (seed " + seed + ") was placed at " + baseRel);
		return abs;
	}

	public static BlockPos growOak(GameTestHelper h, BlockPos baseRel, long seed) { return grow(h, TreeFeatures.OAK, baseRel, seed); }

	/** Every natural log 26-connected to {@code anyLog} (absolute), at most 400. */
	public static Set<BlockPos> logsOf(GameTestHelper h, BlockPos anyLog) {
		ServerLevel level = h.getLevel();
		Set<BlockPos> seen = new HashSet<>();
		ArrayDeque<BlockPos> q = new ArrayDeque<>();
		seen.add(anyLog);
		q.add(anyLog);
		while (!q.isEmpty() && seen.size() < 400) {
			BlockPos p = q.poll();
			for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
				BlockPos n = p.offset(dx, dy, dz);
				if (!seen.contains(n) && level.getBlockState(n).is(BlockTags.OVERWORLD_NATURAL_LOGS)) {
					seen.add(n);
					q.add(n);
				}
			}
		}
		return seen;
	}

	public static int standing(GameTestHelper h, Set<BlockPos> logs) {
		int n = 0;
		for (BlockPos p : logs) if (h.getLevel().getBlockState(p).is(BlockTags.OVERWORLD_NATURAL_LOGS)) n++;
		return n;
	}

	/**
	 * Removes natural logs and leaves, and item entities, within {@code radius} blocks of the test (leftovers of earlier lumber tests:
	 * grown saplings, falling leaf drops). Safe because every lumber test has its own environment, so it runs alone; call it before
	 * anything is built.
	 */
	public static void sweepStrayTrees(GameTestHelper h, int radius) {
		ServerLevel level = h.getLevel();
		BlockPos c = h.absolutePos(BlockPos.ZERO);
		for (int x = -radius; x <= radius + 32; x++) for (int z = -radius; z <= radius + 32; z++) {
			int bx = c.getX() + x, bz = c.getZ() + z;
			if (level.getChunkSource().getChunkNow(bx >> 4, bz >> 4) == null) continue;
			for (int y = c.getY(); y <= c.getY() + 40; y++) {
				BlockPos p = new BlockPos(bx, y, bz);
				var s = level.getBlockState(p);
				if (s.is(BlockTags.OVERWORLD_NATURAL_LOGS) || s.is(BlockTags.LEAVES) || s.is(BlockTags.SAPLINGS)) {
					level.setBlock(p, Blocks.AIR.defaultBlockState(), 3 | 16);
				}
			}
		}
		for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(c).inflate(radius + 32.0))) e.discard();
	}

	// ---- bounds -----------------------------------------------------------------------------------

	/** Ticks one log takes with a tool of this tier (level 1, default needs). */
	public static double ticksPerLog(ToolTier tier) { return Balance.BASE_TICKS_PER_LOG / ToolRules.multiplier(tier); }

	/**
	 * Measured bound: villagers walk about 10 ticks per block (unseeded AI), felling is deterministic.
	 * {@code logs * ticksPerLog + 10 * dist + 200}.
	 */
	public static int fellBound(int logs, double dist, ToolTier tier) {
		return (int) Math.ceil(logs * ticksPerLog(tier) + 10 * dist + 200);
	}

	public static ItemStack axe(ToolTier t) {
		Item item = switch (t) {
			case WOOD -> Items.WOODEN_AXE;
			case STONE -> Items.STONE_AXE;
			case COPPER -> Items.COPPER_AXE;
			case GOLD -> Items.GOLDEN_AXE;
			case IRON -> Items.IRON_AXE;
			case DIAMOND -> Items.DIAMOND_AXE;
			case NETHERITE -> Items.NETHERITE_AXE;
		};
		return new ItemStack(item);
	}

	/** Records the game tick of every removal among a set of logs (register {@code h.onEachTick(() -> w.sample(h))} from the test body). */
	public static final class Watcher {
		private Set<BlockPos> logs = Set.of();
		private boolean active;
		private int standing;
		public final List<Long> removals = new ArrayList<>();

		public void start(GameTestHelper h, Set<BlockPos> logs) {
			this.logs = logs;
			this.standing = LumberTestSupport.standing(h, logs);
			this.removals.clear();
			this.active = true;
		}

		public void sample(GameTestHelper h) {
			if (!active) return;
			int now = LumberTestSupport.standing(h, logs);
			while (standing > now) {
				removals.add(h.getTick());
				standing--;
			}
		}

		/** Ticks between consecutive removals. */
		public List<Long> gaps() {
			List<Long> g = new ArrayList<>();
			for (int i = 1; i < removals.size(); i++) g.add(removals.get(i) - removals.get(i - 1));
			return g;
		}
	}

	/** Saves {@code v} to NBT, discards it and loads a copy ("the chunk unloaded and loaded again"). */
	public static Villager reloadEntity(ServerLevel level, Villager v) {
		net.minecraft.world.level.storage.TagValueOutput out = net.minecraft.world.level.storage.TagValueOutput
				.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess());
		v.saveWithoutId(out);
		net.minecraft.nbt.CompoundTag tag = out.buildResult();
		v.discard();
		net.minecraft.world.entity.Entity e = EntityType.create(EntityType.VILLAGER, net.minecraft.world.level.storage.TagValueInput
				.create(net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), tag), level,
				net.minecraft.world.entity.EntitySpawnReason.LOAD).orElseThrow();
		level.addFreshEntity(e);
		WorkerTestSupport.track((Villager) e);
		return (Villager) e;
	}

	public static List<Villager> villagers(GameTestHelper h) { return new ArrayList<>(h.getEntities(EntityType.VILLAGER)); }
}
