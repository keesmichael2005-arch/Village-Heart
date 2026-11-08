package dev.villageheart.gametest;

import dev.villageheart.core.Balance;
import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.farm.CropKind;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.worker.HireResult;
import dev.villageheart.worker.Workers;
import dev.villageheart.worker.farm.FarmRuntime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * Helpers for the farmer GameTests (M10). Rules (docs/TESTING.md, "Farmer GameTests"): one environment per test ({@code farm_<name>}, a
 * {@code minecraft:game_rules} environment with {@code random_tick_speed 0} so crops never grow and farmland never dries: every age comes
 * from the test, the farmer's replant or its bone meal), the yard structure with {@code skyAccess}, {@code BuildingTestSupport.begin} first
 * and {@code VillageTestSupport.done} last, fields built after the buildings validate, measured bounds.
 */
public final class FarmTestSupport {
	private FarmTestSupport() {}

	public static String env(String name) { return "villageheart-gametest:farm_" + name; }

	/** Farmhouse at {@link WorkerTestSupport#ORIGIN_A} (door in the north wall), Storage at {@link WorkerTestSupport#ORIGIN_B}. */
	public static final BlockPos FARM = WorkerTestSupport.ORIGIN_A;
	public static final BlockPos STORE = WorkerTestSupport.ORIGIN_B;

	public static BlockPos farmMarker() { return BuildingTestSupport.markerPos(FARM, 5); }

	public static BlockPos storeMarker() { return BuildingTestSupport.markerPos(STORE, 5); }

	/** The first composter (relative): the farmer's job block. */
	public static BlockPos composter() { return BuildingTestSupport.inside(FARM, 0, 0, 0); }

	public static BlockPos composter2() { return BuildingTestSupport.inside(FARM, 4, 0, 0); }

	/** The Farmhouse chest (relative). */
	public static BlockPos chest() { return BuildingTestSupport.inside(FARM, 4, 0, 3); }

	public static BlockPos storeChest1() { return BuildingTestSupport.inside(STORE, 0, 0, 3); }

	public static BlockPos storeChest2() { return BuildingTestSupport.inside(STORE, 4, 0, 3); }

	/** Where a farmer is spawned: next to the composter. */
	public static BlockPos spawnSpot() { return BuildingTestSupport.inside(FARM, 1, 0, 1); }

	public static void buildStorage(GameTestHelper h) {
		BuildingTestSupport.room(h, STORE, 5, 4);
		h.setBlock(storeChest1(), Blocks.CHEST);
		h.setBlock(storeChest2(), Blocks.CHEST);
	}

	public static void placeMarkers(GameTestHelper h, boolean storage) {
		BuildingTestSupport.placeMarker(h, BuildingType.FARMHOUSE, farmMarker(), Direction.NORTH);
		if (storage) BuildingTestSupport.placeMarker(h, BuildingType.STORAGE, storeMarker(), Direction.NORTH);
	}

	public static void assertValid(GameTestHelper h, boolean storage) {
		BuildingTestSupport.assertStatus(h, farmMarker(), dev.villageheart.block.MarkerStatus.VALID);
		if (storage) BuildingTestSupport.assertStatus(h, storeMarker(), dev.villageheart.block.MarkerStatus.VALID);
	}

	/** The environment sets {@code random_tick_speed 0}; a mis-parsed environment fails loudly here instead of flaking later. */
	public static void assertRandomTicksOff(GameTestHelper h) {
		h.assertValueEqual(0, (int) h.getLevel().getGameRules().get(GameRules.RANDOM_TICK_SPEED), "random_tick_speed (the farm_* environment)");
	}

	/** Claim, sweep, grass, a valid Farmhouse with {@code composters} composters (and Storage): ends when the buildings are valid. */
	public static GameTestSequence scene(GameTestHelper h, int composters, boolean storage) {
		return BuildingTestSupport.begin(h).thenExecute(() -> {
			assertRandomTicksOff(h);
			sweepStrayCrops(h, 40);
			LumberTestSupport.grass(h, 0, 0, 31, 31);
			WorkerTestSupport.farmhouse(h, FARM, composters);
			if (storage) buildStorage(h);
			placeMarkers(h, storage);
		}).thenWaitUntil(() -> assertValid(h, storage));
	}

	public static Villager hire(GameTestHelper h, BlockPos spawnRel) {
		Villager v = WorkerTestSupport.spawnVillager(h, spawnRel);
		HireResult r = Workers.hire(h.getLevel(), v, JobType.FARMER, Optional.empty());
		h.assertTrue(r instanceof HireResult.Hired, "the farmer was hired: " + r);
		return v;
	}

	public static dev.villageheart.core.worker.WorkerStatus status(GameTestHelper h, Villager v) { return LumberTestSupport.status(h, v); }

	// ---- fields ------------------------------------------------------------------------------------

	public static BlockState farmland() { return Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7); }

	public static BlockState crop(CropKind kind, int age) {
		Block b = BuiltInRegistries.BLOCK.getValue(Identifier.parse(kind.blockId()));
		return ((CropBlock) b).getStateForAge(age);
	}

	/** Moist farmland at relative ground level (y = -1) and the crop above it (no neighbour updates, so nothing re-checks the cell). */
	public static void plantAt(GameTestHelper h, int x, int z, CropKind kind, int age) { plantAt(h, x, -1, z, kind, age); }

	/** {@code gy} = the farmland's relative y; the crop sits at {@code gy + 1}. */
	public static void plantAt(GameTestHelper h, int x, int gy, int z, CropKind kind, int age) {
		ServerLevel level = h.getLevel();
		level.setBlock(h.absolutePos(new BlockPos(x, gy, z)), farmland(), 2);
		level.setBlock(h.absolutePos(new BlockPos(x, gy + 1, z)), crop(kind, age), 2);
	}

	/** Moist farmland with nothing above it. */
	public static void emptyFarmland(GameTestHelper h, int x, int z) {
		h.getLevel().setBlock(h.absolutePos(new BlockPos(x, -1, z)), farmland(), 2);
		h.getLevel().setBlock(h.absolutePos(new BlockPos(x, 0, z)), Blocks.AIR.defaultBlockState(), 2);
	}

	/** A rectangle of one crop (inclusive); {@code waterCentre}: the middle cell is water instead (an 80-cell 9x9 field). */
	public static List<BlockPos> field(GameTestHelper h, int x0, int z0, int x1, int z1, CropKind kind, int age, boolean waterCentre) {
		List<BlockPos> cells = new ArrayList<>();
		int cx = (x0 + x1) / 2, cz = (z0 + z1) / 2;
		for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
			if (waterCentre && x == cx && z == cz) {
				h.getLevel().setBlock(h.absolutePos(new BlockPos(x, -1, z)), Blocks.WATER.defaultBlockState(), 2);
				continue;
			}
			plantAt(h, x, z, kind, age);
			cells.add(new BlockPos(x, 0, z));
		}
		return cells;
	}

	public static CropKind cropAt(GameTestHelper h, BlockPos rel) {
		BlockState s = h.getBlockState(rel);
		return CropKind.byBlockId(BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString()).orElse(null);
	}

	/** The age of the crop at {@code rel}, or -1 when there is none. */
	public static int ageAt(GameTestHelper h, BlockPos rel) {
		BlockState s = h.getBlockState(rel);
		return s.getBlock() instanceof CropBlock c ? c.getAge(s) : -1;
	}

	/**
	 * Removes farmland, crops, stems and crop items around the test (leftovers of earlier farm tests: their areas reach into the
	 * neighbouring test boxes) and empties the yard. Safe because every farm test has its own environment, so it runs alone; call it before
	 * anything is built.
	 */
	public static void sweepStrayCrops(GameTestHelper h, int radius) {
		LumberTestSupport.clearYard(h);
		ServerLevel level = h.getLevel();
		BlockPos c = h.absolutePos(BlockPos.ZERO);
		for (int x = -radius; x <= radius + 32; x++) for (int z = -radius; z <= radius + 32; z++) {
			int bx = c.getX() + x, bz = c.getZ() + z;
			if (level.getChunkSource().getChunkNow(bx >> 4, bz >> 4) == null) continue;
			for (int y = c.getY() - 3; y <= c.getY() + 12; y++) {
				BlockPos p = new BlockPos(bx, y, bz);
				BlockState s = level.getBlockState(p);
				if (s.getBlock() instanceof CropBlock || s.is(Blocks.FARMLAND) || s.is(Blocks.MELON_STEM) || s.is(Blocks.PUMPKIN_STEM)
						|| s.is(Blocks.ATTACHED_MELON_STEM) || s.is(Blocks.ATTACHED_PUMPKIN_STEM) || s.is(Blocks.SWEET_BERRY_BUSH) || s.is(Blocks.NETHER_WART)) {
					level.setBlock(p, s.is(Blocks.FARMLAND) ? Blocks.DIRT.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2 | 16);
				}
			}
		}
		for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(c).inflate(radius + 32.0))) e.discard();
	}

	// ---- counting ----------------------------------------------------------------------------------

	public static long stored(GameTestHelper h, String id) {
		return dev.villageheart.stock.Stockpiles.totalsById(h.getLevel().getServer()).getOrDefault(id, 0L);
	}

	public static int carried(Villager v, net.minecraft.world.item.Item item) { return v.getInventory().countItem(item); }

	/** Items of this kind lying around the farm (loose item entities within the box of the yard). */
	public static int loose(GameTestHelper h, net.minecraft.world.item.Item item) {
		int n = 0;
		for (ItemEntity e : h.getLevel().getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(h.absolutePos(BlockPos.ZERO)).inflate(60.0))) {
			if (e.getItem().is(item)) n += e.getItem().getCount();
		}
		return n;
	}

	/** Everything of this item the village holds: carried, in Storage, or lying on the ground. */
	public static long total(GameTestHelper h, Villager v, net.minecraft.world.item.Item item) {
		return carried(v, item) + stored(h, BuiltInRegistries.ITEM.getKey(item).toString()) + loose(h, item);
	}

	public static String inventory(Villager v) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < v.getInventory().getContainerSize(); i++) if (!v.getInventory().getItem(i).isEmpty()) sb.append(v.getInventory().getItem(i)).append(' ');
		return sb.toString();
	}

	public static ItemStack hoe(String tier) {
		return new ItemStack(switch (tier) {
			case "wood" -> Items.WOODEN_HOE;
			case "stone" -> Items.STONE_HOE;
			case "gold" -> Items.GOLDEN_HOE;
			case "iron" -> Items.IRON_HOE;
			default -> Items.DIAMOND_HOE;
		});
	}

	// ---- bounds ------------------------------------------------------------------------------------

	/**
	 * Measured bound: villagers walk about 10 ticks per block (unseeded AI), a unit costs {@code unitTicks}, every re-target costs a tick
	 * or two ({@code 20} each is generous), plus slack. {@code units * (unitTicks + 20) + 10 * dist + 200}.
	 */
	public static int farmBound(int units, double unitTicks, double dist) {
		return (int) Math.ceil(units * (unitTicks + 20) + 10 * dist + 200);
	}

	public static int harvestBound(int units, double dist) { return farmBound(units, Balance.FARM_TICKS_HARVEST, dist); }

	// ---- the field watcher -------------------------------------------------------------------------

	/**
	 * Samples, at the end of every tick, only the cells of its own field (a few dozen) and joins them with the {@link FarmRuntime} unit log:
	 * a watched cell must never be air ("harvest and replant are one tick"), every HARVEST in the log must show as age 0 at the next sample,
	 * and the tick a cell is seen going from ripe to age 0 is recorded. Problems are collected (never thrown inside the tick callback).
	 */
	public static final class Watcher {
		private final List<BlockPos> cells = new ArrayList<>();
		private final Map<BlockPos, Integer> last = new HashMap<>();
		private boolean active;
		private boolean mustBeFilled;
		private int logSeen;
		public final List<String> problems = new ArrayList<>();
		/** Cell -> game ticks at which it was seen to turn from ripe to a younger age. */
		public final Map<BlockPos, List<Long>> replantSeen = new HashMap<>();

		/** @param filled true: no watched cell may be air at any sample. */
		public void start(GameTestHelper h, List<BlockPos> relCells, boolean filled) {
			cells.clear();
			last.clear();
			for (BlockPos r : relCells) {
				cells.add(h.absolutePos(r));
				last.put(h.absolutePos(r), ageAbs(h.getLevel(), h.absolutePos(r)));
			}
			this.mustBeFilled = filled;
			this.logSeen = FarmRuntime.get(h.getLevel().getServer()).unitLog().size();
			this.active = true;
		}

		public void stop() { active = false; }

		private static int ageAbs(ServerLevel level, BlockPos p) {
			BlockState s = level.getBlockState(p);
			if (s.getBlock() instanceof CropBlock c) return c.getAge(s);
			return s.isAir() ? -2 : -1;
		}

		public void sample(GameTestHelper h) {
			if (!active) return;
			ServerLevel level = h.getLevel();
			long now = level.getGameTime();
			for (BlockPos p : cells) {
				int age = ageAbs(level, p);
				int before = last.get(p);
				if (mustBeFilled && age == -2) problems.add("@" + now + " cell " + p + " is air");
				if (before >= 3 && age >= 0 && age < before && before >= 3) replantSeen.computeIfAbsent(p, k -> new ArrayList<>()).add(now);
				last.put(p, age);
			}
			List<FarmRuntime.Unit> log = FarmRuntime.get(level.getServer()).unitLog();
			for (int i = Math.min(logSeen, log.size()); i < log.size(); i++) {
				FarmRuntime.Unit u = log.get(i);
				if (u.unit().equals("HARVEST") && cells.contains(u.cell()) && ageAbs(level, u.cell()) != 0) {
					problems.add("@" + now + " harvest of " + u.cell() + " logged at " + u.tick() + " but the cell reads age " + ageAbs(level, u.cell()));
				}
			}
			logSeen = log.size();
		}
	}
}
