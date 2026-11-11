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

import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.gear.CraftTier;
import dev.villageheart.core.gear.GearNeed;
import dev.villageheart.core.gear.ToolRecipe;
import dev.villageheart.core.gear.ToolRecipes;
import dev.villageheart.core.smith.RepairRules;
import dev.villageheart.core.smith.SmithKind;
import dev.villageheart.core.smith.SmithRecipe;
import dev.villageheart.core.smith.SmithRecipes;
import dev.villageheart.golem.GolemGate;
import dev.villageheart.worker.gear.SelfCraftSwitch;
import dev.villageheart.worker.smith.SmithRuntime;
import dev.villageheart.world.VillageClaims;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
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
