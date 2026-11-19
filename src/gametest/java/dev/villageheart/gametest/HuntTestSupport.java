package dev.villageheart.gametest;

import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.building.AreaCounter;
import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.hunt.HuntTable;
import dev.villageheart.core.hunt.MeatWeights;
import dev.villageheart.core.mine.HaulRng;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.room.LevelBlockView;
import dev.villageheart.room.RoomScans;
import dev.villageheart.worker.HireResult;
import dev.villageheart.worker.Workers;
import dev.villageheart.worker.hunt.HuntSites;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * Helpers for the hunter GameTests (M17). Rules (docs/TESTING.md, "Hunter GameTests"): one environment per test ({@code hunt_<name>}), the
 * {@code yard} structure, {@code WorkerTestSupport.begin} first, {@code VillageTestSupport.done} + {@code WorkerTestSupport.end} last.
 *
 * <p><b>The scene.</b> The whole footprint is a stone floor at y -1 (the flat world's grass is replaced, so nothing outside the field is
 * "natural"), a Hunting Lodge room (fletching table, chest) at {@code ORIGIN_A}, a Storage room at {@code ORIGIN_B}, and an 11 x 11 grass
 * field with the Hunting Ground marker (on a fence post) in its middle: exactly 121 natural cells within 12 blocks. The GameTest world is
 * a flat plains world with seed 0, so the biome weights around the ground are 9 x (beef 8, porkchop 10, mutton 12, chicken 10).
 * <b>Every weapon in these tests is unenchanted</b> (Unbreaking would make the wear random).
 */
public final class HuntTestSupport {
	private HuntTestSupport() {}

	public static String env(String name) { return "villageheart-gametest:hunt_" + name; }

	public static final BlockPos LODGE = WorkerTestSupport.ORIGIN_A;
	public static final BlockPos STORE = WorkerTestSupport.ORIGIN_B;
	/** Marker of the standard ground and its fence post (the marker hangs on the post's south face). */
	public static final BlockPos GROUND = new BlockPos(15, 0, 17);
	/** Marker of the far ground (T-L2-9), more than 40 blocks from the far spot of T-L2-9 (6, 0, -10); its sphere reaches outside the footprint (real grass there), so the scene counts at least 121 natural cells. */
	public static final BlockPos GROUND_FAR = new BlockPos(26, 0, 26);
	public static final int FIELD = 11;
	/** What the plains biome gives around the ground: 9 sample columns of (beef 8, porkchop 10, mutton 12, chicken 10). */
	public static final MeatWeights PLAINS = new MeatWeights(72, 90, 108, 90);

	public static BlockPos lodgeMarker() { return BuildingTestSupport.markerPos(LODGE, 5); }

	public static BlockPos storeMarker() { return BuildingTestSupport.markerPos(STORE, 5); }

	public static BlockPos table() { return BuildingTestSupport.inside(LODGE, 0, 0, 0); }

	public static BlockPos table2() { return BuildingTestSupport.inside(LODGE, 4, 0, 0); }

	public static BlockPos chest() { return BuildingTestSupport.inside(LODGE, 4, 0, 3); }

	/** A second chest for a second hunter. Not beside {@link #chest()}. */
	public static BlockPos chest2() { return BuildingTestSupport.inside(LODGE, 0, 0, 3); }

	public static BlockPos spawnSpot() { return BuildingTestSupport.inside(LODGE, 1, 0, 1); }

	public static BlockPos spawnSpot2() { return BuildingTestSupport.inside(LODGE, 2, 0, 1); }

	private static BlockPos post(BlockPos ground) { return ground.north(); }

	// ---- building -----------------------------------------------------------------------------------

	/** Stone under the whole footprint (y -1), then {@code field} of grass around {@code ground}. */
	public static void floorAndField(GameTestHelper h, BlockPos ground) {
		for (int x = 0; x <= 31; x++) for (int z = 0; z <= 31; z++) for (int y = -5; y <= -1; y++) h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
		field(h, ground, true);
	}

	public static void field(GameTestHelper h, BlockPos ground, boolean grass) {
		int r = FIELD / 2;
		for (int x = ground.getX() - r; x <= ground.getX() + r; x++) {
			for (int z = ground.getZ() - r; z <= ground.getZ() + r; z++) h.setBlock(new BlockPos(x, -1, z), grass ? Blocks.GRASS_BLOCK : Blocks.STONE);
		}
	}

	public static void buildLodge(GameTestHelper h, boolean secondTable) {
		BuildingTestSupport.room(h, LODGE, 5, 4);
		h.setBlock(table(), Blocks.FLETCHING_TABLE);
		if (secondTable) h.setBlock(table2(), Blocks.FLETCHING_TABLE);
		h.setBlock(chest(), Blocks.CHEST);
		if (secondTable) h.setBlock(chest2(), Blocks.CHEST);
		h.setBlock(BuildingTestSupport.inside(LODGE, 2, 0, 3), Blocks.TORCH);
	}

