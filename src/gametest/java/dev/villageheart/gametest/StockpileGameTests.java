package dev.villageheart.gametest;

import static dev.villageheart.gametest.StockpileTestSupport.*;

import dev.villageheart.core.room.Pos;
import dev.villageheart.core.stock.StorageAlert;
import dev.villageheart.core.stock.StockSnapshot;
import dev.villageheart.core.stock.Stockpile;
import dev.villageheart.stock.ContainerBinder;
import dev.villageheart.stock.StackKey;
import dev.villageheart.stock.StorageContainers;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

/**
 * Layer 2: the stockpile (M6). The glue tests (T-L2-4..7) share the no-claim environment {@code stockpile_glue} and build their
 * stockpile by hand through {@link StockpileTestSupport#siteOf}; the facade tests (further down) follow the Building GameTests rules.
 */
public class StockpileGameTests {
	private static ItemStack stack(net.minecraft.world.item.Item item, int n) { return new ItemStack(item, n); }

	// ---- T-L2-4 ----------------------------------------------------------------------------------

	@GameTest(environment = GLUE_ENV, maxTicks = 100)
	public void doubleChestHalvesCountOnce(GameTestHelper h) {
		floor(h, 6);
		BlockPos left = new BlockPos(1, 1, 1), right = new BlockPos(2, 1, 1), barrel = new BlockPos(4, 1, 1);
		BlockState base = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH);
		h.setBlock(left, base.setValue(ChestBlock.TYPE, ChestType.LEFT));
		h.setBlock(right, base.setValue(ChestBlock.TYPE, ChestType.RIGHT));
		h.setBlock(barrel, Blocks.BARREL);
		BlockState leftState = h.getBlockState(left);
		h.assertTrue(ChestBlock.getConnectedBlockPos(h.absolutePos(left), leftState).equals(h.absolutePos(right)), "the halves are connected");
		fill(h, left, stack(Items.OAK_LOG, 10));
		fill(h, right, stack(Items.OAK_LOG, 10));
		fill(h, barrel, stack(Items.OAK_LOG, 5));

		Stockpile<StackKey> s = siteOf(h, new BlockPos(0, 1, 0), new BlockPos(3, 1, 0), left, right, barrel);
		StackKey log = StackKey.of(Items.OAK_LOG);
		StockSnapshot<StackKey> snap = s.snapshot(0, 0, 0, StorageAlert.OK);
		h.assertValueEqual(25L, s.count(log), "log count (not 45)");
		h.assertValueEqual(3, snap.containers(), "containers");
		h.assertValueEqual(81, snap.slots(), "slots");

		h.assertValueEqual(200, s.deposit(pos(h, left), log, 200), "stored");
		List<ItemStack> l = slots(h, left), r = slots(h, right), b = slots(h, barrel);
		h.assertValueEqual(64, l.get(0).getCount(), "first half slot 0");
		h.assertValueEqual(64, r.get(0).getCount(), "second half slot 0");
		h.assertValueEqual(64, b.get(0).getCount(), "barrel slot 0");
		h.assertValueEqual(33, l.get(1).getCount(), "first half slot 1");
		int others = 0;
		for (List<ItemStack> list : List.of(l, r, b)) for (int i = 0; i < list.size(); i++) {
			boolean expected = i == 0 || (list == l && i == 1);
			if (!expected && !list.get(i).isEmpty()) others++;
		}
		h.assertValueEqual(0, others, "every other slot is empty");

