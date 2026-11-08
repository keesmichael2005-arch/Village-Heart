package dev.villageheart.build;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.villageheart.core.blueprint.Material;
import dev.villageheart.core.blueprint.PaletteEntry;
import dev.villageheart.core.blueprint.Rotation4;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.Nullable;

/**
 * Blueprint palette states in the world: the template parsed once, tag materials substituted by the block of the item actually used (with
 * every property of the template that the block has), rotation by vanilla {@code BlockState.rotate}. Plan 3.2 #3 and #4.
 */
public final class PaletteStates {
	private PaletteStates() {}

	private static final Map<String, BlockState> TEMPLATES = new ConcurrentHashMap<>();

	/** The template state of a palette string ({@code minecraft:oak_door[facing=south,half=lower]}). */
	public static BlockState template(String state) {
		return TEMPLATES.computeIfAbsent(state, s -> {
			try {
				return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, s, false).blockState();
			} catch (CommandSyntaxException e) {
				throw new IllegalStateException("Bad blueprint block state '" + s + "': " + e.getMessage(), e);
			}
		});
	}

	public static Rotation rotation(Rotation4 r) { return Rotation.valueOf(r.name()); }

	/**
	 * The state to place. {@code substitute}: the block of the item used for a tag material (null: the template's own block); the other
	 * half of a unit gets the same block as its primary.
	 */
	public static BlockState resolve(PaletteEntry entry, Rotation4 rotation, @Nullable Block substitute) {
		BlockState state = template(entry.state());
		if (substitute != null && substitute != state.getBlock()) state = copyProperties(state, substitute.defaultBlockState());
		return state.rotate(rotation(rotation));
	}

	/** The block a tag material places for this item (the item's own block), or null for an exact material. */
	@Nullable
	public static Block substituteFor(PaletteEntry entry, Item item) {
		Material m = entry.material();
		if (m == null || !m.isTag()) return null;
		Block b = Block.byItem(item);
		return b == null || b == net.minecraft.world.level.block.Blocks.AIR ? null : b;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static BlockState copyProperties(BlockState from, BlockState onto) {
		BlockState out = onto;
		for (Property p : from.getProperties()) {
			if (out.hasProperty(p)) out = out.trySetValue(p, from.getValue(p));
		}
		return out;
	}

	// ---- materials and items ----

	public static TagKey<Item> itemTag(Material m) {
		return TagKey.create(Registries.ITEM, Identifier.parse(m.bareId()));
	}

	/** True when {@code item} can serve as {@code m}: in its tag, or the exact item. */
	public static boolean satisfies(Material m, Item item) {
		if (m.isTag()) return item.builtInRegistryHolder().is(itemTag(m));
		return BuiltInRegistries.ITEM.getKey(item).toString().equals(m.id());
	}

	/** The item of an exact material, or of the first item of a tag (for display). */
	@Nullable
	public static Item displayItem(Material m) {
		if (!m.isTag()) return BuiltInRegistries.ITEM.getOptional(Identifier.parse(m.id())).orElse(null);
		return BuiltInRegistries.ITEM.getTagOrEmpty(itemTag(m)).iterator().hasNext()
				? BuiltInRegistries.ITEM.getTagOrEmpty(itemTag(m)).iterator().next().value() : null;
	}

	/**
	 * Whether a block in the world is what the entry wants: for a tag material any block whose item is in the tag, otherwise the same
	 * block (flowing and still water are one block; a farmland's moisture or an open door do not matter).
	 */
	public static boolean holds(BlockState state, PaletteEntry entry) {
		if (entry.material() != null && entry.material().isTag() && !entry.converts()) {
			return state.getBlock().asItem() != net.minecraft.world.item.Items.AIR && satisfies(entry.material(), state.getBlock().asItem());
		}
		return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().equals(entry.blockId());
	}
}
