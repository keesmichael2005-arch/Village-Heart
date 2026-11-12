package dev.villageheart.gametest;

import static dev.villageheart.gametest.MineTestSupport.*;
import static dev.villageheart.gametest.SelfCraftGameTests.items;
import static dev.villageheart.gametest.SelfCraftGameTests.mineScene;
import static dev.villageheart.gametest.SelfCraftLifecycleGameTests.words;
import static dev.villageheart.gametest.SelfCraftTestSupport.*;
import static dev.villageheart.gametest.WorkerTestSupport.begin;
import static dev.villageheart.gametest.WorkerTestSupport.end;

import dev.villageheart.core.farm.CropKind;
import dev.villageheart.core.gear.CraftTier;
import dev.villageheart.core.gear.GearNeed;
import dev.villageheart.core.gear.PlankTable;
import dev.villageheart.core.gear.ToolRecipe;
import dev.villageheart.core.gear.ToolRecipes;
import dev.villageheart.core.job.ToolRules;
import dev.villageheart.core.worker.WorkerStatus;
import dev.villageheart.gametest.SelfCraftTestSupport.Sampler;
import dev.villageheart.worker.gear.SelfCraftRuntime;
import dev.villageheart.worker.gear.SmithService;
import dev.villageheart.worker.job.AwayTrips;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;

/** Layer 2: the hunter's and the farmer's own tools, the smith seam, and the tables against the game's recipes (M19a). */
public class SelfCraftJobsGameTests {
	private static final String YARD = BuildingTestSupport.YARD;

	private static WorkerStatus status(GameTestHelper h, Villager v) { return LumberTestSupport.status(h, v); }

	// ---- T-L2-13 ---------------------------------------------------------------------------------

