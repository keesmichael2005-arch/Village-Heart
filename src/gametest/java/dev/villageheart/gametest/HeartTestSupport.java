package dev.villageheart.gametest;

import static dev.villageheart.gametest.BuildingTestSupport.*;

import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.heart.HeartSnapshot;
import dev.villageheart.core.heart.HeartSnapshotBuilder;
import dev.villageheart.core.Rank;
import dev.villageheart.heart.HeartFacts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Shared seed and helpers of the Heart screen GameTests (M8). */
public final class HeartTestSupport {
	private HeartTestSupport() {}

	public static final BlockPos STORAGE = new BlockPos(8, 0, 4);
	public static final BlockPos FARMHOUSE = new BlockPos(18, 0, 4);

	public static String env(String name) { return "villageheart-gametest:heart_" + name; }

	public static BlockPos chest1() { return inside(STORAGE, 0, 0, 3); }

	public static BlockPos chest2() { return inside(STORAGE, 4, 0, 3); }

	/**
	 * The common seed (plan section 5): a claim, a Storage with 2 chests (64 oak logs, 20 bread), a Farmhouse with 1 composter,
	 * both valid. The returned sequence has run {@code begin}; call this first in the test. Villagers are spawned by the test.
	 */
	public static GameTestSequence seed(GameTestHelper h) {
		WorkerTestSupport.begin(h);
		GameTestSequence seq = begin(h).thenExecute(() -> {
			BlockPos sm = room(h, STORAGE, 5, 4);
			h.setBlock(chest1(), net.minecraft.world.level.block.Blocks.CHEST);
			h.setBlock(chest2(), net.minecraft.world.level.block.Blocks.CHEST);
			StockpileTestSupport.fill(h, chest1(), new ItemStack(Items.OAK_LOG, 64));
			StockpileTestSupport.fill(h, chest2(), new ItemStack(Items.BREAD, 20));
			BlockPos fm = WorkerTestSupport.farmhouse(h, FARMHOUSE, 1);
			placeMarker(h, BuildingType.STORAGE, sm, Direction.NORTH);
			placeMarker(h, BuildingType.FARMHOUSE, fm, Direction.NORTH);
		});
		return awaitWithin(h, seq, bound(2, 0), "both buildings valid", () -> {
			h.assertTrue(status(h, markerPos(STORAGE, 5)) == dev.villageheart.block.MarkerStatus.VALID, "storage valid");
			h.assertTrue(status(h, markerPos(FARMHOUSE, 5)) == dev.villageheart.block.MarkerStatus.VALID, "farmhouse valid");
		});
	}

	public static Villager villager(GameTestHelper h) {
		return WorkerTestSupport.spawnVillager(h, inside(FARMHOUSE, 2, 0, 1));
	}

	/** The snapshot for {@code rank}, built from fresh facts (not the cache). */
	public static HeartSnapshot snapshot(GameTestHelper h, Rank rank) {
		return HeartSnapshotBuilder.build(HeartFacts.collect(h.getLevel().getServer()).orElseThrow(), rank);
	}

	public static void done(GameTestHelper h) {
		VillageTestSupport.done(h);
		WorkerTestSupport.end(h);
	}
}