	public static void buildStorage(GameTestHelper h) {
		BuildingTestSupport.room(h, STORE, 5, 4);
		h.setBlock(BuildingTestSupport.inside(STORE, 0, 0, 3), Blocks.CHEST);
		h.setBlock(BuildingTestSupport.inside(STORE, 4, 0, 3), Blocks.CHEST);
	}

	/** Torches every 4 blocks outside the rooms and away from the ground's post (y 0), so a late hunt is not spawn-proofed by darkness. */
	public static void lightYard(GameTestHelper h) {
		for (int x = 0; x <= 31; x += 4) for (int z = 0; z <= 31; z += 4) {
			boolean inRooms = x >= 7 && x <= 27 && z >= 2 && z <= 10;
			boolean nearMarker = Math.abs(x - GROUND.getX()) <= 2 && Math.abs(z - GROUND.getZ()) <= 2 || Math.abs(x - GROUND_FAR.getX()) <= 2 && Math.abs(z - GROUND_FAR.getZ()) <= 2;
			if (!inRooms && !nearMarker) h.setBlock(new BlockPos(x, 0, z), Blocks.TORCH);
		}
	}

	public static void placeGround(GameTestHelper h, BlockPos ground) {
		h.setBlock(post(ground), Blocks.OAK_FENCE);
		BuildingTestSupport.placeMarker(h, BuildingType.HUNTING_GROUND, ground, Direction.SOUTH);
	}

	/**
	 * Claim, sweep, stone floor, a Hunting Lodge, a Storage and a Hunting Ground with its field; the sequence ends when all three are
	 * valid and the ground is linked to the Lodge, and the scene's premises hold (ground chosen, plains weights, 121 natural cells).
	 */
	public static GameTestSequence scene(GameTestHelper h, boolean secondTable) { return scene(h, secondTable, GROUND); }

	public static GameTestSequence scene(GameTestHelper h, boolean secondTable, BlockPos ground) {
		return BuildingTestSupport.begin(h).thenExecute(() -> {
			LumberTestSupport.sweepStrayTrees(h, 48);
			floorAndField(h, ground);
			buildLodge(h, secondTable);
			buildStorage(h);
			BuildingTestSupport.setTier(h, dev.villageheart.core.Tier.VILLAGE);
			BuildingTestSupport.placeMarker(h, BuildingType.HUNTING_LODGE, lodgeMarker(), Direction.NORTH);
			BuildingTestSupport.placeMarker(h, BuildingType.STORAGE, storeMarker(), Direction.NORTH);
			placeGround(h, ground);
		}).thenWaitUntil(() -> assertValid(h, ground)).thenExecute(() -> premise(h, ground));
	}

	public static void assertValid(GameTestHelper h, BlockPos ground) {
		BuildingTestSupport.assertStatus(h, lodgeMarker(), dev.villageheart.block.MarkerStatus.VALID);
		BuildingTestSupport.assertStatus(h, storeMarker(), dev.villageheart.block.MarkerStatus.VALID);
		BuildingTestSupport.assertStatus(h, ground, dev.villageheart.block.MarkerStatus.VALID);
		h.assertValueEqual(BuildingTestSupport.loc(h, lodgeMarker()), BuildingTestSupport.record(h, ground).orElseThrow().link(), "the ground is linked to the Lodge");
	}

	/** The scene's premises: a wrong world (biome, seed, stray natural blocks) fails here, in every hunter test, with the numbers it read. */
	public static void premise(GameTestHelper h, BlockPos ground) {
		ServerLevel level = h.getLevel();
		var server = level.getServer();
		HeartLocation lodge = BuildingTestSupport.loc(h, lodgeMarker());
		HeartLocation g = BuildingTestSupport.loc(h, ground);
		var site = HuntSites.groundFor(server, lodge);
		h.assertTrue(site.isPresent() && site.get().loc().equals(g), "HuntSites.groundFor is this scene's ground: " + site);
		if (ground.equals(GROUND)) {
			h.assertValueEqual(121, natural(h, ground), "natural cells within 12 blocks of the marker (the field and nothing else)");
			MeatWeights w = HuntSites.meatWeights(level, g, h.absolutePos(ground));
			h.assertValueEqual(PLAINS, w, "the meat weights around the ground (flat PLAINS world, 9 samples)");
		} else {
			// the far ground sits in the corner: its sphere reaches outside the footprint (real grass there, chunks that may not be loaded)
			h.assertTrue(natural(h, ground) >= 121, "natural cells within 12 blocks of the marker: " + natural(h, ground));
		}
	}

	public static int natural(GameTestHelper h, BlockPos ground) {
		return AreaCounter.count(new LevelBlockView(h.getLevel()), RoomScans.pos(h.absolutePos(ground)), 12, "#natural").n();
	}

	// ---- villagers and items ------------------------------------------------------------------------

