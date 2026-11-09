package dev.villageheart.gametest;

import dev.villageheart.block.JobBlocks;
import dev.villageheart.core.building.JobBlockType;
import dev.villageheart.block.MarkerStatus;
import dev.villageheart.core.Tier;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.economy.PayResult;
import dev.villageheart.core.tier.TierProgress;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.economy.VillageDawn;
import dev.villageheart.world.VillageClaims;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.Blocks;

/**
 * Helpers for the tier tests (M14). The yard holds a Farmhouse (3 composters, 3 farmer slots), a Storage with bread, a Kitchen and a
 * Builder's Hut (a Village building: it stays red with "Requires Village tier" until the tier-up), and six villagers. Dinner and wages stay at their
 * reset values (off) unless a test switches them. Progress is seeded as a written history: the L1 tests prove the counting.
 */
public final class TierTestSupport {
	public static final BlockPos FARM = WorkerTestSupport.ORIGIN_A;
	public static final BlockPos STORE = WorkerTestSupport.ORIGIN_B;
	public static final BlockPos KITCHEN = new BlockPos(8, 0, 14);
	public static final BlockPos HUT = new BlockPos(18, 0, 14);

	private TierTestSupport() {}

	public static String env(String name) { return "villageheart-gametest:tiers_" + name; }

	public record Layout(boolean kitchen, boolean food, int villagers, int farmers) {
		public static final Layout FULL = new Layout(true, true, 6, 3);
		public Layout withoutKitchen() { return new Layout(false, food, villagers, farmers); }
		public Layout withoutFood() { return new Layout(kitchen, false, villagers, farmers); }
	}

	public static BlockPos kitchenMarker() { return BuildingTestSupport.markerPos(KITCHEN, 3); }

	public static BlockPos hutMarker() { return BuildingTestSupport.markerPos(HUT, 3); }

	public static void buildKitchen(GameTestHelper h) {
		BuildingTestSupport.room(h, KITCHEN, 3, 3);
		h.setBlock(BuildingTestSupport.inside(KITCHEN, 0, 0, 0), Blocks.SMOKER);
		h.setBlock(BuildingTestSupport.inside(KITCHEN, 1, 0, 2), Blocks.CRAFTING_TABLE);
		h.setBlock(BuildingTestSupport.inside(KITCHEN, 2, 0, 2), Blocks.CHEST);
		h.setBlock(BuildingTestSupport.inside(KITCHEN, 2, 0, 0), Blocks.TORCH);
		BuildingTestSupport.placeMarker(h, BuildingType.KITCHEN, kitchenMarker(), Direction.NORTH);
	}

	/** The Hut is complete (table, chest, torch) but a Village building. */
	public static void buildHut(GameTestHelper h) {
		BuildingTestSupport.room(h, HUT, 3, 3);
		h.setBlock(BuildingTestSupport.inside(HUT, 0, 0, 0), JobBlocks.BLOCKS.get(JobBlockType.BUILDERS_TABLE));
		h.setBlock(BuildingTestSupport.inside(HUT, 2, 0, 2), Blocks.CHEST);
		h.setBlock(BuildingTestSupport.inside(HUT, 2, 0, 0), Blocks.TORCH);
		BuildingTestSupport.placeMarker(h, BuildingType.BUILDERS_HUT, hutMarker(), Direction.NORTH);
	}

	/** Claim, rooms, villagers, farmers hired through the real path. Ends once every marker shows its verdict (the Hut red, the rest green). */
	public static GameTestSequence layout(GameTestHelper h, Layout l) {
		return BuildingTestSupport.begin(h).thenExecute(() -> {
			NeedsTestSupport.sweepVillagers(h);
			LumberTestSupport.grass(h, 0, 0, 31, 31);
			WorkerTestSupport.light(h, 0, 0, 31, 31);
			WorkerTestSupport.farmhouse(h, FARM, 2);
			h.setBlock(BuildingTestSupport.inside(FARM, 0, 0, 1), Blocks.COMPOSTER);
			FarmTestSupport.buildStorage(h);
			BuildingTestSupport.placeMarker(h, BuildingType.FARMHOUSE, FarmTestSupport.farmMarker(), Direction.NORTH);
			BuildingTestSupport.placeMarker(h, BuildingType.STORAGE, FarmTestSupport.storeMarker(), Direction.NORTH);
			if (l.kitchen()) buildKitchen(h);
			buildHut(h);
		}).thenWaitUntil(() -> {
			BuildingTestSupport.assertStatus(h, FarmTestSupport.farmMarker(), MarkerStatus.VALID);
			BuildingTestSupport.assertStatus(h, FarmTestSupport.storeMarker(), MarkerStatus.VALID);
			if (l.kitchen()) BuildingTestSupport.assertStatus(h, kitchenMarker(), MarkerStatus.VALID);
			BuildingTestSupport.assertStatus(h, hutMarker(), MarkerStatus.INVALID);
		}).thenExecute(() -> {
			if (l.food()) NeedsTestSupport.putBread(h, 20);
			for (int i = 0; i < l.villagers(); i++) {
				Villager v = WorkerTestSupport.spawnVillager(h, new BlockPos(4 + 2 * i, 0, 28));
				if (i < l.farmers()) {
					var r = dev.villageheart.worker.Workers.hire(h.getLevel(), v, JobType.FARMER, java.util.Optional.empty());
					h.assertTrue(r instanceof dev.villageheart.worker.HireResult.Hired, "farmer " + i + " hired: " + r);
				}
			}
		});
	}

	public static VillageState village(GameTestHelper h) { return VillageClaims.current(h.getLevel().getServer()).orElseThrow(); }

	public static TierProgress progress(GameTestHelper h) { return village(h).tierProgress(); }

	public static long today(GameTestHelper h) { return WageTestSupport.dayIndex(h); }

	public static void seedProgress(GameTestHelper h, TierProgress p) {
		VillageClaims.data(h.getLevel().getServer()).update(v -> v.withTierProgress(p));
	}

	/**
	 * Jumps forward to the next day's dinner (today counts as served) and waits (60 ticks) until M11's record moves to it. Returns the day
	 * index of that dinner through {@code day[0]}.
	 */
	public static GameTestSequence toDinner(GameTestHelper h, GameTestSequence seq, long[] day) {
		GameTestSequence s = seq.thenExecute(() -> {
			day[0] = today(h) + 1;
			NeedsTestSupport.nextDinner(h);
		});
		return BuildingTestSupport.awaitWithin(h, s, 60, "the dinner", () ->
				h.assertValueEqual(day[0], NeedsTestSupport.lastDinnerDay(h), "M11 served the dinner of day " + day[0]));
	}

	public static GameTestSequence toDawn(GameTestHelper h, GameTestSequence seq, PayResult[] out) {
		return WageTestSupport.toDawn(h, seq, out);
	}

	/** Runs after M14's listener in the dawn tick and records what it sees there. */
	public static final class DawnProbe {
		public volatile Tier tier;
		public volatile int priorityQueued = -1;
		public volatile int fires;

		public void register(GameTestHelper h) {
			VillageDawn.register("test:tier_probe", (server, village, day, wages) -> {
				fires++;
				tier = VillageClaims.current(server).map(VillageState::tier).orElse(null);
				priorityQueued = dev.villageheart.building.BuildingSystem.priorityQueuedForTests(server);
			});
		}
	}
}
