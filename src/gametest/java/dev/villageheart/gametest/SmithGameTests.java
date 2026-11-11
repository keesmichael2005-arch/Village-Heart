package dev.villageheart.gametest;

import static dev.villageheart.gametest.SmithTestSupport.bank;
import static dev.villageheart.gametest.SmithTestSupport.count;
import static dev.villageheart.gametest.SmithTestSupport.hire;
import static dev.villageheart.gametest.SmithTestSupport.lit;
import static dev.villageheart.gametest.SmithTestSupport.log;
import static dev.villageheart.gametest.SmithTestSupport.putStore;
import static dev.villageheart.gametest.SmithTestSupport.scene;
import static dev.villageheart.gametest.SmithTestSupport.setDayTime;
import static dev.villageheart.gametest.SmithTestSupport.smithBound;
import static dev.villageheart.gametest.SmithTestSupport.walkBlocks;

import dev.villageheart.block.JobBlocks;
import dev.villageheart.building.Buildings;
import dev.villageheart.core.Balance;
import dev.villageheart.core.building.BuildingRequirements;
import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.building.JobBlockType;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.gear.CraftTier;
import dev.villageheart.core.gear.GearCraft;
import dev.villageheart.core.gear.GearNeed;
import dev.villageheart.core.gear.ItemCount;
import dev.villageheart.core.gear.OrderKey;
import dev.villageheart.core.gear.ToolRecipe;
import dev.villageheart.core.gear.ToolRecipes;
import dev.villageheart.core.smith.RepairRules;
import dev.villageheart.core.smith.SmithCraft;
import dev.villageheart.core.smith.SmithDecision;
import dev.villageheart.core.smith.SmithKind;
import dev.villageheart.core.smith.SmithRecipe;
import dev.villageheart.core.smith.SmithRecipes;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.core.worker.WorkerStatus;
import dev.villageheart.golem.GolemGate;
import dev.villageheart.stock.StackKey;
import dev.villageheart.stock.Stockpiles;
import dev.villageheart.core.job.ToolKind;
import dev.villageheart.worker.WorkerAttachments;
import dev.villageheart.worker.Workers;
import dev.villageheart.worker.gear.GearOrders;
import dev.villageheart.worker.gear.SelfCraftSwitch;
import dev.villageheart.worker.job.CampTools;
import dev.villageheart.worker.gear.ToolStacks;
import dev.villageheart.worker.job.WorkStats;
import dev.villageheart.worker.job.WorkerStatuses;
import dev.villageheart.worker.smith.SmithRuntime;
import dev.villageheart.worker.smith.SmithyLayout;
import dev.villageheart.world.VillageClaims;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Blocks;

/**
 * Layer 2: the smith (M19). The spawn-gate tests are the first of them. Self-crafting is off (M19's rule for every smith test except
 * T-L2-5l and T-L2-5m) so a long scene cannot start a wood craft beside the assertion.
 */
public class SmithGameTests {
	private static final String YARD = BuildingTestSupport.YARD;

	/**
	 * T-L2-3. Forty gossip rounds are the number of chances two in-game days would give (48 000 / 1 200). They are forty real attempts
	 * run back to back, not two days of simulated life. Each one is checked to be a would-spawn before it is offered to the gate.
	 */
	@GameTest(environment = "villageheart-gametest:smith_no_vanilla_golem", structure = YARD, skyAccess = true, maxTicks = 400)
	public void noVanillaGolemInClaim(GameTestHelper h) {
		WorkerTestSupport.begin(h);
		List<Villager> pen = new ArrayList<>();
		BuildingTestSupport.begin(h).thenExecute(() -> {
			SelfCraftSwitch.off(); // M19: the gate test does not hire anyone, and self-crafting stays off anyway
			ServerLevel level = h.getLevel();
			HeartLocation heart = VillageClaims.current(level.getServer()).orElseThrow().heart();
			BlockPos centre = h.absolutePos(new BlockPos(16, 1, 16));
			SmithTestSupport.sweepGolems(level, centre, 24);
			SmithTestSupport.pad(level, centre, 11);
			int[][] at = {{-1, -1}, {-1, 0}, {-1, 1}, {0, -1}, {0, 0}, {0, 1}, {1, -1}, {1, 0}, {1, 1}, {2, 0}};
			for (int[] o : at) pen.add(SmithTestSupport.villagerAt(level, centre.offset(o[0], 0, o[1])));
			GolemGate.reset();
			long t = Math.max(level.getGameTime(), 1200L) + 1201L;
			for (int round = 0; round < 40; round++) {
				assertWouldSpawn(h, pen, t);
				for (int p = 0; p < 5; p++) pen.get(p * 2).gossip(level, pen.get(p * 2 + 1), t);
				t += 1201L;
			}
			assertWouldSpawn(h, pen, t);
			for (int i = 0; i < 10; i++) pen.get(0).spawnGolemIfNeeded(level, t, 3);
			h.assertTrue(SmithTestSupport.golemsNear(level, centre, 24) == 0, "no vanilla golem inside the claim");
			h.assertValueEqual(210, GolemGate.calls(), "every gossip and panic call reached the gate");
			h.assertValueEqual(210, GolemGate.blocked(), "every one of those was a real would-spawn");
			// control: the same pen, the claim released, one more round. Vanilla does spawn here.
			VillageClaims.releaseAt(level, new BlockPos(heart.x(), heart.y(), heart.z()));
			t += 1201L;
			assertWouldSpawn(h, pen, t);
			for (int p = 0; p < 5; p++) pen.get(p * 2).gossip(level, pen.get(p * 2 + 1), t);
			h.assertTrue(SmithTestSupport.golemsNear(level, centre, 16) >= 1, "vanilla spawns once the claim is gone");
			SmithTestSupport.sweepGolems(level, centre, 24);
			System.out.println("[m19] noVanillaGolemInClaim: 210 calls blocked, then a golem after release");
			VillageTestSupport.done(h);
			WorkerTestSupport.end(h);
		});
	}

	/** T-L2-3b. 84 blocks is outside the 80-block sphere and inside the 8-block margin. 100 blocks is not. */
	@GameTest(environment = "villageheart-gametest:smith_gate_edge", structure = YARD, skyAccess = true, maxTicks = 400)
	public void gateEdgeOfClaim(GameTestHelper h) {
		WorkerTestSupport.begin(h);
		BuildingTestSupport.begin(h).thenExecute(() -> {
			SelfCraftSwitch.off(); // M19
			ServerLevel level = h.getLevel();
			HeartLocation heart = VillageClaims.current(level.getServer()).orElseThrow().heart();
			BlockPos near = new BlockPos(heart.x() + 84, heart.y(), heart.z());
			BlockPos far = new BlockPos(heart.x() + 100, heart.y(), heart.z());
			// the chunks have to tick once before they will accept entities; a forced chunk is not in the entity tracker on the tick it was loaded
			SmithTestSupport.force(level, near, 16);
			SmithTestSupport.force(level, far, 16);
		}).thenIdle(5).thenExecute(() -> {
			ServerLevel level = h.getLevel();
			HeartLocation heart = VillageClaims.current(level.getServer()).orElseThrow().heart();
			BlockPos near = new BlockPos(heart.x() + 84, heart.y(), heart.z());
			BlockPos far = new BlockPos(heart.x() + 100, heart.y(), heart.z());
			trySpawnAt(h, level, near, false);
			trySpawnAt(h, level, far, true);
			SmithTestSupport.sweepGolems(level, near, 20);
			SmithTestSupport.sweepGolems(level, far, 20);
			SmithTestSupport.releaseForced(level);
			System.out.println("[m19] gateEdgeOfClaim: blocked at 84, spawned at 100");
			VillageTestSupport.done(h);
			WorkerTestSupport.end(h);
		});
	}

	/** T-L2-9. Four iron blocks and a carved pumpkin are the player's golem, and the villager gate does not see that path. */
	@GameTest(environment = "villageheart-gametest:smith_player_golem", structure = YARD, skyAccess = true, maxTicks = 200)
	public void playerBuiltGolemStillWorks(GameTestHelper h) {
		WorkerTestSupport.begin(h);
		BlockPos centre = new BlockPos(16, 1, 12);
		BuildingTestSupport.begin(h).thenExecute(() -> {
			SelfCraftSwitch.off(); // M19
			ServerLevel level = h.getLevel();
			SmithTestSupport.sweepGolems(level, h.absolutePos(centre), 8);
			// top ~^~ , middle ### , bottom ~#~ ; the pumpkin goes on last, which is what runs CarvedPumpkinBlock.onPlace
			h.setBlock(centre, Blocks.IRON_BLOCK);
			h.setBlock(centre.above().west(), Blocks.IRON_BLOCK);
			h.setBlock(centre.above(), Blocks.IRON_BLOCK);
			h.setBlock(centre.above().east(), Blocks.IRON_BLOCK);
			h.setBlock(centre.above(2), Blocks.CARVED_PUMPKIN);
		}).thenIdle(20).thenExecute(() -> {
			h.assertTrue(SmithTestSupport.golemsNear(h.getLevel(), h.absolutePos(centre), 6) >= 1, "the pumpkin built a golem");
			SmithTestSupport.sweepGolems(h.getLevel(), h.absolutePos(centre), 8);
			System.out.println("[m19] playerBuiltGolemStillWorks: pumpkin golem appeared");
			VillageTestSupport.done(h);
			WorkerTestSupport.end(h);
		});
	}

	/** Five villagers on a pad, all willing, and one panic-path call. {@code expectGolem} is the control outside the margin. */
	private static void trySpawnAt(GameTestHelper h, ServerLevel level, BlockPos centre, boolean expectGolem) {
		SmithTestSupport.pad(level, centre, 11);
		SmithTestSupport.sweepGolems(level, centre, 20);
		h.assertTrue(level.getBlockState(centre.below()).is(Blocks.STONE), "the pad is stone, saw " + level.getBlockState(centre.below()));
		h.assertTrue(level.getBlockState(centre).isAir() && level.getBlockState(centre.above(3)).isAir(), "the pad has air above it");
		List<Villager> pen = new ArrayList<>();
		for (int i = 0; i < 5; i++) pen.add(SmithTestSupport.villagerAt(level, centre.offset(i - 2, 0, 0)));
		int inWorld = level.getEntities(net.minecraft.world.entity.EntityType.VILLAGER, new net.minecraft.world.phys.AABB(centre).inflate(10), net.minecraft.world.entity.npc.villager.Villager::isAlive).size();
		h.assertTrue(inWorld >= 5, "the five villagers are in the world, saw " + inWorld);
		long t = Math.max(level.getGameTime(), 1200L) + 1201L;
		assertWouldSpawn(h, pen, t);
		int calls = GolemGate.calls();
		pen.get(2).spawnGolemIfNeeded(level, t, 3);
		h.assertTrue(GolemGate.calls() == calls + 1, "the panic call reached the gate at " + centre);
		int found = SmithTestSupport.golemsNear(level, centre, 16);
		if (expectGolem) h.assertTrue(found >= 1, "a golem spawns at " + centre);
		else h.assertTrue(found == 0, "the margin still blocks at " + centre);
		for (Villager v : pen) v.discard();
	}

	// ---- T-L2-10 tables -------------------------------------------------------------------------------

