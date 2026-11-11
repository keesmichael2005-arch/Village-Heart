package dev.villageheart.build;

import dev.villageheart.VillageHeart;
import dev.villageheart.core.Balance;
import dev.villageheart.core.Tier;
import dev.villageheart.core.blueprint.Blueprint;
import dev.villageheart.core.blueprint.Blueprints;
import dev.villageheart.core.blueprint.PaletteEntry;
import dev.villageheart.core.building.BuildingRules;
import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.building.RoomFacts;
import dev.villageheart.core.room.BlockView;
import dev.villageheart.core.room.Cell;
import dev.villageheart.core.room.CellKind;
import dev.villageheart.core.room.Dir;
import dev.villageheart.core.room.DoorPart;
import dev.villageheart.core.room.Pos;
import dev.villageheart.core.room.RoomScanner;
import dev.villageheart.room.RoomCells;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Loads every {@code minecraft:village/} structure template, keeps the ones that are real houses, and offers those as blueprints.
 * The scan result (pass or the reason) is kept for the test and the pull request.
 */
public final class VillageHouses {
	private VillageHouses() {}

	public record Row(String id, boolean offered, String reason) {}

	private static List<Row> rows = List.of();
	private static boolean loaded;

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			load(server.getResourceManager());
			SavedBlueprints.loadInto(server);
		});
	}

	public static List<Row> rows() { return rows; }

	public static void load(ResourceManager resources) {
		if (loaded) return;
		loaded = true;
		List<Row> out = new ArrayList<>();
		Map<Identifier, net.minecraft.server.packs.resources.Resource> found;
		try {
			found = resources.listResources("structure", id -> id.getNamespace().equals("minecraft") && id.getPath().startsWith("structure/village/")
					&& id.getPath().endsWith(".nbt"));
		} catch (Exception e) {
			VillageHeart.LOG.warn("Village house scan could not list templates: {}", e.toString());
			rows = List.of();
			return;
		}
		List<Identifier> ids = new ArrayList<>(found.keySet());
		ids.sort(Comparator.comparing(Identifier::toString));
		for (Identifier file : ids) {
			String path = file.getPath().substring("structure/".length());
			if (path.endsWith(".nbt")) path = path.substring(0, path.length() - 4);
			String id = "minecraft:" + path;
			try {
				String reason = consider(found.get(file).open(), id);
				boolean offered = reason.equals("pass");
				out.add(new Row(id, offered, offered ? "pass" : reason));
			} catch (Exception e) {
				out.add(new Row(id, false, "unreadable: " + e.getClass().getSimpleName()));
			}
		}
		rows = List.copyOf(out);
		long pass = rows.stream().filter(Row::offered).count();
		VillageHeart.LOG.info("Vanilla village scan: {} templates, {} offered as houses", rows.size(), pass);
	}

	/** @return "pass" or a short reason */
	private static String consider(InputStream in, String id) throws Exception {
		CompoundTag tag;
		try (in) {
			tag = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
		}
		List<BlockState> palette = palette(tag);
		ListTag blocks = tag.getListOrEmpty("blocks");
		if (palette.isEmpty() || blocks.isEmpty()) return "empty";
		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		record At(int x, int y, int z, BlockState state) {}
		record Entrance(int x, int y, int z, String facing) {}
		List<At> cells = new ArrayList<>();
		List<Entrance> entrances = new ArrayList<>();
		for (Tag raw : blocks) {
			if (!(raw instanceof CompoundTag c)) continue;
			int[] pos = c.getIntArray("pos").orElse(null);
			if (pos == null) {
				net.minecraft.nbt.ListTag list = c.getListOrEmpty("pos");
				if (list.size() >= 3) pos = new int[] {list.getIntOr(0, 0), list.getIntOr(1, 0), list.getIntOr(2, 0)};
			}
			if (pos == null || pos.length < 3) continue;
			int state = c.getInt("state").orElse(0);
			if (state < 0 || state >= palette.size()) continue;
			BlockState st = palette.get(state);
			// A jigsaw is a slot, not a wall. The template says what it becomes (the floor plank, the front step).
			// structure_void and a jigsaw whose final state is void stay air, so they do not punch a hole.
			if (st.is(Blocks.JIGSAW)) {
				CompoundTag nbt = c.getCompoundOrEmpty("nbt");
				if (nbt.getStringOr("name", "").endsWith("building_entrance")) {
					entrances.add(new Entrance(pos[0], pos[1], pos[2], horizontal(nbt.getStringOr("orientation", ""))));
				}
				st = finalState(c);
			}
			if (st.isAir() || st.is(Blocks.STRUCTURE_VOID) || st.is(Blocks.STRUCTURE_BLOCK) || st.is(Blocks.JIGSAW)) continue;
			cells.add(new At(pos[0], pos[1], pos[2], st));
			minX = Math.min(minX, pos[0]); minY = Math.min(minY, pos[1]); minZ = Math.min(minZ, pos[2]);
			maxX = Math.max(maxX, pos[0]); maxY = Math.max(maxY, pos[1]); maxZ = Math.max(maxZ, pos[2]);
		}
		if (cells.isEmpty()) return "no blocks";
		int w = maxX - minX + 1, h = maxY - minY + 1, d = maxZ - minZ + 1;
		if (w > Balance.VANILLA_MAX_SIDE || h > Balance.VANILLA_MAX_SIDE || d > Balance.VANILLA_MAX_SIDE) return "over the size cap";
		if (cells.size() > Balance.VANILLA_MAX_CELLS) return "over the size cap";
		boolean housePath = id.contains("/houses/") && !id.contains("/zombie/");
		if (!housePath) return "not a house template";
		Map<Long, BlockState> world = new HashMap<>();
		for (At a : cells) world.put(Pos.pack(a.x - minX, a.y - minY, a.z - minZ), a.state);
		boolean anyDoor = false;
		for (BlockState st : world.values()) if (RoomCells.of(st).door() == DoorPart.LOWER) anyDoor = true;
		if (!anyDoor) {
			for (Entrance en : entrances) {
				int x = en.x() - minX, y = Math.max(en.y() - minY, 1), z = en.z() - minZ;
				long lower = Pos.pack(x, y, z), upper = Pos.pack(x, y + 1, z);
				if (world.containsKey(lower) || world.containsKey(upper)) continue;
				Direction facing = Direction.byName(en.facing());
				if (facing == null || !facing.getAxis().isHorizontal()) facing = Direction.NORTH;
				BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
						.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER);
				world.put(lower, door);
				world.put(upper, door.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
				if (y + 1 >= h) h = y + 2;
				break;
			}
		}
		HouseView view = new HouseView(world, h);
		String doorFail = null;
		String rulesFail = null;
		for (var e : world.entrySet()) {
			Cell cell = RoomCells.of(e.getValue());
			if (cell.door() != DoorPart.LOWER) continue;
			Pos door = Pos.unpack(e.getKey());
			for (Dir inward : new Dir[] {Dir.NORTH, Dir.SOUTH, Dir.EAST, Dir.WEST}) {
				var scan = RoomScanner.scanFromDoor(view, door, inward);
				if (!scan.enclosed()) {
					if (doorFail == null) doorFail = scan.problems().isEmpty() ? "not enclosed" : scan.problems().get(0).toString();
					continue;
				}
				var verdict = BuildingRules.checkRoom(BuildingType.HOUSE, RoomFacts.of(scan),
						new BuildingRules.CheckContext(Tier.VILLAGE, null, null, 0));
				if (!verdict.reasons().isEmpty()) {
					String msg = verdict.reasons().get(0).message();
					// A workplace template is enclosed and big enough, but the bed lives in another piece. Put one in an empty floor pair.
					if (msg.toLowerCase(java.util.Locale.ROOT).contains("bed") && scan.floorArea() >= 14) {
						long[] placed = placeBed(world, scan);
						if (placed != null) {
							var again = RoomScanner.scanFromDoor(view, door, inward);
							var againVerdict = again.enclosed()
									? BuildingRules.checkRoom(BuildingType.HOUSE, RoomFacts.of(again),
											new BuildingRules.CheckContext(Tier.VILLAGE, null, null, 0))
									: null;
							if (againVerdict != null && againVerdict.reasons().isEmpty()) {
								scan = again;
								verdict = againVerdict;
							} else {
								world.remove(placed[0]);
								world.remove(placed[1]);
								rulesFail = againVerdict == null || againVerdict.reasons().isEmpty() ? msg : againVerdict.reasons().get(0).message();
								continue;
							}
						}
					}
					if (!verdict.reasons().isEmpty()) {
						rulesFail = msg;
						continue;
					}
				}
				Dir outward = inward.opposite();
				Pos marker = door.offset(outward).offset(Dir.UP).offset(Dir.UP);
				if (world.containsKey(marker.pack())) marker = door.offset(outward);
				if (world.containsKey(marker.pack())) {
					doorFail = "no marker spot";
					continue;
				}
				return offer(id, world, w, h, d, marker, outward) ? "pass" : "could not build the blueprint";
			}
		}
		if (rulesFail != null) return rulesFail;
		return doorFail == null ? "no door" : doorFail;
	}

	private static boolean offer(String templateId, Map<Long, BlockState> blocks, int w, int h, int d, Pos marker, Dir facing) {
		String slug = templateId.substring(templateId.lastIndexOf('/') + 1);
		String id = "vanilla_" + slug;
		int mw = Math.max(w, marker.x() + 1), md = Math.max(d, marker.z() + 1), mh = Math.max(h, marker.y() + 1);
		// shift if the marker is negative
		int ox = Math.max(0, -marker.x()), oy = Math.max(0, -marker.y()), oz = Math.max(0, -marker.z());
		mw += ox; md += oz; mh += oy;
		Map<Long, BlockState> shifted = new HashMap<>();
		for (var e : blocks.entrySet()) {
			Pos p = Pos.unpack(e.getKey());
			shifted.put(Pos.pack(p.x() + ox, p.y() + oy, p.z() + oz), e.getValue());
		}
		Pos m = new Pos(marker.x() + ox, marker.y() + oy, marker.z() + oz);
		List<String> symbols = new ArrayList<>();
		Map<String, Character> byState = new HashMap<>();
		char next = 0x100;
		StringBuilder palette = new StringBuilder();
		palette.append("palette\n. any\n_ air\n");
		String[][] rows = new String[mh][md];
		for (int y = 0; y < mh; y++) for (int z = 0; z < md; z++) {
			char[] line = new char[mw];
			java.util.Arrays.fill(line, '.');
			for (int x = 0; x < mw; x++) {
				long key = Pos.pack(x, y, z);
				if (x == m.x() && y == m.y() && z == m.z()) {
					line[x] = 'M';
					continue;
				}
				BlockState st = shifted.get(key);
				if (st == null) {
					line[x] = '_';
					continue;
				}
				if (st.getBlock().asItem() == net.minecraft.world.item.Items.AIR) {
					line[x] = '_';
					continue;
				}
				String s = stateString(st);
				Character sym = byState.get(s);
				if (sym == null) {
					sym = next++;
					byState.put(s, sym);
					boolean upper = RoomCells.of(st).door() == dev.villageheart.core.room.DoorPart.UPPER
							&& shifted.get(Pos.pack(x, y - 1, z)) != null
							&& shifted.get(Pos.pack(x, y - 1, z)).getBlock() == st.getBlock();
					itemLine(st, sym, palette, upper);
				}
				line[x] = sym;
			}
			rows[y][z] = new String(line);
		}
		palette.append("M villageheart:house_marker[facing=").append(facing.name().toLowerCase(java.util.Locale.ROOT))
				.append(",status=unchecked] <- villageheart:house_marker \"House marker\" pass=attached cell=open\n");
		StringBuilder text = new StringBuilder();
		String nice = slug.replace('_', ' ');
		text.append("id ").append(id).append("\nname ").append(nice).append("\ntier VILLAGE\nkind BUILDING\nbuilds house\nbase 0\nanchor ")
				.append(m.x()).append(' ').append(m.z()).append("\nfront ").append(facing.name()).append("\nsize ").append(mw).append(' ').append(mh)
				.append(' ').append(md).append('\n');
		text.append(palette);
		for (int y = 0; y < mh; y++) {
			text.append("layer ").append(y).append('\n');
			for (int z = 0; z < md; z++) text.append(rows[y][z]).append('\n');
		}
		text.append("end\n");
		try {
			Blueprint bp = dev.villageheart.core.blueprint.BlueprintParser.parse(text.toString());
			Blueprints.addDynamic(bp);
			return true;
		} catch (RuntimeException e) {
			VillageHeart.LOG.warn("Could not offer {}: {}", id, e.getMessage());
			return false;
		}
	}

	private static void itemLine(BlockState st, char sym, StringBuilder palette, boolean upperDoor) {
		String state = stateString(st);
		Cell cell = RoomCells.of(st);
		palette.append(sym).append(' ').append(state);
		if (upperDoor) {
			palette.append(" part=DOWN cell=solid\n");
			return;
		}
		String item = BuiltInRegistries.ITEM.getKey(st.getBlock().asItem()).toString();
		String name = BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath().replace('_', ' ');
		palette.append(" <- ").append(item).append(" \"").append(name).append("\"");
		if (cell.kind() == CellKind.OPEN) palette.append(" cell=open");
		for (String c : cell.contents()) if (!c.equals(BuiltInRegistries.BLOCK.getKey(st.getBlock()).toString())) palette.append(" +").append(c);
		palette.append('\n');
	}

	private static String stateString(BlockState state) {
		StringBuilder sb = new StringBuilder(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
		if (!state.getProperties().isEmpty()) {
			sb.append('[');
			boolean first = true;
			for (Property<?> p : state.getProperties()) {
				if (!first) sb.append(',');
				first = false;
				sb.append(p.getName()).append('=').append(state.getValue(p).toString().toLowerCase(java.util.Locale.ROOT));
			}
			sb.append(']');
		}
		return sb.toString();
	}

	private static List<BlockState> palette(CompoundTag tag) {
		ListTag palette = tag.getListOrEmpty("palette");
		if (palette.isEmpty()) {
			ListTag palettes = tag.getListOrEmpty("palettes");
			if (!palettes.isEmpty() && palettes.get(0) instanceof ListTag first) palette = first;
		}
		List<BlockState> out = new ArrayList<>();
		for (Tag raw : palette) {
			if (!(raw instanceof CompoundTag c)) continue;
			String name = c.getStringOr("Name", "minecraft:air");
			Block block = BuiltInRegistries.BLOCK.getOptional(Identifier.parse(name)).orElse(Blocks.AIR);
			BlockState state = block.defaultBlockState();
			CompoundTag props = c.getCompoundOrEmpty("Properties");
			for (String key : props.keySet()) {
				for (Property<?> p : state.getProperties()) {
					if (!p.getName().equals(key)) continue;
					state = set(state, p, props.getStringOr(key, ""));
				}
			}
			out.add(state);
		}
		return out;
	}

	/** Two empty floor cells become a white bed, so a workplace piece can pass the same bed rule as a player house. */
	private static long[] placeBed(Map<Long, BlockState> world, dev.villageheart.core.room.RoomScan scan) {
		java.util.Set<Long> floor = new java.util.HashSet<>();
		for (long k : scan.interior()) {
			Pos p = Pos.unpack(k);
			BlockState below = world.get(Pos.pack(p.x(), p.y() - 1, p.z()));
			if (below != null && !below.isAir()) floor.add(k);
		}
		for (long k : floor) {
			Pos foot = Pos.unpack(k);
			for (Dir d : new Dir[] {Dir.EAST, Dir.SOUTH, Dir.WEST, Dir.NORTH}) {
				Pos head = foot.offset(d);
				if (!floor.contains(head.pack())) continue;
				Direction facing = Direction.byName(d.name().toLowerCase(java.util.Locale.ROOT));
				if (facing == null) continue;
				BlockState state = Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING, facing).setValue(BedBlock.PART, BedPart.FOOT)
						.setValue(BlockStateProperties.OCCUPIED, false);
				world.put(foot.pack(), state);
				world.put(head.pack(), state.setValue(BedBlock.PART, BedPart.HEAD));
				return new long[] {foot.pack(), head.pack()};
			}
		}
		return null;
	}

	private static String horizontal(String orientation) {
		for (String part : orientation.split("_")) {
			if (part.equals("north") || part.equals("south") || part.equals("east") || part.equals("west")) return part;
		}
		return "north";
	}

	/** {@code minecraft:oak_planks} or {@code minecraft:oak_stairs[facing=east,half=bottom]}. */
	private static BlockState finalState(CompoundTag block) {
		String raw = block.getCompoundOrEmpty("nbt").getStringOr("final_state", "minecraft:air");
		int bracket = raw.indexOf('[');
		String name = bracket < 0 ? raw : raw.substring(0, bracket);
		String props = bracket < 0 ? "" : raw.substring(bracket + 1, raw.endsWith("]") ? raw.length() - 1 : raw.length());
		Block blockType = BuiltInRegistries.BLOCK.getOptional(Identifier.parse(name)).orElse(Blocks.AIR);
		BlockState state = blockType.defaultBlockState();
		if (props.isEmpty()) return state;
		for (String part : props.split(",")) {
			int eq = part.indexOf('=');
			if (eq <= 0) continue;
			String key = part.substring(0, eq);
			String value = part.substring(eq + 1);
			for (Property<?> p : state.getProperties()) if (p.getName().equals(key)) state = set(state, p, value);
		}
		return state;
	}

	private static <T extends Comparable<T>> BlockState set(BlockState state, Property<T> p, String value) {
		return p.getValue(value).map(v -> state.setValue(p, v)).orElse(state);
	}

	private static final class HouseView implements BlockView {
		private final Map<Long, BlockState> blocks;
		private final int height;

		HouseView(Map<Long, BlockState> blocks, int height) {
			this.blocks = blocks;
			this.height = height;
		}

		@Override
		public Cell cell(int x, int y, int z) {
			BlockState st = blocks.get(Pos.pack(x, y, z));
			return st == null ? Cell.OPEN : RoomCells.of(st);
		}

		@Override
		public boolean openToSky(int x, int y, int z) {
			for (int yy = y + 1; yy <= height + 2; yy++) if (cell(x, yy, z).kind() == CellKind.SOLID) return false;
			return true;
		}
	}
}