	public static Villager hire(GameTestHelper h) { return hireAt(h, spawnSpot()); }

	public static Villager hireAt(GameTestHelper h, BlockPos spawnRel) {
		Villager v = WorkerTestSupport.spawnVillager(h, spawnRel);
		HireResult r = Workers.hire(h.getLevel(), v, JobType.HUNTER, Optional.empty());
		h.assertTrue(r instanceof HireResult.Hired, "the hunter was hired: " + r);
		// Tests pile every weapon into the first chest. Each hunter only opens their own, so a spare moves across.
		moveSpareWeapon(h, v);
		return v;
	}

	public static void putInChest(GameTestHelper h, int slot, ItemStack stack) { LumberTestSupport.putInChest(h, chest(), slot, stack); }

	/** A spare sword or bow in the lodge chest moves into this hunter's chest when that chest is a different one and empty of weapons. */
	private static void moveSpareWeapon(GameTestHelper h, Villager v) {
		var data = v.getAttached(dev.villageheart.worker.WorkerAttachments.WORKER);
		if (data == null || data.chest().isEmpty()) return;
		BlockPos own = new BlockPos(data.chest().get().x(), data.chest().get().y(), data.chest().get().z());
		BlockPos pile = h.absolutePos(chest());
		if (own.equals(pile)) return;
		var from = (net.minecraft.world.Container) h.getLevel().getBlockEntity(pile);
		var to = (net.minecraft.world.Container) h.getLevel().getBlockEntity(own);
		if (from == null || to == null) return;
		for (int i = 0; i < to.getContainerSize(); i++) {
			if (dev.villageheart.worker.job.CampTools.isTool(dev.villageheart.core.job.ToolKind.BOW_OR_SWORD, to.getItem(i))) return;
		}
		for (int i = 0; i < from.getContainerSize(); i++) {
			var s = from.getItem(i);
			if (!dev.villageheart.worker.job.CampTools.isTool(dev.villageheart.core.job.ToolKind.BOW_OR_SWORD, s)) continue;
			for (int j = 0; j < to.getContainerSize(); j++) {
				if (!to.getItem(j).isEmpty()) continue;
				to.setItem(j, s.copy());
				from.setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
				from.setChanged();
				to.setChanged();
				return;
			}
		}
	}

	public static ItemStack chestSlot(GameTestHelper h, int slot) { return StockpileTestSupport.container(h, chest()).getItem(slot); }

	public static void clearSlot(GameTestHelper h, int slot) {
		var c = StockpileTestSupport.container(h, chest());
		c.setItem(slot, ItemStack.EMPTY);
		c.setChanged();
	}

	/** Items of this id in the Lodge chest. */
	public static int inChest(GameTestHelper h, Item item) {
		var c = StockpileTestSupport.container(h, chest());
		int n = 0;
		for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).is(item)) n += c.getItem(i).getCount();
		return n;
	}

	public static long count(GameTestHelper h, String id) {
		return dev.villageheart.stock.Stockpiles.count(h.getLevel().getServer(), BuiltInRegistries.ITEM.getValue(Identifier.parse(id)));
	}

	public static long meatInStorage(GameTestHelper h) {
		long n = 0;
		for (String id : new String[] {"minecraft:beef", "minecraft:porkchop", "minecraft:mutton", "minecraft:chicken"}) n += count(h, id);
		return n;
	}

	/** The yields of trips 0 .. n-1 of this hunter, summed; {@code arrow(i)} says whether trip i took an arrow. */
	public static Map<String, Integer> predicted(ServerLevel level, UUID hunter, int n, java.util.function.IntPredicate arrow) {
		Map<String, Integer> sum = new LinkedHashMap<>();
		for (int i = 0; i < n; i++) predictedTrip(level, hunter, i, arrow.test(i)).forEach((k, c) -> sum.merge(k, c, Integer::sum));
		return sum;
	}

	public static Map<String, Integer> predictedTrip(ServerLevel level, UUID hunter, long index, boolean arrow) {
		return HuntTable.roll(PLAINS, arrow, HaulRng.forHaul(level.getSeed(), hunter, index));
	}

	public static int meat(Map<String, Integer> m) {
		int n = 0;
		for (String id : new String[] {"minecraft:beef", "minecraft:porkchop", "minecraft:mutton", "minecraft:chicken"}) n += m.getOrDefault(id, 0);
		return n;
	}

	/** Asserts that Storage holds exactly {@code want} of every hunted item. */
	public static void assertStorage(GameTestHelper h, Map<String, Integer> want, String what) {
		for (String id : HuntTable.allItems()) h.assertValueEqual((long) want.getOrDefault(id, 0), count(h, id), what + ": Storage " + id);
	}

	public static ItemStack sword(String tier) {
		return new ItemStack(switch (tier) {
			case "wood" -> Items.WOODEN_SWORD;
			case "stone" -> Items.STONE_SWORD;
			default -> throw new IllegalArgumentException(tier);
		});
	}
}
