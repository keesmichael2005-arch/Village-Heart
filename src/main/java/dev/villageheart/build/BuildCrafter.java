package dev.villageheart.build;

import dev.villageheart.core.blueprint.BuildCraft;
import dev.villageheart.core.blueprint.BuildCraft.Input;
import dev.villageheart.core.blueprint.BuildCraft.Recipe;
import dev.villageheart.core.blueprint.Material;
import dev.villageheart.stock.StackKey;
import dev.villageheart.stock.Stockpiles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.util.context.ContextMap;
import org.jetbrains.annotations.Nullable;

/**
 * Vanilla crafting and smelting of building blocks, done by a builder standing at Storage. The output goes into the builder's inventory.
 * Not tools, food or redstone.
 */
public final class BuildCrafter {
	private BuildCrafter() {}

	private static List<Recipe> cached;
	private static int cachedEpoch = -1;
	private static String lastId = "";

	public static String lastCraftedId() { return lastId; }

	public static boolean canCraft(MinecraftServer server, @Nullable Material material) {
		if (material == null) return false;
		Item item = PaletteStates.displayItem(material);
		if (item == null || !building(item)) {
			// a tag such as #planks: any building block in the tag that a recipe makes
			if (!material.isTag()) return false;
		}
		Map<String, Integer> stock = stock(server);
		List<Recipe> recipes = recipes(server);
		if (item != null && building(item)) {
			return BuildCraft.plan(BuiltInRegistries.ITEM.getKey(item).toString(), 1, stock, recipes).isPresent();
		}
		if (!material.isTag()) return false;
		for (Recipe r : recipes) {
			Item out = BuiltInRegistries.ITEM.getOptional(Identifier.parse(r.output())).orElse(null);
			if (out == null || !PaletteStates.satisfies(material, out)) continue;
			if (BuildCraft.plan(r.output(), 1, stock, recipes).isPresent()) return true;
		}
		return false;
	}

	/** Crafts up to {@code want} of {@code material} into {@code villager}'s inventory. Returns how many were made. */
	public static int craft(ServerLevel level, Villager villager, Material material, int want) {
		if (want <= 0) return 0;
		MinecraftServer server = level.getServer();
		List<Recipe> recipes = recipes(server);
		Map<String, Integer> stock = stock(server);
		String output = target(material, recipes, stock);
		if (output == null) return 0;
		var plan = BuildCraft.plan(output, want, stock, recipes);
		if (plan.isEmpty()) return 0;
		BlockPos at = villager.blockPosition();
		int made = 0;
		for (BuildCraft.Step step : plan.get().steps()) {
			Recipe recipe = find(recipes, step.output());
			if (recipe == null) return made;
			for (int t = 0; t < step.times(); t++) {
				if (step.smelt() && !takeFuel(level, at)) return made;
				if (!takeInputs(level, at, villager, recipe)) return made;
				Item item = BuiltInRegistries.ITEM.getOptional(Identifier.parse(recipe.output())).orElse(null);
				if (item == null) return made;
				ItemStack stack = new ItemStack(item, recipe.count());
				ItemStack rest = villager.getInventory().addItem(stack);
				int got = recipe.count() - rest.getCount();
				if (!rest.isEmpty()) Stockpiles.deposit(level, at, rest);
				made += recipe.output().equals(output) ? got : 0;
				lastId = recipe.output();
			}
		}
		return made;
	}

	@Nullable
	private static String target(Material material, List<Recipe> recipes, Map<String, Integer> stock) {
		Item display = PaletteStates.displayItem(material);
		if (display != null && building(display)) {
			String id = BuiltInRegistries.ITEM.getKey(display).toString();
			if (BuildCraft.plan(id, 1, stock, recipes).isPresent()) return id;
		}
		if (!material.isTag()) return material.id();
		for (Recipe r : recipes) {
			Item out = BuiltInRegistries.ITEM.getOptional(Identifier.parse(r.output())).orElse(null);
			if (out != null && PaletteStates.satisfies(material, out) && BuildCraft.plan(r.output(), 1, stock, recipes).isPresent()) return r.output();
		}
		return null;
	}

