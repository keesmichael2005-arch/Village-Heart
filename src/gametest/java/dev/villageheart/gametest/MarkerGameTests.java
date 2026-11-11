package dev.villageheart.gametest;

import dev.villageheart.block.JobBlocks;
import dev.villageheart.block.MarkerBlock;
import dev.villageheart.block.MarkerBlocks;
import dev.villageheart.block.MarkerStatus;
import dev.villageheart.block.ModBlocks;
import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.building.JobBlockType;
import dev.villageheart.item.ModCreativeTab;
import dev.villageheart.item.ModItems;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

import dev.villageheart.core.Balance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.gamerules.GameRules;
import dev.villageheart.core.building.ShapedSpec;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.ArrayList;
import java.util.List;

/** Layer 2: markers and job blocks. All tests share the environment {@code villageheart-gametest:markers}. */
public class MarkerGameTests {
	private static final String ENV = "villageheart-gametest:markers";

	private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("villageheart", path); }

	/** T-L2-13 */
	@GameTest(environment = ENV)
	public void registrySmoke(GameTestHelper h) {
		for (BuildingType t : BuildingType.values()) {
			Identifier id = id(t.markerId());
			h.assertTrue(BuiltInRegistries.BLOCK.containsKey(id), "block " + id);
			h.assertTrue(BuiltInRegistries.ITEM.containsKey(id), "item " + id);
			Block b = BuiltInRegistries.BLOCK.getValue(id);
			h.assertTrue(b instanceof MarkerBlock, "MarkerBlock " + id);
			MarkerBlock mb = (MarkerBlock) b;
			h.assertValueEqual(t, mb.type(), "type of " + id);
			h.assertValueEqual(12, mb.getStateDefinition().getPossibleStates().size(), "states of " + id);
			h.assertValueEqual(MarkerStatus.UNCHECKED, mb.defaultBlockState().getValue(MarkerBlock.STATUS), "default status");
			BlockState rotated = mb.defaultBlockState().setValue(MarkerBlock.STATUS, MarkerStatus.VALID).rotate(Rotation.CLOCKWISE_90);
			h.assertValueEqual(Direction.EAST, rotated.getValue(MarkerBlock.FACING), "rotated facing");
			h.assertValueEqual(MarkerStatus.VALID, rotated.getValue(MarkerBlock.STATUS), "rotate keeps status");
		}
		for (JobBlockType t : JobBlockType.values()) {
			h.assertTrue(BuiltInRegistries.BLOCK.containsKey(id(t.id())), "block " + t.id());
			h.assertTrue(BuiltInRegistries.ITEM.containsKey(id(t.id())), "item " + t.id());
		}
		h.assertTrue(BuiltInRegistries.ITEM.containsKey(id("blank_marker")), "blank_marker");

		CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.getValue(ModCreativeTab.ID);
		h.assertTrue(tab != null, "creative tab villageheart:main");
		tab.buildContents(new CreativeModeTab.ItemDisplayParameters(h.getLevel().enabledFeatures(), true, h.getLevel().registryAccess()));
		List<Item> expected = new ArrayList<>();
		expected.add(ModBlocks.VILLAGE_HEART_ITEM);
		expected.add(ModItems.BLANK_MARKER);
		for (BuildingType t : BuildingType.values()) expected.add(MarkerBlocks.ITEMS.get(t));
		for (JobBlockType t : JobBlockType.values()) expected.add(JobBlocks.ITEMS.get(t));
		for (int i = 0; i < dev.villageheart.core.blueprint.Blueprints.all().size(); i++) expected.add(ModItems.BLUEPRINT); // M20: one Blueprint per blueprint
		List<Item> actual = new ArrayList<>();
		for (ItemStack s : tab.getDisplayItems()) actual.add(s.getItem());
		h.assertTrue(!actual.isEmpty(), "creative tab contents non-empty");
		h.assertValueEqual(expected, actual, "creative tab items in order");
		h.succeed();
	}

	// ---------------------------------------------------------------- recipes

	private static Item item(String id) { return BuiltInRegistries.ITEM.getValue(Identifier.parse(id)); }

	/** A stack for a recipe ingredient string; tags resolve to the first or last member. */
	private static ItemStack stackFor(String ingredient, boolean last, boolean damaged) {
		Item item;
		if (ingredient.startsWith("#")) {
			TagKey<Item> tag = TagKey.create(Registries.ITEM, Identifier.parse(ingredient.substring(1)));
			Item found = null;
			for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
				if (found == null || last) found = holder.value();
				if (!last) break;
			}
			if (found == null) throw new IllegalStateException("empty tag " + ingredient);
			item = found;
		} else {
			item = item(ingredient);
		}
		ItemStack s = new ItemStack(item);
		if (damaged && s.isDamageableItem()) s.setDamageValue(s.getMaxDamage() / 2);
		return s;
	}

	private static List<ItemStack> grid(List<String> ingredients, boolean last, boolean damaged) {
		List<ItemStack> g = new ArrayList<>();
		for (String i : ingredients) g.add(i == null ? ItemStack.EMPTY : stackFor(i, last, damaged));
		return g;
	}

	/** All villageheart crafting recipes whose pattern matches this 3x3 grid. */
	private static List<String> modMatches(GameTestHelper h, List<ItemStack> grid) {
		CraftingInput input = CraftingInput.of(3, 3, grid);
		List<String> ids = new ArrayList<>();
		for (RecipeHolder<?> holder : h.getLevel().recipeAccess().getRecipes()) {
			if (holder.value() instanceof CraftingRecipe cr && holder.id().identifier().getNamespace().equals("villageheart")
					&& cr.matches(input, h.getLevel())) ids.add(holder.id().identifier().getPath());
		}
		return ids;
	}

	private static List<String> allMatches(GameTestHelper h, List<ItemStack> grid) {
		CraftingInput input = CraftingInput.of(3, 3, grid);
		List<String> ids = new ArrayList<>();
		for (RecipeHolder<?> holder : h.getLevel().recipeAccess().getRecipes()) {
			if (holder.value() instanceof CraftingRecipe cr && cr.matches(input, h.getLevel())) ids.add(holder.id().identifier().toString());
		}
		return ids;
	}

	private static void expectRecipe(GameTestHelper h, List<ItemStack> grid, String resultPath, Item resultItem, int count, String what) {
		CraftingInput input = CraftingInput.of(3, 3, grid);
		var found = h.getLevel().recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, h.getLevel());
		h.assertTrue(found.isPresent(), what + ": a recipe matches");
		h.assertValueEqual("villageheart:" + resultPath, found.get().id().identifier().toString(), what + ": recipe id");
		ItemStack out = found.get().value().assemble(input, h.getLevel().registryAccess());
		h.assertTrue(out.is(resultItem), what + ": result item " + out);
		h.assertValueEqual(count, out.getCount(), what + ": result count");
		h.assertValueEqual(List.of("villageheart:" + resultPath), allMatches(h, grid), what + ": exactly one recipe matches");
	}

	/** T-L2-1 */
	@GameTest(environment = ENV)
	public void everyMarkerRecipeCrafts(GameTestHelper h) {
		for (BuildingType t : BuildingType.values()) {
			expectRecipe(h, grid(t.ring().grid(), false, false), t.markerId(), MarkerBlocks.ITEMS.get(t), 1, t.id() + " first-of-tag");
		}
		h.succeed();
	}

	/** T-L2-1b */
	@GameTest(environment = ENV)
	public void markerRecipesAcceptTagVariants(GameTestHelper h) {
		for (BuildingType t : BuildingType.values()) {
			expectRecipe(h, grid(t.ring().grid(), true, false), t.markerId(), MarkerBlocks.ITEMS.get(t), 1, t.id() + " last-of-tag");
			expectRecipe(h, grid(t.ring().grid(), true, true), t.markerId(), MarkerBlocks.ITEMS.get(t), 1, t.id() + " damaged tools");
		}
		h.succeed();
	}

	/** T-L2-1c */
	@GameTest(environment = ENV)
	public void markerRecipeNegatives(GameTestHelper h) {
		for (BuildingType t : BuildingType.values()) {
			String mine = t.markerId();
			List<String> ring = t.ring().grid();
			List<String> frame = new ArrayList<>(ring);
			frame.set(4, "minecraft:item_frame");
			h.assertTrue(!modMatches(h, grid(frame, false, false)).contains(mine), t.id() + ": item frame centre");
			h.assertTrue(modMatches(h, grid(frame, false, false)).isEmpty(), t.id() + ": item frame centre matches nothing of ours");
			List<String> empty = new ArrayList<>(ring);
			empty.set(4, null);
			h.assertTrue(modMatches(h, grid(empty, false, false)).isEmpty(), t.id() + ": empty centre");
			if (!t.ring().corner().equals(t.ring().left())) {
				List<String> swapped = new ArrayList<>(ring);
				for (int c : new int[] {0, 2, 6, 8}) swapped.set(c, t.ring().left());
				for (int e : new int[] {1, 3, 5, 7}) swapped.set(e, t.ring().corner());
				h.assertTrue(!modMatches(h, grid(swapped, false, false)).contains(mine), t.id() + ": corners and edges swapped");
			}
			if (!t.ring().top().equals(t.ring().bottom())) {
				List<String> flipped = new ArrayList<>(ring);
				flipped.set(1, t.ring().bottom());
				flipped.set(7, t.ring().top());
				h.assertTrue(!modMatches(h, grid(flipped, false, false)).contains(mine), t.id() + ": tool on the bottom edge");
				List<String> side = new ArrayList<>(ring);
				side.set(1, t.ring().left());
				side.set(3, t.ring().top());
				h.assertTrue(!modMatches(h, grid(side, false, false)).contains(mine), t.id() + ": tool on the left edge");
			}
		}
		h.succeed();
	}

	private static List<String> blankGrid(String frameCentre) {
		List<String> g = new ArrayList<>();
		for (int i = 0; i < 9; i++) g.add(i == 4 ? frameCentre : "#minecraft:planks");
		return g;
	}

	/** T-L2-2 */
	@GameTest(environment = ENV)
	public void blankMarkerRecipe(GameTestHelper h) {
		List<ItemStack> g = new ArrayList<>(grid(blankGrid("minecraft:item_frame"), false, false));
		Item[] planks = {Items.OAK_PLANKS, Items.BIRCH_PLANKS, Items.SPRUCE_PLANKS};
		int n = 0;
		for (int i = 0; i < 9; i++) if (i != 4) g.set(i, new ItemStack(planks[n++ % 3]));
		expectRecipe(h, g, "blank_marker", ModItems.BLANK_MARKER, Balance.BLANK_MARKER_YIELD, "blank marker");
		List<ItemStack> seven = new ArrayList<>(g);
		seven.set(0, ItemStack.EMPTY);
		h.assertTrue(modMatches(h, seven).isEmpty(), "7 planks do not craft");
		h.assertTrue(modMatches(h, grid(blankGrid("minecraft:leather"), false, false)).isEmpty(), "leather centre does not craft");
		h.succeed();
	}

	/** T-L2-3 */
	@GameTest(environment = ENV)
	public void jobBlockRecipes(GameTestHelper h) {
		ItemStack axe = new ItemStack(Items.STONE_AXE), log = new ItemStack(Items.OAK_LOG);
		for (int col : new int[] {0, 1, 2}) {
			List<ItemStack> g = new ArrayList<>();
			for (int i = 0; i < 9; i++) g.add(ItemStack.EMPTY);
			g.set(col, axe);
			g.set(3 + col, log);
			expectRecipe(h, g, "chopping_block", JobBlocks.ITEMS.get(JobBlockType.CHOPPING_BLOCK), 1, "chopping block col " + col);
		}
		List<ItemStack> table = grid(java.util.Arrays.asList(null, "minecraft:paper", null, "minecraft:brick", "minecraft:crafting_table", "minecraft:brick", null, null, null), false, false);
		expectRecipe(h, table, "builders_table", JobBlocks.ITEMS.get(JobBlockType.BUILDERS_TABLE), 1, "builders table");
		List<ItemStack> stall = new ArrayList<>(List.of(new ItemStack(Items.WHITE_WOOL), new ItemStack(Items.RED_WOOL), new ItemStack(Items.BLUE_WOOL),
				new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.EMERALD), new ItemStack(Items.SPRUCE_PLANKS),
				ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY));
		expectRecipe(h, stall, "market_stall", JobBlocks.ITEMS.get(JobBlockType.MARKET_STALL), 1, "market stall");
		h.succeed();
	}

	private static List<String> modIngredients(ShapedSpec spec) { return new ArrayList<>(spec.key().values()); }

	/** T-L2-4 */
	@GameTest(environment = ENV)
	public void modRecipesLoadAndResolve(GameTestHelper h) {
		Set<String> expected = new TreeSet<>();
		expected.add("village_heart");
		expected.add("blank_marker");
		expected.add("blueprint_quill");
		List<String> ingredients = new ArrayList<>(List.of("#minecraft:planks", "minecraft:item_frame"));
		for (BuildingType t : BuildingType.values()) { expected.add(t.markerId()); ingredients.addAll(modIngredients(t.ring().recipe("villageheart:x", 1))); }
		for (JobBlockType t : JobBlockType.values()) { expected.add(t.id()); ingredients.addAll(modIngredients(t.recipe())); }
		Set<String> actual = new TreeSet<>();
		for (RecipeHolder<?> holder : h.getLevel().recipeAccess().getRecipes()) {
			if (holder.id().identifier().getNamespace().equals("villageheart")) actual.add(holder.id().identifier().getPath());
		}
		h.assertValueEqual(expected, actual, "villageheart recipe ids");
		h.assertValueEqual(20, actual.size(), "recipe count");
		for (String ing : ingredients) {
			if (ing.startsWith("#")) {
				TagKey<Item> tag = TagKey.create(Registries.ITEM, Identifier.parse(ing.substring(1)));
				h.assertTrue(BuiltInRegistries.ITEM.getTagOrEmpty(tag).iterator().hasNext(), "tag non-empty: " + ing);
			} else {
				h.assertTrue(BuiltInRegistries.ITEM.containsKey(Identifier.parse(ing)), "item exists: " + ing);
			}
		}
		// the Heart recipe still crafts
		var holder = h.getLevel().recipeAccess().byKey(ResourceKey.create(Registries.RECIPE, id("village_heart")));
		h.assertTrue(holder.isPresent(), "village_heart recipe present");
		h.succeed();
	}

	/** T-L2-5 */
	@GameTest(environment = ENV)
	public void recipeAdvancementsUnlock(GameTestHelper h) {
		var server = h.getLevel().getServer();
		List<String> all = new ArrayList<>();
		all.add("blank_marker");
		for (BuildingType t : BuildingType.values()) all.add(t.markerId());
		for (JobBlockType t : JobBlockType.values()) all.add(t.id());
		for (String path : all) {
			var adv = server.getAdvancements().get(id("recipes/misc/" + path));
			h.assertTrue(adv != null, "advancement for " + path);
			ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, id(path));
			h.assertTrue(adv.value().rewards().recipes().contains(key), "advancement rewards recipe " + path);
		}
		ServerPlayer p = MarkerTestSupport.player(h, "unlocker", GameType.SURVIVAL, new BlockPos(1, 1, 1));
		try {
			for (String path : all) {
				h.assertTrue(!p.getRecipeBook().contains(ResourceKey.create(Registries.RECIPE, id(path))), "starts without " + path);
			}
			p.getInventory().add(new ItemStack(Items.ITEM_FRAME));
			p.inventoryMenu.broadcastChanges();
			for (String path : all) {
				boolean has = p.getRecipeBook().contains(ResourceKey.create(Registries.RECIPE, id(path)));
				h.assertTrue(has == path.equals("blank_marker"), "after item frame only blank_marker is unlocked: " + path + " has=" + has);
			}
			p.getInventory().add(new ItemStack(ModItems.BLANK_MARKER));
			p.inventoryMenu.broadcastChanges();
			for (String path : all) {
				h.assertTrue(p.getRecipeBook().contains(ResourceKey.create(Registries.RECIPE, id(path))), "after blank marker unlocked: " + path);
			}
		} finally {
			MarkerTestSupport.removePlayer(h, p);
		}
		h.succeed();
	}

	// ---------------------------------------------------------------- placement

	private static final BlockPos WALL = new BlockPos(3, 1, 3);

	private static void set(GameTestHelper h, BlockPos rel, BlockState state) {
		h.getLevel().setBlock(h.absolutePos(rel), state, 2);
	}

	private static void clearBox(GameTestHelper h, int x0, int y0, int z0, int x1, int y1, int z1) {
		for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) {
			set(h, new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
		}
		for (ItemEntity e : h.getEntities(EntityType.ITEM)) e.discard();
	}

	private static MarkerBlock markerAt(GameTestHelper h, BlockPos rel) {
		return h.getBlockState(rel).getBlock() instanceof MarkerBlock m ? m : null;
	}

	private static void requireDrops(GameTestHelper h) {
		h.assertTrue(h.getLevel().getGameRules().get(GameRules.BLOCK_DROPS), "precondition: block drops are on");
	}

	/** T-L2-6 */
	@GameTest(environment = ENV, maxTicks = 200)
	public void markerAttachesFacingOut(GameTestHelper h) {
		ServerPlayer p = MarkerTestSupport.player(h, "placer6", GameType.SURVIVAL, new BlockPos(1, 1, 1));
		try {
			for (Direction d : Direction.Plane.HORIZONTAL) {
				for (BuildingType t : BuildingType.values()) {
					clearBox(h, 1, 1, 1, 5, 3, 5);
					set(h, WALL, Blocks.STONE.defaultBlockState());
					MarkerTestSupport.look(p, d.getOpposite());
					ItemStack stack = new ItemStack(MarkerBlocks.ITEMS.get(t), 2);
					MarkerTestSupport.clickFace(h, p, WALL, d, stack);
					BlockPos at = WALL.relative(d);
					BlockState st = h.getBlockState(at);
					String what = t.id() + " facing " + d;
					h.assertTrue(st.getBlock() == MarkerBlocks.BLOCKS.get(t), what + ": marker placed, found " + st);
					h.assertValueEqual(d, st.getValue(MarkerBlock.FACING), what + ": FACING");
					h.assertValueEqual(MarkerStatus.UNCHECKED, st.getValue(MarkerBlock.STATUS), what + ": status");
					h.assertValueEqual(WALL, at.relative(st.getValue(MarkerBlock.FACING).getOpposite()), what + ": support position");
					h.assertValueEqual(1, stack.getCount(), what + ": one item consumed");
				}
			}
		} finally {
			MarkerTestSupport.removePlayer(h, p);
		}
		h.succeed();
	}

	private static boolean anyMarkerAround(GameTestHelper h, BlockPos c) {
		for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) {
			if (markerAt(h, c.offset(x, y, z)) != null) return true;
		}
		return false;
	}

	/** T-L2-7 */
	@GameTest(environment = ENV, maxTicks = 200)
	public void markerRefusedWithoutWall(GameTestHelper h) {
		ServerPlayer p = MarkerTestSupport.player(h, "placer7", GameType.SURVIVAL, new BlockPos(1, 1, 1));
		try {
			Item item = MarkerBlocks.ITEMS.get(BuildingType.HOUSE);
			BlockPos c = new BlockPos(3, 3, 3);
			// (a) top face of a lone stone block
			clearBox(h, 1, 1, 1, 6, 6, 6);
			set(h, c, Blocks.STONE.defaultBlockState());
			ItemStack a = new ItemStack(item, 2);
			MarkerTestSupport.look(p, Direction.NORTH);
			MarkerTestSupport.clickFace(h, p, c, Direction.UP, a);
			h.assertTrue(!anyMarkerAround(h, c.above()), "(a) nothing placed on a lone block's top");
			h.assertValueEqual(2, a.getCount(), "(a) count unchanged");
			// (b) torch, carpet and an existing marker, with no wall around
			for (String what : new String[] {"torch", "carpet", "marker"}) {
				clearBox(h, 1, 1, 1, 6, 6, 6);
				BlockState st = switch (what) {
					case "torch" -> Blocks.TORCH.defaultBlockState();
					case "carpet" -> Blocks.WHITE_CARPET.defaultBlockState();
					default -> MarkerBlocks.BLOCKS.get(BuildingType.STORAGE).defaultBlockState();
				};
				if (what.equals("marker")) set(h, c.south(), Blocks.STONE.defaultBlockState());
				set(h, c, st);
				ItemStack b = new ItemStack(item, 2);
				MarkerTestSupport.look(p, Direction.NORTH);
				MarkerTestSupport.clickFace(h, p, c, what.equals("marker") ? Direction.EAST : Direction.NORTH, b);
				BlockPos target = what.equals("marker") ? c.east() : c.north();
				h.assertTrue(markerAt(h, target) == null || markerAt(h, target).type() != BuildingType.HOUSE, "(b) refused on " + what);
				h.assertValueEqual(2, b.getCount(), "(b) count unchanged on " + what);
			}
			// (c) side of a bottom slab attaches
			clearBox(h, 1, 1, 1, 6, 6, 6);
			set(h, c, Blocks.STONE_SLAB.defaultBlockState());
			ItemStack sc = new ItemStack(item, 2);
			MarkerTestSupport.look(p, Direction.SOUTH);
			MarkerTestSupport.clickFace(h, p, c, Direction.NORTH, sc);
			h.assertTrue(markerAt(h, c.north()) != null, "(c) marker on a bottom slab side");
			h.assertValueEqual(1, sc.getCount(), "(c) consumed");
			// (d) top of a floor block next to a wall, looking at the wall
			clearBox(h, 1, 1, 1, 6, 6, 6);
			set(h, c, Blocks.STONE.defaultBlockState());
			set(h, c.above().south(), Blocks.STONE.defaultBlockState());
			ItemStack sd = new ItemStack(item, 2);
			MarkerTestSupport.look(p, Direction.SOUTH);
			MarkerTestSupport.clickFace(h, p, c, Direction.UP, sd);
			BlockState dState = h.getBlockState(c.above());
			h.assertTrue(dState.getBlock() instanceof MarkerBlock, "(d) marker placed on the wall behind the click");
			h.assertValueEqual(Direction.NORTH, dState.getValue(MarkerBlock.FACING), "(d) facing out of the wall");
			// (e) side of a carpet while looking at a wall behind the clicked cell
			clearBox(h, 1, 1, 1, 6, 6, 6);
			set(h, c, Blocks.WHITE_CARPET.defaultBlockState());
			set(h, c.north(2), Blocks.STONE.defaultBlockState());
			ItemStack se = new ItemStack(item, 2);
			MarkerTestSupport.look(p, Direction.NORTH);
			MarkerTestSupport.clickFace(h, p, c, Direction.NORTH, se);
			BlockState eState = h.getBlockState(c.north());
			h.assertTrue(eState.getBlock() instanceof MarkerBlock, "(e) marker placed after the fallback");
			h.assertValueEqual(Direction.SOUTH, eState.getValue(MarkerBlock.FACING), "(e) facing out of the far wall");
		} finally {
			MarkerTestSupport.removePlayer(h, p);
		}
		h.succeed();
	}

	/** T-L2-8 (a) wall replaced by air */
	@GameTest(environment = ENV, maxTicks = 40)
	public void markerDropsWhenWallSetToAir(GameTestHelper h) {
		requireDrops(h);
		clearBox(h, 1, 1, 1, 6, 6, 6);
		Item item = MarkerBlocks.ITEMS.get(BuildingType.KITCHEN);
		BlockPos marker = new BlockPos(3, 1, 2);
		h.setBlock(WALL, Blocks.STONE);
		h.setBlock(marker, MarkerBlocks.BLOCKS.get(BuildingType.KITCHEN));
		h.assertTrue(markerAt(h, marker) != null, "marker stands before");
		h.setBlock(WALL, Blocks.AIR);
		h.succeedWhen(() -> {
			h.assertTrue(h.getBlockState(marker).isAir(), "marker position is air");
			h.assertItemEntityCountIs(item, marker, 1.5, 1);
		});
	}

	/** T-L2-8 (b) wall destroyed */
	@GameTest(environment = ENV, maxTicks = 40)
	public void markerDropsWhenWallDestroyed(GameTestHelper h) {
		requireDrops(h);
		clearBox(h, 1, 1, 1, 6, 6, 6);
		Item item = MarkerBlocks.ITEMS.get(BuildingType.MARKET);
		BlockPos marker = new BlockPos(3, 1, 2);
		h.setBlock(WALL, Blocks.STONE);
		h.setBlock(marker, MarkerBlocks.BLOCKS.get(BuildingType.MARKET));
		h.destroyBlock(WALL);
		h.succeedWhen(() -> {
			h.assertTrue(h.getBlockState(marker).isAir(), "marker position is air");
			h.assertItemEntityCountIs(item, marker, 1.5, 1);
		});
	}

	/** T-L2-8 (c) a sticky piston pulls the wall away */
	@GameTest(environment = ENV, maxTicks = 80)
	public void markerDropsWhenWallPulledByPiston(GameTestHelper h) {
		requireDrops(h);
		clearBox(h, 1, 1, 1, 6, 6, 7);
		Item item = MarkerBlocks.ITEMS.get(BuildingType.SMITHY);
		BlockPos marker = new BlockPos(3, 1, 2);
		BlockPos redstone = new BlockPos(3, 1, 6);
		h.setBlock(new BlockPos(3, 1, 5), Blocks.STICKY_PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.NORTH));
		h.setBlock(redstone, Blocks.REDSTONE_BLOCK);
		h.runAfterDelay(8, () -> {
			h.assertTrue(h.getBlockState(new BlockPos(3, 1, 4)).is(Blocks.PISTON_HEAD), "piston extended");
			h.setBlock(WALL, Blocks.STONE);
			h.setBlock(marker, MarkerBlocks.BLOCKS.get(BuildingType.SMITHY));
			h.assertTrue(markerAt(h, marker) != null, "marker stands on the wall against the piston head");
			h.setBlock(redstone, Blocks.AIR);
			h.succeedWhen(() -> {
				// the wall was moved by the piston, not removed some other way: it now stands where the extended head was
				h.assertTrue(h.getBlockState(new BlockPos(3, 1, 4)).is(Blocks.STONE), "the sticky piston pulled the wall block to z=4");
				h.assertTrue(!h.getBlockState(WALL).is(Blocks.STONE), "the wall position is vacated");
				h.assertTrue(h.getBlockState(marker).isAir(), "marker position is air");
				h.assertItemEntityCountIs(item, marker, 1.5, 1);
			});
		});
	}

	/** T-L2-9 */
	@GameTest(environment = ENV, maxTicks = 40)
	public void markerIgnoresOtherNeighbours(GameTestHelper h) {
		clearBox(h, 1, 1, 1, 6, 6, 6);
		BlockPos m = new BlockPos(3, 3, 2);
		h.setBlock(m.south(), Blocks.STONE);
		h.setBlock(m, MarkerBlocks.BLOCKS.get(BuildingType.STORAGE));
		for (BlockPos n : new BlockPos[] {m.above(), m.below(), m.east(), m.west(), m.north()}) {
			h.setBlock(n, Blocks.STONE);
			h.setBlock(n, Blocks.AIR);
			h.setBlock(n, Blocks.STONE);
			h.setBlock(n, Blocks.AIR);
		}
		h.runAfterDelay(5, () -> {
			h.assertTrue(markerAt(h, m) != null, "marker still there");
			h.assertTrue(h.getEntities(EntityType.ITEM).isEmpty(), "no item entity");
			h.succeed();
		});
	}

	/** T-L2-10 */
	@GameTest(environment = ENV, maxTicks = 60)
	public void siteMarkerOnFenceAndGlass(GameTestHelper h) {
		requireDrops(h);
		clearBox(h, 1, 1, 1, 6, 6, 6);
		ServerPlayer p = MarkerTestSupport.player(h, "placer10", GameType.SURVIVAL, new BlockPos(1, 1, 1));
		BlockPos fence = new BlockPos(3, 1, 3), glass = new BlockPos(5, 1, 3);
		try {
			set(h, fence, Blocks.OAK_FENCE.defaultBlockState());
			set(h, glass, Blocks.GLASS.defaultBlockState());
			MarkerTestSupport.look(p, Direction.SOUTH);
			MarkerTestSupport.clickFace(h, p, fence, Direction.NORTH, new ItemStack(MarkerBlocks.ITEMS.get(BuildingType.MINESHAFT)));
			MarkerTestSupport.clickFace(h, p, glass, Direction.NORTH, new ItemStack(MarkerBlocks.ITEMS.get(BuildingType.TRAINING_YARD)));
		} finally {
			MarkerTestSupport.removePlayer(h, p);
		}
		h.assertTrue(markerAt(h, fence.north()) != null && markerAt(h, fence.north()).type() == BuildingType.MINESHAFT, "mineshaft marker on the fence post");
		h.assertTrue(markerAt(h, glass.north()) != null && markerAt(h, glass.north()).type() == BuildingType.TRAINING_YARD, "training yard marker on glass");
		h.setBlock(fence, Blocks.AIR);
		h.succeedWhen(() -> {
			h.assertTrue(h.getBlockState(fence.north()).isAir(), "post marker removed");
			h.assertItemEntityCountIs(MarkerBlocks.ITEMS.get(BuildingType.MINESHAFT), fence.north(), 1.5, 1);
		});
	}

	/** T-L2-11 */
	@GameTest(environment = ENV, maxTicks = 60)
	public void markerAndJobBlocksDropThemselves(GameTestHelper h) {
		requireDrops(h);
		clearBox(h, 1, 1, 1, 6, 6, 6);
		BlockPos at = new BlockPos(4, 1, 4);
		List<Block> blocks = new ArrayList<>();
		List<Item> items = new ArrayList<>();
		for (BuildingType t : BuildingType.values()) { blocks.add(MarkerBlocks.BLOCKS.get(t)); items.add(MarkerBlocks.ITEMS.get(t)); }
		for (JobBlockType t : JobBlockType.values()) { blocks.add(JobBlocks.BLOCKS.get(t)); items.add(JobBlocks.ITEMS.get(t)); }
		for (int i = 0; i < blocks.size(); i++) {
			set(h, at.south(), Blocks.STONE.defaultBlockState());
			set(h, at, blocks.get(i).defaultBlockState());
			h.getLevel().destroyBlock(h.absolutePos(at), true);
			int count = 0;
			for (ItemEntity e : h.getEntities(EntityType.ITEM, at, 2.0)) {
				if (e.getItem().is(items.get(i))) count += e.getItem().getCount();
				else h.fail("unexpected drop " + e.getItem() + " for " + blocks.get(i).getDescriptionId());
			}
			h.assertValueEqual(1, count, "drop count of " + blocks.get(i).getDescriptionId());
			for (ItemEntity e : h.getEntities(EntityType.ITEM)) e.discard();
		}
		h.succeed();
	}

	/** T-L2-12 */
	@GameTest(environment = ENV, maxTicks = 60)
	public void jobBlocksFacePlacer(GameTestHelper h) {
		clearBox(h, 1, 1, 1, 6, 6, 6);
		ServerPlayer p = MarkerTestSupport.player(h, "placer12", GameType.SURVIVAL, new BlockPos(1, 1, 1));
		try {
			for (JobBlockType t : JobBlockType.values()) {
				clearBox(h, 1, 1, 1, 6, 6, 6);
				BlockPos floor = new BlockPos(4, 1, 4);
				set(h, floor, Blocks.STONE.defaultBlockState());
				MarkerTestSupport.look(p, Direction.NORTH);
				MarkerTestSupport.clickFace(h, p, floor, Direction.UP, new ItemStack(JobBlocks.ITEMS.get(t)));
				BlockState st = h.getBlockState(floor.above());
				h.assertTrue(st.getBlock() == JobBlocks.BLOCKS.get(t), t.id() + " placed, found " + st);
				h.assertValueEqual(Direction.SOUTH, st.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING), t.id() + " faces the placer");
				double maxY = st.getShape(h.getLevel(), h.absolutePos(floor.above())).bounds().maxY;
				h.assertValueEqual(t == JobBlockType.CHOPPING_BLOCK ? 0.75 : 1.0, maxY, t.id() + " shape height");
			}
		} finally {
			MarkerTestSupport.removePlayer(h, p);
		}
		h.succeed();
	}
}
