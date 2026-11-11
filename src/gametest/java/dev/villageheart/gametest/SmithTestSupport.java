package dev.villageheart.gametest;

import dev.villageheart.block.JobBlocks;
import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.building.JobBlockType;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.stock.Stockpiles;
import dev.villageheart.worker.HireResult;
import dev.villageheart.worker.WorkerAttachments;
import dev.villageheart.worker.Workers;
import dev.villageheart.worker.gear.SelfCraftSwitch;
import dev.villageheart.world.VillageClaims;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;

/** Helpers for the smith GameTests (M19). Own environment per test, {@code yard}, sky access. */
public final class SmithTestSupport {
	private static final Set<Long> FORCED = new HashSet<>();

	private SmithTestSupport() {}

	public static String env(String name) { return "villageheart-gametest:smith_" + name; }

	/** Drop every iron golem in a box around {@code centre}. Earlier tests share the world. */
	public static void sweepGolems(ServerLevel level, BlockPos centre, double radius) {
		AABB box = new AABB(centre).inflate(radius);
		for (IronGolem g : level.getEntities(EntityType.IRON_GOLEM, box, e -> true)) g.discard();
	}

	public static int golemsNear(ServerLevel level, BlockPos centre, double radius) {
		return level.getEntities(EntityType.IRON_GOLEM, new AABB(centre).inflate(radius), IronGolem::isAlive).size();
	}

	/** Load and force every chunk the box touches. Released by {@link #releaseForced} and by {@code VillageTestSupport.reset}. */
	public static void force(ServerLevel level, BlockPos centre, int radius) {
		int x0 = (centre.getX() - radius) >> 4, x1 = (centre.getX() + radius) >> 4;
		int z0 = (centre.getZ() - radius) >> 4, z1 = (centre.getZ() + radius) >> 4;
		for (int cx = x0; cx <= x1; cx++) for (int cz = z0; cz <= z1; cz++) {
			level.getChunk(cx, cz);
			level.setChunkForced(cx, cz, true);
			FORCED.add(((long) cx << 32) | (cz & 0xffffffffL));
		}
	}

	public static void releaseForced(ServerLevel level) {
		for (long key : FORCED) level.setChunkForced((int) (key >> 32), (int) key, false);
		FORCED.clear();
	}

	/**
	 * Stone on the air at the door's ground level (y = 0), north of the Smithy and Storage. A real village path sits level
	 * with the floor the door stands on; the yard's grass is one block lower, so {@link dev.villageheart.worker.smith.SmithyLayout#waitSpot}
	 * would otherwise find nothing to stand on.
	 */
	public static void pave(GameTestHelper h) {
		for (int x = 0; x <= 31; x++) for (int z = 0; z <= 3; z++) {
			BlockPos p = new BlockPos(x, 0, z);
			if (h.getBlockState(p).isAir()) h.setBlock(p, Blocks.STONE);
		}
	}