	private static boolean takeInputs(ServerLevel level, BlockPos at, Villager villager, Recipe recipe) {
		for (Input in : recipe.inputs()) {
			int left = in.count();
			var inv = villager.getInventory();
			for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
				ItemStack s = inv.getItem(i);
				if (s.isEmpty() || !BuiltInRegistries.ITEM.getKey(s.getItem()).toString().equals(in.item())) continue;
				int n = Math.min(left, s.getCount());
				s.shrink(n);
				left -= n;
			}
			while (left > 0) {
				Item item = BuiltInRegistries.ITEM.getOptional(Identifier.parse(in.item())).orElse(null);
				if (item == null) return false;
				ItemStack got = Stockpiles.withdraw(level, at, StackKey.of(item), left, null);
				if (got.isEmpty()) return false;
				left -= got.getCount();
			}
		}
		return true;
	}

	private static boolean takeFuel(ServerLevel level, BlockPos at) {
		for (Item fuel : new Item[] {Items.COAL, Items.CHARCOAL, Items.OAK_PLANKS, Items.STICK}) {
			ItemStack got = Stockpiles.withdraw(level, at, StackKey.of(fuel), 1, null);
			if (!got.isEmpty()) return true;
		}
		return false;
	}

	@Nullable
	private static Recipe find(List<Recipe> recipes, String output) {
		for (Recipe r : recipes) if (r.output().equals(output)) return r;
		return null;
	}

	private static Map<String, Integer> stock(MinecraftServer server) {
		Map<String, Integer> out = new HashMap<>();
		for (var t : Stockpiles.snapshot(server).totals()) {
			if (!t.key().patch().isEmpty()) continue;
			out.merge(t.key().id(), (int) Math.min(Integer.MAX_VALUE, t.count()), Integer::sum);
		}
		return out;
	}

	private static List<Recipe> recipes(MinecraftServer server) {
		int epoch = server.getTickCount() / 200;
		if (cached != null && cachedEpoch == epoch) return cached;
		List<Recipe> out = new ArrayList<>();
		ContextMap ctx = new ContextMap.Builder().create(new net.minecraft.util.context.ContextKeySet.Builder().build());
		for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
			var recipe = holder.value();
			ItemStack result = resultOf(recipe, ctx);
			if (result.isEmpty() || !building(result.getItem())) continue;
			List<Input> inputs = new ArrayList<>();
			boolean smelt = false;
			if (recipe instanceof ShapedRecipe shaped) {
				countIngredients(shaped.getIngredients(), inputs);
			} else if (recipe instanceof ShapelessRecipe shapeless) {
				countIngredients(shapeless.placementInfo().ingredients(), inputs);
			} else if (recipe instanceof AbstractCookingRecipe cooking) {
				smelt = true;
				Ingredient ingredient = cooking.input();
				Item one = firstItem(ingredient);
				if (one == null) continue;
				inputs.add(new Input(BuiltInRegistries.ITEM.getKey(one).toString(), 1));
			} else continue;
			if (inputs.isEmpty()) continue;
			String id = BuiltInRegistries.ITEM.getKey(result.getItem()).toString();
			out.add(new Recipe(id, result.getCount(), inputs, smelt));
		}
		cached = List.copyOf(out);
		cachedEpoch = epoch;
		return cached;
	}

	private static void countIngredients(List<?> ingredients, List<Input> inputs) {
		Map<String, Integer> counts = new HashMap<>();
		for (Object o : ingredients) {
			Ingredient ingredient = null;
			if (o instanceof Ingredient i) ingredient = i;
			else if (o instanceof java.util.Optional<?> opt && opt.orElse(null) instanceof Ingredient i) ingredient = i;
			if (ingredient == null || ingredient.isEmpty()) continue;
			Item one = firstItem(ingredient);
			if (one == null) continue;
			counts.merge(BuiltInRegistries.ITEM.getKey(one).toString(), 1, Integer::sum);
		}
		for (var e : counts.entrySet()) inputs.add(new Input(e.getKey(), e.getValue()));
	}

	@Nullable
	private static Item firstItem(Ingredient ingredient) {
		return ingredient.items().map(h -> h.value()).findFirst().orElse(null);
	}

	private static ItemStack resultOf(net.minecraft.world.item.crafting.Recipe<?> recipe, ContextMap ctx) {
		List<RecipeDisplay> displays;
		try {
			displays = recipe.display();
		} catch (Throwable e) {
			return ItemStack.EMPTY;
		}
		if (displays.isEmpty()) return ItemStack.EMPTY;
		try {
			return displays.get(0).result().resolveForFirstStack(ctx);
		} catch (Throwable e) {
			return ItemStack.EMPTY;
		}
	}

	/** A block a builder may craft: the building blocks of a house, not tools, food or redstone. */
	public static boolean building(Item item) {
		if (!(item instanceof BlockItem blockItem)) return false;
		if (item.components().has(net.minecraft.core.component.DataComponents.FOOD)) return false;
		Block block = blockItem.getBlock();
		if (block instanceof StairBlock || block instanceof SlabBlock || block instanceof WallBlock || block instanceof FenceBlock
				|| block instanceof FenceGateBlock || block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof BedBlock
				|| block instanceof IronBarsBlock) return true;
		var state = block.defaultBlockState();
		return state.is(BlockTags.PLANKS) || state.is(BlockTags.LOGS) || state.is(BlockTags.WOOL) || state.is(BlockTags.TERRACOTTA)
				|| state.is(BlockTags.STAIRS) || state.is(BlockTags.SLABS) || state.is(BlockTags.WALLS) || state.is(BlockTags.FENCES)
				|| state.is(BlockTags.DOORS) || state.is(BlockTags.TRAPDOORS) || state.is(BlockTags.FENCE_GATES)
				|| block == net.minecraft.world.level.block.Blocks.GLASS || block == net.minecraft.world.level.block.Blocks.GLASS_PANE
				|| block == net.minecraft.world.level.block.Blocks.BRICKS
				|| block == net.minecraft.world.level.block.Blocks.STONE_BRICKS || block == net.minecraft.world.level.block.Blocks.STONE
				|| block == net.minecraft.world.level.block.Blocks.SAND || block == net.minecraft.world.level.block.Blocks.WHITE_CONCRETE;
	}
}