		Container combined = ChestBlock.getContainer((ChestBlock) Blocks.CHEST, h.getBlockState(left), h.getLevel(), h.absolutePos(left), true);
		h.assertTrue(combined != null && combined.getContainerSize() == 54, "the menu container has 54 slots");
		int sum = 0;
		for (int i = 0; i < combined.getContainerSize(); i++) sum += combined.getItem(i).getCount();
		h.assertValueEqual(161, sum, "the combined container sum");
		h.succeed();
	}

	// ---- T-L2-5 ----------------------------------------------------------------------------------

	@GameTest(environment = GLUE_ENV, maxTicks = 100)
	public void componentsAreRespected(GameTestHelper h) {
		floor(h, 4);
		BlockPos chest = new BlockPos(1, 1, 1);
		h.setBlock(chest, Blocks.CHEST);
		ItemStack plain = stack(Items.PAPER, 32);
		ItemStack named = stack(Items.PAPER, 16);
		named.set(DataComponents.CUSTOM_NAME, Component.literal("Note"));
		ItemStack damaged = stack(Items.IRON_SWORD, 1);
		damaged.setDamageValue(5);
		ItemStack fresh = stack(Items.IRON_SWORD, 1);
		fill(h, chest, plain, named, damaged, fresh);

		Stockpile<StackKey> s = siteOf(h, new BlockPos(0, 1, 0), new BlockPos(0, 1, 0), chest);
		h.assertValueEqual(4, s.snapshot(0, 0, 0, StorageAlert.OK).totals().size(), "four variants");

		// the key is vanilla's stacking rule: all 36 unordered pairs of the 8 stacks (the 4 and a copy of each)
		List<ItemStack> eight = new ArrayList<>(List.of(plain, named, damaged, fresh));
		for (ItemStack st : List.of(plain, named, damaged, fresh)) eight.add(st.copy());
		int pairs = 0;
		for (int i = 0; i < eight.size(); i++) for (int j = i; j < eight.size(); j++) {
			ItemStack a = eight.get(i), b = eight.get(j);
			StackKey ka = StackKey.of(a), kb = StackKey.of(b);
			h.assertTrue(ka.equals(kb) == ItemStack.isSameItemSameComponents(a, b), "pair " + i + "," + j + " agrees with vanilla");
			if (ka.equals(kb)) h.assertTrue(ka.hashCode() == kb.hashCode(), "equal keys, equal hash " + i + "," + j);
			pairs++;
		}
		h.assertValueEqual(36, pairs, "pairs");

		StackKey plainKey = StackKey.of(plain);
		h.assertValueEqual(10, s.deposit(pos(h, chest), plainKey, 10), "deposit plain paper");
		h.assertValueEqual(42, slots(h, chest).get(0).getCount(), "plain slot");
		h.assertValueEqual(16, slots(h, chest).get(1).getCount(), "named slot untouched");
		h.assertValueEqual(40, s.withdraw(pos(h, chest), plainKey, 40, null), "withdrawn plain paper");
		h.assertValueEqual(2, slots(h, chest).get(0).getCount(), "plain slot after");
		h.assertTrue(slots(h, chest).get(1).has(DataComponents.CUSTOM_NAME), "the named paper is intact");
		h.assertValueEqual(18L, s.snapshot(0, 0, 0, StorageAlert.OK).totalsBy(StackKey::id).get("minecraft:paper"), "all paper variants");

		ItemStack renamed = stack(Items.PAPER, 1);
		renamed.set(DataComponents.CUSTOM_NAME, Component.literal("x"));
		renamed.remove(DataComponents.CUSTOM_NAME);
		h.assertTrue(StackKey.of(renamed).equals(plainKey), "a paper whose name was removed is plain again");
		h.succeed();
	}

	// ---- T-L2-6 ----------------------------------------------------------------------------------

	@GameTest(environment = GLUE_ENV, maxTicks = 100)
	public void depositMarksChestChanged(GameTestHelper h) {
		floor(h, 5);
		BlockPos chest = new BlockPos(1, 1, 1), comparator = new BlockPos(2, 1, 1);
		h.setBlock(chest, Blocks.CHEST);
		// a comparator FACING points at its input (the block behind it), so WEST reads the chest at x - 1
		h.setBlock(comparator, Blocks.COMPARATOR.defaultBlockState().setValue(ComparatorBlock.FACING, Direction.WEST));
		Stockpile<StackKey> s = siteOf(h, new BlockPos(0, 1, 0), new BlockPos(0, 1, 0), chest);
		h.startSequence().thenIdle(5).thenExecute(() -> {
			h.assertValueEqual(0, h.getBlockEntity(comparator, ComparatorBlockEntity.class).getOutputSignal(), "empty chest signal");
			h.assertValueEqual(64, s.deposit(pos(h, chest), StackKey.of(Items.COBBLESTONE), 64), "stored");
		}).thenIdle(4).thenExecute(() -> {
			h.assertValueEqual(1, h.getBlockEntity(comparator, ComparatorBlockEntity.class).getOutputSignal(), "signal after the deposit");
		}).thenSucceed();
	}

	// ---- T-L2-7 ----------------------------------------------------------------------------------

	@GameTest(environment = GLUE_ENV, maxTicks = 100)
	public void unloadedAndReplacedAreSkipped(GameTestHelper h) {
		floor(h, 6);
		// (a) a position 100 000 blocks away: null, and the chunk is not loaded by the lookup
		BlockPos far = h.absolutePos(new BlockPos(1, 1, 1)).offset(100_000, 0, 0);
		int cx = far.getX() >> 4, cz = far.getZ() >> 4;
		h.assertFalse(h.getLevel().hasChunk(cx, cz), "the far chunk starts unloaded");
		h.assertTrue(StorageContainers.at(h.getLevel(), far) == null, "no container in an unloaded chunk");
		h.assertFalse(h.getLevel().hasChunk(cx, cz), "the lookup did not load the chunk");

		// (b) a chest, a furnace and air in one site
		BlockPos chest = new BlockPos(1, 1, 1), furnace = new BlockPos(2, 1, 1), air = new BlockPos(3, 1, 1);
		h.setBlock(chest, Blocks.CHEST);
		h.setBlock(furnace, Blocks.FURNACE);
		var bound = ContainerBinder.bind(h.getLevel(), List.of(candidate(h, new BlockPos(0, 1, 0), new BlockPos(0, 1, 0), chest, furnace, air)));
		h.assertValueEqual(1, bound.sites().get(0).containers().size(), "one container bound");
		h.assertValueEqual(2, bound.unreadContainers(), "two unread");
		h.assertValueEqual(0, bound.emptySites(), "no empty site");
		var onlyAir = ContainerBinder.bind(h.getLevel(), List.of(candidate(h, new BlockPos(0, 1, 0), new BlockPos(0, 1, 0), air)));
		h.assertTrue(onlyAir.sites().isEmpty(), "a site listing only air is dropped");
		h.assertValueEqual(1, onlyAir.emptySites(), "and counted unknown");

		// (c) an ender chest and a shulker box are not stockpile containers
		BlockPos ender = new BlockPos(1, 1, 3), shulker = new BlockPos(2, 1, 3);
		h.setBlock(ender, Blocks.ENDER_CHEST);
		h.setBlock(shulker, Blocks.SHULKER_BOX);
		var other = ContainerBinder.bind(h.getLevel(), List.of(candidate(h, new BlockPos(0, 1, 0), new BlockPos(0, 1, 0), ender, shulker)));
		h.assertTrue(other.sites().isEmpty(), "ender chest and shulker box are skipped");
		h.assertValueEqual(2, other.unreadContainers(), "both counted unread");
		h.succeed();
	}
}