	/** Every smith recipe matches the game: crafting grids, blasting cook time 100, coal's burn time, and the repair rule. */
	@GameTest(environment = "villageheart-gametest:smith_tables", structure = YARD, skyAccess = true, maxTicks = 200)
	public void smithTablesMatchVanilla(GameTestHelper h) {
		var level = h.getLevel();
		ItemStack empty = ItemStack.EMPTY;
		int crafts = 0;
		for (SmithRecipe recipe : SmithRecipes.ALL) {
			if (recipe.kind() != SmithKind.CRAFT) continue;
			crafts++;
			ToolRecipe shape = recipe.shape().orElseThrow();
			if (shape.tier() == CraftTier.WOODEN || shape.tier() == CraftTier.STONE) {
				h.assertTrue(shape == ToolRecipes.of(shape.need(), shape.tier()), "wooden and stone shapes are the M19a objects: " + shape.outputId());
			}
			String headId = headItem(shape);
			ItemStack x = stack(headId), s = stack("minecraft:stick");
			List<ItemStack> grid = grid(shape.need(), x, s, empty);
			ItemStack out = craft(h, grid);
			h.assertValueEqual(shape.outputId(), BuiltInRegistries.ITEM.getKey(out.getItem()).toString(), "the pattern of " + shape.outputId());
			ItemStack tool = new ItemStack(out.getItem());
			h.assertTrue(tool.isValidRepairItem(new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(headId)))),
					shape.outputId() + " is repaired with " + headId);
		}
		h.assertValueEqual(36, crafts, "20 tools and 16 armour pieces");
		for (SmithRecipe recipe : SmithRecipes.ALL) {
			if (recipe.kind() != SmithKind.SMELT) continue;
			var input = new SingleRecipeInput(stack(recipe.inputs().get(0).itemId()));
			var found = level.getServer().getRecipeManager().getRecipeFor(RecipeType.BLASTING, input, level);
			h.assertTrue(found.isPresent(), "a blasting recipe for " + recipe.inputs().get(0).itemId());
			var cooked = found.get().value();
			h.assertTrue(cooked instanceof AbstractCookingRecipe, "blasting is a cooking recipe");
			h.assertValueEqual(100, ((AbstractCookingRecipe) cooked).cookingTime(), "blasting cook time of " + recipe.outputId());
			h.assertTrue(cooked.assemble(input, level.registryAccess()).is(BuiltInRegistries.ITEM.getValue(Identifier.parse(recipe.outputId()))),
					"the blast furnace makes " + recipe.outputId());
		}
		h.assertValueEqual(1600, level.fuelValues().burnDuration(new ItemStack(Items.COAL)), "coal burns for 1600");
		int max = new ItemStack(Items.IRON_SWORD).getMaxDamage();
		h.assertValueEqual(0, RepairRules.units(40, max), "40 damage on an iron sword (max " + max + ") is not a whole repair unit");
		VillageTestSupport.done(h);
	}

	// ---- T-L2-4 ---------------------------------------------------------------------------------------

	/** 16 raw iron, 2 raw copper, 2 raw gold and 3 coal become ingots. The coal is burned and the furnace goes dark afterwards. */
	@GameTest(environment = "villageheart-gametest:smith_smelt", structure = YARD, skyAccess = true, maxTicks = 8000)
	public void smeltsRawOre(GameTestHelper h) {
		long[] t0 = new long[1];
		boolean[] sawLit = new boolean[1];
		int bound = smithBound(20, 100, walkBlocks());
		scene(h).thenExecute(() -> {
			putStore(h, Items.RAW_IRON, 16);
			putStore(h, Items.RAW_COPPER, 2);
			putStore(h, Items.RAW_GOLD, 2);
			putStore(h, Items.COAL, 3);
			setDayTime(h, 1000);
			t0[0] = h.getTick();
			hire(h);
			log("smelt bound " + bound + " (walk " + walkBlocks() + ")");
		}).thenWaitUntil(() -> {
			if (lit(h)) sawLit[0] = true;
			h.assertTrue(count(h, Items.IRON_INGOT) == 16 && count(h, Items.COPPER_INGOT) == 2 && count(h, Items.GOLD_INGOT) == 2,
					"ingots iron " + count(h, Items.IRON_INGOT) + " copper " + count(h, Items.COPPER_INGOT) + " gold " + count(h, Items.GOLD_INGOT));
		}).thenExecute(() -> {
			long took = h.getTick() - t0[0];
			log("smelt took " + took + " bound " + bound + " bank " + bank(h));
			h.assertTrue(took <= bound, "smelting 20 took " + took + ", bound " + bound);
			h.assertTrue(count(h, Items.RAW_IRON) == 0 && count(h, Items.RAW_COPPER) == 0 && count(h, Items.RAW_GOLD) == 0, "no raw ore left");
			h.assertTrue(count(h, Items.COAL) == 0, "coal left " + count(h, Items.COAL));
			h.assertValueEqual(800, bank(h), "heat left in the furnace");
			h.assertTrue(sawLit[0], "the furnace was lit while smelting");
			h.assertFalse(lit(h), "the furnace is dark once the ore is gone");
			VillageTestSupport.done(h);
		});
	}

	// ---- T-L2-5e --------------------------------------------------------------------------------------

	/** Five oak planks and an empty Lumber Camp: a wooden axe, made slowly, and the two spare sticks back in Storage. */
	@GameTest(environment = "villageheart-gametest:smith_wooden_axe", structure = YARD, skyAccess = true, maxTicks = 4000)
	public void bootstrapWoodenAxe(GameTestHelper h) {
		Villager[] smith = new Villager[1];
		scene(h).thenExecute(() -> SmithTestSupport.addLumberCamp(h)).thenWaitUntil(() ->
				BuildingTestSupport.assertStatus(h, SmithTestSupport.campMarker(), dev.villageheart.block.MarkerStatus.VALID)
		).thenExecute(() -> {
			putStore(h, Items.OAK_PLANKS, 5);
			setDayTime(h, 1000);
			SmithTestSupport.hireLumberjack(h);
			smith[0] = hire(h);
			log("wooden axe camp ready");
		}).thenWaitUntil(() -> {
			var units = SmithRuntime.unitsOf(h.getLevel().getServer(), smith[0].getUUID());
			boolean axe = SmithTestSupport.chestHas(h, SmithTestSupport.campChest(), Items.WOODEN_AXE);
			h.assertTrue(axe && count(h, Items.OAK_PLANKS) == 0 && count(h, Items.STICK) == 2,
					"axe " + axe + " planks " + count(h, Items.OAK_PLANKS) + " sticks " + count(h, Items.STICK) + " units " + units);
		}).thenExecute(() -> {
			var units = SmithRuntime.unitsOf(h.getLevel().getServer(), smith[0].getUUID());
			long crafts = units.stream().filter(u -> u.kind().equals("CRAFT")).count();
			h.assertValueEqual(1L, crafts, "one craft: " + units);
			long commit = units.stream().filter(u -> u.kind().equals("COMMIT")).mapToLong(SmithRuntime.Unit::tick).findFirst().orElse(-1);
			long finish = units.stream().filter(u -> u.kind().equals("CRAFT")).mapToLong(SmithRuntime.Unit::tick).findFirst().orElse(-1);
			log("wooden axe commit " + commit + " finish " + finish + " delta " + (finish - commit));
			h.assertTrue(finish - commit >= 600 - 20, "commit to finish was " + (finish - commit) + ", wanted >= 580");
			VillageTestSupport.done(h);
		});
	}

	// ---- T-L2-4b --------------------------------------------------------------------------------------

	/** A hired cook keeps two coal. A third coal is enough for eight ingots and no more. */
	@GameTest(environment = "villageheart-gametest:smith_cook_fuel", structure = YARD, skyAccess = true, maxTicks = 8000)
	public void smithLeavesCookFuel(GameTestHelper h) {
		Villager[] smith = new Villager[1];
		scene(h).thenExecute(() -> SmithTestSupport.addKitchen(h)).thenWaitUntil(() ->
				BuildingTestSupport.assertStatus(h, SmithTestSupport.kitchenMarker(), dev.villageheart.block.MarkerStatus.VALID)
		).thenExecute(() -> {
			putStore(h, Items.RAW_IRON, 16);
			putStore(h, Items.COAL, 2);
			setDayTime(h, 1000);
			SmithTestSupport.hireCook(h);
			smith[0] = hire(h);
			log("cook fuel: two coal reserved");
		}).thenWaitUntil(() -> {
			h.assertTrue(SmithRuntime.hint(h.getLevel().getServer(), smith[0].getUUID()) == SmithDecision.Hint.NEEDS_FUEL,
					"hint " + SmithRuntime.hint(h.getLevel().getServer(), smith[0].getUUID()));
			h.assertValueEqual(2L, count(h, Items.COAL), "the cook's coal");
			h.assertValueEqual(0L, count(h, Items.IRON_INGOT), "no ingots while the reserve holds");
		}).thenExecute(() -> putStore(h, Items.COAL, 1)).thenWaitUntil(() ->
				h.assertValueEqual(8L, count(h, Items.IRON_INGOT), "eight ingots from the one spare coal")
		).thenExecute(() -> {
			h.assertValueEqual(2L, count(h, Items.COAL), "two coal still held for the cook");
			h.assertValueEqual(8L, count(h, Items.RAW_IRON), "the rest of the ore waits");
			log("cook fuel left 2 coal, smelted 8");
			VillageTestSupport.done(h);
		});
	}

	// ---- T-L2-5 ---------------------------------------------------------------------------------------

	/** A miner with an empty chest gets one stone pickaxe, made slowly, and nothing wooden. */
	@GameTest(environment = "villageheart-gametest:smith_stone_pick", structure = YARD, skyAccess = true, maxTicks = 6000)
	public void minerWithoutPickaxeGetsStone(GameTestHelper h) {
		Villager[] smith = new Villager[1];
		Villager[] miner = new Villager[1];
		long[] t0 = {0};
		scene(h).thenExecute(() -> SmithTestSupport.addMine(h)).thenWaitUntil(() -> {
			BuildingTestSupport.assertStatus(h, SmithTestSupport.mineMarker(), dev.villageheart.block.MarkerStatus.VALID);
			BuildingTestSupport.assertStatus(h, MineTestSupport.shaftMarker(0, SmithTestSupport.SHAFT), dev.villageheart.block.MarkerStatus.VALID);
		}).thenExecute(() -> {
			putStore(h, Items.OAK_PLANKS, 4);
			putStore(h, Items.STICK, 2);
			putStore(h, Items.COBBLESTONE, 3);
			setDayTime(h, 1000);
			t0[0] = h.getTick();
			miner[0] = SmithTestSupport.hireMiner(h);
			smith[0] = hire(h);
			log("stone pick bound " + (smithBound(1, 1200, walkBlocks()) + 400));
		}).thenWaitUntil(() -> {
			boolean pick = SmithTestSupport.chestHas(h, SmithTestSupport.mineChest(), Items.STONE_PICKAXE);
			h.assertTrue(pick, "stone pickaxe in the mine chest");
			h.assertTrue(WorkerStatuses.get(miner[0].getUUID()) != WorkerStatus.NEEDS_TOOL, "miner still needs a pickaxe");
		}).thenExecute(() -> {
			var units = SmithRuntime.unitsOf(h.getLevel().getServer(), smith[0].getUUID());
			long crafts = units.stream().filter(u -> u.kind().equals("CRAFT")).count();
			h.assertValueEqual(1L, crafts, "one craft: " + units);
			h.assertTrue(units.stream().anyMatch(u -> u.kind().equals("CRAFT") && u.id().equals("minecraft:stone_pickaxe")), "the craft is a stone pickaxe");
			h.assertTrue(units.stream().noneMatch(u -> u.id().equals("minecraft:wooden_pickaxe")), "no wooden pickaxe");
			long commit = units.stream().filter(u -> u.kind().equals("COMMIT")).mapToLong(SmithRuntime.Unit::tick).findFirst().orElse(-1);
			long finish = units.stream().filter(u -> u.kind().equals("CRAFT")).mapToLong(SmithRuntime.Unit::tick).findFirst().orElse(-1);
			log("stone pick commit " + commit + " finish " + finish + " delta " + (finish - commit) + " took " + (h.getTick() - t0[0]));
			h.assertTrue(finish - commit >= 1200 - 20, "commit to finish was " + (finish - commit));
			h.assertValueEqual(4L, count(h, Items.OAK_PLANKS), "planks were not used");
			h.assertValueEqual(0L, count(h, Items.COBBLESTONE), "cobble");
			h.assertValueEqual(0L, count(h, Items.STICK), "sticks");
			VillageTestSupport.done(h);
		});
	}

	// ---- T-L2-5b --------------------------------------------------------------------------------------

	/**
	 * A miner that really hauls, with a fresh stone pickaxe and iron in Storage. The smith does not upgrade a working tool.
	 * The worn line (20 remaining) is 12 hauls away, so a day that hauls at most 11 never reaches it. The shaft is in the deep band,
	 * so hauls bring raw iron and coal and the smith smelts them: the placed 30 ingots are not spent, and the count may rise.
	 */
	@GameTest(environment = "villageheart-gametest:smith_working_pick", structure = YARD, skyAccess = true, maxTicks = 10600)
	public void workingPickaxeNotReplaced(GameTestHelper h) {
		Villager[] smith = new Villager[1];
		long[] hauls0 = {0};
		long[] delivered0 = {0};
		scene(h).thenExecute(() -> SmithTestSupport.addMine(h)).thenWaitUntil(() -> {
			BuildingTestSupport.assertStatus(h, SmithTestSupport.mineMarker(), dev.villageheart.block.MarkerStatus.VALID);
			BuildingTestSupport.assertStatus(h, MineTestSupport.shaftMarker(0, SmithTestSupport.SHAFT), dev.villageheart.block.MarkerStatus.VALID);
		}).thenExecute(() -> {
			var chest = (net.minecraft.world.Container) h.getLevel().getBlockEntity(h.absolutePos(SmithTestSupport.mineChest()));
			chest.setItem(0, new ItemStack(Items.STONE_PICKAXE));
			putStore(h, Items.IRON_INGOT, 30);
			putStore(h, Items.STICK, 4);
			putStore(h, Items.COBBLESTONE, 3);
			setDayTime(h, 1000);
			SmithTestSupport.hireMiner(h);
			smith[0] = hire(h);
			hauls0[0] = WorkStats.get("miner", "hauls");
			delivered0[0] = WorkStats.get("smith", "delivered");
			log("working pickaxe, day 1000");
		}).thenExecuteFor(10000, () -> {
			var units = SmithRuntime.unitsOf(h.getLevel().getServer(), smith[0].getUUID());
			boolean craft = units.stream().anyMatch(u -> u.kind().equals("COMMIT") || u.kind().equals("CRAFT"));
			boolean delivered = WorkStats.get("smith", "delivered") > delivered0[0];
			if (craft || delivered) h.assertTrue(pickWorn(h), "a craft or delivery while the stone pickaxe was not worn");
			h.assertTrue(count(h, Items.IRON_INGOT) >= 30, "the placed iron was spent: " + count(h, Items.IRON_INGOT));
			h.assertValueEqual(4L, count(h, Items.STICK), "sticks");
			h.assertValueEqual(3L, count(h, Items.COBBLESTONE), "cobble");
		}).thenExecute(() -> {
			long hauls = WorkStats.get("miner", "hauls") - hauls0[0];
			long iron = count(h, Items.IRON_INGOT);
			log("hauls " + hauls + " iron " + iron + " day " + (h.getLevel().getDayTime() % 24000) + " hint " + SmithRuntime.hint(h.getLevel().getServer(), smith[0].getUUID()));
			h.assertTrue(hauls >= 1 && hauls <= 11, "hauls " + hauls);
			var units = SmithRuntime.unitsOf(h.getLevel().getServer(), smith[0].getUUID());
			h.assertTrue(units.stream().noneMatch(u -> u.kind().equals("CRAFT") || u.kind().equals("COMMIT")), "a craft happened: " + units);
			h.assertTrue(iron >= 30, "iron " + iron);
			h.assertValueEqual(4L, count(h, Items.STICK), "sticks");
			h.assertValueEqual(3L, count(h, Items.COBBLESTONE), "cobble");
			h.assertTrue(SmithRuntime.hint(h.getLevel().getServer(), smith[0].getUUID()) == SmithDecision.Hint.NO_WORK, "hint");
			h.assertTrue(SmithTestSupport.chestHas(h, SmithTestSupport.mineChest(), Items.STONE_PICKAXE), "the stone pickaxe is gone");
			VillageTestSupport.done(h);
		});
	}

	// ---- T-L2-5c --------------------------------------------------------------------------------------

	/**
	 * A worn tool is replaced by the best tier the village can afford. A worn iron axe is not swapped for a stone one while the lease
	 * still hands out the iron axe. Each part resets the camp chest, Storage and the clock.
	 */
	@GameTest(environment = "villageheart-gametest:smith_worn_axe", structure = YARD, skyAccess = true, maxTicks = 20000)
	public void wornToolReplacedWithBestAffordable(GameTestHelper h) {
		Villager[] jack = new Villager[1];
		int[] stoneWorn = {0};
		int[] ironWorn = {0};
		scene(h).thenExecute(() -> SmithTestSupport.addLumberCamp(h)).thenWaitUntil(() ->
				BuildingTestSupport.assertStatus(h, SmithTestSupport.campMarker(), dev.villageheart.block.MarkerStatus.VALID)
		).thenExecute(() -> {
			stoneWorn[0] = new ItemStack(Items.STONE_AXE).getMaxDamage() - 10;
			ironWorn[0] = new ItemStack(Items.IRON_AXE).getMaxDamage() - 10;
			jack[0] = SmithTestSupport.hireLumberjack(h);
			hire(h);
			resetCamp(h);
			putCamp(h, Items.STONE_AXE, stoneWorn[0]);
			putStore(h, Items.IRON_INGOT, 3);
			putStore(h, Items.COBBLESTONE, 3);
			putStore(h, Items.STICK, 2);
			setDayTime(h, 1000);
			log("worn stone axe, iron available");
		}).thenWaitUntil(() -> {
			boolean ironAxe = toolDamage(h, SmithTestSupport.campChest(), Items.IRON_AXE, 0);
			boolean moved = toolDamage(h, SmithTestSupport.smithyChest(), Items.STONE_AXE, stoneWorn[0]);
			h.assertTrue(ironAxe && count(h, Items.IRON_INGOT) == 0 && count(h, Items.COBBLESTONE) == 3 && moved,
					"ironAxe " + ironAxe + " iron " + count(h, Items.IRON_INGOT) + " cobble " + count(h, Items.COBBLESTONE) + " moved " + moved);
		}).thenExecute(() -> {
			log("iron axe delivered, worn stone axe moved to the smithy");
			resetCamp(h);
			putCamp(h, Items.STONE_AXE, stoneWorn[0]);
			putStore(h, Items.COBBLESTONE, 3);
			putStore(h, Items.STICK, 2);
			setDayTime(h, 1000);
			log("worn stone axe, only cobble");
		}).thenWaitUntil(() -> {
			boolean fresh = toolDamage(h, SmithTestSupport.campChest(), Items.STONE_AXE, 0);
			h.assertTrue(fresh && count(h, Items.COBBLESTONE) == 0 && count(h, Items.STICK) == 0,
					"fresh stone " + fresh + " cobble " + count(h, Items.COBBLESTONE) + " sticks " + count(h, Items.STICK));
		}).thenExecute(() -> {
			log("stone axe delivered");
			resetCamp(h);
			putCamp(h, Items.IRON_AXE, ironWorn[0]);
			putStore(h, Items.COBBLESTONE, 3);
			putStore(h, Items.STICK, 2);
			setDayTime(h, 1000);
			log("worn iron axe, only cobble");
		}).thenWaitUntil(() -> {
			boolean stone = toolDamage(h, SmithTestSupport.campChest(), Items.STONE_AXE, 0);
			var lease = CampTools.lease(h.getLevel().getServer(), jack[0], ToolKind.AXE, null);
			boolean iron = lease instanceof CampTools.Held held && held.lease().stack().is(Items.IRON_AXE);
			h.assertTrue(stone && iron && count(h, Items.COBBLESTONE) == 0 && count(h, Items.STICK) == 0,
					"stone " + stone + " lease " + lease + " cobble " + count(h, Items.COBBLESTONE) + " sticks " + count(h, Items.STICK));
		}).thenExecute(() -> {
			log("stone axe delivered, lease still the iron axe");
			VillageTestSupport.done(h);
		});
	}

	// ---- T-L2-5d --------------------------------------------------------------------------------------

	/**
	 * A pickaxe put in the Mine chest cancels the order. Pending: while the cobble and sticks are still in the smith's hands.
	 * Committed: once the stone pickaxe craft has 600 ticks behind it. The refund is the same 3 cobble and 2 sticks.
	 */
	@GameTest(environment = "villageheart-gametest:smith_cancel_order", structure = YARD, skyAccess = true, maxTicks = 12000)
	public void playerToolCancelsOrder(GameTestHelper h) {
		Villager[] smith = new Villager[1];
		Villager[] miner = new Villager[1];
		int pending = 2 * Balance.SMITH_CANCEL_CHECK_TICKS + 40;
		int committed = 2 * Balance.SMITH_CANCEL_CHECK_TICKS + 20;
		scene(h).thenExecute(() -> SmithTestSupport.addMine(h)).thenWaitUntil(() -> {
			BuildingTestSupport.assertStatus(h, SmithTestSupport.mineMarker(), dev.villageheart.block.MarkerStatus.VALID);
			BuildingTestSupport.assertStatus(h, MineTestSupport.shaftMarker(0, SmithTestSupport.SHAFT), dev.villageheart.block.MarkerStatus.VALID);
		}).thenExecute(() -> {
			putStore(h, Items.COBBLESTONE, 3);
			putStore(h, Items.STICK, 2);
			setDayTime(h, 1000);
			miner[0] = SmithTestSupport.hireMiner(h);
			smith[0] = hire(h);
			log("cancel: waiting for the fetch");
		}).thenWaitUntil(() -> {
			int cobble = smith[0].getInventory().countItem(Items.COBBLESTONE);
			int sticks = smith[0].getInventory().countItem(Items.STICK);
			boolean committedCraft = SmithRuntime.unitsOf(h.getLevel().getServer(), smith[0].getUUID()).stream().anyMatch(u -> u.kind().equals("COMMIT"));
			h.assertTrue(cobble >= 3 && sticks >= 2 && !committedCraft && craftProgress(h) < 0,
					"carried cobble " + cobble + " sticks " + sticks + " committed " + committedCraft + " progress " + craftProgress(h));
		}).thenExecute(() -> {
			var chest = (net.minecraft.world.Container) h.getLevel().getBlockEntity(h.absolutePos(SmithTestSupport.mineChest()));
			chest.setItem(0, new ItemStack(Items.IRON_PICKAXE));
			log("iron pickaxe placed while the smith is carrying the inputs");
		}).thenExecuteFor(pending, () -> { }).thenExecute(() -> {
			var units = SmithRuntime.unitsOf(h.getLevel().getServer(), smith[0].getUUID());
			h.assertTrue(units.stream().noneMatch(u -> u.kind().equals("COMMIT") || u.kind().equals("CRAFT")), "a craft was committed: " + units);
			h.assertValueEqual(3L, count(h, Items.COBBLESTONE), "cobble back");
			h.assertValueEqual(2L, count(h, Items.STICK), "sticks back");
			log("pending order cancelled");
			var chest = (net.minecraft.world.Container) h.getLevel().getBlockEntity(h.absolutePos(SmithTestSupport.mineChest()));
			chest.setItem(0, ItemStack.EMPTY);
			setDayTime(h, 1000);
		}).thenWaitUntil(() -> h.assertTrue(craftProgress(h) >= 600, "progress " + craftProgress(h))
		).thenExecute(() -> {
			var chest = (net.minecraft.world.Container) h.getLevel().getBlockEntity(h.absolutePos(SmithTestSupport.mineChest()));
			chest.setItem(0, new ItemStack(Items.IRON_PICKAXE));
			log("iron pickaxe placed at progress " + craftProgress(h));
		}).thenExecuteFor(committed, () -> { }).thenExecute(() -> {
			h.assertTrue(craftProgress(h) < 0, "the saved craft is still there, progress " + craftProgress(h));
			h.assertValueEqual(3L, count(h, Items.COBBLESTONE), "refunded cobble");
			h.assertValueEqual(2L, count(h, Items.STICK), "refunded sticks");
			log("committed craft refunded");
		}).thenExecuteFor(2000, () -> h.assertFalse(SmithTestSupport.chestHas(h, SmithTestSupport.mineChest(), Items.STONE_PICKAXE), "a stone pickaxe was made")
		).thenExecute(() -> {
			var lease = CampTools.lease(h.getLevel().getServer(), miner[0], ToolKind.PICKAXE, null);
			h.assertTrue(lease instanceof CampTools.Held held && held.lease().stack().is(Items.IRON_PICKAXE), "lease " + lease);
			var units = SmithRuntime.unitsOf(h.getLevel().getServer(), smith[0].getUUID());
			h.assertTrue(units.stream().noneMatch(u -> u.kind().equals("CRAFT") && u.id().equals("minecraft:stone_pickaxe")), "stone pickaxe crafted: " + units);
			log("miner leases the iron pickaxe");
			VillageTestSupport.done(h);
		});
	}

	// ---- T-L2-5h --------------------------------------------------------------------------------------

	/**
	 * A saved iron pickaxe pauses at dusk, survives a runtime restart, and finishes the next morning. Breaking the Smithy
	 * refunds the committed ingots and sticks once.
	 */
	@GameTest(environment = "villageheart-gametest:smith_craft_night", structure = YARD, skyAccess = true, maxTicks = 4000)
	public void craftPausesOvernightAndSurvivesRestart(GameTestHelper h) {
		Villager[] smith = new Villager[1];
		int[] dusk = {-1};
		int[] held = {0};
		long[] finishDay = {-1};
		scene(h).thenExecute(() -> SmithTestSupport.addMine(h)).thenWaitUntil(() -> {
			BuildingTestSupport.assertStatus(h, SmithTestSupport.mineMarker(), dev.villageheart.block.MarkerStatus.VALID);
			BuildingTestSupport.assertStatus(h, MineTestSupport.shaftMarker(0, SmithTestSupport.SHAFT), dev.villageheart.block.MarkerStatus.VALID);
		}).thenExecute(() -> {
			GearOrders.reset();
			SmithTestSupport.hireMiner(h);
			smith[0] = hire(h);
			seedPick(h, 1200);
			setDayTime(h, 10400);
			log("iron pickaxe seeded at 1200, day 10400");
		}).thenExecuteFor(1000, () -> {
			long day = Math.floorMod(h.getLevel().getDayTime(), 24000L);
			int progress = craftProgress(h);
			if (dusk[0] < 0 && day >= 11100 && day < 12000) {
				dusk[0] = progress;
				h.assertTrue(Math.abs(progress - 1800) <= 20, "dusk progress " + progress);
				log("dusk progress " + progress);
			}
			if (dusk[0] >= 0 && held[0] < 200) {
				held[0]++;
				h.assertValueEqual(dusk[0], progress, "progress moved overnight");
			}
		}).thenExecute(() -> {
			h.assertTrue(dusk[0] >= 0, "dusk was not sampled");
			h.assertValueEqual(200, held[0], "overnight samples");
			var server = h.getLevel().getServer();
			SmithRuntime.resetForTests();
			GearOrders.reset();
			GearOrders.rebuild(server);
			setDayTime(h, 980);
			h.assertValueEqual(1, mineOrders(h), "open mine orders after restart");
			log("restarted at day 980 with one mine order, progress " + craftProgress(h));
		}).thenWaitUntil(() -> {
			long day = Math.floorMod(h.getLevel().getDayTime(), 24000L);
			if (craftProgress(h) >= 0) h.assertValueEqual(1, mineOrders(h), "a second mine order opened");
			if (finishDay[0] < 0 && SmithRuntime.unitsOf(h.getLevel().getServer(), smith[0].getUUID()).stream()
					.anyMatch(u -> u.kind().equals("CRAFT") && u.id().equals("minecraft:iron_pickaxe"))) finishDay[0] = day;
			h.assertTrue(SmithTestSupport.chestHas(h, SmithTestSupport.mineChest(), Items.IRON_PICKAXE),
					"iron pickaxe not delivered, day " + day + " progress " + craftProgress(h));
		}).thenExecute(() -> {
			int expect = 1000 + (Balance.SMITH_TOOL_IRON - dusk[0]);
			h.assertTrue(finishDay[0] > 0 && Math.abs(finishDay[0] - expect) <= 80,
					"finished on day " + finishDay[0] + ", expected about " + expect);
			log("iron pickaxe delivered, finished day " + finishDay[0]);
			seedPick(h, 100);
			h.setBlock(SmithTestSupport.smithyMarker(), Blocks.AIR);
			log("smithy marker broken");
		}).thenExecuteFor(200, () -> { }).thenExecute(() -> {
			h.assertValueEqual(3L, count(h, Items.IRON_INGOT), "refunded ingots");
			h.assertValueEqual(2L, count(h, Items.STICK), "refunded sticks");
			h.assertTrue(smithCraftsEmpty(h), "the craft is still saved");
			log("refunded once");
		}).thenExecuteFor(400, () -> {
			h.assertValueEqual(3L, count(h, Items.IRON_INGOT), "ingots doubled");
			h.assertValueEqual(2L, count(h, Items.STICK), "sticks doubled");
			h.assertTrue(smithCraftsEmpty(h), "a craft came back");
		}).thenExecute(() -> {
			log("refund stayed put");
			VillageTestSupport.done(h);
		});
	}

	// ---- T-L2-5i --------------------------------------------------------------------------------------

	/**
	 * Two smiths at two furnaces share one camp: two iron axes, one from each. Fired mid-craft, the other smith adopts the
	 * saved craft after finishing its own, and both axes still arrive.
	 */
	@GameTest(environment = "villageheart-gametest:smith_two_smiths", structure = YARD, skyAccess = true, maxTicks = 20000)
	public void twoLumberjacksTwoSmithsShareACamp(GameTestHelper h) {
		Villager[] smiths = new Villager[2];
		Villager[] jacks = new Villager[2];
		BlockPos secondFurnace = BuildingTestSupport.inside(SmithTestSupport.SMITHY, 3, 0, 1);
		BlockPos secondBlock = BuildingTestSupport.inside(SmithTestSupport.CAMP, 3, 0, 1);
		scene(h).thenExecute(() -> {
			h.setBlock(secondFurnace, Blocks.BLAST_FURNACE);
			SmithTestSupport.addLumberCamp(h);
			h.setBlock(secondBlock, JobBlocks.BLOCKS.get(JobBlockType.CHOPPING_BLOCK));
		}).thenWaitUntil(() -> {
			BuildingTestSupport.assertStatus(h, SmithTestSupport.campMarker(), dev.villageheart.block.MarkerStatus.VALID);
			h.assertValueEqual(2, contents(h, SmithTestSupport.smithyMarker(), BuildingType.SMITHY, BuildingRequirements.BLAST_FURNACE), "furnaces");
			h.assertValueEqual(2, contents(h, SmithTestSupport.campMarker(), BuildingType.LUMBER_CAMP, BuildingRequirements.CHOPPING_BLOCK), "chopping blocks");
		}).thenExecute(() -> {
			GearOrders.reset();
			putStore(h, Items.IRON_INGOT, 6);
			putStore(h, Items.STICK, 4);
			setDayTime(h, 1000);
			jacks[0] = SmithTestSupport.hireLumberjack(h);
			jacks[1] = SmithTestSupport.hireLumberjack(h);
			smiths[0] = hireEitherFurnace(h);
			smiths[1] = hireEitherFurnace(h);
			log("two smiths, two lumberjacks");
		}).thenWaitUntil(() -> h.assertValueEqual(2, chestCount(h, SmithTestSupport.campChest(), Items.IRON_AXE), "axes in the camp")
		).thenExecute(() -> {
			h.assertValueEqual(1L, craftUnits(h, smiths[0]), "the first smith's axe");
			h.assertValueEqual(1L, craftUnits(h, smiths[1]), "the second smith's axe");
			h.assertTrue(leasesAxe(h, jacks[0]) && leasesAxe(h, jacks[1]), "both lumberjacks lease an axe");
			log("two axes, one from each smith, day " + Math.floorMod(h.getLevel().getDayTime(), 24000L));
			GearOrders.reset();
			resetCamp(h);
			putStore(h, Items.IRON_INGOT, 6);
			putStore(h, Items.STICK, 4);
			// The first pair used the afternoon. The next morning is a full work day; stepping the clock backward
			// leaves the smiths out of work.
			long now = h.getLevel().getDayTime();
			long day = Math.floorMod(now, 24000L);
			setDayTime(h, now - day + 24000L + 1000L);
		}).thenWaitUntil(() -> {
			long dayNow = Math.floorMod(h.getLevel().getDayTime(), 24000L);
			h.assertTrue(craftAt(h, smiths[0]) && craftAt(h, smiths[1]), part2(h, smiths, dayNow));
		}).thenExecute(() -> {
			h.assertTrue(Workers.fire(h.getLevel(), smiths[1]), "the second smith is fired");
			log("second smith fired mid-craft");
		}).thenWaitUntil(() -> h.assertValueEqual(2, chestCount(h, SmithTestSupport.campChest(), Items.IRON_AXE), "axes after the adoption")
		).thenExecute(() -> {
			h.assertTrue(leasesAxe(h, jacks[0]) && leasesAxe(h, jacks[1]), "both still lease an axe");
			log("adopted craft delivered, two axes");
			VillageTestSupport.done(h);
		});
	}

	private static int craftProgress(GameTestHelper h) {
		HeartLocation furnace = BuildingTestSupport.loc(h, SmithTestSupport.furnace());
		return VillageClaims.current(h.getLevel().getServer()).flatMap(v -> v.smithCraft(furnace)).map(c -> c.craft().progress()).orElse(-1);
	}

	private static void resetCamp(GameTestHelper h) {
		for (BlockPos rel : new BlockPos[] {SmithTestSupport.campChest(), SmithTestSupport.storeChest1(), SmithTestSupport.storeChest2(), SmithTestSupport.smithyChest()}) {
			var box = (net.minecraft.world.Container) h.getLevel().getBlockEntity(h.absolutePos(rel));
			for (int i = 0; i < box.getContainerSize(); i++) box.setItem(i, ItemStack.EMPTY);
			box.setChanged();
		}
	}

	private static void putCamp(GameTestHelper h, net.minecraft.world.item.Item item, int damage) {
		ItemStack stack = new ItemStack(item);
		stack.setDamageValue(damage);
		var box = (net.minecraft.world.Container) h.getLevel().getBlockEntity(h.absolutePos(SmithTestSupport.campChest()));
		box.setItem(0, stack);
		box.setChanged();
	}

	private static boolean toolDamage(GameTestHelper h, BlockPos rel, net.minecraft.world.item.Item item, int damage) {
		var box = (net.minecraft.world.Container) h.getLevel().getBlockEntity(h.absolutePos(rel));
		for (int i = 0; i < box.getContainerSize(); i++) {
			ItemStack s = box.getItem(i);
			if (s.is(item) && s.getDamageValue() == damage) return true;
		}
		return false;
	}

	private static boolean pickWorn(GameTestHelper h) {
		var chest = (net.minecraft.world.Container) h.getLevel().getBlockEntity(h.absolutePos(SmithTestSupport.mineChest()));
		for (int i = 0; i < chest.getContainerSize(); i++) {
			ItemStack s = chest.getItem(i);
			if (s.is(Items.STONE_PICKAXE)) return ToolStacks.worn(s, JobType.MINER);
		}
		return false;
	}

	// ---- T-L2-5g --------------------------------------------------------------------------------------

	/** An iron axe already in Storage is carried to the camp. The smith does not make another. */
	@GameTest(environment = "villageheart-gametest:smith_stored_axe", structure = YARD, skyAccess = true, maxTicks = 4000)
	public void storedToolDeliveredNotCrafted(GameTestHelper h) {
		Villager[] smith = new Villager[1];
		scene(h).thenExecute(() -> SmithTestSupport.addLumberCamp(h)).thenWaitUntil(() ->
				BuildingTestSupport.assertStatus(h, SmithTestSupport.campMarker(), dev.villageheart.block.MarkerStatus.VALID)
		).thenExecute(() -> {
			putStore(h, Items.IRON_AXE, 1);
			putStore(h, Items.IRON_INGOT, 3);
			putStore(h, Items.STICK, 2);
			setDayTime(h, 1000);
			SmithTestSupport.hireLumberjack(h);
			smith[0] = hire(h);
		}).thenWaitUntil(() -> h.assertTrue(SmithTestSupport.chestHas(h, SmithTestSupport.campChest(), Items.IRON_AXE), "the stored axe is in the camp")
		).thenExecute(() -> {
			var units = SmithRuntime.unitsOf(h.getLevel().getServer(), smith[0].getUUID());
			h.assertTrue(units.stream().noneMatch(u -> u.kind().equals("CRAFT")), "no craft: " + units);
			h.assertValueEqual(3L, count(h, Items.IRON_INGOT), "ingots were not spent");
			h.assertValueEqual(0L, count(h, Items.IRON_AXE), "the axe left Storage");
			VillageTestSupport.done(h);
		});
	}

	// ---- T-L2-6 ---------------------------------------------------------------------------------------

	/** A damaged chestplate in the Smithy chest is repaired there and never picked up. */
	@GameTest(environment = "villageheart-gametest:smith_repair_chest", structure = YARD, skyAccess = true, maxTicks = 4000)
	public void repairsInSmithyChest(GameTestHelper h) {
		boolean[] left = {false};
		scene(h).thenExecute(() -> {
			ItemStack plate = new ItemStack(Items.IRON_CHESTPLATE);
			plate.setDamageValue(200);
			var chest = StockpileTestSupport.container(h, SmithTestSupport.smithyChest());
			chest.setItem(0, plate);
			putStore(h, Items.IRON_INGOT, 5);
			setDayTime(h, 1000);
			hire(h);
		}).thenWaitUntil(() -> {
			ItemStack s = StockpileTestSupport.container(h, SmithTestSupport.smithyChest()).getItem(0);
			if (s.isEmpty() || !s.is(Items.IRON_CHESTPLATE)) left[0] = true;
			h.assertTrue(s.is(Items.IRON_CHESTPLATE) && s.getDamageValue() == 20, "damage " + (s.isEmpty() ? "empty" : s.getDamageValue()));
		}).thenExecute(() -> {
			h.assertFalse(left[0], "the chestplate left the Smithy chest");
			h.assertValueEqual(2L, count(h, Items.IRON_INGOT), "three ingots spent");
			log("chestplate repaired to 20");
			VillageTestSupport.done(h);
		});
	}

	// ---- T-L2-5f --------------------------------------------------------------------------------------

	/** One guard missing a sword and a chestplate gets the sword first, then the chestplate. Both are slow. */
	@GameTest(environment = "villageheart-gametest:smith_guard_gear", structure = YARD, skyAccess = true, maxTicks = 12000)
	public void craftsGuardGear(GameTestHelper h) {
		Villager[] smith = new Villager[1];
		Villager[] guard = new Villager[1];
		scene(h).thenExecute(() -> SmithTestSupport.addBarracks(h)).thenWaitUntil(() ->
				BuildingTestSupport.assertStatus(h, SmithTestSupport.barracksMarker(), dev.villageheart.block.MarkerStatus.VALID)
		).thenExecute(() -> {
			putStore(h, Items.IRON_INGOT, 10);
			putStore(h, Items.STICK, 1);
			setDayTime(h, 1000);
			guard[0] = SmithTestSupport.hireGuard(h);
			guard[0].setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			guard[0].setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
			guard[0].setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
			guard[0].setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
			smith[0] = hire(h);
			log("guard gear: sword then chestplate");
		}).thenWaitUntil(() -> {
			int sword = gearSeen(h, guard[0], Items.IRON_SWORD);
			int chest = gearSeen(h, guard[0], Items.IRON_CHESTPLATE);
			h.assertTrue(sword >= 1 && chest >= 1, "sword " + sword + " chestplate " + chest);
		}).thenExecute(() -> {
			var units = SmithRuntime.unitsOf(h.getLevel().getServer(), smith[0].getUUID());
			var crafts = units.stream().filter(u -> u.kind().equals("CRAFT")).toList();
			h.assertTrue(crafts.size() >= 2, "two crafts: " + units);
			h.assertTrue(crafts.get(0).id().equals("minecraft:iron_sword"), "sword first, got " + crafts.get(0).id());
			h.assertTrue(crafts.get(1).id().equals("minecraft:iron_chestplate"), "chestplate second, got " + crafts.get(1).id());
			for (String id : new String[] {"minecraft:iron_sword", "minecraft:iron_chestplate"}) {
				long commit = units.stream().filter(u -> u.kind().equals("COMMIT") && u.id().equals(id)).mapToLong(SmithRuntime.Unit::tick).findFirst().orElse(-1);
				long finish = units.stream().filter(u -> u.kind().equals("CRAFT") && u.id().equals(id)).mapToLong(SmithRuntime.Unit::tick).findFirst().orElse(-1);
				log(id + " commit " + commit + " finish " + finish + " delta " + (finish - commit));
				h.assertTrue(finish - commit >= 2400 - 20, id + " commit to finish was " + (finish - commit));
			}
			h.assertValueEqual(0L, count(h, Items.IRON_INGOT), "ingots");
			h.assertValueEqual(0L, count(h, Items.STICK), "sticks");
			h.assertTrue(guard[0].isAlive(), "the guard is still here");
			VillageTestSupport.done(h);
		});
	}

	// ---- T-L2-1 ---------------------------------------------------------------------------------------

	/** Thirty-six ingots and a carved pumpkin become one player-built iron golem, and the iron is accounted for. */
	@GameTest(environment = "villageheart-gametest:smith_golem_build", structure = YARD, skyAccess = true, maxTicks = 24400)
	public void golemFromThirtySixIngots(GameTestHelper h) {
		Villager[] smith = new Villager[1];
		boolean[] sawBuilding = {false};
		boolean[] sawLit = {false};
		long[] startDay = {0};
		scene(h).thenExecute(() -> {
			SmithTestSupport.sweepGolems(h.getLevel(), h.absolutePos(BlockPos.ZERO), Balance.CLAIM_RADIUS + 8);
			SmithTestSupport.pen(h, 4);
			putStore(h, Items.IRON_INGOT, 50);
			putStore(h, Items.CARVED_PUMPKIN, 2);
			setDayTime(h, 1000);
			startDay[0] = h.getLevel().getDayTime();
			smith[0] = hire(h);
			h.assertValueEqual(0, SmithTestSupport.golemsNear(h.getLevel(), h.absolutePos(BlockPos.ZERO), 80), "no golem yet");
			log("golem build start day " + startDay[0]);
		}).thenWaitUntil(() -> {
			if (SmithRuntime.activity(h.getLevel().getServer(), smith[0].getUUID()) == dev.villageheart.core.heart.ActivityKind.BUILDING_GOLEM) sawBuilding[0] = true;
			if (lit(h)) sawLit[0] = true;
			h.assertValueEqual(1, SmithTestSupport.golemsNear(h.getLevel(), h.absolutePos(BlockPos.ZERO), 80), "one golem");
		}).thenExecute(() -> {
			long spawnDay = h.getLevel().getDayTime();
			log("golem spawn day " + spawnDay + " delta " + (spawnDay - startDay[0]) + " building " + sawBuilding[0] + " lit " + sawLit[0]);
			h.assertTrue(spawnDay - startDay[0] <= 24_000, "spawn took " + (spawnDay - startDay[0]) + " day ticks");
			h.assertTrue(sawBuilding[0], "the smith was building a golem");
			h.assertTrue(sawLit[0], "the furnace was lit during the build");
			IronGolem g = h.getLevel().getEntities(EntityType.IRON_GOLEM, new net.minecraft.world.phys.AABB(h.absolutePos(BlockPos.ZERO)).inflate(80),
					e -> true).get(0);
			h.assertTrue(g.isPlayerCreated(), "the golem is player-built");
			var scan = SmithyLayout.scan(h.getLevel().getServer(), BuildingTestSupport.loc(h, SmithTestSupport.smithyMarker()));
			h.assertTrue(scan.isPresent(), "the smithy scan");
			var spot = SmithyLayout.waitSpot(h.getLevel(), scan.get());
			h.assertTrue(spot.isPresent(), "a wait spot");
			h.assertTrue(g.blockPosition().distSqr(spot.get()) <= 8 * 8, "golem at " + g.blockPosition() + " spot " + spot.get());
			h.assertValueEqual(14, SmithTestSupport.ironTotal(h), "iron left");
			h.assertValueEqual(1, SmithTestSupport.worldCount(h, Items.CARVED_PUMPKIN), "one pumpkin left");
			h.assertTrue(VillageClaims.current(h.getLevel().getServer()).orElseThrow().golemBuilds().isEmpty(), "no build still saved");
		}).thenExecuteFor(400, () ->
				h.assertValueEqual(1, SmithTestSupport.golemsNear(h.getLevel(), h.absolutePos(BlockPos.ZERO), 80), "still one golem")
		).thenExecute(() -> {
			h.assertValueEqual(14, SmithTestSupport.ironTotal(h), "iron stayed 14");
			h.assertValueEqual(1, SmithTestSupport.worldCount(h, Items.CARVED_PUMPKIN), "pumpkin stayed 1");
			h.assertFalse(lit(h), "the furnace is dark after the build");
			VillageTestSupport.done(h);
		});
	}

	// ---- T-L2-8b --------------------------------------------------------------------------------------

	/** Killing the smith while it carries the golem's iron drops that iron. The reservation does not keep it. */
	@GameTest(environment = "villageheart-gametest:smith_death_drop", structure = YARD, skyAccess = true, maxTicks = 6000)
	public void smithDeathDropsCarried(GameTestHelper h) {
		Villager[] smith = new Villager[1];
		scene(h).thenExecute(() -> {
			SmithTestSupport.pen(h, 4);
			putStore(h, Items.IRON_INGOT, 36);
			putStore(h, Items.CARVED_PUMPKIN, 1);
			setDayTime(h, 1000);
			smith[0] = hire(h);
		}).thenWaitUntil(() -> {
			int iron = smith[0].getInventory().countItem(Items.IRON_INGOT);
			int pumpkin = smith[0].getInventory().countItem(Items.CARVED_PUMPKIN);
			int progress = VillageClaims.current(h.getLevel().getServer()).map(v -> v.golemProgress(BuildingTestSupport.loc(h, SmithTestSupport.smithyMarker()))).orElse(-1);
			h.assertTrue(iron >= 36 && pumpkin >= 1 && progress < 0, "carrying iron " + iron + " pumpkin " + pumpkin + " progress " + progress);
		}).thenExecute(() -> {
			smith[0].kill(h.getLevel());
		}).thenExecute(() -> {
			h.assertValueEqual(36, SmithTestSupport.ironTotal(h), "the ingots are still in the world");
			h.assertValueEqual(1, SmithTestSupport.worldCount(h, Items.CARVED_PUMPKIN), "the pumpkin is still in the world");
			h.assertValueEqual(0, Stockpiles.reservations(h.getLevel().getServer()).reservedBy(smith[0].getUUID(), StackKey.of(Items.IRON_INGOT)), "iron reservation");
			h.assertValueEqual(0, Stockpiles.reservations(h.getLevel().getServer()).reservedBy(smith[0].getUUID(), StackKey.of(Items.CARVED_PUMPKIN)), "pumpkin reservation");
			log("death dropped the carried golem materials");
			VillageTestSupport.done(h);
		});
	}

	// ---- T-L2-2 ---------------------------------------------------------------------------------------

	/** A golem at 40 health walks to the Smithy and is repaired with exactly three ingots. */
	@GameTest(environment = "villageheart-gametest:smith_golem_repair", structure = YARD, skyAccess = true, maxTicks = 4000)
	public void damagedGolemRepairedAtSmithy(GameTestHelper h) {
		IronGolem[] golem = new IronGolem[1];
		Villager[] smith = new Villager[1];
		BlockPos[] summoned = new BlockPos[1];
		boolean[] sawWaiting = {false};
		boolean[] healFar = {false};
		scene(h).thenExecute(() -> {
			SmithTestSupport.sweepGolems(h.getLevel(), h.absolutePos(BlockPos.ZERO), Balance.CLAIM_RADIUS + 8);
			putStore(h, Items.IRON_INGOT, 10);
			setDayTime(h, 2000);
			smith[0] = hire(h);
			var scan = SmithyLayout.scan(h.getLevel().getServer(), BuildingTestSupport.loc(h, SmithTestSupport.smithyMarker())).orElseThrow();
			BlockPos spot = SmithyLayout.waitSpot(h.getLevel(), scan).orElseThrow();
			BlockPos origin = h.absolutePos(BlockPos.ZERO);
			BlockPos at = spot.offset(16, 0, 0);
			IronGolem g = h.spawn(EntityType.IRON_GOLEM, new BlockPos(at.getX() - origin.getX(), at.getY() - origin.getY(), at.getZ() - origin.getZ()));
			g.setPlayerCreated(true);
			g.setHealth(40f);
			golem[0] = g;
			summoned[0] = at;
			log("golem repair summoned at " + at + " spot " + spot);
		}).thenWaitUntil(() -> {
			if (golem[0].blockPosition().distSqr(summoned[0]) >= 1 && golem[0].getHealth() <= 40.01f) {
				var scan = SmithyLayout.scan(h.getLevel().getServer(), BuildingTestSupport.loc(h, SmithTestSupport.smithyMarker())).orElseThrow();
				BlockPos spot = SmithyLayout.waitSpot(h.getLevel(), scan).orElseThrow();
				if (golem[0].position().distanceTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(spot)) <= 1.5) sawWaiting[0] = true;
			}
			if (golem[0].getHealth() > 40.01f && golem[0].blockPosition().distSqr(summoned[0]) < 10 * 10) healFar[0] = true;
			h.assertTrue(golem[0].getHealth() > 90f, "health " + golem[0].getHealth());
		}).thenExecute(() -> {
			long units = SmithRuntime.unitsOf(h.getLevel().getServer(), smith[0].getUUID()).stream().filter(u -> u.kind().equals("GOLEM_REPAIR")).count();
			h.assertValueEqual(3L, units, "three repair ingots");
			h.assertTrue(sawWaiting[0], "the golem waited at the spot before the first heal");
			h.assertFalse(healFar[0], "a heal happened near where the golem was summoned");
			h.assertValueEqual(7, SmithTestSupport.ironTotal(h), "three ingots consumed");
			h.assertTrue(golem[0].getHealth() > 90f, "health " + golem[0].getHealth());
			log("golem repaired, health " + golem[0].getHealth() + " repair units " + units);
			VillageTestSupport.done(h);
		});
	}

	// ---- T-L2-2c --------------------------------------------------------------------------------------

	/** Combat wins. While the golem is fighting, the repair goal is not running. The repair still happens after the zombie dies. */
	@GameTest(environment = "villageheart-gametest:smith_golem_fight", structure = YARD, skyAccess = true, maxTicks = 8000)
	public void golemFightsFirst(GameTestHelper h) {
		IronGolem[] golem = new IronGolem[1];
		Zombie[] zombie = new Zombie[1];
		Villager[] smith = new Villager[1];
		boolean[] sawTarget = {false};
		boolean[] repairWhileFighting = {false};
		scene(h).thenExecute(() -> {
			SmithTestSupport.pen(h, 2);
			SmithTestSupport.sweepGolems(h.getLevel(), h.absolutePos(BlockPos.ZERO), Balance.CLAIM_RADIUS + 8);
			putStore(h, Items.IRON_INGOT, 3);
			setDayTime(h, 2000);
			smith[0] = hire(h);
			sealNorth(h, true);
			var scan = SmithyLayout.scan(h.getLevel().getServer(), BuildingTestSupport.loc(h, SmithTestSupport.smithyMarker())).orElseThrow();
			BlockPos spot = SmithyLayout.waitSpot(h.getLevel(), scan).orElseThrow();
			BlockPos origin = h.absolutePos(BlockPos.ZERO);
			BlockPos at = spot.offset(8, 0, 0);
			golem[0] = summonHurt(h, at, 40f);
			BlockPos zAt = at.offset(6, 0, 0);
			zombie[0] = h.spawn(EntityType.ZOMBIE, new BlockPos(zAt.getX() - origin.getX(), zAt.getY() - origin.getY(), zAt.getZ() - origin.getZ()));
			zombie[0].setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
			log("fight golem " + at + " zombie " + zAt + " spot " + spot);
		}).thenExecuteFor(2000, () -> {
			if (golem[0].getTarget() != null) {
				sawTarget[0] = true;
				if (repairRunning(golem[0])) repairWhileFighting[0] = true;
			}
		}).thenExecute(() -> {
			h.assertTrue(sawTarget[0], "the golem never targeted the zombie");
			h.assertFalse(repairWhileFighting[0], "the repair goal ran while the golem had a target");
			h.assertFalse(zombie[0].isAlive(), "the zombie lived, golem health " + golem[0].getHealth());
			sealNorth(h, false);
			log("zombie down, repair goal stayed out of the fight");
		}).thenWaitUntil(() -> h.assertTrue(golem[0].isAlive() && golem[0].getHealth() > 90f, "health " + golem[0].getHealth())
		).thenExecute(() -> {
			long units = SmithRuntime.unitsOf(h.getLevel().getServer(), smith[0].getUUID()).stream().filter(u -> u.kind().equals("GOLEM_REPAIR")).count();
			h.assertValueEqual(3L, units, "three repair ingots after the fight");
			h.assertValueEqual(0, SmithTestSupport.ironTotal(h), "three ingots consumed");
			log("repair after the fight, health " + golem[0].getHealth());
			VillageTestSupport.done(h);
		});
	}

	/**
	 * Glass across the yard just north of the Smithy door, two high. The marker hangs at y=3 on that row, so the wall stops at y=2
	 * and the Smithy stays valid. The smith and the penned villagers are on the far side of it.
	 */
	private static void sealNorth(GameTestHelper h, boolean close) {
		for (int x = 0; x <= 31; x++) for (int y = 1; y <= 2; y++) {
			BlockPos p = new BlockPos(x, y, 3);
			if (close) h.setBlock(p, Blocks.GLASS);
			else if (h.getBlockState(p).is(Blocks.GLASS)) h.setBlock(p, Blocks.AIR);
		}
	}

	// ---- creeper --------------------------------------------------------------------------------------

	/** An iron golem inside the claim does not take a creeper as its target. */
	@GameTest(environment = "villageheart-gametest:smith_creeper", structure = YARD, skyAccess = true, maxTicks = 400)
	public void golemIgnoresCreeper(GameTestHelper h) {
		IronGolem[] golem = new IronGolem[1];
		Creeper[] creeper = new Creeper[1];
		scene(h).thenExecute(() -> {
			SmithTestSupport.sweepGolems(h.getLevel(), h.absolutePos(BlockPos.ZERO), 32);
			IronGolem g = h.spawn(EntityType.IRON_GOLEM, new BlockPos(4, 0, 20));
			g.setPlayerCreated(true);
			Creeper c = h.spawn(EntityType.CREEPER, new BlockPos(10, 0, 20));
			c.setNoAi(true);
			golem[0] = g;
			creeper[0] = c;
		}).thenExecuteFor(80, () -> {
			h.assertTrue(!(golem[0].getTarget() instanceof Creeper), "the golem targeted " + golem[0].getTarget());
			h.assertTrue(creeper[0].isAlive() && !creeper[0].isRemoved(), "the creeper is gone");
		}).thenExecute(() -> VillageTestSupport.done(h));
	}

	// ---- T-L2-1b --------------------------------------------------------------------------------------

	/** Population 4 cannot start a golem. A fourth pen villager lifts the cap and a build is saved. */
	@GameTest(environment = "villageheart-gametest:smith_golem_cap", structure = YARD, skyAccess = true, maxTicks = 8000)
	public void golemCapHolds(GameTestHelper h) {
		Villager[] smith = new Villager[1];
		scene(h).thenExecute(() -> {
			SmithTestSupport.sweepGolems(h.getLevel(), h.absolutePos(BlockPos.ZERO), Balance.CLAIM_RADIUS + 8);
			SmithTestSupport.pen(h, 3);
			putStore(h, Items.IRON_INGOT, 50);
			putStore(h, Items.CARVED_PUMPKIN, 2);
			setDayTime(h, 1000);
			smith[0] = hire(h);
		}).thenWaitUntil(() -> h.assertTrue(SmithRuntime.hint(h.getLevel().getServer(), smith[0].getUUID()) == SmithDecision.Hint.NEEDS_VILLAGERS,
				"hint " + SmithRuntime.hint(h.getLevel().getServer(), smith[0].getUUID()))
		).thenExecuteFor(3000, () -> {
			h.assertTrue(buildProgress(h) < 0, "a build was saved under the cap: " + buildProgress(h));
			h.assertValueEqual(50L, count(h, Items.IRON_INGOT), "ingots");
			h.assertTrue(SmithRuntime.hint(h.getLevel().getServer(), smith[0].getUUID()) == SmithDecision.Hint.NEEDS_VILLAGERS, "hint drifted");
		}).thenExecute(() -> {
			WorkerTestSupport.spawnVillager(h, new BlockPos(3, 1, 12));
			log("fourth pen villager added");
		}).thenExecuteFor(400, () -> { }).thenExecute(() -> {
			h.assertTrue(buildProgress(h) >= 0, "no build within 400 ticks, progress " + buildProgress(h));
			log("cap lifted, progress " + buildProgress(h));
			VillageTestSupport.done(h);
		});
	}

	// ---- T-L2-1c --------------------------------------------------------------------------------------

	/**
	 * A golem that just appeared still counts. The next build waits for more villagers. The first phase runs the clock to dusk, so the
	 * cap-lift check starts a fresh work period at day time 2000.
	 */
	@GameTest(environment = "villageheart-gametest:smith_golem_cap_counts", structure = YARD, skyAccess = true, maxTicks = 30000)
	public void golemCapCountsFinishedGolem(GameTestHelper h) {
		boolean[] spawned = {false};
		int[] watch = {0};
		scene(h).thenExecute(() -> {
			SmithTestSupport.sweepGolems(h.getLevel(), h.absolutePos(BlockPos.ZERO), Balance.CLAIM_RADIUS + 8);
			SmithTestSupport.pen(h, 4);
			putStore(h, Items.IRON_INGOT, 72);
			putStore(h, Items.CARVED_PUMPKIN, 2);
			take(h, Items.IRON_INGOT, 36);
			take(h, Items.CARVED_PUMPKIN, 1);
			seedBuild(h, 7800);
			setDayTime(h, 1000);
			hire(h);
			log("seeded golem build at 7800, iron " + count(h, Items.IRON_INGOT));
		}).thenExecuteFor(10000, () -> {
			long day = Math.floorMod(h.getLevel().getDayTime(), 24000L);
			int progress = buildProgress(h);
			int golems = SmithTestSupport.golemsNear(h.getLevel(), h.absolutePos(BlockPos.ZERO), 80);
			if (!spawned[0] && golems >= 1) {
				spawned[0] = true;
				h.assertTrue(progress < 0, "the first build is still saved");
				h.assertValueEqual(36L, count(h, Items.IRON_INGOT), "iron after the first golem");
			}
			if (spawned[0] && watch[0] < 200) {
				watch[0]++;
				h.assertTrue(progress < 0, "a second build started in the stale window, progress " + progress);
				h.assertValueEqual(36L, count(h, Items.IRON_INGOT), "iron in the stale window");
				h.assertValueEqual(1, golems, "golems in the stale window");
			} else if (spawned[0] && day < 11000) {
				h.assertTrue(progress < 0, "a second build started before dusk, progress " + progress);
				h.assertValueEqual(36L, count(h, Items.IRON_INGOT), "iron before dusk");
				h.assertValueEqual(1, golems, "golems before dusk");
			}
		}).thenExecute(() -> {
			h.assertTrue(spawned[0], "the first golem never appeared");
			h.assertTrue(watch[0] >= 200, "stale window was " + watch[0] + " ticks");
			for (int i = 0; i < 5; i++) WorkerTestSupport.spawnVillager(h, new BlockPos(1 + (i % 3), 1, 14));
			setDayTime(h, 2000);
			log("population lifted, day reset to 2000");
		}).thenExecuteFor(400, () -> { }).thenExecute(() -> {
			h.assertTrue(buildProgress(h) >= 0, "no second build within 400 ticks, progress " + buildProgress(h));
			log("second build progress " + buildProgress(h));
			VillageTestSupport.done(h);
		});
	}

	// ---- T-L2-1d --------------------------------------------------------------------------------------

	/**
	 * A build in the late afternoon pauses at dusk and finishes the next morning. Seeded at 5900, not 6000: the smith is already at the
	 * anvil, so 6000 plus every work tick from 9000 to 11000 finishes on the last tick of the day and the overnight freeze cannot be seen.
	 */
	@GameTest(environment = "villageheart-gametest:smith_golem_night", structure = YARD, skyAccess = true, maxTicks = 22000)
	public void golemBuildPausesAtNight(GameTestHelper h) {
		boolean[] night = {false};
		boolean[] dawn = {false};
		int[] frozen = {-1};
		scene(h).thenExecute(() -> {
			SmithTestSupport.sweepGolems(h.getLevel(), h.absolutePos(BlockPos.ZERO), Balance.CLAIM_RADIUS + 8);
			SmithTestSupport.pen(h, 4);
			seedBuild(h, 5900);
			setDayTime(h, 9000);
			hire(h);
			log("night build seeded at 5900, day 9000");
		}).thenExecuteFor(19000, () -> {
			long day = Math.floorMod(h.getLevel().getDayTime(), 24000L);
			int progress = buildProgress(h);
			int golems = SmithTestSupport.golemsNear(h.getLevel(), h.absolutePos(BlockPos.ZERO), 80);
			if (day >= 11000 || (night[0] && day < 1000)) h.assertValueEqual(0, golems, "a golem appeared overnight");
			if (!night[0] && day >= 11100 && day < 12000) {
				night[0] = true;
				frozen[0] = progress;
				h.assertTrue(progress >= 0 && progress < 8000, "progress at dusk " + progress);
				log("dusk progress " + progress);
			}
			if (night[0] && (day >= 11100 || day < 1000)) h.assertValueEqual(frozen[0], progress, "progress moved overnight");
			if (night[0] && day >= 1000 && day < 11000) dawn[0] = true;
		}).thenExecute(() -> {
			h.assertTrue(night[0], "dusk was not sampled");
			h.assertTrue(dawn[0], "the next morning was not sampled");
			h.assertValueEqual(1, SmithTestSupport.golemsNear(h.getLevel(), h.absolutePos(BlockPos.ZERO), 80), "the golem after dawn");
			h.assertTrue(buildProgress(h) < 0, "the build is still saved");
			log("golem finished after dawn, dusk progress was " + frozen[0]);
			VillageTestSupport.done(h);
		});
	}

	// ---- T-L2-2b --------------------------------------------------------------------------------------

	/** With no ingots the golem stays away. Three ingots and it comes in to be repaired. */
	@GameTest(environment = "villageheart-gametest:smith_golem_no_iron", structure = YARD, skyAccess = true, maxTicks = 6000)
	public void golemWithoutIronWaits(GameTestHelper h) {
		IronGolem[] golem = new IronGolem[1];
		Villager[] smith = new Villager[1];
		BlockPos[] spot = new BlockPos[1];
		boolean[] sawWaiting = {false};
		scene(h).thenExecute(() -> {
			SmithTestSupport.sweepGolems(h.getLevel(), h.absolutePos(BlockPos.ZERO), Balance.CLAIM_RADIUS + 8);
			setDayTime(h, 2000);
			smith[0] = hire(h);
			var scan = SmithyLayout.scan(h.getLevel().getServer(), BuildingTestSupport.loc(h, SmithTestSupport.smithyMarker())).orElseThrow();
			spot[0] = SmithyLayout.waitSpot(h.getLevel(), scan).orElseThrow();
			BlockPos origin = h.absolutePos(BlockPos.ZERO);
			BlockPos at = spot[0].offset(16, 0, 0);
			glassPen(h.getLevel(), at, true);
			IronGolem g = h.spawn(EntityType.IRON_GOLEM, new BlockPos(at.getX() - origin.getX(), at.getY() - origin.getY(), at.getZ() - origin.getZ()));
			g.setPlayerCreated(true);
			g.setHealth(40f);
			golem[0] = g;
			log("no-iron golem at " + at + " spot " + spot[0]);
		}).thenExecuteFor(1200, () -> {
			double dist = golem[0].position().distanceTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(spot[0]));
			h.assertTrue(dist > 6, "the golem came within 6 of the spot: " + dist);
			h.assertTrue(golem[0].getHealth() <= 40.01f, "health moved without iron: " + golem[0].getHealth());
		}).thenExecute(() -> {
			glassPen(h.getLevel(), spot[0].offset(16, 0, 0), false);
			putStore(h, Items.IRON_INGOT, 3);
			log("iron delivered, golem " + golem[0].blockPosition() + " dist "
					+ golem[0].position().distanceTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(spot[0])));
		}).thenWaitUntil(() -> {
			double dist = golem[0].position().distanceTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(spot[0]));
			if (golem[0].getHealth() <= 40.01f && dist <= 1.5) sawWaiting[0] = true;
			h.assertTrue(golem[0].getHealth() > 90f, "health " + golem[0].getHealth() + " dist " + dist
					+ " at " + golem[0].blockPosition() + " iron " + count(h, Items.IRON_INGOT)
					+ " day " + Math.floorMod(h.getLevel().getDayTime(), 24000L) + " target " + golem[0].getTarget());
		}).thenExecute(() -> {
			long units = SmithRuntime.unitsOf(h.getLevel().getServer(), smith[0].getUUID()).stream().filter(u -> u.kind().equals("GOLEM_REPAIR")).count();
			h.assertValueEqual(3L, units, "three repair ingots");
			h.assertTrue(sawWaiting[0], "the golem waited at the spot before the first heal");
			h.assertValueEqual(0, SmithTestSupport.ironTotal(h), "the three ingots were used");
			h.assertTrue(golem[0].getHealth() > 90f, "health " + golem[0].getHealth());
			log("no-iron golem repaired after the delivery");
			VillageTestSupport.done(h);
		});
	}

	// ---- T-L2-7 ---------------------------------------------------------------------------------------

	/** An iron sword in the Barracks chest is carried to the anvil and put back at damage 14. */
	@GameTest(environment = "villageheart-gametest:smith_barracks_repair", structure = YARD, skyAccess = true, maxTicks = 6000)
	public void repairsBarracksGear(GameTestHelper h) {
		scene(h).thenExecute(() -> SmithTestSupport.addBarracks(h)).thenWaitUntil(() ->
				BuildingTestSupport.assertStatus(h, SmithTestSupport.barracksMarker(), dev.villageheart.block.MarkerStatus.VALID)
		).thenExecute(() -> {
			var chest = (net.minecraft.world.Container) h.getLevel().getBlockEntity(h.absolutePos(SmithTestSupport.barracksChest()));
			ItemStack sword = new ItemStack(Items.IRON_SWORD);
			sword.setDamageValue(200);
			chest.setItem(0, sword);
			putStore(h, Items.IRON_INGOT, 4);
			setDayTime(h, 1000);
			hire(h);
			log("barracks sword damage 200");
		}).thenWaitUntil(() -> {
			var chest = (net.minecraft.world.Container) h.getLevel().getBlockEntity(h.absolutePos(SmithTestSupport.barracksChest()));
			ItemStack s = chest.getItem(0);
			h.assertTrue(s.is(Items.IRON_SWORD) && s.getDamageValue() == 14, "barracks sword " + (s.isEmpty() ? "empty" : s.getDamageValue()));
		}).thenExecute(() -> {
			h.assertValueEqual(1L, count(h, Items.IRON_INGOT), "one ingot left");
			log("barracks sword repaired to 14");
			VillageTestSupport.done(h);
		});
	}

	// ---- T-L2-2d --------------------------------------------------------------------------------------

	/** Two hurt golems share one Smithy. Only one is repaired at a time, and six ingots finish both. */
	@GameTest(environment = "villageheart-gametest:smith_two_golems", structure = YARD, skyAccess = true, maxTicks = 8000)
	public void twoGolemsTakeTurns(GameTestHelper h) {
		IronGolem[] a = new IronGolem[1];
		IronGolem[] b = new IronGolem[1];
		scene(h).thenExecute(() -> {
			SmithTestSupport.sweepGolems(h.getLevel(), h.absolutePos(BlockPos.ZERO), Balance.CLAIM_RADIUS + 8);
			putStore(h, Items.IRON_INGOT, 6);
			setDayTime(h, 2000);
			hire(h);
			var scan = SmithyLayout.scan(h.getLevel().getServer(), BuildingTestSupport.loc(h, SmithTestSupport.smithyMarker())).orElseThrow();
			BlockPos spot = SmithyLayout.waitSpot(h.getLevel(), scan).orElseThrow();
			a[0] = summonHurt(h, spot.offset(14, 0, 0), 40f);
			b[0] = summonHurt(h, spot.offset(18, 0, 0), 40f);
			log("two golems at " + a[0].blockPosition() + " and " + b[0].blockPosition());
		}).thenWaitUntil(() -> {
			int mid = 0;
			if (a[0].getHealth() > 41f && a[0].getHealth() < 95f) mid++;
			if (b[0].getHealth() > 41f && b[0].getHealth() < 95f) mid++;
			h.assertTrue(mid <= 1, "two golems were repaired at once");
			h.assertTrue(a[0].getHealth() > 90f && b[0].getHealth() > 90f, "health " + a[0].getHealth() + " and " + b[0].getHealth());
		}).thenExecute(() -> {
			h.assertValueEqual(0, SmithTestSupport.ironTotal(h), "six ingots used");
			log("both golems repaired, one at a time");
			VillageTestSupport.done(h);
		});
	}

	// ---- T-L2-2e --------------------------------------------------------------------------------------

	/** A golem above half health stays away. A golem reloaded from NBT still has the repair goal and gets mended. */
	@GameTest(environment = "villageheart-gametest:smith_golem_reload", structure = YARD, skyAccess = true, maxTicks = 8000)
	public void healthyAndLoadedGolems(GameTestHelper h) {
		IronGolem[] healthy = new IronGolem[1];
		IronGolem[] hurt = new IronGolem[1];
		BlockPos[] spot = new BlockPos[1];
		scene(h).thenExecute(() -> {
			SmithTestSupport.sweepGolems(h.getLevel(), h.absolutePos(BlockPos.ZERO), Balance.CLAIM_RADIUS + 8);
			setDayTime(h, 2000);
			hire(h);
			var scan = SmithyLayout.scan(h.getLevel().getServer(), BuildingTestSupport.loc(h, SmithTestSupport.smithyMarker())).orElseThrow();
			spot[0] = SmithyLayout.waitSpot(h.getLevel(), scan).orElseThrow();
			BlockPos far = h.absolutePos(new BlockPos(28, 1, 24));
			glassPen(h.getLevel(), far, true);
			healthy[0] = summonHurt(h, far, 60f);
			IronGolem born = summonHurt(h, spot[0].offset(6, 0, 0), 40f);
			hurt[0] = reloadGolem(h, born);
			h.assertTrue(hasRepairGoal(hurt[0]), "the reloaded golem has the repair goal");
			h.assertTrue(Math.abs(hurt[0].getHealth() - 40f) < 0.1f, "reloaded health " + hurt[0].getHealth());
			log("healthy penned, hurt reloaded");
		}).thenExecuteFor(1200, () -> {
			double dist = healthy[0].position().distanceTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(spot[0]));
			h.assertTrue(dist > 6, "the healthy golem came within 6: " + dist);
			h.assertTrue(healthy[0].getHealth() > 59f, "healthy golem was repaired");
		}).thenExecute(() -> putStore(h, Items.IRON_INGOT, 3)).thenWaitUntil(() -> {
			String state = hurt[0].isRemoved() ? "removed " + hurt[0].getRemovalReason()
					: "health " + hurt[0].getHealth() + " at " + hurt[0].blockPosition();
			h.assertTrue(hurt[0].isAlive() && hurt[0].getHealth() > 90f, "reloaded " + state);
		}).thenExecute(() -> {
			h.assertValueEqual(0, SmithTestSupport.ironTotal(h), "three ingots used on the reloaded golem");
			log("reloaded golem repaired, healthy one stayed away");
			VillageTestSupport.done(h);
		});
	}

	// ---- T-L2-8 ---------------------------------------------------------------------------------------

	/** A saved build survives the smith being reloaded, and breaking the Smithy gives the materials back once. */
	@GameTest(environment = "villageheart-gametest:smith_golem_refund", structure = YARD, skyAccess = true, maxTicks = 14000)
	public void golemBuildSurvivesReloadAndRefunds(GameTestHelper h) {
		Villager[] smith = new Villager[1];
		scene(h).thenExecute(() -> {
			SmithTestSupport.pen(h, 4);
			seedBuild(h, 4000);
			setDayTime(h, 1000);
			smith[0] = hire(h);
			log("reload build seeded at 4000");
		}).thenWaitUntil(() -> h.assertTrue(
				SmithRuntime.activity(h.getLevel().getServer(), smith[0].getUUID()) == dev.villageheart.core.heart.ActivityKind.BUILDING_GOLEM,
				"activity " + SmithRuntime.activity(h.getLevel().getServer(), smith[0].getUUID()))
		).thenExecute(() -> {
			h.assertTrue(buildProgress(h) >= 3800, "progress before the reload " + buildProgress(h));
			smith[0] = LumberTestSupport.reloadEntity(h.getLevel(), smith[0]);
			h.assertTrue(buildProgress(h) >= 3800, "progress after the reload " + buildProgress(h));
			log("smith reloaded, progress " + buildProgress(h));
		}).thenWaitUntil(() -> h.assertValueEqual(1, SmithTestSupport.golemsNear(h.getLevel(), h.absolutePos(BlockPos.ZERO), 80), "the golem"))
		.thenExecute(() -> {
			h.assertTrue(buildProgress(h) < 0, "the first build is still saved");
			seedBuild(h, 100);
			h.setBlock(SmithTestSupport.smithyMarker(), Blocks.AIR);
			log("smithy marker removed");
		}).thenExecuteFor(200, () -> { }).thenExecute(() -> {
			h.assertValueEqual(36L, count(h, Items.IRON_INGOT), "refunded ingots");
			h.assertValueEqual(1L, count(h, Items.CARVED_PUMPKIN), "refunded pumpkin");
			h.assertTrue(VillageClaims.current(h.getLevel().getServer()).orElseThrow().golemBuilds().isEmpty(), "the build entry is gone");
		}).thenExecuteFor(400, () -> {
			h.assertValueEqual(36L, count(h, Items.IRON_INGOT), "ingots doubled");
			h.assertValueEqual(1L, count(h, Items.CARVED_PUMPKIN), "pumpkin doubled");
			h.assertTrue(VillageClaims.current(h.getLevel().getServer()).orElseThrow().golemBuilds().isEmpty(), "the build came back");
		}).thenExecute(() -> {
			BuildingTestSupport.placeMarker(h, dev.villageheart.core.building.BuildingType.SMITHY, SmithTestSupport.smithyMarker(), net.minecraft.core.Direction.NORTH);
		}).thenWaitUntil(() -> BuildingTestSupport.assertStatus(h, SmithTestSupport.smithyMarker(), dev.villageheart.block.MarkerStatus.VALID)
		).thenExecute(() -> {
			for (BlockPos rel : new BlockPos[] {SmithTestSupport.storeChest1(), SmithTestSupport.storeChest2()}) {
				var box = (net.minecraft.world.Container) h.getLevel().getBlockEntity(h.absolutePos(rel));
				for (int i = 0; i < box.getContainerSize(); i++) box.setItem(i, new ItemStack(Items.DIRT, 64));
				box.setChanged();
			}
			var first = (net.minecraft.world.Container) h.getLevel().getBlockEntity(h.absolutePos(SmithTestSupport.storeChest1()));
			first.setItem(0, ItemStack.EMPTY);
			seedBuild(h, 50);
			h.setBlock(SmithTestSupport.smithyMarker(), Blocks.AIR);
			log("full storage, marker removed again");
		}).thenExecuteFor(200, () -> { }).thenExecute(() -> {
			h.assertValueEqual(36L, count(h, Items.IRON_INGOT), "the ingots took the free slot");
			h.assertValueEqual(0L, count(h, Items.CARVED_PUMPKIN), "the pumpkin did not fit");
			h.assertValueEqual(1, SmithTestSupport.worldCount(h, Items.CARVED_PUMPKIN), "the pumpkin is on the ground");
			h.assertTrue(VillageClaims.current(h.getLevel().getServer()).orElseThrow().golemBuilds().isEmpty(), "no build left");
			log("partial refund: ingots stored, pumpkin dropped");
			VillageTestSupport.done(h);
		});
	}

	/** A 5×5 glass pen, three high, so a golem cannot wander into the wait spot. {@code close} builds it. */
	private static IronGolem summonHurt(GameTestHelper h, BlockPos absolute, float health) {
		BlockPos origin = h.absolutePos(BlockPos.ZERO);
		IronGolem g = h.spawn(EntityType.IRON_GOLEM, new BlockPos(absolute.getX() - origin.getX(), absolute.getY() - origin.getY(), absolute.getZ() - origin.getZ()));
		g.setPlayerCreated(true);
		g.setHealth(health);
		return g;
	}

	private static IronGolem reloadGolem(GameTestHelper h, IronGolem g) {
		ServerLevel level = h.getLevel();
		var out = net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess());
		g.saveWithoutId(out);
		var tag = out.buildResult();
		tag.remove("UUID");
		BlockPos origin = h.absolutePos(BlockPos.ZERO);
		BlockPos rel = new BlockPos(g.getBlockX() - origin.getX(), g.getBlockY() - origin.getY(), g.getBlockZ() - origin.getZ());
		g.discard();
		// An addFreshEntity copy is not persistence-required, and the last run had it removed before the repair.
		// Spawn through the helper (that sets persistence and tracks the entity), then load the saved data onto it.
		IronGolem copy = h.spawn(EntityType.IRON_GOLEM, rel);
		copy.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), tag));
		copy.setPersistenceRequired();
		copy.setPlayerCreated(true);
		return copy;
	}

	private static boolean hasRepairGoal(IronGolem g) {
		var goals = ((dev.villageheart.gametest.mixin.MobGoalAccessor) (Object) g).villageheart$goals();
		for (var wrapped : goals.getAvailableGoals()) if (wrapped.getGoal() instanceof dev.villageheart.golem.SmithyRepairGoal) return true;
		return false;
	}

	private static boolean repairRunning(IronGolem g) {
		var goals = ((dev.villageheart.gametest.mixin.MobGoalAccessor) (Object) g).villageheart$goals();
		for (var wrapped : goals.getAvailableGoals()) {
			if (wrapped.getGoal() instanceof dev.villageheart.golem.SmithyRepairGoal && wrapped.isRunning()) return true;
		}
		return false;
	}

	private static void glassPen(ServerLevel level, BlockPos centre, boolean close) {
		for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
			if (Math.abs(dx) != 2 && Math.abs(dz) != 2) continue;
			for (int dy = 0; dy < 3; dy++) {
				BlockPos p = centre.offset(dx, dy, dz);
				if (close) level.setBlock(p, Blocks.GLASS.defaultBlockState(), 3);
				else if (level.getBlockState(p).is(Blocks.GLASS)) level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
			}
		}
	}

	private static HeartLocation smithyAt(GameTestHelper h) {
		return BuildingTestSupport.loc(h, SmithTestSupport.smithyMarker());
	}

	private static int buildProgress(GameTestHelper h) {
		return VillageClaims.current(h.getLevel().getServer()).map(v -> v.golemProgress(smithyAt(h))).orElse(-1);
	}

	private static void seedBuild(GameTestHelper h, int progress) {
		VillageClaims.data(h.getLevel().getServer()).update(v -> v.withGolemBuild(smithyAt(h), progress));
	}

	/** A saved iron pickaxe for the Mine's slot 0, committed as 3 ingots and 2 sticks. */
	private static void seedPick(GameTestHelper h, int progress) {
		HeartLocation mine = BuildingTestSupport.loc(h, SmithTestSupport.mineMarker());
		HeartLocation furnace = BuildingTestSupport.loc(h, SmithTestSupport.furnace());
		OrderKey key = new OrderKey(mine, GearNeed.PICKAXE, 0);
		GearCraft craft = new GearCraft("minecraft:iron_pickaxe", key, progress, List.of(
				new ItemCount("minecraft:iron_ingot", 3), new ItemCount("minecraft:stick", 2)));
		VillageClaims.data(h.getLevel().getServer()).update(v -> v.withSmithCraft(furnace, new SmithCraft(craft, mine)));
	}

	private static int mineOrders(GameTestHelper h) {
		return GearOrders.openOf(BuildingTestSupport.loc(h, SmithTestSupport.mineMarker()), GearNeed.PICKAXE).size();
	}

	private static int contents(GameTestHelper h, BlockPos marker, BuildingType type, String key) {
		HeartLocation want = BuildingTestSupport.loc(h, marker);
		for (Buildings.BuildingView b : Buildings.valid(h.getLevel().getServer(), type)) {
			if (VillageClaims.locate(h.getLevel(), b.marker()).equals(want)) return b.latestScan().map(s -> s.count(key)).orElse(0);
		}
		return 0;
	}

	private static int chestCount(GameTestHelper h, BlockPos rel, net.minecraft.world.item.Item item) {
		var box = (net.minecraft.world.Container) h.getLevel().getBlockEntity(h.absolutePos(rel));
		int n = 0;
		if (box == null) return 0;
		for (int i = 0; i < box.getContainerSize(); i++) if (box.getItem(i).is(item)) n += box.getItem(i).getCount();
		return n;
	}

	private static long craftUnits(GameTestHelper h, Villager smith) {
		return SmithRuntime.unitsOf(h.getLevel().getServer(), smith.getUUID()).stream()
				.filter(u -> u.kind().equals("CRAFT") && u.id().equals("minecraft:iron_axe")).count();
	}

	private static boolean leasesAxe(GameTestHelper h, Villager jack) {
		return CampTools.lease(h.getLevel().getServer(), jack, ToolKind.AXE, null) instanceof CampTools.Held held
				&& held.lease().stack().is(Items.IRON_AXE);
	}

	/** Either blast furnace. {@link SmithTestSupport#hire} insists on the first one, and the second smith takes the other. */
	private static Villager hireEitherFurnace(GameTestHelper h) {
		Villager v = WorkerTestSupport.spawnVillager(h, SmithTestSupport.spawnSpot());
		var r = Workers.hire(h.getLevel(), v, JobType.BLACKSMITH, java.util.Optional.of(BuildingTestSupport.loc(h, SmithTestSupport.smithyMarker())));
		h.assertTrue(r instanceof dev.villageheart.worker.HireResult.Hired, "the smith was hired: " + r);
		var data = v.getAttached(WorkerAttachments.WORKER);
		h.assertTrue(data != null && SmithyLayout.smithyAt(h.getLevel().getServer(), data.jobBlock()).isPresent(),
				"job block " + (data == null ? null : data.jobBlock()));
		return v;
	}

	private static String part2(GameTestHelper h, Villager[] smiths, long day) {
		var server = h.getLevel().getServer();
		var a = SmithRuntime.get(server).state(smiths[0].getUUID());
		var b = SmithRuntime.get(server).state(smiths[1].getUUID());
		return "crafts " + craftAt(h, smiths[0]) + " " + craftAt(h, smiths[1]) + " day " + day
				+ " modes " + a.mode + " " + b.mode + " hints " + a.hint + " " + b.hint
				+ " iron " + count(h, Items.IRON_INGOT) + " orders " + GearOrders.size()
				+ " axes " + chestCount(h, SmithTestSupport.campChest(), Items.IRON_AXE);
	}

	private static boolean craftAt(GameTestHelper h, Villager smith) {
		var data = smith.getAttached(WorkerAttachments.WORKER);
		if (data == null) return false;
		return VillageClaims.current(h.getLevel().getServer()).flatMap(v -> v.smithCraft(data.jobBlock())).isPresent();
	}

	private static boolean smithCraftsEmpty(GameTestHelper h) {
		return VillageClaims.current(h.getLevel().getServer()).map(v -> v.smithCrafts().isEmpty()).orElse(false);
	}

	private static void take(GameTestHelper h, net.minecraft.world.item.Item item, int n) {
		int left = n;
		for (BlockPos rel : new BlockPos[] {SmithTestSupport.storeChest1(), SmithTestSupport.storeChest2()}) {
			var box = (net.minecraft.world.Container) h.getLevel().getBlockEntity(h.absolutePos(rel));
			for (int i = 0; i < box.getContainerSize() && left > 0; i++) {
				ItemStack stack = box.getItem(i);
				if (!stack.is(item) || !stack.getComponentsPatch().isEmpty()) continue;
				int use = Math.min(left, stack.getCount());
				stack.shrink(use);
				left -= use;
			}
			box.setChanged();
		}
		h.assertValueEqual(0, left, "could not take " + n + " of " + item);
	}

	/** Storage, the smith's hands and one villager's worn slots. Cheap enough to sample every tick. */
	private static int gearSeen(GameTestHelper h, Villager who, net.minecraft.world.item.Item item) {
		int n = (int) count(h, item);
		for (Villager v : h.getLevel().getEntities(EntityType.VILLAGER, new net.minecraft.world.phys.AABB(h.absolutePos(BlockPos.ZERO)).inflate(48), e -> true)) {
			n += v.getInventory().countItem(item);
			for (EquipmentSlot slot : EquipmentSlot.values()) if (v.getItemBySlot(slot).is(item)) n += v.getItemBySlot(slot).getCount();
		}
		if (who != null) {
			// who is already in the villager scan
		}
		return n;
	}

	private static ItemStack stack(String id) { return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(id))); }

	private static String headItem(ToolRecipe shape) {
		String tag = shape.headTag();
		if (!tag.startsWith("#")) return tag;
		return switch (tag) {
			case "#minecraft:planks" -> "minecraft:oak_planks";
			case "#minecraft:stone_tool_materials" -> "minecraft:cobblestone";
			case "#minecraft:copper_tool_materials" -> "minecraft:copper_ingot";
			case "#minecraft:iron_tool_materials" -> "minecraft:iron_ingot";
			case "#minecraft:diamond_tool_materials" -> "minecraft:diamond";
			default -> throw new IllegalStateException(tag);
		};
	}

	private static List<ItemStack> grid(GearNeed need, ItemStack x, ItemStack s, ItemStack e) {
		return switch (need) {
			case AXE -> List.of(x, x, e, x, s, e, e, s, e);
			case PICKAXE -> List.of(x, x, x, e, s, e, e, s, e);
			case HOE -> List.of(x, x, e, e, s, e, e, s, e);
			case SWORD -> List.of(x, e, e, x, e, e, s, e, e);
			case HEAD -> List.of(x, x, x, x, e, x, e, e, e);
			case CHEST -> List.of(x, e, x, x, x, x, x, x, x);
			case LEGS -> List.of(x, x, x, x, e, x, x, e, x);
			case FEET -> List.of(e, e, e, x, e, x, x, e, x);
		};
	}

	private static ItemStack craft(GameTestHelper h, List<ItemStack> grid) {
		var found = h.getLevel().recipeAccess().getRecipeFor(RecipeType.CRAFTING, CraftingInput.of(3, 3, grid), h.getLevel());
		if (found.isEmpty()) return ItemStack.EMPTY;
		return found.get().value().assemble(CraftingInput.of(3, 3, grid), h.getLevel().registryAccess());
	}

	/** What the plan requires before every round: at least five want a golem, and nobody has just seen one. */
	private static void assertWouldSpawn(GameTestHelper h, List<Villager> pen, long now) {
		int willing = 0;
		for (Villager v : pen) {
			v.getBrain().setMemory(MemoryModuleType.LAST_SLEPT, v.level().getGameTime());
			v.getBrain().eraseMemory(MemoryModuleType.GOLEM_DETECTED_RECENTLY);
			h.assertTrue(!v.getBrain().hasMemoryValue(MemoryModuleType.GOLEM_DETECTED_RECENTLY), "no recent golem");
			if (v.wantsToSpawnGolem(now)) willing++;
		}
		h.assertTrue(willing >= 5, "at least five villagers would spawn a golem, got " + willing);
	}
}