	/** A stone pad with air above it, glass only on the rim, so a golem has somewhere to stand and the villagers stay put. */
	public static void pad(ServerLevel level, BlockPos feet, int radius) {
		int y = feet.getY() - 1;
		for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
			BlockPos ground = new BlockPos(feet.getX() + dx, y, feet.getZ() + dz);
			boolean rim = Math.abs(dx) == radius || Math.abs(dz) == radius;
			level.setBlock(ground, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
			for (int up = 1; up <= 5; up++) {
				BlockPos above = ground.above(up);
				boolean wall = rim && up <= 3;
				level.setBlock(above, wall ? Blocks.GLASS.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			}
		}
	}

	public static void log(String s) { System.out.println("[m19] " + s); }

	public static final BlockPos SMITHY = new BlockPos(8, 0, 4);
	public static final BlockPos STORE = new BlockPos(16, 0, 4);
	/** South of the Smithy, clear of its shell (z 4..9) and of Storage. Interior 5×4. */
	public static final BlockPos CAMP = new BlockPos(8, 0, 16);

	public static BlockPos smithyMarker() { return BuildingTestSupport.markerPos(SMITHY, 4); }

	public static BlockPos storeMarker() { return BuildingTestSupport.markerPos(STORE, 4); }

	public static BlockPos furnace() { return BuildingTestSupport.inside(SMITHY, 0, 0, 1); }

	public static BlockPos anvil() { return BuildingTestSupport.inside(SMITHY, 2, 0, 1); }

	public static BlockPos smithyChest() { return BuildingTestSupport.inside(SMITHY, 1, 0, 2); }

	public static BlockPos spawnSpot() { return BuildingTestSupport.inside(SMITHY, 1, 0, 0); }

	public static BlockPos storeChest1() { return BuildingTestSupport.inside(STORE, 0, 0, 1); }

	public static BlockPos storeChest2() { return BuildingTestSupport.inside(STORE, 2, 0, 1); }

	public static BlockPos campMarker() { return BuildingTestSupport.markerPos(CAMP, 5); }

	public static BlockPos campChest() { return BuildingTestSupport.inside(CAMP, 4, 0, 3); }

	public static BlockPos campSpawn() { return BuildingTestSupport.inside(CAMP, 1, 0, 1); }

	/** A valid Lumber Camp. The caller waits until the marker reads VALID. */
	public static void addLumberCamp(GameTestHelper h) {
		BuildingTestSupport.room(h, CAMP, 5, 4);
		h.setBlock(BuildingTestSupport.inside(CAMP, 0, 0, 0), JobBlocks.BLOCKS.get(JobBlockType.CHOPPING_BLOCK));
		h.setBlock(campChest(), Blocks.CHEST);
		h.setBlock(BuildingTestSupport.inside(CAMP, 2, 0, 3), Blocks.TORCH);
		BuildingTestSupport.placeMarker(h, BuildingType.LUMBER_CAMP, campMarker(), Direction.NORTH);
	}

	public static boolean chestHas(GameTestHelper h, BlockPos rel, Item item) {
		var c = StockpileTestSupport.container(h, rel);
		for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).is(item)) return true;
		return false;
	}

	/** One-way blocks from the blast furnace to the Storage door. */
	public static int walkBlocks() {
		BlockPos a = furnace();
		BlockPos door = new BlockPos(STORE.getX() + 2, STORE.getY() + 1, STORE.getZ());
		double dx = a.getX() - door.getX(), dz = a.getZ() - door.getZ();
		return (int) Math.ceil(Math.sqrt(dx * dx + dz * dz));
	}

	public static int smithBound(int units, int ticksPerUnit, int dist) { return units * ticksPerUnit + 10 * dist + 400; }

	/** Claim, a Town, a valid Smithy and Storage. Self-made tools are off; the tests that need them turn the switch back on. */
	public static GameTestSequence scene(GameTestHelper h) {
		return BuildingTestSupport.begin(h).thenExecute(() -> {
			SelfCraftSwitch.off();
			h.getLevel().getGameRules().set(GameRules.ADVANCE_TIME, true, h.getLevel().getServer());
			LumberTestSupport.sweepStrayTrees(h, 40);
			LumberTestSupport.grass(h, 0, 0, 31, 31);
			BuildingTestSupport.room(h, SMITHY, 4, 4);
			h.setBlock(furnace(), Blocks.BLAST_FURNACE);
			h.setBlock(anvil(), Blocks.ANVIL);
			h.setBlock(smithyChest(), Blocks.CHEST);
			BuildingTestSupport.room(h, STORE, 4, 4);
			h.setBlock(storeChest1(), Blocks.CHEST);
			h.setBlock(storeChest2(), Blocks.CHEST);
			BuildingTestSupport.setTier(h, dev.villageheart.core.Tier.TOWN);
			BuildingTestSupport.placeMarker(h, BuildingType.SMITHY, smithyMarker(), Direction.NORTH);
			BuildingTestSupport.placeMarker(h, BuildingType.STORAGE, storeMarker(), Direction.NORTH);
			pave(h);
		}).thenWaitUntil(() -> {
			BuildingTestSupport.assertStatus(h, smithyMarker(), dev.villageheart.block.MarkerStatus.VALID);
			BuildingTestSupport.assertStatus(h, storeMarker(), dev.villageheart.block.MarkerStatus.VALID);
		});
	}

	public static void putStore(GameTestHelper h, Item item, int n) {
		CookTestSupport.put(h, item, n, storeChest1(), storeChest2());
	}

	public static long count(GameTestHelper h, Item item) { return Stockpiles.count(h.getLevel().getServer(), item); }

	public static int bank(GameTestHelper h) {
		return VillageClaims.current(h.getLevel().getServer()).map(s -> s.smokerHeat(BuildingTestSupport.loc(h, furnace()))).orElse(0);
	}

	public static boolean lit(GameTestHelper h) { return h.getBlockState(furnace()).getValue(AbstractFurnaceBlock.LIT); }

	public static void setDayTime(GameTestHelper h, long t) { h.getLevel().setDayTime(t); }

	public static Villager hireLumberjack(GameTestHelper h) {
		Villager v = WorkerTestSupport.spawnVillager(h, campSpawn());
		HireResult r = Workers.hire(h.getLevel(), v, JobType.LUMBERJACK, Optional.empty());
		h.assertTrue(r instanceof HireResult.Hired, "the lumberjack was hired: " + r);
		return v;
	}

	public static Villager hire(GameTestHelper h) {
		Villager v = WorkerTestSupport.spawnVillager(h, spawnSpot());
		HireResult r = Workers.hire(h.getLevel(), v, JobType.BLACKSMITH, Optional.of(BuildingTestSupport.loc(h, smithyMarker())));
		h.assertTrue(r instanceof HireResult.Hired, "the smith was hired: " + r);
		var data = v.getAttached(WorkerAttachments.WORKER);
		h.assertTrue(data != null && data.jobBlock().equals(BuildingTestSupport.loc(h, furnace())), "the job block is the blast furnace");
		return v;
	}

	/** A Kitchen that does not overlap the Smithy or Storage. The cook is what keeps the smith off the fuel reserve. */
	public static final BlockPos KITCHEN = new BlockPos(22, 0, 16);

	public static BlockPos kitchenMarker() { return BuildingTestSupport.markerPos(KITCHEN, 4); }

	public static void addKitchen(GameTestHelper h) {
		BuildingTestSupport.room(h, KITCHEN, 4, 4);
		h.setBlock(BuildingTestSupport.inside(KITCHEN, 0, 0, 0), Blocks.SMOKER);
		h.setBlock(BuildingTestSupport.inside(KITCHEN, 2, 0, 0), Blocks.CRAFTING_TABLE);
		h.setBlock(BuildingTestSupport.inside(KITCHEN, 0, 0, 2), Blocks.CHEST);
		h.setBlock(BuildingTestSupport.inside(KITCHEN, 2, 0, 2), Blocks.TORCH);
		BuildingTestSupport.placeMarker(h, BuildingType.KITCHEN, kitchenMarker(), Direction.NORTH);
	}

	public static Villager hireCook(GameTestHelper h) {
		Villager v = WorkerTestSupport.spawnVillager(h, BuildingTestSupport.inside(KITCHEN, 1, 0, 1));
		HireResult r = Workers.hire(h.getLevel(), v, JobType.COOK, Optional.of(BuildingTestSupport.loc(h, kitchenMarker())));
		h.assertTrue(r instanceof HireResult.Hired, "the cook was hired: " + r);
		return v;
	}

	/** A Mine plus a linked shaft, clear of the Smithy (x 8..13) and Storage (x 16..21). */
	public static final BlockPos MINE = new BlockPos(0, 0, 18);
	public static final int[] SHAFT = {28, 28};

	public static BlockPos mineMarker() { return BuildingTestSupport.markerPos(MINE, 4); }

	public static BlockPos mineChest() { return BuildingTestSupport.inside(MINE, 2, 0, 2); }

	public static BlockPos mineSpawn() { return BuildingTestSupport.inside(MINE, 1, 0, 1); }

	public static void addMine(GameTestHelper h) {
		BuildingTestSupport.room(h, MINE, 4, 4);
		h.setBlock(BuildingTestSupport.inside(MINE, 0, 0, 0), Blocks.STONECUTTER);
		h.setBlock(mineChest(), Blocks.CHEST);
		h.setBlock(BuildingTestSupport.inside(MINE, 2, 0, 0), Blocks.TORCH);
		MineTestSupport.buildShaft(h, 0, SHAFT);
		BuildingTestSupport.placeMarker(h, BuildingType.MINE, mineMarker(), Direction.NORTH);
		BuildingTestSupport.placeMarker(h, BuildingType.MINESHAFT, MineTestSupport.shaftMarker(0, SHAFT), Direction.SOUTH);
	}

	public static Villager hireMiner(GameTestHelper h) {
		Villager v = WorkerTestSupport.spawnVillager(h, mineSpawn());
		HireResult r = Workers.hire(h.getLevel(), v, JobType.MINER, Optional.of(BuildingTestSupport.loc(h, mineMarker())));
		h.assertTrue(r instanceof HireResult.Hired, "the miner was hired: " + r);
		return v;
	}

	/** A one-guard Barracks, clear of the Smithy and Storage. */
	public static final BlockPos BARRACKS = new BlockPos(22, 0, 16);

	public static BlockPos barracksMarker() { return BuildingTestSupport.markerPos(BARRACKS, 4); }

	public static BlockPos barracksChest() { return BuildingTestSupport.inside(BARRACKS, 0, 0, 2); }

	public static void addBarracks(GameTestHelper h) {
		BuildingTestSupport.room(h, BARRACKS, 4, 4);
		h.setBlock(BuildingTestSupport.inside(BARRACKS, 0, 0, 0), Blocks.GRINDSTONE);
		WorkerTestSupport.bed(h, BuildingTestSupport.inside(BARRACKS, 2, 0, 1));
		h.setBlock(barracksChest(), Blocks.CHEST);
		h.setBlock(BuildingTestSupport.inside(BARRACKS, 2, 0, 0), Blocks.TORCH);
		BuildingTestSupport.placeMarker(h, BuildingType.BARRACKS, barracksMarker(), Direction.NORTH);
	}

	public static Villager hireGuard(GameTestHelper h) {
		Villager v = WorkerTestSupport.spawnVillager(h, BuildingTestSupport.inside(BARRACKS, 1, 0, 1));
		HireResult r = Workers.hire(h.getLevel(), v, JobType.GUARD, Optional.of(BuildingTestSupport.loc(h, barracksMarker())));
		h.assertTrue(r instanceof HireResult.Hired, "the guard was hired: " + r);
		return v;
	}

	/** Unemployed villagers behind an iron door, so they count toward the golem cap and cannot wander out. */
	public static void pen(GameTestHelper h, int villagers) {
		BlockPos o = new BlockPos(0, 0, 11);
		for (int x = 0; x <= 4; x++) for (int z = 0; z <= 4; z++) {
			boolean wall = x == 0 || x == 4 || z == 0 || z == 4;
			boolean gate = x == 2 && z == 0;
			for (int y = 1; y <= 2; y++) if (wall && !gate) h.setBlock(o.offset(x, y, z), Blocks.GLASS);
			h.setBlock(o.offset(x, 3, z), Blocks.OAK_PLANKS);
		}
		BlockPos door = o.offset(2, 1, 0);
		var iron = Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
		h.setBlock(door, iron.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		h.setBlock(door.above(), iron.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		for (int i = 0; i < villagers; i++) WorkerTestSupport.spawnVillager(h, o.offset(1 + (i % 2), 1, 1 + i / 2));
	}

	/** Iron ingots in building containers, villager inventories, worn slots and item entities around the yard. */
	public static int ironTotal(GameTestHelper h) { return worldCount(h, net.minecraft.world.item.Items.IRON_INGOT); }

	public static int worldCount(GameTestHelper h, Item item) {
		ServerLevel level = h.getLevel();
		BlockPos origin = h.absolutePos(BlockPos.ZERO);
		int n = 0;
		for (int x = -2; x <= 40; x++) for (int y = 0; y <= 20; y++) for (int z = -2; z <= 40; z++) {
			if (level.getBlockEntity(origin.offset(x, y, z)) instanceof Container c) {
				for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).is(item)) n += c.getItem(i).getCount();
			}
		}
		AABB box = new AABB(origin).inflate(48);
		for (Villager v : level.getEntities(EntityType.VILLAGER, box, e -> true)) {
			n += v.getInventory().countItem(item);
			for (EquipmentSlot slot : EquipmentSlot.values()) {
				ItemStack worn = v.getItemBySlot(slot);
				if (worn.is(item)) n += worn.getCount();
			}
		}
		for (ItemEntity it : level.getEntities(EntityType.ITEM, box, e -> e.getItem().is(item))) n += it.getItem().getCount();
		return n;
	}

	public static Villager villagerAt(ServerLevel level, BlockPos feet) {
		Villager v = EntityType.VILLAGER.create(level, EntitySpawnReason.COMMAND);
		if (v == null) throw new IllegalStateException("no villager");
		v.snapTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, 0, 0);
		level.addFreshEntity(v);
		WorkerTestSupport.track(v);
		return v;
	}
}
