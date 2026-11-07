package dev.villageheart.gametest;

import dev.villageheart.core.room.Pos;
import dev.villageheart.core.stock.Reservations;
import dev.villageheart.core.stock.StorageSet.SiteCandidate;
import dev.villageheart.core.stock.Stockpile;
import dev.villageheart.room.RoomScans;
import dev.villageheart.stock.ContainerBinder;
import dev.villageheart.stock.StackKey;
import dev.villageheart.stock.StorageContainers;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * Helpers for the stockpile GameTests (M6). Glue tests that only use {@link #siteOf} need no claim and no buildings; tests that
 * go through the facade follow the Building GameTests rules ({@link BuildingTestSupport}).
 */
public final class StockpileTestSupport {
	public static final String GLUE_ENV = "villageheart-gametest:stockpile_glue";

	private StockpileTestSupport() {}

	public static String env(String name) { return "villageheart-gametest:stockpile_" + name; }

	public static Pos pos(GameTestHelper h, BlockPos rel) { return RoomScans.pos(h.absolutePos(rel)); }

	/** A stone floor under y = 1 so blocks that need support (comparators) survive; everything else is built at y >= 1. */
	public static void floor(GameTestHelper h, int size) {
		for (int x = 0; x < size; x++) for (int z = 0; z < size; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
	}

	/** A candidate site over the given relative positions (no buildings, no facade). */
	public static SiteCandidate candidate(GameTestHelper h, BlockPos markerRel, BlockPos anchorRel, BlockPos... containerRels) {
		List<Pos> cs = new ArrayList<>();
		for (BlockPos c : containerRels) cs.add(pos(h, c));
		return new SiteCandidate(pos(h, markerRel), pos(h, anchorRel), cs);
	}

	/** A stockpile over hand-placed chests/barrels, built through {@link ContainerBinder} like the real glue. */
	public static Stockpile<StackKey> siteOf(GameTestHelper h, BlockPos markerRel, BlockPos anchorRel, BlockPos... containerRels) {
		var bound = ContainerBinder.bind(h.getLevel(), List.of(candidate(h, markerRel, anchorRel, containerRels)));
		return new Stockpile<>(bound.sites(), new Reservations<>());
	}

	public static Container container(GameTestHelper h, BlockPos rel) {
		Container c = StorageContainers.at(h.getLevel(), h.absolutePos(rel));
		if (c == null) throw new AssertionError("no chest or barrel at " + rel);
		return c;
	}

	/** Put {@code stacks} into the first slots of the container at {@code rel} (slot 0, 1, ...). */
	public static void fill(GameTestHelper h, BlockPos rel, ItemStack... stacks) {
		Container c = container(h, rel);
		for (int i = 0; i < stacks.length; i++) c.setItem(i, stacks[i]);
		c.setChanged();
	}

	/** Every slot of the container at {@code rel} holds {@code stack} (copied). */
	public static void fillAll(GameTestHelper h, BlockPos rel, ItemStack stack) {
		Container c = container(h, rel);
		for (int i = 0; i < c.getContainerSize(); i++) c.setItem(i, stack.copy());
		c.setChanged();
	}

	/** Copies of the container's slots. */
	public static List<ItemStack> slots(GameTestHelper h, BlockPos rel) {
		Container c = container(h, rel);
		List<ItemStack> out = new ArrayList<>(c.getContainerSize());
		for (int i = 0; i < c.getContainerSize(); i++) out.add(c.getItem(i).copy());
		return out;
	}
}
