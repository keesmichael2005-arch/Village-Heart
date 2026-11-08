package dev.villageheart.gametest;

import dev.villageheart.core.gear.SelfCraft;
import dev.villageheart.stock.Stockpiles;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.worker.gear.SelfCraftRuntime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Helpers of the self-craft GameTests (M19a). Rules (docs/TESTING.md, "Self-craft GameTests (M19a)"): one environment per test
 * ({@code selfcraft_<name>}), a scene of the existing supports (Mine, Lumber Camp, Hunting Lodge, Farmhouse) with a Storage a few blocks
 * from it, {@code VillageTestSupport.done} + {@code WorkerTestSupport.end} last, every test logs {@code [m19a]} tick counts.
 */
public final class SelfCraftTestSupport {
	private SelfCraftTestSupport() {}

	public static String env(String name) { return "villageheart-gametest:selfcraft_" + name; }

	/** The bound of a self-craft: the work at speed 1.0 (level 1, pinned needs), 10 ticks a block of walking and 400 ticks of slack. */
	public static int selfBound(int ticks, int dist) { return ticks + 10 * dist + 400; }

	public static List<SelfCraft> records(GameTestHelper h) {
		return VillageClaims.current(h.getLevel().getServer()).map(v -> v.selfCrafts()).orElse(List.of());
	}

	public static Optional<SelfCraft> record(GameTestHelper h, UUID worker) {
		return VillageClaims.current(h.getLevel().getServer()).flatMap(v -> v.selfCraft(worker));
	}

	public static long stored(GameTestHelper h, Item item) { return Stockpiles.count(h.getLevel().getServer(), item); }

	public static int countIn(GameTestHelper h, BlockPos chestRel, Item item) {
		Container c = StockpileTestSupport.container(h, chestRel);
		int n = 0;
		for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).is(item)) n += c.getItem(i).getCount();
		return n;
	}

	public static int firstEmptySlot(GameTestHelper h, BlockPos chestRel) {
		Container c = StockpileTestSupport.container(h, chestRel);
		for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).isEmpty()) return i;
		return -1;
	}

	/** Puts the stacks into the first free slots of the container (after what is already there). */
	public static void add(GameTestHelper h, BlockPos chestRel, ItemStack... stacks) {
		Container c = StockpileTestSupport.container(h, chestRel);
		for (ItemStack s : stacks) {
			int slot = firstEmptySlot(h, chestRel);
			if (slot < 0) throw new AssertionError("no free slot in " + chestRel);
			c.setItem(slot, s);
		}
		c.setChanged();
	}

	public static void fillWithDirt(GameTestHelper h, BlockPos chestRel) {
		Container c = StockpileTestSupport.container(h, chestRel);
		for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).isEmpty()) c.setItem(i, new ItemStack(net.minecraft.world.item.Items.DIRT, 64));
		c.setChanged();
	}

	public static void clearChest(GameTestHelper h, BlockPos chestRel) {
		Container c = StockpileTestSupport.container(h, chestRel);
		for (int i = 0; i < c.getContainerSize(); i++) c.setItem(i, ItemStack.EMPTY);
		c.setChanged();
	}

	/** The reserved count of every stockpile key held by this worker. */
	public static int reservedBy(GameTestHelper h, UUID worker, Item item) {
		return Stockpiles.reservations(h.getLevel().getServer()).reservedBy(worker, dev.villageheart.stock.StackKey.of(item));
	}

	/**
	 * Watches the saved craft records every tick (register {@code h.onEachTick(() -> s.sample(h))} from the test body): when a worker's
	 * record appeared (the commit) and disappeared (finish or refund), its recipe, and the ticks its progress really advanced (the clock).
	 */
	public static final class Sampler {
		private final Map<UUID, Long> commit = new HashMap<>();
		private final Map<UUID, Long> gone = new HashMap<>();
		private final Map<UUID, String> recipe = new HashMap<>();
		private final Map<UUID, Integer> clock = new HashMap<>();
		private final Map<UUID, Double> lastProgress = new HashMap<>();
		public final List<String> log = new ArrayList<>();

		public void sample(GameTestHelper h) {
			long tick = h.getTick();
			List<SelfCraft> now = records(h);
			for (SelfCraft c : now) {
				UUID w = c.worker();
				if (!commit.containsKey(w) || gone.containsKey(w)) {
					commit.put(w, tick);
					gone.remove(w);
					clock.put(w, 0);
					recipe.put(w, c.craft().recipeId());
					log.add("tick " + tick + " commit " + c.craft().recipeId() + " " + c.craft().committed());
				}
				SelfCraftRuntime.State st = SelfCraftRuntime.get(h.getLevel().getServer()).peek(w);
				if (st != null && st.progress >= 0) {
					double prev = lastProgress.getOrDefault(w, 0.0);
					if (st.progress > prev + 1e-9) clock.merge(w, 1, Integer::sum);
					lastProgress.put(w, st.progress);
				}
			}
			for (UUID w : new ArrayList<>(commit.keySet())) {
				if (!gone.containsKey(w) && now.stream().noneMatch(c -> c.worker().equals(w))) {
					gone.put(w, tick);
					log.add("tick " + tick + " gone " + recipe.get(w) + " clock " + clock.get(w));
				}
			}
		}

		public boolean committed(UUID w) { return commit.containsKey(w); }

		public boolean finished(UUID w) { return gone.containsKey(w); }

		public long commitTick(UUID w) { return commit.getOrDefault(w, -1L); }

		public long goneTick(UUID w) { return gone.getOrDefault(w, -1L); }

		public String recipeOf(UUID w) { return recipe.get(w); }

		/** Ticks the craft's progress advanced between commit and the record's end. */
		public int clock(UUID w) { return clock.getOrDefault(w, 0); }
	}

	public static Villager reload(GameTestHelper h, Villager v, Runnable whileUnloaded) {
		return LumberTestSupport.reloadEntity(h.getLevel(), v, whileUnloaded);
	}
}
