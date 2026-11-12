package dev.villageheart.sim;

import dev.villageheart.VillageHeart;
import dev.villageheart.block.JobBlocks;
import dev.villageheart.block.MarkerBlock;
import dev.villageheart.block.MarkerBlocks;
import dev.villageheart.building.Buildings;
import dev.villageheart.core.Balance;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.building.JobBlockType;
import dev.villageheart.core.sim.RampProfile;
import dev.villageheart.core.sim.ScenarioAction;
import dev.villageheart.core.sim.SiteFinder;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.stock.StorageContainers;
import dev.villageheart.world.VillageCensus;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.worker.HireResult;
import dev.villageheart.worker.WorkerAttachments;
import dev.villageheart.worker.Workers;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.phys.AABB;

/**
 * The scenario actions of M11 (plan 3.9): {@code site} finds and flattens a lit patch of untouched land, {@code prefab} builds a room with
 * its marker, {@code field} a moist crop field, {@code grove} natural trees, {@code hire} puts the nearest free villager into a job (and,
 * with {@code replace}, hires a replacement whenever the job has nobody: the {@code starter} village loses workers to zombies now and then).
 * Positions of prefab, field and grove are relative to the site origin (its min corner, at the ground level).
 */
public final class StarterActions {
	private StarterActions() {}

	private static final Set<net.minecraft.world.level.block.Block> NATURAL_GROUND = Set.of(Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.COARSE_DIRT,
			Blocks.PODZOL, Blocks.SAND, Blocks.GRAVEL);

	private static int num(ScenarioAction a, String k, int dflt) { return a.fields().get(k) instanceof Number n ? n.intValue() : dflt; }

	private static int[] ints(ScenarioAction a, String k) {
		List<?> l = (List<?>) a.fields().get(k);
		int[] out = new int[l.size()];
		for (int i = 0; i < out.length; i++) out[i] = ((Number) l.get(i)).intValue();
		return out;
	}

	private static BlockPos site(Host ctx) {
		BlockPos s = ctx.site();
		if (s == null) throw new IllegalStateException("this action needs a site action first");
		return s;
	}

	private static void set(ServerLevel level, BlockPos p, BlockState s) { level.setBlock(p, s, Block.UPDATE_CLIENTS); }

	// ---- site ----------------------------------------------------------------------------------

