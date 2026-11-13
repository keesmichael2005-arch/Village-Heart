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
import dev.villageheart.core.Rank;
import dev.villageheart.core.building.BuildingRequirements;
import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.building.JobBlockType;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.heart.HeartTexts;
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
import dev.villageheart.worker.gear.SelfCraftRuntime;
import dev.villageheart.worker.gear.SelfCraftSwitch;
import dev.villageheart.worker.gear.SmithService;
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
		}).thenWaitUntil(() -> {
			// A forced chunk does not accept entities on the tick it loads. The five villagers stand on
			// two pads, two blocks either side of each centre, and a centre can sit on a chunk boundary.
			ServerLevel level = h.getLevel();
			HeartLocation heart = VillageClaims.current(level.getServer()).orElseThrow().heart();
			boolean ready = true;
			for (int dx : new int[] {84, 100}) {
				for (int i = -2; i <= 2; i++) {
					if (!level.isPositionEntityTicking(new BlockPos(heart.x() + dx + i, heart.y(), heart.z()))) ready = false;
				}
			}
			h.assertTrue(ready, "the far chunks are not ticking yet");
		}).thenExecute(() -> {
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
			h.assertValueEqual(0L, SmithTestSupport.groundCount(h, Items.IRON_INGOT) + SmithTestSupport.groundCount(h, Items.COPPER_INGOT)
					+ SmithTestSupport.groundCount(h, Items.GOLD_INGOT), "smelted ingots lying on the ground (duplicated)");
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

	// ---- T-L2-5j --------------------------------------------------------------------------------------

	/**
	 * A camp chest full of dirt cannot take the axe. It goes to Storage once, the smith stays away for the backoff, and the axe is
	 * delivered only after a slot is free and the backoff has passed.
	 */
	@GameTest(environment = "villageheart-gametest:smith_full_chest", structure = YARD, skyAccess = true, maxTicks = 8000)
	public void fullCampChestBacksOff(GameTestHelper h) {
		Villager[] smith = new Villager[1];
		long[] failedAt = {0};
		boolean[] hinted = {false};
		BlockPos camp = SmithTestSupport.campChest();
		scene(h).thenExecute(() -> SmithTestSupport.addLumberCamp(h)).thenWaitUntil(() ->
				BuildingTestSupport.assertStatus(h, SmithTestSupport.campMarker(), dev.villageheart.block.MarkerStatus.VALID)
		).thenExecute(() -> {
			fillChest(h, camp, Items.DIRT);
			putStore(h, Items.OAK_PLANKS, 5);
			setDayTime(h, 1000);
			SmithTestSupport.hireLumberjack(h);
			smith[0] = hire(h);
		}).thenWaitUntil(() -> {
			h.assertValueEqual(1L, count(h, Items.WOODEN_AXE), "the axe is in Storage");
			h.assertValueEqual(0, chestCount(h, camp, Items.WOODEN_AXE), "the full camp took none");
		}).thenExecute(() -> {
			failedAt[0] = h.getLevel().getDayTime();
			freeChestSlot(h, camp, 0);
		}).thenExecuteFor(Balance.SMITH_DELIVERY_BACKOFF - 1, () -> {
			h.assertValueEqual(0, chestCount(h, camp, Items.WOODEN_AXE), "no delivery during the backoff");
			h.assertValueEqual(1L, count(h, Items.WOODEN_AXE), "still the one axe in Storage");
			BlockPos target = SmithRuntime.get(h.getLevel().getServer()).state(smith[0].getUUID()).walk.target();
			h.assertFalse(h.absolutePos(camp).equals(target), "the smith walked to the camp again");
			if ("Lumber Camp chest is full".equals(SmithRuntime.blockedHint(h.getLevel().getServer(), smith[0].getUUID()))) hinted[0] = true;
		}).thenExecute(() -> {
			h.assertTrue(hinted[0], "hint Lumber Camp chest is full");
			h.assertTrue(h.getLevel().getDayTime() < failedAt[0] + Balance.SMITH_DELIVERY_BACKOFF, "the backoff was still running");
		}).thenWaitUntil(() -> h.assertValueEqual(1, chestCount(h, camp, Items.WOODEN_AXE), "the axe arrives after the backoff")
		).thenExecute(() -> {
			h.assertTrue(h.getLevel().getDayTime() >= failedAt[0] + Balance.SMITH_DELIVERY_BACKOFF - 1, "delivered before the backoff ended");
			h.assertValueEqual(0L, count(h, Items.WOODEN_AXE), "Storage gave the axe up");
			long crafts = SmithRuntime.unitsOf(h.getLevel().getServer(), smith[0].getUUID()).stream().filter(u -> u.kind().equals("CRAFT")).count();
			h.assertValueEqual(1L, crafts, "the full chest did not make a second axe");
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
			h.assertValueEqual(0L, SmithTestSupport.groundCount(h, Items.STONE_PICKAXE), "stone pickaxes lying on the ground (duplicated)");
			h.assertValueEqual(4L, count(h, Items.OAK_PLANKS), "planks were not used");
			h.assertValueEqual(0L, count(h, Items.COBBLESTONE), "cobble");
			h.assertValueEqual(0L, count(h, Items.STICK), "sticks");
			VillageTestSupport.done(h);
		});
	}

	// ---- T-L2-5k --------------------------------------------------------------------------------------

	/**
	 * A stone pickaxe that has already been committed keeps going when the Mine cannot be read. Pulling a wall out makes the Mine
	 * invalid (the census answers unknown, which pauses and never cancels); the wall goes back and the pickaxe still arrives.
	 */
	@GameTest(environment = "villageheart-gametest:smith_unloaded_camp", structure = YARD, skyAccess = true, maxTicks = 8000)
	public void unloadedCampDoesNotCancel(GameTestHelper h) {
		Villager[] smith = new Villager[1];
		int[] floor = {-1};
		BlockPos wall = new BlockPos(SmithTestSupport.MINE.getX() + 5, 1, SmithTestSupport.MINE.getZ() + 2);
		BlockPos wallAbove = wall.above();
		scene(h).thenExecute(() -> SmithTestSupport.addMine(h)).thenWaitUntil(() -> {
			BuildingTestSupport.assertStatus(h, SmithTestSupport.mineMarker(), dev.villageheart.block.MarkerStatus.VALID);
			BuildingTestSupport.assertStatus(h, MineTestSupport.shaftMarker(0, SmithTestSupport.SHAFT), dev.villageheart.block.MarkerStatus.VALID);
		}).thenExecute(() -> {
			putStore(h, Items.OAK_PLANKS, 4);
			putStore(h, Items.STICK, 2);
			putStore(h, Items.COBBLESTONE, 3);
			setDayTime(h, 1000);
			SmithTestSupport.hireMiner(h);
			smith[0] = hire(h);
		}).thenWaitUntil(() -> h.assertTrue(craftProgress(h) >= 600, "craft progress " + craftProgress(h))
		).thenExecute(() -> {
			floor[0] = craftProgress(h);
			h.setBlock(wall, Blocks.AIR);
			h.setBlock(wallAbove, Blocks.AIR);
		}).thenWaitUntil(() -> BuildingTestSupport.assertStatus(h, SmithTestSupport.mineMarker(), dev.villageheart.block.MarkerStatus.INVALID)
		).thenExecute(() -> floor[0] = craftProgress(h)
		).thenExecuteFor(400, () -> {
			int p = craftProgress(h);
			h.assertTrue(p >= floor[0], "the craft was cancelled or rewound: " + p + " after " + floor[0]);
			floor[0] = p;
		}).thenExecute(() -> {
			h.setBlock(wall, Blocks.OAK_PLANKS);
			h.setBlock(wallAbove, Blocks.OAK_PLANKS);
		}).thenWaitUntil(() -> BuildingTestSupport.assertStatus(h, SmithTestSupport.mineMarker(), dev.villageheart.block.MarkerStatus.VALID)
		).thenWaitUntil(() -> h.assertTrue(SmithTestSupport.chestHas(h, SmithTestSupport.mineChest(), Items.STONE_PICKAXE), "the stone pickaxe arrives")
		).thenExecute(() -> {
			long crafts = SmithRuntime.unitsOf(h.getLevel().getServer(), smith[0].getUUID()).stream().filter(u -> u.kind().equals("CRAFT")).count();
			h.assertValueEqual(1L, crafts, "the pause did not start a second pickaxe");
			VillageTestSupport.done(h);
		});
	}

	// ---- T-L2-5l --------------------------------------------------------------------------------------

	/**
	 * A lumberjack already making a stone axe keeps that craft when a smith is hired. The smith takes the other lumberjack's
	 * slot, and that lumberjack never starts one of their own.
	 *
	 * The second lumberjack is hired in the same tick as the smith. Hired earlier, with nobody serving, they start a fetch of
	 * their own, and a fetch already walking is not cancelled when a smith arrives.
	 */
	@GameTest(environment = "villageheart-gametest:smith_self_takeover", structure = YARD, skyAccess = true, maxTicks = 9000)
	public void smithTakesOverFromSelfCraft(GameTestHelper h) {
		Villager[] jacks = new Villager[2];
		Villager[] smith = new Villager[1];
		long[] hiredAt = {-1};
		boolean[] jack2Crafted = {false};
		SelfCraftTestSupport.Sampler sampler = new SelfCraftTestSupport.Sampler();
		BlockPos treeA = new BlockPos(4, 0, 28);
		BlockPos treeB = new BlockPos(22, 0, 28);
		BlockPos secondBlock = BuildingTestSupport.inside(SmithTestSupport.CAMP, 3, 0, 1);
		h.onEachTick(() -> {
			sampler.sample(h);
			if (jacks[1] != null && h.getTick() % 20 == 0 && SelfCraftTestSupport.record(h, jacks[1].getUUID()).isPresent()) jack2Crafted[0] = true;
		});
		scene(h).thenExecute(() -> {
			SelfCraftSwitch.reset();
			SmithTestSupport.addLumberCamp(h);
			h.setBlock(secondBlock, JobBlocks.BLOCKS.get(JobBlockType.CHOPPING_BLOCK));
		}).thenWaitUntil(() -> {
			BuildingTestSupport.assertStatus(h, SmithTestSupport.campMarker(), dev.villageheart.block.MarkerStatus.VALID);
			h.assertValueEqual(2, contents(h, SmithTestSupport.campMarker(), BuildingType.LUMBER_CAMP, BuildingRequirements.CHOPPING_BLOCK), "chopping blocks");
		}).thenExecute(() -> {
			clearStonePad(h, treeA);
			clearStonePad(h, treeB);
			LumberTestSupport.growOak(h, treeA, 1);
			LumberTestSupport.growOak(h, treeB, 2);
			putStore(h, Items.OAK_PLANKS, 10);
			putStore(h, Items.COBBLESTONE, 6);
			putStore(h, Items.STICK, 4);
			setDayTime(h, 1000);
			jacks[0] = SmithTestSupport.hireLumberjack(h);
		}).thenWaitUntil(() -> {
			var rec = SelfCraftTestSupport.record(h, jacks[0].getUUID());
			h.assertTrue(rec.isPresent() && rec.get().craft().recipeId().equals("minecraft:stone_axe"), "no stone axe self-craft yet");
			h.assertValueEqual("Making a stone axe", activityWords(h, jacks[0]), "status");
		}).thenExecute(() -> {
			smith[0] = hire(h);
			jacks[1] = SmithTestSupport.hireLumberjack(h);
			hiredAt[0] = h.getTick();
		}).thenWaitUntil(() -> h.assertTrue(GearOrders.heldBy(smith[0].getUUID()).isPresent(), "the smith has not ordered")
		).thenExecute(() -> {
			var order = GearOrders.heldBy(smith[0].getUUID()).orElseThrow();
			var self = SelfCraftTestSupport.record(h, jacks[0].getUUID()).orElseThrow();
			HeartLocation camp = BuildingTestSupport.loc(h, SmithTestSupport.campMarker());
			h.assertTrue(h.getTick() - hiredAt[0] <= 200, "the smith ordered after " + (h.getTick() - hiredAt[0]) + " ticks");
			h.assertFalse(order.key().equals(self.craft().key()), "the smith took the self-crafter's key");
			h.assertTrue(order.key().building().equals(camp) && order.key().need() == GearNeed.AXE, "the order is the camp's other axe: " + order.key());
			h.assertValueEqual(1L, GearOrders.openOf(camp, GearNeed.AXE).stream().filter(o -> o.holder().equals(smith[0].getUUID())).count(), "one smith order");
			h.assertTrue(SelfCraftTestSupport.record(h, jacks[1].getUUID()).isEmpty(), "lumberjack 2 started a self-craft");
		}).thenWaitUntil(() -> {
			h.assertTrue(sampler.finished(jacks[0].getUUID()), "the self-craft has not finished");
			h.assertValueEqual(2, chestCount(h, SmithTestSupport.campChest(), Items.STONE_AXE), "axes in the camp");
			h.assertValueEqual(0, GearOrders.size(), "an order is still open");
		}).thenExecute(() -> {
			int clock = sampler.clock(jacks[0].getUUID());
			h.assertTrue(clock >= 4800 - 20 && clock <= 4800 + 100, "the self-craft clock: " + clock + " " + sampler.log);
			h.assertValueEqual("minecraft:stone_axe", sampler.recipeOf(jacks[0].getUUID()), "lumberjack 1 made a stone axe");
			h.assertFalse(jack2Crafted[0], "lumberjack 2 got a self-craft");
			h.assertFalse(sampler.committed(jacks[1].getUUID()), "lumberjack 2 committed: " + sampler.log);
			h.assertValueEqual(0, chestCount(h, SmithTestSupport.campChest(), Items.WOODEN_AXE), "a wooden axe");
			h.assertValueEqual(0L, count(h, Items.COBBLESTONE), "cobble left");
			h.assertValueEqual(0L, count(h, Items.STICK), "sticks left");
			log("self-craft clock " + clock + " smith ordered at " + hiredAt[0]);
			VillageTestSupport.done(h);
		});
	}

	/** The paved yard is stone at y = 0. A tree needs that cell clear, the way the lumber tests grow one on grass. */
	private static void clearStonePad(GameTestHelper h, BlockPos base) {
		for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
			BlockPos p = base.offset(dx, 0, dz);
			if (p.getX() < 0 || p.getZ() < 0 || p.getX() > 31 || p.getZ() > 31) continue;
			if (h.getBlockState(p).is(Blocks.STONE)) h.setBlock(p, Blocks.AIR);
		}
	}

	private static String activityWords(GameTestHelper h, Villager v) {
		var row = HeartTestSupport.snapshot(h, Rank.OWNER).villagers().stream().filter(r -> r.id().equals(v.getUUID())).findFirst().orElseThrow();
		return HeartTexts.EN.get(HeartTexts.activityKey(row.activity(), row.jobId()));
	}

	// ---- T-L2-5m --------------------------------------------------------------------------------------

	/**
	 * A smith busy with a saved diamond axe does not take the miner's pickaxe. The miner waits out the fallback, then makes a stone
	 * pickaxe. The lumberjack's axe slot stays the smith's order, so that lumberjack never starts a self-craft.
	 */
	@GameTest(environment = "villageheart-gametest:smith_self_fallback", structure = YARD, skyAccess = true, maxTicks = 9000)
	public void selfCraftFallbackWhileSmithBusy(GameTestHelper h) {
		Villager[] jack = new Villager[1];
		Villager[] miner = new Villager[1];
		Villager[] smith = new Villager[1];
		long[] t0 = {-1};
		long[] started = {-1};
		long[] openAtStart = {-1};
		String[] startedRecipe = {""};
		boolean[] jackCrafted = {false};
		boolean[] smithTookPick = {false};
		boolean[] diamondCrafted = {false};
		SelfCraftTestSupport.Sampler sampler = new SelfCraftTestSupport.Sampler();
		h.onEachTick(() -> {
			sampler.sample(h);
			if (jack[0] != null && h.getTick() % 20 == 0 && SelfCraftTestSupport.record(h, jack[0].getUUID()).isPresent()) jackCrafted[0] = true;
			if (miner[0] != null && t0[0] >= 0 && h.getTick() > t0[0] && started[0] < 0 && h.getTick() % 20 == 0) {
				h.assertValueEqual("Needs pickaxe", activityWords(h, miner[0]), "status");
				h.assertTrue(smithServes(h), "the smith is not serving");
				h.assertValueEqual(0, mineOrders(h), "a pickaxe order is open");
				h.assertTrue(SelfCraftTestSupport.record(h, miner[0].getUUID()).isEmpty(), "the miner self-crafted early");
			}
			if (miner[0] != null && started[0] < 0 && fallbackStarted(h, miner[0])) {
				started[0] = h.getTick();
				var st = SelfCraftRuntime.get(h.getLevel().getServer()).peek(miner[0].getUUID());
				openAtStart[0] = st == null ? -1 : st.slotOpenTicks;
				var rec = SelfCraftTestSupport.record(h, miner[0].getUUID());
				if (rec.isPresent()) startedRecipe[0] = rec.get().craft().recipeId();
				else if (st != null && st.fetching != null) startedRecipe[0] = st.fetching.plan().recipe().outputId();
			}
			if (smith[0] != null && !diamondCrafted[0] && SmithRuntime.unitsOf(h.getLevel().getServer(), smith[0].getUUID()).stream()
					.anyMatch(u -> u.kind().equals("CRAFT") && u.id().equals("minecraft:diamond_axe"))) diamondCrafted[0] = true;
			if (diamondCrafted[0]) {
				var order = GearOrders.heldBy(smith[0].getUUID());
				if (order.isPresent() && order.get().key().need() == GearNeed.PICKAXE) smithTookPick[0] = true;
			}
		});
		scene(h).thenExecute(() -> {
			SelfCraftSwitch.reset();
			SmithTestSupport.addLumberCamp(h);
			SmithTestSupport.addMine(h);
		}).thenWaitUntil(() -> {
			BuildingTestSupport.assertStatus(h, SmithTestSupport.campMarker(), dev.villageheart.block.MarkerStatus.VALID);
			BuildingTestSupport.assertStatus(h, SmithTestSupport.mineMarker(), dev.villageheart.block.MarkerStatus.VALID);
			BuildingTestSupport.assertStatus(h, MineTestSupport.shaftMarker(0, SmithTestSupport.SHAFT), dev.villageheart.block.MarkerStatus.VALID);
		}).thenExecute(() -> {
			putStore(h, Items.COBBLESTONE, 3);
			putStore(h, Items.STICK, 2);
			setDayTime(h, 1000);
			jack[0] = SmithTestSupport.hireLumberjack(h);
			miner[0] = SmithTestSupport.hireMiner(h);
			smith[0] = hire(h);
			seedDiamondAxe(h, smith[0]);
			t0[0] = h.getTick();
		}).thenWaitUntil(() -> h.assertTrue(started[0] >= 0, "the fallback has not started")
		).thenExecute(() -> {
			long at = started[0] - t0[0];
			log("fallback at " + at + " open " + openAtStart[0] + " recipe " + startedRecipe[0]);
			h.assertTrue(at >= 2900 && at <= 3100, "the fallback window: " + at);
			h.assertTrue(openAtStart[0] >= Balance.SELF_CRAFT_FALLBACK_TICKS, "work ticks at the start: " + openAtStart[0]);
			h.assertValueEqual("minecraft:stone_pickaxe", startedRecipe[0], "the fallback recipe");
			var order = GearOrders.heldBy(miner[0].getUUID());
			h.assertTrue(order.isPresent() && order.get().key().need() == GearNeed.PICKAXE, "the miner holds the pickaxe key: " + order);
			h.assertTrue(GearOrders.heldBy(smith[0].getUUID()).map(o -> o.key().need() != GearNeed.PICKAXE).orElse(true), "the smith holds the miner's key");
		}).thenWaitUntil(() -> h.assertValueEqual(1, chestCount(h, SmithTestSupport.mineChest(), Items.STONE_PICKAXE), "the stone pickaxe")
		).thenExecute(() -> {
			h.assertTrue(diamondCrafted[0], "the diamond axe never finished");
			h.assertFalse(smithTookPick[0], "the smith ordered the miner's key after the diamond axe");
			h.assertFalse(jackCrafted[0], "the lumberjack self-crafted");
			h.assertFalse(sampler.committed(jack[0].getUUID()), "the lumberjack committed: " + sampler.log);
			h.assertValueEqual("minecraft:stone_pickaxe", sampler.recipeOf(miner[0].getUUID()), "the miner made the pickaxe");
			h.assertValueEqual(0, chestCount(h, SmithTestSupport.mineChest(), Items.WOODEN_PICKAXE), "a wooden pickaxe");
			h.assertValueEqual(0, chestCount(h, SmithTestSupport.mineChest(), Items.IRON_PICKAXE), "an iron pickaxe");
			h.assertValueEqual(0, chestCount(h, SmithTestSupport.mineChest(), Items.DIAMOND_PICKAXE), "a diamond pickaxe");
			h.assertTrue(SmithRuntime.unitsOf(h.getLevel().getServer(), smith[0].getUUID()).stream()
					.noneMatch(u -> u.id().contains("pickaxe")), "the smith crafted a pickaxe");
			log("fallback finished, diamond axe crafted, miner kept the key");
			VillageTestSupport.done(h);
		});
	}

	/** A saved diamond axe at progress 0 for the Lumber Camp's axe slot 0, already the smith's order. */
	private static void seedDiamondAxe(GameTestHelper h, Villager smith) {
		HeartLocation camp = BuildingTestSupport.loc(h, SmithTestSupport.campMarker());
		HeartLocation furnace = BuildingTestSupport.loc(h, SmithTestSupport.furnace());
		OrderKey key = new OrderKey(camp, GearNeed.AXE, 0);
		GearCraft craft = new GearCraft("minecraft:diamond_axe", key, 0, List.of(
				new ItemCount("minecraft:diamond", 3), new ItemCount("minecraft:stick", 2)));
		VillageClaims.data(h.getLevel().getServer()).update(v -> v.withSmithCraft(furnace, new SmithCraft(craft, camp)));
		GearOrders.claim(key, smith.getUUID(), h.getTick(), true);
	}

	private static boolean smithServes(GameTestHelper h) {
		var server = h.getLevel().getServer();
		return VillageClaims.current(server).map(v -> SmithService.servesNow(server, v)).orElse(false);
	}

	private static boolean fallbackStarted(GameTestHelper h, Villager miner) {
		if (SelfCraftTestSupport.record(h, miner.getUUID()).isPresent()) return true;
		var st = SelfCraftRuntime.get(h.getLevel().getServer()).peek(miner.getUUID());
		return st != null && st.fetching != null;
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
	@GameTest(environment = "villageheart-gametest:smith_craft_night", structure = YARD, skyAccess = true, maxTicks = 7000)
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
			setDayTime(h, 8300);
			log("iron pickaxe seeded at 1200, day 8300");
		}).thenExecuteFor(3200, () -> {
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
		BlockPos secondAnvil = BuildingTestSupport.inside(SmithTestSupport.SMITHY, 3, 0, 2);
		BlockPos secondBlock = BuildingTestSupport.inside(SmithTestSupport.CAMP, 3, 0, 1);
		scene(h).thenExecute(() -> {
			h.setBlock(secondFurnace, Blocks.BLAST_FURNACE);
			h.setBlock(secondAnvil, Blocks.ANVIL);
			SmithTestSupport.addLumberCamp(h);
			h.setBlock(secondBlock, JobBlocks.BLOCKS.get(JobBlockType.CHOPPING_BLOCK));
		}).thenWaitUntil(() -> {
			BuildingTestSupport.assertStatus(h, SmithTestSupport.campMarker(), dev.villageheart.block.MarkerStatus.VALID);
			h.assertValueEqual(2, contents(h, SmithTestSupport.smithyMarker(), BuildingType.SMITHY, BuildingRequirements.BLAST_FURNACE), "furnaces");
			h.assertValueEqual(2, contents(h, SmithTestSupport.smithyMarker(), BuildingType.SMITHY, BuildingRequirements.ANVIL), "anvils");
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
			standAtFurnace(h, smiths[0]);
			standAtFurnace(h, smiths[1]);
		}).thenWaitUntil(() -> h.assertValueEqual(2, chestCount(h, SmithTestSupport.campChest(), Items.IRON_AXE), "axes in the camp")
		).thenExecute(() -> {
			h.assertValueEqual(1L, craftUnits(h, smiths[0]), "the first smith's axe");
			h.assertValueEqual(1L, craftUnits(h, smiths[1]), "the second smith's axe");
			h.assertTrue(leasesAxe(h, jacks[0]) && leasesAxe(h, jacks[1]), "both lumberjacks lease an axe");
			GearOrders.reset();
			resetCamp(h);
			putStore(h, Items.IRON_INGOT, 6);
			putStore(h, Items.STICK, 4);
			// The first pair used the afternoon. The next morning is a full work day; stepping the clock backward
			// leaves the smiths out of work.
			long now = h.getLevel().getDayTime();
			long day = Math.floorMod(now, 24000L);
			setDayTime(h, now - day + 24000L + 1000L);
			standAtFurnace(h, smiths[0]);
			standAtFurnace(h, smiths[1]);
		}).thenWaitUntil(() -> h.assertTrue(craftAt(h, smiths[0]) && craftAt(h, smiths[1]), "both crafts committed")
		).thenExecute(() -> h.assertTrue(Workers.fire(h.getLevel(), smiths[1]), "the second smith is fired")
		).thenWaitUntil(() -> h.assertValueEqual(2, chestCount(h, SmithTestSupport.campChest(), Items.IRON_AXE), "axes after the adoption")
		).thenExecute(() -> {
			h.assertTrue(leasesAxe(h, jacks[0]) && leasesAxe(h, jacks[1]), "both still lease an axe");
			VillageTestSupport.done(h);
		});
	}

	private static int craftProgress(GameTestHelper h) {
		HeartLocation furnace = BuildingTestSupport.loc(h, SmithTestSupport.furnace());
		return VillageClaims.current(h.getLevel().getServer()).flatMap(v -> v.smithCraft(furnace)).map(c -> c.craft().progress()).orElse(-1);
	}

	private static void fillChest(GameTestHelper h, BlockPos rel, net.minecraft.world.item.Item item) {
		var box = (net.minecraft.world.Container) h.getLevel().getBlockEntity(h.absolutePos(rel));
		for (int i = 0; i < box.getContainerSize(); i++) box.setItem(i, new ItemStack(item, item.getDefaultMaxStackSize()));
		box.setChanged();
	}

	private static void freeChestSlot(GameTestHelper h, BlockPos rel, int slot) {
		var box = (net.minecraft.world.Container) h.getLevel().getBlockEntity(h.absolutePos(rel));
		box.setItem(slot, ItemStack.EMPTY);
		box.setChanged();
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

	/**
	 * An iron golem never takes a creeper as its target (owner decision: nobody attacks creepers). Vanilla's nearest-attackable goal
	 * already skips creepers, so the test forces the other routes: a direct setTarget, and the hurt-by goal after a creeper hurts it.
	 * The control is a zombie, which the same calls do hand over.
	 */
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
			var zombie = h.spawn(EntityType.ZOMBIE, new BlockPos(4, 0, 26));
			zombie.setNoAi(true);
			g.setTarget(zombie);
			h.assertTrue(g.getTarget() == zombie, "control: a zombie can be the golem's target");
			g.setTarget(null);
			zombie.discard();
			g.setTarget(c);
			h.assertTrue(!(g.getTarget() instanceof Creeper), "setTarget handed the golem a creeper");
			g.setLastHurtByMob(c);
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

	/**
	 * The golem has to stand on the wait spot, not just near it. A wide mob's path ends on a node corner and counts as done about 0.7
	 * short of it, so approaching from some sides it stopped 1.4 to 1.7 blocks away, outside the 1.5 radius, and sat there until the
	 * walk timed out (golemWithoutIronWaits flaked about one run in three). This golem starts on the corner a stalled one was seen
	 * on (spot + (2.0, 1.0), 1.58 from the centre): it has to come inside the radius within 150 ticks.
	 */
	@GameTest(environment = "villageheart-gametest:smith_golem_spot", structure = YARD, skyAccess = true, maxTicks = 600)
	public void golemStandsOnTheWaitSpot(GameTestHelper h) {
		IronGolem[] golem = new IronGolem[1];
		BlockPos[] spot = new BlockPos[1];
		double[] closest = {Double.MAX_VALUE};
		scene(h).thenExecute(() -> {
			SmithTestSupport.sweepGolems(h.getLevel(), h.absolutePos(BlockPos.ZERO), Balance.CLAIM_RADIUS + 8);
			putStore(h, Items.IRON_INGOT, 3);
			setDayTime(h, 2000);
			// The smith only has to be working so that the golem is offered the spot. Frozen, it cannot walk past the golem and nudge
			// it onto the spot, which would hide the stall this test is about.
			hire(h).setNoAi(true);
			var scan = SmithyLayout.scan(h.getLevel().getServer(), BuildingTestSupport.loc(h, SmithTestSupport.smithyMarker())).orElseThrow();
			spot[0] = SmithyLayout.waitSpot(h.getLevel(), scan).orElseThrow();
			golem[0] = summonHurt(h, spot[0], 40f);
			golem[0].snapTo(spot[0].getX() + 2.0, spot[0].getY(), spot[0].getZ() + 1.0, 90f, 0f);
			double start = golem[0].position().distanceTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(spot[0]));
			h.assertTrue(start > Balance.GOLEM_WAIT_RADIUS && start < 1.7, "the golem starts just outside the radius: " + start);
			log("spot " + spot[0] + " golem starts " + start + " from its centre");
		}).thenExecuteFor(150, () -> closest[0] = Math.min(closest[0],
				golem[0].position().distanceTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(spot[0])))
		).thenExecute(() -> {
			// About 1.6 blocks to walk. A stalled golem waits for the walk timeout (1200 ticks) and then a cooldown.
			h.assertTrue(closest[0] <= Balance.GOLEM_WAIT_RADIUS, "no arrival within 150 ticks, closest " + closest[0]);
			VillageTestSupport.done(h);
		});
	}

	// ---- M19 review round 1: items 5, 7 and 9 ----------------------------------------------------------

	/**
	 * Damaged gear in Storage is fetched, mended at the anvil and put back (the scan used to list it and the fetch skipped every Storage
	 * hit, so the smith walked to the chest and back forever and never got to anything below repairs).
	 */
	@GameTest(environment = "villageheart-gametest:smith_storage_repair", structure = YARD, skyAccess = true, maxTicks = 6000)
	public void repairsStorageGear(GameTestHelper h) {
		Villager[] smith = new Villager[1];
		scene(h).thenExecute(() -> {
			ItemStack sword = new ItemStack(Items.IRON_SWORD);
			sword.setDamageValue(150);
			StockpileTestSupport.container(h, SmithTestSupport.storeChest1()).setItem(0, sword);
			putStore(h, Items.IRON_INGOT, 5);
			setDayTime(h, 1000);
			smith[0] = hire(h);
		}).thenWaitUntil(() -> {
			ItemStack s = storedSword(h);
			h.assertTrue(s != null && s.getDamageValue() < 62, "storage sword " + (s == null ? "missing" : s.getDamageValue()) + " world "
					+ SmithTestSupport.worldCount(h, Items.IRON_SWORD) + " carried " + smith[0].getInventory().countItem(Items.IRON_SWORD)
					+ " units " + SmithRuntime.unitsOf(h.getLevel().getServer(), smith[0].getUUID()).size());
		}).thenExecute(() -> {
			h.assertValueEqual(1, SmithTestSupport.worldCount(h, Items.IRON_SWORD), "one sword in the world");
			h.assertValueEqual(3L, count(h, Items.IRON_INGOT), "two ingots spent");
			h.assertValueEqual(0L, SmithTestSupport.groundCount(h, Items.IRON_SWORD), "swords on the ground");
			VillageTestSupport.done(h);
		});
	}

	private static ItemStack storedSword(GameTestHelper h) {
		for (BlockPos chest : new BlockPos[] {SmithTestSupport.storeChest1(), SmithTestSupport.storeChest2()}) {
			var c = StockpileTestSupport.container(h, chest);
			for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).is(Items.IRON_SWORD)) return c.getItem(i);
		}
		return null;
	}

	/**
	 * A smith whose eight slots are always full never loses gear: the sword stays in the Barracks chest (no fetch without room, and
	 * nothing is taken out of a chest it cannot carry), and once there is room it is mended as before.
	 */
	@GameTest(environment = "villageheart-gametest:smith_full_inventory", structure = YARD, skyAccess = true, maxTicks = 6000)
	public void noGearIsLostWhenTheSmithIsFull(GameTestHelper h) {
		Villager[] smith = new Villager[1];
		boolean[] fill = {true};
		scene(h).thenExecute(() -> SmithTestSupport.addBarracks(h)).thenWaitUntil(() ->
				BuildingTestSupport.assertStatus(h, SmithTestSupport.barracksMarker(), dev.villageheart.block.MarkerStatus.VALID)
		).thenExecute(() -> {
			var chest = (net.minecraft.world.Container) h.getLevel().getBlockEntity(h.absolutePos(SmithTestSupport.barracksChest()));
			ItemStack sword = new ItemStack(Items.IRON_SWORD);
			sword.setDamageValue(200);
			chest.setItem(0, sword);
			putStore(h, Items.IRON_INGOT, 6);
			// Storage is full, so the junk in the smith's pockets cannot be handed in (a deposit trip ends FULL) and he works with full
			// pockets. Otherwise a smith with junk would just keep depositing it.
			for (BlockPos rel : new BlockPos[] {SmithTestSupport.storeChest1(), SmithTestSupport.storeChest2()}) {
				var box = StockpileTestSupport.container(h, rel);
				for (int i = 0; i < box.getContainerSize(); i++) if (box.getItem(i).isEmpty()) box.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
				box.setChanged();
			}
			setDayTime(h, 1000);
			smith[0] = hire(h);
		}).thenExecuteFor(1500, () -> {
			var inv = smith[0].getInventory();
			for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).isEmpty()) inv.setItem(i, new ItemStack(Items.DIRT));
			h.assertValueEqual(1, SmithTestSupport.worldCount(h, Items.IRON_SWORD), "the sword was lost while the smith was full");
			h.assertValueEqual(0L, SmithTestSupport.groundCount(h, Items.IRON_SWORD), "the sword was dropped");
		}).thenExecute(() -> {
			var chest = (net.minecraft.world.Container) h.getLevel().getBlockEntity(h.absolutePos(SmithTestSupport.barracksChest()));
			h.assertTrue(chest.getItem(0).is(Items.IRON_SWORD) && chest.getItem(0).getDamageValue() == 200, "the sword is still in the chest, unrepaired");
			h.assertValueEqual(6L, count(h, Items.IRON_INGOT), "no ingot was spent or lost");
			var inv = smith[0].getInventory();
			for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(Items.DIRT)) inv.setItem(i, ItemStack.EMPTY);
		}).thenWaitUntil(() -> {
			var chest = (net.minecraft.world.Container) h.getLevel().getBlockEntity(h.absolutePos(SmithTestSupport.barracksChest()));
			ItemStack s = chest.getItem(0);
			h.assertTrue(s.is(Items.IRON_SWORD) && s.getDamageValue() == 14, "barracks sword " + (s.isEmpty() ? "empty" : s.getDamageValue()));
		}).thenExecute(() -> VillageTestSupport.done(h));
	}

	/**
	 * An in-place repair marks the chest's chunk for saving. The chunk's unsaved flag is cleared every tick; on each tick where the
	 * damage changed it must be set again, or the repair would be lost on a save before the chunk is dirtied by something else.
	 */
	@GameTest(environment = "villageheart-gametest:smith_repair_saved", structure = YARD, skyAccess = true, maxTicks = 4000)
	public void inPlaceRepairMarksTheChunkForSaving(GameTestHelper h) {
		int[] last = {200};
		int[] changes = {0};
		boolean[] missed = {false};
		scene(h).thenExecute(() -> {
			ItemStack plate = new ItemStack(Items.IRON_CHESTPLATE);
			plate.setDamageValue(200);
			StockpileTestSupport.container(h, SmithTestSupport.smithyChest()).setItem(0, plate);
			putStore(h, Items.IRON_INGOT, 5);
			setDayTime(h, 1000);
			hire(h);
		}).thenWaitUntil(() -> {
			var chunk = h.getLevel().getChunkAt(h.absolutePos(SmithTestSupport.smithyChest()));
			int d = StockpileTestSupport.container(h, SmithTestSupport.smithyChest()).getItem(0).getDamageValue();
			if (d != last[0]) {
				changes[0]++;
				if (!chunk.isUnsaved()) missed[0] = true;
				last[0] = d;
			}
			chunk.tryMarkSaved();
			h.assertTrue(d == 20, "damage " + d);
		}).thenExecute(() -> {
			h.assertTrue(changes[0] >= 3, "repair steps seen: " + changes[0]);
			h.assertFalse(missed[0], "a repair step left the chunk unmarked for saving");
			VillageTestSupport.done(h);
		});
	}

	/**
	 * A player swaps the scanned sword for another damaged item while the smith is at it. The job's iron is not spent on it: the repair
	 * ends, and the new item is a job of its own (here it needs leather, which the village has none of).
	 */
	@GameTest(environment = "villageheart-gametest:smith_repair_swap", structure = YARD, skyAccess = true, maxTicks = 4000)
	public void repairStopsWhenTheItemIsSwapped(GameTestHelper h) {
		Villager[] smith = new Villager[1];
		scene(h).thenExecute(() -> {
			ItemStack sword = new ItemStack(Items.IRON_SWORD);
			sword.setDamageValue(150);
			StockpileTestSupport.container(h, SmithTestSupport.smithyChest()).setItem(0, sword);
			putStore(h, Items.IRON_INGOT, 10);
			setDayTime(h, 1000);
			smith[0] = hire(h);
		}).thenWaitUntil(() -> h.assertTrue(SmithRuntime.unitsOf(h.getLevel().getServer(), smith[0].getUUID()).stream().anyMatch(u -> u.kind().equals("REPAIR")),
				"the first repair unit")
		).thenExecute(() -> {
			ItemStack plate = new ItemStack(Items.LEATHER_CHESTPLATE);
			plate.setDamageValue(60);
			StockpileTestSupport.container(h, SmithTestSupport.smithyChest()).setItem(0, plate);
		}).thenExecuteFor(600, () -> { }).thenExecute(() -> {
			ItemStack s = StockpileTestSupport.container(h, SmithTestSupport.smithyChest()).getItem(0);
			h.assertTrue(s.is(Items.LEATHER_CHESTPLATE) && s.getDamageValue() == 60, "the swapped chestplate was repaired with iron: " + s);
			h.assertValueEqual(9L, count(h, Items.IRON_INGOT), "only the one ingot of the real job was spent");
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

	// ---- M19 review round 1: items 2, 3 and 6 ---------------------------------------------------------

	/**
	 * A saved craft whose refund is pending (a bad save, or a refund that could not finish) is never resumed: the smith gives its items
	 * back and crafts the pickaxe afresh. Resuming would finish it with no COMMIT, and the refund would never be acted on.
	 */
	@GameTest(environment = "villageheart-gametest:smith_refund_pending", structure = YARD, skyAccess = true, maxTicks = 6000)
	public void refundPendingCraftIsRefundedNotResumed(GameTestHelper h) {
		Villager[] smith = new Villager[1];
		scene(h).thenExecute(() -> SmithTestSupport.addMine(h)).thenWaitUntil(() -> {
			BuildingTestSupport.assertStatus(h, SmithTestSupport.mineMarker(), dev.villageheart.block.MarkerStatus.VALID);
			BuildingTestSupport.assertStatus(h, MineTestSupport.shaftMarker(0, SmithTestSupport.SHAFT), dev.villageheart.block.MarkerStatus.VALID);
		}).thenExecute(() -> {
			GearOrders.reset();
			HeartLocation mine = BuildingTestSupport.loc(h, SmithTestSupport.mineMarker());
			HeartLocation furnace = BuildingTestSupport.loc(h, SmithTestSupport.furnace());
			GearCraft craft = new GearCraft("minecraft:iron_pickaxe", new OrderKey(mine, GearNeed.PICKAXE, 0), 0,
					List.of(new ItemCount("minecraft:iron_ingot", 3), new ItemCount("minecraft:stick", 2)));
			VillageClaims.data(h.getLevel().getServer()).update(v -> v.withSmithCraft(furnace, new SmithCraft(craft, mine, true)));
			setDayTime(h, 1000);
			SmithTestSupport.hireMiner(h);
			smith[0] = hire(h);
		}).thenWaitUntil(() -> h.assertTrue(SmithTestSupport.chestHas(h, SmithTestSupport.mineChest(), Items.IRON_PICKAXE), "iron pickaxe in the mine chest")
		).thenExecute(() -> {
			var units = SmithRuntime.unitsOf(h.getLevel().getServer(), smith[0].getUUID());
			long commits = units.stream().filter(u -> u.kind().equals("COMMIT")).count();
			long crafts = units.stream().filter(u -> u.kind().equals("CRAFT")).count();
			h.assertValueEqual(1L, commits, "the pickaxe was committed afresh after the refund: " + units);
			h.assertValueEqual(1L, crafts, "one craft: " + units);
			h.assertTrue(smithCraftsEmpty(h), "the record is gone");
			h.assertValueEqual(0L, SmithTestSupport.groundCount(h, Items.IRON_PICKAXE), "pickaxes on the ground");
			VillageTestSupport.done(h);
		});
	}

	/** The compare-and-insert starts refuse to overwrite: a second golem build keeps the first one's progress, a second craft the first record. */
	@GameTest(environment = "villageheart-gametest:smith_cas", structure = YARD, skyAccess = true, maxTicks = 400)
	public void startsRefuseToOverwrite(GameTestHelper h) {
		scene(h).thenExecute(() -> {
			var server = h.getLevel().getServer();
			HeartLocation smithy = smithyAt(h);
			seedBuild(h, 3000);
			h.assertFalse(dev.villageheart.worker.smith.GolemBuilds.start(server, smithy), "a second build was started in the same Smithy");
			h.assertValueEqual(3000, buildProgress(h), "the first build's progress was reset");
			dev.villageheart.worker.smith.GolemBuilds.refund(server, smithy);
			h.assertTrue(dev.villageheart.worker.smith.GolemBuilds.start(server, smithy), "a free Smithy starts a build");
			h.assertValueEqual(0, buildProgress(h), "fresh build");
			dev.villageheart.worker.smith.GolemBuilds.refund(server, smithy);
			HeartLocation mine = BuildingTestSupport.loc(h, SmithTestSupport.mineMarker());
			HeartLocation camp = BuildingTestSupport.loc(h, SmithTestSupport.campMarker());
			HeartLocation furnace = BuildingTestSupport.loc(h, SmithTestSupport.furnace());
			SmithCraft pick = new SmithCraft(new GearCraft("minecraft:iron_pickaxe", new OrderKey(mine, GearNeed.PICKAXE, 0), 40,
					List.of(new ItemCount("minecraft:iron_ingot", 3), new ItemCount("minecraft:stick", 2))), mine);
			SmithCraft axe = new SmithCraft(new GearCraft("minecraft:iron_axe", new OrderKey(camp, GearNeed.AXE, 0), 0,
					List.of(new ItemCount("minecraft:iron_ingot", 3), new ItemCount("minecraft:stick", 2))), camp);
			h.assertTrue(dev.villageheart.worker.smith.SmithCrafts.commit(server, furnace, pick), "a free furnace takes a craft");
			h.assertFalse(dev.villageheart.worker.smith.SmithCrafts.commit(server, furnace, axe), "a second craft replaced the first");
			h.assertValueEqual(pick, VillageClaims.current(server).flatMap(v -> v.smithCraft(furnace)).orElseThrow(), "the saved pickaxe");
			dev.villageheart.worker.smith.SmithCrafts.refund(server, furnace, pick);
			VillageTestSupport.done(h);
		});
	}

	/**
	 * Two smiths in one Smithy, 10 villagers and 72 ingots: exactly one build is started and 36 ingots stay in the world while it runs
	 * (the second smith used to fetch another 36 and overwrite the first one's progress, so a whole golem's materials vanished).
	 */
	@GameTest(environment = "villageheart-gametest:smith_two_golem_smiths", structure = YARD, skyAccess = true, maxTicks = 4000)
	public void twoSmithsStartOneGolemBuild(GameTestHelper h) {
		BlockPos secondFurnace = BuildingTestSupport.inside(SmithTestSupport.SMITHY, 3, 0, 1);
		BlockPos secondAnvil = BuildingTestSupport.inside(SmithTestSupport.SMITHY, 3, 0, 2);
		int[] builds = {0};
		scene(h).thenExecute(() -> {
			h.setBlock(secondFurnace, Blocks.BLAST_FURNACE);
			h.setBlock(secondAnvil, Blocks.ANVIL);
		}).thenWaitUntil(() -> h.assertValueEqual(2, contents(h, SmithTestSupport.smithyMarker(), BuildingType.SMITHY, BuildingRequirements.BLAST_FURNACE), "furnaces")
		).thenExecute(() -> {
			SmithTestSupport.sweepGolems(h.getLevel(), h.absolutePos(BlockPos.ZERO), Balance.CLAIM_RADIUS + 8);
			SmithTestSupport.pen(h, 4);
			for (int i = 0; i < 4; i++) WorkerTestSupport.spawnVillager(h, new BlockPos(1 + (i % 3), 1, 14));
			putStore(h, Items.IRON_INGOT, 72);
			putStore(h, Items.CARVED_PUMPKIN, 2);
			setDayTime(h, 1000);
			hireEitherFurnace(h);
			hireEitherFurnace(h);
		}).thenExecuteFor(1500, () -> {
			int saved = VillageClaims.current(h.getLevel().getServer()).map(v -> v.golemBuilds().size()).orElse(0);
			builds[0] = Math.max(builds[0], saved);
			h.assertTrue(saved <= 1, "two builds are saved: " + saved);
			if (builds[0] >= 1) h.assertTrue(SmithTestSupport.ironTotal(h) >= 36, "a second build took ingots, iron left " + SmithTestSupport.ironTotal(h));
		}).thenExecute(() -> {
			h.assertValueEqual(1, builds[0], "a build was started");
			h.assertValueEqual(36, SmithTestSupport.ironTotal(h), "one build's worth of ingots left");
			VillageTestSupport.done(h);
		});
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

	/** Puts the smith on their furnace so the two of them are not left in the same block after a delivery. */
	private static void standAtFurnace(GameTestHelper h, Villager smith) {
		var data = smith.getAttached(WorkerAttachments.WORKER);
		if (data == null) return;
		BlockPos f = SmithyLayout.block(data.jobBlock());
		smith.teleportTo(f.getX() + 0.5, f.getY(), f.getZ() - 0.5);
		smith.setDeltaMovement(0, 0, 0);
		smith.getNavigation().stop();
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

	// ---- M19 round 2: unknown saved item ids ------------------------------------------------------------

	/**
	 * A saved craft whose committed list holds an id that is not a registered item (a removed or modded item) used to hang the server:
	 * the id resolved to AIR, an AIR stack reports a count of 0, and the refund loop never ended. The record is refund-pending, so the 100-tick
	 * {@code retryPending} sweep refunds it. The unknown id is dropped with a warning and everything else comes back.
	 */
	@GameTest(environment = "villageheart-gametest:smith_unknown_id", structure = YARD, skyAccess = true, maxTicks = 600)
	public void refundSkipsUnknownItemId(GameTestHelper h) {
		HeartLocation furnace = BuildingTestSupport.loc(h, SmithTestSupport.furnace());
		long[] sticks = new long[1];
		scene(h).thenExecute(() -> {
			sticks[0] = count(h, Items.STICK);
			dev.villageheart.core.gear.OrderKey key = new dev.villageheart.core.gear.OrderKey(
					BuildingTestSupport.loc(h, SmithTestSupport.smithyMarker()), GearNeed.PICKAXE, 0);
			var craft = new dev.villageheart.core.smith.SmithCraft(new dev.villageheart.core.gear.GearCraft("minecraft:stone_pickaxe", key, 100,
					List.of(new dev.villageheart.core.gear.ItemCount("minecraft:no_such_item", 3), new dev.villageheart.core.gear.ItemCount("minecraft:stick", 2))),
					furnace, true);
			VillageClaims.data(h.getLevel().getServer()).update(vs -> vs.withSmithCraft(furnace, craft));
		}).thenWaitUntil(() -> {
			h.assertTrue(VillageClaims.current(h.getLevel().getServer()).map(v -> v.smithCraft(furnace).isEmpty()).orElse(false), "the record is still there");
		}).thenExecute(() -> {
			h.assertValueEqual(sticks[0] + 2, count(h, Items.STICK), "the sticks were refunded");
			VillageTestSupport.done(h);
		});
	}
}