	private static ItemStack stack(String id, int n) { return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(id)), n); }

	private static ItemStack craft(GameTestHelper h, int w, int ht, List<ItemStack> grid) {
		var found = h.getLevel().recipeAccess().getRecipeFor(RecipeType.CRAFTING, CraftingInput.of(w, ht, grid), h.getLevel());
		if (found.isEmpty()) return ItemStack.EMPTY;
		return found.get().value().assemble(CraftingInput.of(w, ht, grid), h.getLevel().registryAccess());
	}

	/** Every table entry equals the game's recipe: the shapes craft the item, the log/plank/stick conversions give the counts. */
	@GameTest(environment = "villageheart-gametest:selfcraft_tables")
	public void toolTablesMatchVanilla(GameTestHelper h) {
		ItemStack E = ItemStack.EMPTY;
		for (ToolRecipe r : ToolRecipes.ALL) {
			String head = r.tier() == CraftTier.STONE ? "minecraft:cobblestone" : "minecraft:oak_planks";
			ItemStack x = stack(head, 1), s = stack("minecraft:stick", 1);
			List<ItemStack> grid = switch (r.need()) {
				case AXE -> List.of(x, x, E, x, s, E, E, s, E);
				case PICKAXE -> List.of(x, x, x, E, s, E, E, s, E);
				case HOE -> List.of(x, x, E, E, s, E, E, s, E);
				case SWORD -> List.of(x, E, E, x, E, E, s, E, E);
				case HEAD, CHEST, LEGS, FEET -> throw new IllegalStateException("self-craft recipes are tools: " + r.outputId());
			};
			int count = 0;
			for (ItemStack g : grid) if (!g.isEmpty()) count++;
			h.assertValueEqual(r.head() + r.sticks(), count, r.outputId() + ": ingredient count");
			ItemStack out = craft(h, 3, 3, grid);
			h.assertValueEqual(r.outputId(), BuiltInRegistries.ITEM.getKey(out.getItem()).toString(), "the pattern of " + r.outputId());
			h.assertTrue(ToolRules.tierOf(r.outputId()).isPresent(), "ToolRules knows " + r.outputId());
			h.assertValueEqual(r.tier() == CraftTier.STONE ? dev.villageheart.core.job.ToolTier.STONE : dev.villageheart.core.job.ToolTier.WOOD,
					ToolRules.tierOf(r.outputId()).orElseThrow(), "the tier of " + r.outputId());
		}
		// sticks
		ItemStack sticks = craft(h, 1, 2, List.of(stack("minecraft:oak_planks", 1), stack("minecraft:oak_planks", 1)));
		h.assertTrue(sticks.is(Items.STICK) && sticks.getCount() == ToolRecipes.STICKS_PER_BATCH, "2 planks make 4 sticks: " + sticks);
		// every log gives its planks, four of them
		for (String wood : PlankTable.woods()) {
			boolean nether = wood.equals("crimson") || wood.equals("warped");
			String log = "minecraft:" + wood + (nether ? "_stem" : "_log");
			ItemStack out = craft(h, 1, 1, List.of(stack(log, 1)));
			h.assertValueEqual("minecraft:" + wood + "_planks", BuiltInRegistries.ITEM.getKey(out.getItem()).toString(), "planks of " + log);
			h.assertValueEqual(ToolRecipes.PLANKS_PER_LOG, out.getCount(), "planks per " + log);
			h.assertValueEqual("minecraft:" + wood + "_planks", PlankTable.planksOf(log).orElseThrow(), "the table maps " + log);
		}
		// the tags hold what the plan says
		Item[] stones = {Items.COBBLESTONE, Items.BLACKSTONE, Items.COBBLED_DEEPSLATE};
		for (Item i : stones) h.assertTrue(i.builtInRegistryHolder().is(net.minecraft.tags.ItemTags.STONE_TOOL_MATERIALS), i + " is a stone tool material");
		h.assertTrue(Items.OAK_PLANKS.builtInRegistryHolder().is(net.minecraft.tags.ItemTags.PLANKS), "planks");
		int toolNeeds = 0;
		for (GearNeed n : GearNeed.values()) if (!n.isArmour()) toolNeeds++;
		h.assertValueEqual(toolNeeds, 4, "four tool needs");
		h.succeed();
	}

	// ---- T-L2-14 ---------------------------------------------------------------------------------

	/** A hunter with no weapon and two cobblestone plus a stick in Storage makes a stone sword, then goes hunting. */
	@GameTest(environment = "villageheart-gametest:selfcraft_hunter", structure = YARD, skyAccess = true, maxTicks = 7000)
	public void hunterMakesSword(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		Sampler s = new Sampler();
		h.onEachTick(() -> s.sample(h));
		boolean[] words = {false};
		h.onEachTick(() -> {
			if (v[0] != null && h.getTick() % 20 == 0 && safeStatus(h, v[0]) == WorkerStatus.MAKING_STONE_TOOL && "Making a stone sword".equals(words(h, v[0]))) words[0] = true;
		});
		BlockPos storeChest = BuildingTestSupport.inside(HuntTestSupport.STORE, 0, 0, 3);
		GameTestSequence seq = HuntTestSupport.scene(h, false).thenExecute(() -> {
			HuntTestSupport.lightYard(h);
			add(h, storeChest, new ItemStack(Items.COBBLESTONE, 2), new ItemStack(Items.STICK, 1));
			h.getLevel().setDayTime(1000);
			v[0] = HuntTestSupport.hire(h);
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, selfBound(4800, 20), "a stone sword in the Lodge chest",
				() -> h.assertTrue(countIn(h, HuntTestSupport.chest(), Items.STONE_SWORD) == 1, "no sword yet: " + s.log));
		seq = seq.thenExecute(() -> {
			System.out.println("[m19a] hunterMakesSword: " + s.log);
			int clock = s.clock(v[0].getUUID());
			h.assertTrue(clock >= 4800 - 20 && clock <= 4800 + 100, "the stone time: " + clock);
			h.assertTrue(words[0], "the row said Making a stone sword");
			h.assertValueEqual(0L, stored(h, Items.COBBLESTONE) + stored(h, Items.STICK), "everything used");
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 1500, "the hunter goes hunting", () -> h.assertTrue(AwayTrips.isAway(v[0]), "not out yet"));
		seq.thenExecute(() -> {
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-15 ---------------------------------------------------------------------------------

	private static int mature(GameTestHelper h, List<BlockPos> cells) {
		int n = 0;
		for (BlockPos c : cells) if (FarmTestSupport.ageAt(h, c) >= 7) n++;
		return n;
	}

	/** The farmer makes its hoe only in idle time: never while a crop is ripe, and a craft in progress waits for the harvest. */
	@GameTest(environment = "villageheart-gametest:selfcraft_farmer", structure = YARD, skyAccess = true, maxTicks = 10500)
	public void farmerHoeOnlyWhenIdle(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		Sampler s = new Sampler();
		List<BlockPos>[] cells = new List[1];
		int[] matureAtCommit = {-1};
		double[] last = {0};
		int[] progressedWhileRipe = {0};
		h.onEachTick(() -> {
			s.sample(h);
			if (v[0] == null || cells[0] == null) return;
			if (matureAtCommit[0] < 0 && s.committed(v[0].getUUID())) matureAtCommit[0] = mature(h, cells[0]);
			var st = SelfCraftRuntime.get(h.getLevel().getServer()).peek(v[0].getUUID());
			if (st != null && st.progress >= 0) {
				if (st.progress > last[0] + 1e-9 && mature(h, cells[0]) > 0) progressedWhileRipe[0]++;
				last[0] = st.progress;
			}
		});
		GameTestSequence seq = FarmTestSupport.scene(h, 1, true).thenExecute(() -> {
			cells[0] = FarmTestSupport.field(h, 8, 14, 12, 18, CropKind.WHEAT, 7, false);
			add(h, FarmTestSupport.storeChest1(), new ItemStack(Items.COBBLESTONE, 2), new ItemStack(Items.STICK, 2));
			h.getLevel().setDayTime(1000);
			v[0] = FarmTestSupport.hire(h, FarmTestSupport.spawnSpot());
		});
		seq = seq.thenWaitUntil(() -> h.assertTrue(s.committed(v[0].getUUID()), "no craft yet: status " + status(h, v[0])));
		seq = seq.thenWaitUntil(() -> {
			var st = SelfCraftRuntime.get(h.getLevel().getServer()).peek(v[0].getUUID());
			h.assertTrue(st != null && st.progress >= 150, "the craft has not worked 150 ticks");
		});
		double[] frozen = new double[1];
		seq = seq.thenExecute(() -> {
			h.assertValueEqual(0, matureAtCommit[0], "the craft started only once the field was harvested and replanted");
			// the crops ripen again mid-craft: the progress freezes, the farmer harvests
			for (BlockPos c : cells[0]) {
				var state = h.getBlockState(c);
				if (state.getBlock() instanceof net.minecraft.world.level.block.CropBlock cb) h.setBlock(c, cb.getStateForAge(7));
			}
			frozen[0] = SelfCraftRuntime.get(h.getLevel().getServer()).peek(v[0].getUUID()).progress;
		});
		seq = seq.thenIdle(60);
		seq = seq.thenExecute(() -> {
			double now = SelfCraftRuntime.get(h.getLevel().getServer()).peek(v[0].getUUID()).progress;
			h.assertTrue(now - frozen[0] <= 25, "the progress waits while crops are ripe: " + frozen[0] + " -> " + now);
		});
		seq = seq.thenWaitUntil(() -> h.assertTrue(countIn(h, FarmTestSupport.chest(), Items.STONE_HOE) == 1, "no hoe yet: " + s.log + " ripe " + mature(h, cells[0])));
		seq.thenExecute(() -> {
			System.out.println("[m19a] farmerHoeOnlyWhenIdle: " + s.log + " ticks of progress while crops were ripe: " + progressedWhileRipe[0]);
			h.assertTrue(progressedWhileRipe[0] <= 25, "the craft barely moved while crops were ripe: " + progressedWhileRipe[0]);
			h.assertTrue(s.log.stream().anyMatch(l -> l.contains("commit minecraft:stone_hoe")), "a stone hoe: " + s.log);
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-16 ---------------------------------------------------------------------------------

	/** With a smith serving no new self-craft starts, except the fallback after 3000 work ticks for a missing required tool. */
	@GameTest(environment = "villageheart-gametest:selfcraft_smith_seam", structure = YARD, skyAccess = true, maxTicks = 4500)
	public void smithSeamStopsSelfCraft(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		Sampler s = new Sampler();
		long[] t0 = new long[1];
		h.onEachTick(() -> s.sample(h));
		GameTestSequence seq = mineScene(h, v, s, items(new ItemStack(Items.OAK_PLANKS, 5)));
		seq = seq.thenExecute(() -> {
			SmithService.CURRENT.set((server, village) -> true);
			t0[0] = h.getTick();
		});
		seq = seq.thenWaitUntil(() -> h.assertTrue(h.getTick() - t0[0] >= 2900, "waiting"));
		seq = seq.thenExecute(() -> {
			h.assertTrue(s.log.isEmpty(), "no self-craft while the smith serves: " + s.log);
			h.assertValueEqual(WorkerStatus.NEEDS_TOOL, status(h, v[0]), "still Needs pickaxe");
			h.assertValueEqual("Needs pickaxe", words(h, v[0]), "words");
		});
		seq = seq.thenWaitUntil(() -> h.assertTrue(s.committed(v[0].getUUID()), "the fallback has not started"));
		seq.thenExecute(() -> {
			long at = s.commitTick(v[0].getUUID()) - t0[0];
			System.out.println("[m19a] smithSeamStopsSelfCraft: the fallback committed " + at + " ticks after hiring");
			h.assertTrue(at >= 2950 && at <= 3400, "the fallback after about 3000 work ticks: " + at);
			SmithService.CURRENT.clear();
			VillageTestSupport.done(h);
			end(h);
		});
	}

	/** The smith goes away: self-crafting resumes at once. */
	@GameTest(environment = "villageheart-gametest:selfcraft_smith_gone", structure = YARD, skyAccess = true, maxTicks = 1500)
	public void selfCraftResumesWhenTheSmithGoes(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		Sampler s = new Sampler();
		long[] t0 = new long[1];
		h.onEachTick(() -> s.sample(h));
		GameTestSequence seq = mineScene(h, v, s, items(new ItemStack(Items.OAK_PLANKS, 5)));
		seq = seq.thenExecute(() -> {
			SmithService.CURRENT.set((server, village) -> true);
			t0[0] = h.getTick();
		});
		seq = seq.thenIdle(600);
		seq = seq.thenExecute(() -> {
			h.assertTrue(s.log.isEmpty(), "nothing while the smith serves: " + s.log);
			SmithService.CURRENT.clear();
			t0[0] = h.getTick();
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 100, "a craft starts", () -> h.assertTrue(s.committed(v[0].getUUID()) || status(h, v[0]) == WorkerStatus.MAKING_WOODEN_TOOL, "not yet"));
		seq.thenExecute(() -> {
			VillageTestSupport.done(h);
			end(h);
		});
	}
}
