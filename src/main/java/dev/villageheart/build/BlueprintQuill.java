package dev.villageheart.build;

import dev.villageheart.VillageHeart;
import dev.villageheart.core.Balance;
import dev.villageheart.core.Tier;
import dev.villageheart.core.blueprint.Blueprint;
import dev.villageheart.core.blueprint.BlueprintParser;
import dev.villageheart.core.blueprint.Blueprints;
import dev.villageheart.core.building.BuildingRules;
import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.building.RoomFacts;
import dev.villageheart.core.room.Dir;
import dev.villageheart.core.room.DoorPart;
import dev.villageheart.core.room.Pos;
import dev.villageheart.core.room.RoomScanner;
import dev.villageheart.room.LevelBlockView;
import dev.villageheart.room.RoomCells;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.component.DataComponentType;
import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;

/** Two corners of a building the player already made. If it passes the room check, it is saved as a blueprint. */
public class BlueprintQuill extends Item {
	public static final DataComponentType<BlockPos> CORNER = DataComponentType.<BlockPos>builder()
			.persistent(BlockPos.CODEC).networkSynchronized(BlockPos.STREAM_CODEC).build();

	public BlueprintQuill(Properties properties) { super(properties); }

	public static void registerComponent() {
		Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Identifier.fromNamespaceAndPath(VillageHeart.MOD_ID, "quill_corner"), CORNER);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player)) return InteractionResult.SUCCESS;
		BlockPos clicked = context.getClickedPos();
		BlockPos first = context.getItemInHand().get(CORNER);
		if (first == null) {
			context.getItemInHand().set(CORNER, clicked);
			player.displayClientMessage(Component.literal("First corner set. Click the opposite corner."), true);
			return InteractionResult.SUCCESS;
		}
		context.getItemInHand().remove(CORNER);
		String reason = save(level, first, clicked, player);
		player.displayClientMessage(Component.literal(reason), false);
		return InteractionResult.SUCCESS;
	}

	/** @return a sentence for the player */
	public static String save(ServerLevel level, BlockPos a, BlockPos b, ServerPlayer player) {
		int minX = Math.min(a.getX(), b.getX()), maxX = Math.max(a.getX(), b.getX());
		int minY = Math.min(a.getY(), b.getY()), maxY = Math.max(a.getY(), b.getY());
		int minZ = Math.min(a.getZ(), b.getZ()), maxZ = Math.max(a.getZ(), b.getZ());
		int w = maxX - minX + 1, h = maxY - minY + 1, d = maxZ - minZ + 1;
		if (w > Balance.BLUEPRINT_MAX_SIDE || h > Balance.BLUEPRINT_MAX_SIDE || d > Balance.BLUEPRINT_MAX_SIDE) return "Too big (max 16x16x16)";
		if ((long) w * h * d > Balance.BLUEPRINT_MAX_CELLS) return "Too big (max 1024 blocks)";
		LevelBlockView view = new LevelBlockView(level);
		String fail = "No door";
		Pos door = null;
		Dir inward = null;
		for (int y = minY; y <= maxY && door == null; y++) for (int z = minZ; z <= maxZ && door == null; z++) for (int x = minX; x <= maxX && door == null; x++) {
			var cell = RoomCells.of(level.getBlockState(new BlockPos(x, y, z)));
			if (cell.door() != DoorPart.LOWER) continue;
			Pos at = new Pos(x, y, z);
			for (Dir in : new Dir[] {Dir.NORTH, Dir.SOUTH, Dir.EAST, Dir.WEST}) {
				var scan = RoomScanner.scanFromDoor(view, at, in);
				if (!scan.enclosed()) {
					fail = scan.problems().isEmpty() ? "Not enclosed" : scan.problems().get(0).toString();
					continue;
				}
				var verdict = BuildingRules.checkRoom(BuildingType.HOUSE, RoomFacts.of(scan), new BuildingRules.CheckContext(Tier.VILLAGE, null, null, 0));
				if (!verdict.reasons().isEmpty()) {
					fail = verdict.reasons().get(0).message();
					continue;
				}
				door = at;
				inward = in;
			}
		}
		if (door == null) return fail;
		String id = "saved_" + Integer.toUnsignedString(player.getUUID().hashCode() ^ (minX * 31 + minZ), 36);
		String text = capture(level, minX, minY, minZ, w, h, d, id);
		try {
			Blueprint bp = BlueprintParser.parse(text);
			Blueprints.addDynamic(bp);
			SavedBlueprints.of(level.getServer()).add(text);
		} catch (RuntimeException e) {
			return "Could not save: " + e.getMessage();
		}
		return "Saved blueprint " + id;
	}

	private static String capture(ServerLevel level, int minX, int minY, int minZ, int w, int h, int d, String id) {
		Map<String, Character> symbols = new HashMap<>();
		String alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
		int next = 0;
		StringBuilder palette = new StringBuilder("palette\n. any\n_ air\n");
		String[][] rows = new String[h][d];
		for (int y = 0; y < h; y++) for (int z = 0; z < d; z++) {
			char[] line = new char[w];
			java.util.Arrays.fill(line, '_');
			for (int x = 0; x < w; x++) {
				BlockState st = level.getBlockState(new BlockPos(minX + x, minY + y, minZ + z));
				if (st.isAir()) continue;
				String body = paletteBody(st);
				Character sym = symbols.get(body);
				if (sym == null) {
					if (next >= alphabet.length()) throw new IllegalStateException("too many block types");
					sym = alphabet.charAt(next++);
					symbols.put(body, sym);
					palette.append(sym).append(' ').append(body).append('\n');
				}
				line[x] = sym;
			}
			rows[y][z] = new String(line);
		}
		StringBuilder text = new StringBuilder();
		text.append("id ").append(id).append("\nname Saved building\ntier VILLAGE\nkind BUILDING\nbuilds house\nbase 0\nanchor 0 0\nfront NORTH\nsize ")
				.append(w).append(' ').append(h).append(' ').append(d).append('\n').append(palette);
		for (int y = 0; y < h; y++) {
			text.append("layer ").append(y).append('\n');
			for (int z = 0; z < d; z++) text.append(rows[y][z]).append('\n');
		}
		text.append("end\n");
		return text.toString();
	}

	/** Palette text after the symbol: a primary with a material, or the other half of a door or bed. */
	private static String paletteBody(BlockState state) {
		String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
		String stateText = blockState(state);
		String half = prop(state, "half");
		String part = prop(state, "part");
		if (id.endsWith("_door") && "upper".equals(half)) return stateText + " part=DOWN cell=solid";
		if (id.endsWith("_bed") && "head".equals(part)) return stateText + " part=" + opposite(prop(state, "facing")) + " cell=solid +#bed";
		var item = state.getBlock().asItem();
		String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString();
		if (itemId.endsWith("air")) itemId = id;
		String name = itemId.substring(itemId.indexOf(':') + 1);
		StringBuilder line = new StringBuilder(stateText).append(" <- ").append(itemId).append(" \"").append(name).append("\"");
		if (id.equals("minecraft:farmland")) return "minecraft:farmland[moisture=7] <- minecraft:dirt \"dirt\" converts";
		if (id.equals("minecraft:water")) return "minecraft:water <- minecraft:water_bucket \"water bucket\" converts";
		if (id.endsWith("_door")) line.append(" cell=solid +#door");
		else if (id.endsWith("_bed")) line.append(" cell=solid");
		else if (id.contains("torch") || id.endsWith("_lantern")) line.append(" pass=attached cell=open +#light");
		else if (id.endsWith("_marker")) line.append(" pass=attached cell=open");
		return line.toString();
	}

	private static String prop(BlockState state, String name) {
		for (var p : state.getProperties()) {
			if (p.getName().equals(name)) return state.getValue(p).toString().toLowerCase(java.util.Locale.ROOT);
		}
		return "";
	}

	private static String opposite(String facing) {
		return switch (facing) {
			case "north" -> "SOUTH";
			case "south" -> "NORTH";
			case "east" -> "WEST";
			case "west" -> "EAST";
			default -> "DOWN";
		};
	}

	private static String blockState(BlockState state) {
		StringBuilder sb = new StringBuilder(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
		if (!state.getProperties().isEmpty()) {
			sb.append('[');
			boolean first = true;
			for (var p : state.getProperties()) {
				if (!first) sb.append(',');
				first = false;
				sb.append(p.getName()).append('=').append(state.getValue(p).toString().toLowerCase(java.util.Locale.ROOT));
			}
			sb.append(']');
		}
		return sb.toString();
	}
}