	private static SiteFinder.Column column(ServerLevel level, int x, int z) {
		if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) return null;
		int top = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) - 1;
		// a tree standing on natural ground does not disqualify it: the flatten removes it (leaves and logs are looked through)
		boolean tree = false;
		for (int i = 0; i < 40 && (level.getBlockState(new BlockPos(x, top, z)).is(BlockTags.LEAVES) || level.getBlockState(new BlockPos(x, top, z)).is(BlockTags.LOGS)); i++) {
			top--;
			tree = true;
		}
		BlockPos p = new BlockPos(x, top, z);
		BlockState ground = level.getBlockState(p);
		BlockState above = level.getBlockState(p.above());
		boolean plantOrAir = tree || above.isAir() || (above.getFluidState().isEmpty() && (above.is(BlockTags.REPLACEABLE) || above.is(BlockTags.FLOWERS)));
		boolean natural = NATURAL_GROUND.contains(ground.getBlock()) && ground.getFluidState().isEmpty() && plantOrAir;
		return new SiteFinder.Column(top, natural, level.isPositionEntityTicking(p.above()));
	}

	public static void site(Host ctx, ScenarioAction a) {
		ServerLevel level = ctx.level();
		BlockPos heart = ctx.heart();
		if (heart == null) throw new IllegalStateException("site needs the claim first");
		int[] size = ints(a, "size");
		int w = size[0], d = size[1];
		Map<Long, SiteFinder.Column> cache = new HashMap<>();
		SiteFinder.Terrain terrain = (x, z) -> {
			long key = ((long) x << 32) ^ (z & 0xffffffffL);
			if (cache.containsKey(key)) return cache.get(key);
			SiteFinder.Column c = column(level, x, z);
			cache.put(key, c);
			return c;
		};
		long t0 = System.nanoTime();
		SiteFinder.Site found = SiteFinder.find(terrain, heart.getX(), heart.getZ(), w, d, num(a, "min_distance", 0), num(a, "max_distance", 72),
				Balance.CLAIM_RADIUS - 8, num(a, "max_deviation", 3), num(a, "odd_percent", 0)).orElseThrow(() -> new IllegalStateException("no flat site " + w + "x" + d + " near the Heart: "
				+ describeTerrain(terrain, heart, num(a, "max_distance", 72))));
		BlockPos origin = new BlockPos(found.x(), found.floorY(), found.z());
		VillageHeart.LOG.info("[sim] site {}x{} at {} (flatten cost {}, {} ms search)", w, d, origin.toShortString(), found.cost(), (System.nanoTime() - t0) / 1_000_000);

		// M26: the box covers the ramp ring too (RAMP blocks around the patch are re-graded): a villager standing there was buried (full on seed 1)
		AABB box = new AABB(origin.getX() - RAMP - 2, origin.getY() - 6, origin.getZ() - RAMP - 2, origin.getX() + w + RAMP + 2, origin.getY() + 14, origin.getZ() + d + RAMP + 2);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> !(x instanceof Player))) {
			if (e instanceof Enemy) {
				e.discard();
			} else {
				int ox = origin.getX() - RAMP - 4, oz = origin.getZ() + d / 2;
				int oy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ox, oz);
				e.teleportTo(ox + 0.5, oy, oz + 0.5);
			}
		}
		BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
		BlockState dirt = Blocks.DIRT.defaultBlockState();
		BlockState air = Blocks.AIR.defaultBlockState();
		for (int x = origin.getX(); x < origin.getX() + w; x++) for (int z = origin.getZ(); z < origin.getZ() + d; z++) {
			for (int y = origin.getY() - 4; y < origin.getY(); y++) set(level, new BlockPos(x, y, z), dirt);
			set(level, new BlockPos(x, origin.getY(), z), grass);
			for (int y = origin.getY() + 1; y <= origin.getY() + 12; y++) {
				BlockPos p = new BlockPos(x, y, z);
				if (!level.getBlockState(p).isAir()) set(level, p, air);
			}
		}
		ramp(level, origin, w, d);
		for (int x = origin.getX() + 2; x < origin.getX() + w; x += 6) for (int z = origin.getZ() + 2; z < origin.getZ() + d; z += 6) {
			set(level, new BlockPos(x, origin.getY() + 1, z), Blocks.TORCH.defaultBlockState());
		}
		ctx.setSite(origin);
	}

	/** Why nothing fit: counts of the columns around the Heart, and the height spread. */
	private static String describeTerrain(SiteFinder.Terrain t, BlockPos heart, int radius) {
		int total = 0, unloaded = 0, natural = 0, ticking = 0, lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
		for (int x = heart.getX() - radius; x <= heart.getX() + radius; x++) for (int z = heart.getZ() - radius; z <= heart.getZ() + radius; z++) {
			total++;
			SiteFinder.Column c = t.column(x, z);
			if (c == null) { unloaded++; continue; }
			if (c.natural()) { natural++; lo = Math.min(lo, c.y()); hi = Math.max(hi, c.y()); }
			if (c.entityTicking()) ticking++;
		}
		StringBuilder map = new StringBuilder("\n");
		for (int z = heart.getZ() - radius; z <= heart.getZ() + radius; z += 4) {
			for (int x = heart.getX() - radius; x <= heart.getX() + radius; x += 2) {
				SiteFinder.Column c = t.column(x, z);
				map.append(c == null ? '?' : !c.natural() ? '#' : !c.entityTicking() ? 'x' : (char) ('a' + Math.floorMod(c.y() - 60, 26)));
			}
			map.append('\n');
		}
		VillageHeart.LOG.info("[sim] terrain around the Heart (a=y60, b=61, ...; # not natural; x not ticking){}", map);
		return total + " columns: " + unloaded + " unloaded, " + natural + " natural (heights " + lo + ".." + hi + "), " + ticking + " entity-ticking";
	}

	/** Width of the ramp ring around the flattened patch: the patch may differ from the land by at most this many blocks (max_deviation <= 8). */
	private static final int RAMP = 9;

	/**
	 * Blends the flattened patch into the land around it with {@link RampProfile}: every column is within 1 block of each of its four neighbours
	 * (the ring is a slope of one block per step, so a villager that walks off the patch can always walk back; a cliff trapped the starter
	 * workers), a column k blocks outside the patch is within k of the patch floor, and the slope is extended past the ring (up to 12 more columns)
	 * until it meets the land. The ground of a column is found through the canopy: leaves, logs, air pockets under them and plants are skipped
	 * down to the first solid block, so no grass is placed in mid-air.
	 */
	public static void ramp(ServerLevel level, BlockPos origin, int w, int d) {
		BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState(), dirt = Blocks.DIRT.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
		int ox = origin.getX(), oz = origin.getZ();
		java.util.Map<Long, Integer> ground = new HashMap<>();
		RampProfile.Ground natural = (x, z) -> ground.computeIfAbsent(((long) x << 32) ^ (z & 0xffffffffL), key -> groundY(level, ox + x, oz + z));
		RampProfile.Ramp ramp = RampProfile.solve(natural, w, d, origin.getY(), RAMP);
		int m = ramp.margin();
		for (int i = 0; i < ramp.h().length; i++) for (int j = 0; j < ramp.h()[i].length; j++) {
			int x = ox + i - m, z = oz + j - m, t = ramp.h()[i][j];
			boolean patch = x >= ox && x < ox + w && z >= oz && z < oz + d;
			if (patch || t == RampProfile.UNKNOWN) continue;
			int g = natural.height(i - m, j - m);
			if (g == t) continue;
			int top = Math.max(level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), Math.max(g, t)) + 1;
			for (int y = Math.min(g, t) + 1; y < t; y++) set(level, new BlockPos(x, y, z), dirt);
			set(level, new BlockPos(x, t, z), grass);
			for (int y = t + 1; y <= top; y++) {
				BlockPos p = new BlockPos(x, y, z);
				if (!level.getBlockState(p).isAir()) set(level, p, air);
			}
		}
	}

	/** The real ground of a column: the first solid block that is not leaves or a log, seen from the top (through canopy air pockets); UNKNOWN when not loaded. */
	private static int groundY(ServerLevel level, int x, int z) {
		if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) return RampProfile.UNKNOWN;
		int y = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) - 1;
		for (int i = 0; i < 100 && y > level.getMinY(); i++, y--) {
			BlockPos p = new BlockPos(x, y, z);
			BlockState s = level.getBlockState(p);
			if (!s.isAir() && !s.is(BlockTags.LEAVES) && !s.is(BlockTags.LOGS) && s.blocksMotion()) break;
		}
		return y;
	}

	// ---- prefab --------------------------------------------------------------------------------

	private static Direction door(String s) {
		return switch (s) {
			case "north" -> Direction.NORTH;
			case "south" -> Direction.SOUTH;
			case "east" -> Direction.EAST;
			default -> Direction.WEST;
		};
	}

	public static void prefab(Host ctx, ScenarioAction a) {
		ServerLevel level = ctx.level();
		BlockPos site = site(ctx);
		BuildingType type = BuildingType.byId((String) a.fields().get("building")).orElseThrow();
		int[] at = ints(a, "at"), size = ints(a, "size");
		int w = size[0], d = size[1];
		Direction facing = door((String) a.fields().get("door"));
		BlockPos o = new BlockPos(site.getX() + at[0], site.getY(), site.getZ() + at[1]); // the floor planks replace the ground row: no step at the door
		BlockState planks = Blocks.OAK_PLANKS.defaultBlockState();
		for (int x = 0; x <= w + 1; x++) for (int y = 0; y <= 4; y++) for (int z = 0; z <= d + 1; z++) {
			boolean edge = x == 0 || x == w + 1 || y == 0 || y == 4 || z == 0 || z == d + 1;
			set(level, o.offset(x, y, z), edge ? planks : Blocks.AIR.defaultBlockState());
		}
		// the door and the marker above it, outside
		BlockPos lower;
		BlockPos marker;
		switch (facing) {
			case NORTH -> { lower = o.offset((w + 1) / 2, 1, 0); marker = lower.offset(0, 2, -1); }
			case SOUTH -> { lower = o.offset((w + 1) / 2, 1, d + 1); marker = lower.offset(0, 2, 1); }
			case WEST -> { lower = o.offset(0, 1, (d + 1) / 2); marker = lower.offset(-1, 2, 0); }
			default -> { lower = o.offset(w + 1, 1, (d + 1) / 2); marker = lower.offset(1, 2, 0); }
		}
		BlockState doorState = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, facing);
		set(level, lower, doorState.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		set(level, lower.above(), doorState.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));

		// contents along the inner walls, in list order. Frame: lateral 0..L-1 along the door wall, depth 0 (next to the door wall) ..D-1 (far wall).
		boolean alongX = facing.getAxis() == Direction.Axis.Z;
		int lat = alongX ? w : d, dep = alongX ? d : w;
		int doorLat = (lat + 1) / 2 - 1;
		List<int[]> ring = new ArrayList<>();
		for (int l = 0; l < lat; l++) ring.add(new int[] {l, dep - 1});
		for (int dd = dep - 2; dd >= 0; dd--) ring.add(new int[] {0, dd});
		for (int dd = dep - 2; dd >= 0; dd--) if (lat > 1) ring.add(new int[] {lat - 1, dd});
		Set<Long> used = new HashSet<>();
		List<BlockPos> chests = new ArrayList<>();
		for (Object c : (List<?>) a.fields().get("contents")) {
			String what = (String) c;
			if (what.equals("bed")) {
				boolean placed = false;
				for (int l = 0; l < lat && !placed; l++) {
					int[] head = {l, dep - 1}, foot = {l, dep - 2};
					if (dep < 2 || used.contains(key(head)) || used.contains(key(foot))) continue;
					BlockPos hp = cell(o, facing, w, d, head[0], head[1]), fp = cell(o, facing, w, d, foot[0], foot[1]);
					BlockState bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, facing.getOpposite());
					set(level, fp, bed.setValue(BedBlock.PART, BedPart.FOOT));
					set(level, hp, bed.setValue(BedBlock.PART, BedPart.HEAD));
					used.add(key(head));
					used.add(key(foot));
					placed = true;
				}
				if (!placed) throw new IllegalStateException("prefab " + type.id() + ": no room for a bed");
				continue;
			}
			boolean chest = what.equals("chest");
			int[] spot = null;
			for (int[] cand : ring) {
				if (used.contains(key(cand))) continue;
				if (cand[1] == 0 && Math.abs(cand[0] - doorLat) <= 1) continue; // keep the way in free
				if (chest && adjacentToChest(cand, chests, o, facing, w, d)) continue;
				spot = cand;
				break;
			}
			if (spot == null) throw new IllegalStateException("prefab " + type.id() + ": no room for " + what);
			used.add(key(spot));
			BlockPos p = cell(o, facing, w, d, spot[0], spot[1]);
			BlockState state = switch (what) {
				case "light" -> Blocks.TORCH.defaultBlockState();
				case "chest" -> Blocks.CHEST.defaultBlockState();
				case "composter" -> Blocks.COMPOSTER.defaultBlockState();
				case "smoker" -> Blocks.SMOKER.defaultBlockState();
				case "crafting_table" -> Blocks.CRAFTING_TABLE.defaultBlockState();
				case "grindstone" -> Blocks.GRINDSTONE.defaultBlockState();
				case "target" -> Blocks.TARGET.defaultBlockState();
				case "blast_furnace" -> Blocks.BLAST_FURNACE.defaultBlockState();
				case "anvil" -> Blocks.ANVIL.defaultBlockState();
				case "builders_table" -> JobBlocks.BLOCKS.get(JobBlockType.BUILDERS_TABLE).defaultBlockState();
				case "stonecutter" -> Blocks.STONECUTTER.defaultBlockState();
				case "fletching_table" -> Blocks.FLETCHING_TABLE.defaultBlockState();
				case "market_stall" -> JobBlocks.BLOCKS.get(JobBlockType.MARKET_STALL).defaultBlockState();
				default -> JobBlocks.BLOCKS.get(JobBlockType.CHOPPING_BLOCK).defaultBlockState();
			};
			set(level, p, state);
			if (chest) chests.add(p);
		}
		if (a.fields().get("chest_items") instanceof Map<?, ?> items) fill(level, chests, items);

		BlockState m = MarkerBlocks.BLOCKS.get(type).defaultBlockState().setValue(MarkerBlock.FACING, facing);
		if (!m.canSurvive(level, marker)) throw new IllegalStateException("prefab " + type.id() + ": the marker has no wall at " + marker.toShortString());
		set(level, marker, m);
		VillageHeart.LOG.info("[sim] prefab {} at {} (marker {})", type.id(), o.toShortString(), marker.toShortString());
	}

	private static long key(int[] c) { return ((long) c[0] << 32) | c[1]; }

	/** The block position of the interior cell (lateral, depth) in the room frame of a door facing {@code f}. */
	private static BlockPos cell(BlockPos o, Direction f, int w, int d, int lat, int depth) {
		return switch (f) {
			case NORTH -> o.offset(1 + lat, 1, 1 + depth);
			case SOUTH -> o.offset(1 + lat, 1, d - depth);
			case WEST -> o.offset(1 + depth, 1, 1 + lat);
			default -> o.offset(w - depth, 1, 1 + lat);
		};
	}

	private static boolean adjacentToChest(int[] cand, List<BlockPos> chests, BlockPos o, Direction f, int w, int d) {
		BlockPos p = cell(o, f, w, d, cand[0], cand[1]);
		for (BlockPos c : chests) if (Math.abs(c.getX() - p.getX()) + Math.abs(c.getZ() - p.getZ()) <= 1) return true;
		return false;
	}

	private static void fill(ServerLevel level, List<BlockPos> chests, Map<?, ?> items) {
		List<Container> cs = new ArrayList<>();
		for (BlockPos c : chests) {
			Container k = StorageContainers.at(level, c);
			if (k != null) cs.add(k);
		}
		if (cs.isEmpty()) throw new IllegalStateException("chest_items: the prefab has no chest");
		int ci = 0, slot = 0;
		for (Map.Entry<?, ?> e : items.entrySet()) {
			Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse((String) e.getKey()));
			int left = ((Number) e.getValue()).intValue();
			while (left > 0) {
				if (slot >= cs.get(ci).getContainerSize()) {
					ci++;
					slot = 0;
					if (ci >= cs.size()) throw new IllegalStateException("chest_items: the chests are full");
				}
				int n = Math.min(left, new ItemStack(item).getMaxStackSize());
				cs.get(ci).setItem(slot++, new ItemStack(item, n));
				left -= n;
			}
		}
		for (Container k : cs) k.setChanged();
	}

	// ---- field ---------------------------------------------------------------------------------

	public static void field(Host ctx, ScenarioAction a) {
		ServerLevel level = ctx.level();
		BlockPos site = site(ctx);
		int[] at = ints(a, "at");
		int size = num(a, "size", 9);
		String crop = (String) a.fields().get("crop");
		int age = num(a, "age", 7);
		Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse("minecraft:" + crop));
		BlockState cropState = ((CropBlock) block).getStateForAge(age);
		BlockState farmland = Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7);
		int cx = size / 2;
		int cells = 0;
		for (int x = 0; x < size; x++) for (int z = 0; z < size; z++) {
			BlockPos g = new BlockPos(site.getX() + at[0] + x, site.getY(), site.getZ() + at[1] + z);
			if (x == cx && z == cx) {
				set(level, g, Blocks.WATER.defaultBlockState());
				set(level, g.above(), Blocks.AIR.defaultBlockState());
				continue;
			}
			set(level, g, farmland);
			set(level, g.above(), cropState);
			cells++;
		}
		VillageHeart.LOG.info("[sim] field {}x{} of {} at age {}: {} cells", size, size, crop, age, cells);
	}

	// ---- grove ---------------------------------------------------------------------------------

	public static void grove(Host ctx, ScenarioAction a) {
		ServerLevel level = ctx.level();
		BlockPos site = site(ctx);
		int[] area = ints(a, "area");
		int trees = num(a, "trees", 1), spacing = num(a, "spacing", 5);
		long seed = num(a, "seed", 0);
		ConfiguredFeature<?, ?> feature = level.registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE).getOrThrow(TreeFeatures.OAK).value();
		int placed = 0, tries = 0;
		for (int x = spacing / 2; x < area[2] && placed < trees; x += spacing) for (int z = spacing / 2; z < area[3] && placed < trees; z += spacing) {
			BlockPos base = new BlockPos(site.getX() + area[0] + x, site.getY() + 1, site.getZ() + area[1] + z);
			if (!level.getBlockState(base.below()).is(Blocks.GRASS_BLOCK)) continue;
			if (feature.place(level, level.getChunkSource().getGenerator(), RandomSource.create(seed + tries++), base)) placed++;
		}
		if (placed < trees) throw new IllegalStateException("grove: only " + placed + " of " + trees + " trees could be placed");
		VillageHeart.LOG.info("[sim] grove: {} oak trees", placed);
	}

	// ---- hire ----------------------------------------------------------------------------------

	/** Standing hires ({@code replace: true}): job id to building type. */
	private record Standing(BuildingType type, int want) {}

	private static final Map<String, Standing> STANDING = new java.util.LinkedHashMap<>();

	public static void resetStanding() {
		STANDING.clear();
		LAST_WAIT.clear();
	}

	/** The last reason each job's empty slot stayed empty (logged once per change). */
	private static final Map<String, String> LAST_WAIT = new java.util.HashMap<>();

	/** A standing hire had an empty slot this sample and could not fill it: {@code work.sim.rehire_wait.<job>.<reason>} (see {@code DeathLog}). */
	private static void rehireWait(String job, String reason) {
		dev.villageheart.worker.job.WorkStats.add("sim", "rehire_wait." + job + "." + reason, 1);
		if (!reason.equals(LAST_WAIT.put(job, reason))) VillageHeart.LOG.info("[sim] {} slot empty, not re-hired: {}", job, reason);
	}

	/** Why {@code hireNow} could not hire: {@code no_building}, {@code no_free_villager} or {@code refused_<result>}. */
	static final class HireBlocked extends IllegalStateException {
		final String reason;

		HireBlocked(String reason, String message) {
			super(message);
			this.reason = reason;
		}
	}

	public static void hire(Host ctx, ScenarioAction a) {
		JobType job = JobType.byId((String) a.fields().get("job")).orElseThrow();
		BuildingType type = BuildingType.byId((String) a.fields().get("building")).orElseThrow();
		boolean teleport = !(a.fields().get("teleport") instanceof Boolean b) || b;
		hireNow(ctx, job, type, teleport, true);
		dev.villageheart.worker.job.WorkStats.add("sim", "hires", 1);
		if (a.fields().get("replace") instanceof Boolean b && b) registerStanding(a);
	}

	static void registerStanding(ScenarioAction a) {
		// one more worker wanted per action: two guard hires keep two guards on the roll
		String job = (String) a.fields().get("job");
		Standing old = STANDING.get(job);
		STANDING.put(job, new Standing(BuildingType.byId((String) a.fields().get("building")).orElseThrow(), old == null ? 1 : old.want() + 1));
	}

	/** Called every 100 ticks: a standing hire whose job has nobody gets a replacement (a worker died). */
	public static void maintain(Host ctx) {
		RaidSim.keepGolemCount(ctx.level());
		Optional<VillageState> village = VillageClaims.current(ctx.server());
		if (village.isEmpty()) return;
		sampleNotTicking(ctx, village.get());
		if (STANDING.isEmpty()) return;
		for (Map.Entry<String, Standing> e : STANDING.entrySet()) {
			long staffed = village.get().workers().stream().filter(w -> w.jobId().equals(e.getKey())).count();
			if (staffed >= e.getValue().want()) continue;
			// a guard is hired by day (M18 r3): the one that fell at night is replaced the next morning, not before
			if (e.getKey().equals(JobType.GUARD.id()) && !dev.villageheart.core.guard.GuardTraining.mayHire(ctx.level().getDayTime())) {
				rehireWait(e.getKey(), "night");
				continue;
			}
			try {
				hireNow(ctx, JobType.byId(e.getKey()).orElseThrow(), e.getValue().type(), true, false);
				dev.villageheart.worker.job.WorkStats.add("sim", "hires", 1);
				dev.villageheart.worker.job.WorkStats.add("sim", "rehires", 1);
				LAST_WAIT.remove(e.getKey());
				VillageHeart.LOG.info("[sim] re-hired a {}", e.getKey());
			} catch (HireBlocked ex) {
				rehireWait(e.getKey(), ex.reason);
				VillageHeart.LOG.info("[sim] could not re-hire a {} yet: {}", e.getKey(), ex.getMessage());
			} catch (RuntimeException ex) {
				rehireWait(e.getKey(), "error");
				VillageHeart.LOG.info("[sim] could not re-hire a {} yet: {}", e.getKey(), ex.toString());
			}
		}
	}

	/**
	 * Counts (into {@code work.workers.not_ticking}) the hired workers that stand in a chunk that does not tick entities, or are not loaded: such a
	 * villager never runs its brain again and looks "ok" for good. {@code starter} asserts the sum is 0.
	 */
	private static void sampleNotTicking(Host ctx, VillageState village) {
		long n = 0;
		for (var w : village.workers()) {
			Villager v = Workers.findLoaded(ctx.server(), w.villager()).orElse(null);
			if (v == null || !(v.level() instanceof ServerLevel l) || !l.isPositionEntityTicking(v.blockPosition())) n++;
		}
		if (n > 0) {
			dev.villageheart.worker.job.WorkStats.add("workers", "not_ticking", n);
			VillageHeart.LOG.warn("[sim] {} hired worker(s) are not in an entity-ticking chunk", n);
		}
	}

	/**
	 * Every {@link dev.villageheart.core.sim.SimArea#FOLLOW_INTERVAL_TICKS} ticks: moves the follow tickets ({@link SimTickets}, a player's
	 * simulation distance around each hired worker) and records the day's farthest worker from the Heart in {@code work.workers.max_heart_distance}
	 * (blocks, rounded down), so a worker that keeps ticking far away is still seen.
	 */
	public static void followWorkers(Host ctx, SimTickets tickets) {
		Optional<VillageState> village = VillageClaims.current(ctx.server());
		Map<java.util.UUID, dev.villageheart.core.sim.SimArea.Chunk> at = new java.util.HashMap<>();
		double far = -1;
		if (village.isPresent()) {
			for (var w : village.get().workers()) {
				Villager v = Workers.findLoaded(ctx.server(), w.villager()).orElse(null);
				if (v == null || v.level() != ctx.level()) continue;
				at.put(w.villager(), SimTickets.chunkOf(v.blockPosition()));
				if (ctx.heart() != null) far = Math.max(far, Math.sqrt(v.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(ctx.heart()))));
			}
		}
		tickets.follow(at);
		if (far >= 0) dev.villageheart.worker.job.WorkStats.max("workers", "max_heart_distance", (long) far);
	}

	private static void hireNow(Host ctx, JobType job, BuildingType type, boolean teleport, boolean strict) {
		ServerLevel level = ctx.level();
		BlockPos from = ctx.site() != null ? ctx.site() : ctx.heart();
		List<Buildings.BuildingView> valid = Buildings.valid(ctx.server(), type);
		if (valid.isEmpty()) {
			StringBuilder why = new StringBuilder();
			for (Buildings.BuildingView b : Buildings.all(ctx.server())) {
				if (b.type() == type) why.append(b.status()).append(' ').append(b.verdict().map(v -> v.reasons().toString()).orElse("")).append("; ");
			}
			throw new HireBlocked("no_building", "no valid " + type.id() + " to hire a " + job.id() + " into: " + why);
		}
		Buildings.BuildingView b = valid.stream().min(Comparator.comparingDouble(v -> v.marker().distSqr(from))).orElseThrow();
		VillageState village = VillageClaims.current(ctx.server()).orElseThrow();
		List<Villager> candidates = new ArrayList<>();
		for (Villager v : VillageCensus.of(ctx.server(), village)) {
			if (v.isBaby() || WorkerAttachments.isWorker(v) || v.getVillagerData().profession().is(VillagerProfession.NITWIT)) continue;
			candidates.add(v);
		}
		if (candidates.isEmpty()) throw new HireBlocked("no_free_villager", "no free adult villager to hire as " + job.id());
		candidates.sort(Comparator.<Villager, Boolean>comparing(v -> !v.getVillagerData().profession().is(VillagerProfession.NONE))
				.thenComparingDouble(v -> v.distanceToSqr(b.marker().getX(), b.marker().getY(), b.marker().getZ())));
		Villager v = candidates.get(0);
		if (teleport) {
			BlockPos front = b.marker().below(2);
			v.teleportTo(front.getX() + 0.5, front.getY(), front.getZ() + 0.5);
		}
		HireResult r = Workers.hire(level, v, job, Optional.of(VillageClaims.locate(b.level(), b.marker())));
		if (!(r instanceof HireResult.Hired)) throw new HireBlocked("refused_" + r.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT), "hire refused: " + r);
		VillageHeart.LOG.info("[sim] hired a {} at {}", job.id(), b.marker().toShortString());
	}
}
