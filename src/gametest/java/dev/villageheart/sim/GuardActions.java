package dev.villageheart.sim;

import dev.villageheart.VillageHeart;
import dev.villageheart.block.MarkerBlock;
import dev.villageheart.block.MarkerBlocks;
import dev.villageheart.core.Balance;
import dev.villageheart.core.Tier;
import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.sim.OutsideBand;
import dev.villageheart.core.sim.ScenarioAction;
import dev.villageheart.core.sim.WallRing;
import dev.villageheart.world.VillageClaims;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The scenario actions M18 adds (plan 3.10): {@code tier} sets the village tier (no tier command exists), {@code training_yard} builds a
 * Training Yard (an oak fence post with the marker and two target blocks), {@code night_spawn} places a few hostile mobs inside the claim at a
 * given tick (the scripted threat of the {@code defended} scenario: it makes "at most one mob death" mean something).
 */
public final class GuardActions {
	private GuardActions() {}

	private static int num(ScenarioAction a, String k, int dflt) { return a.fields().get(k) instanceof Number n ? n.intValue() : dflt; }

	private static void set(ServerLevel level, BlockPos p, BlockState s) { level.setBlock(p, s, Block.UPDATE_CLIENTS); }

	public static void tier(Host ctx, ScenarioAction a) {
		Tier t = Tier.valueOf(((String) a.fields().get("tier")).toUpperCase(java.util.Locale.ROOT));
		VillageClaims.data(ctx.server()).update(v -> v.withTier(t));
		VillageHeart.LOG.info("[sim] tier set to {}", t);
	}

	public static void trainingYard(Host ctx, ScenarioAction a) {
		ServerLevel level = ctx.level();
		BlockPos site = ctx.site();
		if (site == null) throw new IllegalStateException("training_yard needs a site action first");
		var at = (java.util.List<?>) a.fields().get("at");
		BlockPos post = new BlockPos(site.getX() + ((Number) at.get(0)).intValue(), site.getY() + 1, site.getZ() + ((Number) at.get(1)).intValue());
		set(level, post, Blocks.OAK_FENCE.defaultBlockState());
		set(level, post.east(2).south(), Blocks.TARGET.defaultBlockState());
		set(level, post.south(3), Blocks.TARGET.defaultBlockState());
		BlockPos marker = post.south();
		BlockState m = MarkerBlocks.BLOCKS.get(BuildingType.TRAINING_YARD).defaultBlockState().setValue(MarkerBlock.FACING, Direction.SOUTH);
		if (!m.canSurvive(level, marker)) throw new IllegalStateException("training_yard: the marker has no support at " + marker.toShortString());
		set(level, marker, m);
		VillageHeart.LOG.info("[sim] training yard at {}", marker.toShortString());
	}

	/**
	 * {@code wall_ring} (the {@code defended_walled} scenario): a two-high cobblestone wall on the edge of the site ({@code size} [w, d], the same as the
	 * {@code site} action) with ONE oak fence gate in the south edge at x offset {@code gate} (default w / 2), the cell above the gate left open for the
	 * walker. Solid cobblestone, not wall blocks (an unconnected wall block is a 4-pixel post a zombie walks between). A torch on top every 6 blocks.
	 * The wall is two blocks above the flattened site, and higher where the natural ground just outside is higher: its top is always at least
	 * {@link #WALL_CLEARANCE} blocks above the outside ground (M26 prep: on seed 1 the ground west of the site is a block up, so a two-high wall
	 * was a one-block step from outside and the scripted zombies walked over it). The ground in front of the gate is cut down to the site's level
	 * ({@link #digGateApproach}): a mob one block up in front of a fence gate steps over it.
	 */
	public static void wallRing(Host ctx, ScenarioAction a) {
		ServerLevel level = ctx.level();
		BlockPos site = ctx.site();
		if (site == null) throw new IllegalStateException("wall_ring needs a site action first");
		var size = (java.util.List<?>) a.fields().get("size");
		int w = ((Number) size.get(0)).intValue(), d = ((Number) size.get(1)).intValue();
		int gateX = num(a, "gate", w / 2);
		int y = site.getY();
		int dug = digGateApproach(level, site, d, gateX);
		int cells = 0, raised = 0, maxRaise = 0;
		for (int x = 0; x < w; x++) {
			for (int z = 0; z < d; z++) {
				if (x != 0 && x != w - 1 && z != 0 && z != d - 1) continue;
				boolean gate = z == d - 1 && x == gateX;
				BlockPos base = new BlockPos(site.getX() + x, y, site.getZ() + z);
				int top = wallTop(level, site, w, d, x, z); // the y of the first air cell above the wall
				if (top > y + 3) {
					raised++;
					maxRaise = Math.max(maxRaise, top - (y + 3));
				}
				set(level, base, Blocks.COBBLESTONE.defaultBlockState());
				if (gate) {
					if (top > y + 3) VillageHeart.LOG.warn("[sim] wall ring: the ground outside the gate is {} block(s) up; a zombie may jump the gate", top - (y + 3));
					set(level, base.above(), Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(net.minecraft.world.level.block.FenceGateBlock.FACING, Direction.NORTH)
							.setValue(net.minecraft.world.level.block.FenceGateBlock.OPEN, false));
					set(level, base.above(2), Blocks.AIR.defaultBlockState());
					set(level, base.above(3), Blocks.AIR.defaultBlockState());
				} else {
					for (int wy = y + 1; wy < top; wy++) set(level, new BlockPos(base.getX(), wy, base.getZ()), Blocks.COBBLESTONE.defaultBlockState());
					if ((x + z) % 6 == 0) set(level, new BlockPos(base.getX(), top, base.getZ()), Blocks.TORCH.defaultBlockState());
				}
				cells++;
			}
		}
		VillageHeart.LOG.info("[sim] wall ring {}x{} at {}: {} cells, gate at x+{}, {} cells raised for higher ground outside (up to +{}), {} gate approach blocks dug",
				w, d, site.toShortString(), cells, gateX, raised, maxRaise, dug);
	}

	/**
	 * The ground in front of the gate (3 wide, 2 deep, outside the south edge) is cut down to the site's level: a fence gate is 1.5 high, so a
	 * mob standing one block up in front of it steps over it (seed 1: the ground there is a block up). Walkers step down to the gate and through.
	 */
	static int digGateApproach(ServerLevel level, BlockPos site, int d, int gateX) {
		int dug = 0;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = 0; dz <= 1; dz++) {
				int x = site.getX() + gateX + dx, z = site.getZ() + d + dz;
				for (int y = site.getY() + 1; y <= site.getY() + 1 + MAX_GROUND_STEP; y++) {
					BlockPos p = new BlockPos(x, y, z);
					if (level.getBlockState(p).isAir()) continue;
					set(level, p, Blocks.AIR.defaultBlockState());
					dug++;
				}
				BlockPos floor = new BlockPos(x, site.getY(), z);
				if (!level.getBlockState(floor).isSolidRender()) set(level, floor, Blocks.GRASS_BLOCK.defaultBlockState());
			}
		}
		return dug;
	}

	/** A wall's top must be this many blocks above the ground a mob stands on outside it (a zombie steps up one block, a fence's 1.5 stops it). */
	public static final int WALL_CLEARANCE = 2;

	/** Outside columns more than this many blocks above the site's walking level are not ground (the {@code site} action's largest deviation). */
	static final int MAX_GROUND_STEP = 5;

	/**
	 * The first air y above the wall cell ({@code x}, {@code z}) of the ring: two blocks above the site's floor, or {@link #WALL_CLEARANCE}
	 * above the highest natural ground in the cells just outside it (the outward neighbours, diagonals at the corners), whichever is higher.
	 */
	static int wallTop(ServerLevel level, BlockPos site, int w, int d, int x, int z) {
		int top = site.getY() + 3;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				int nx = x + dx, nz = z + dz;
				if (nx >= 0 && nx < w && nz >= 0 && nz < d) continue; // on or inside the ring
				int feet = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, site.getX() + nx, site.getZ() + nz);
				if (feet > site.getY() + 1 + MAX_GROUND_STEP) continue; // a trunk or a building, not ground a mob walks on
				top = Math.max(top, feet + WALL_CLEARANCE);
			}
		}
		return top;
	}

	/**
	 * {@code lamps}: fence posts with a torch on top (3 high, light 14) on a grid of {@code spacing} blocks over the site, on bare grass or dirt only
	 * (never on a building, a field or a path), so the open ground inside the wall is lit and nothing spawns there.
	 */
	public static void lamps(Host ctx, ScenarioAction a) {
		ServerLevel level = ctx.level();
		BlockPos site = ctx.site();
		if (site == null) throw new IllegalStateException("lamps needs a site action first");
		var size = (java.util.List<?>) a.fields().get("size");
		int w = ((Number) size.get(0)).intValue(), d = ((Number) size.get(1)).intValue();
		int spacing = num(a, "spacing", 7);
		int placed = 0;
		for (int x = spacing / 2 + 1; x < w - 1; x += spacing) {
			for (int z = spacing / 2 + 1; z < d - 1; z += spacing) {
				BlockPos ground = new BlockPos(site.getX() + x, site.getY(), site.getZ() + z);
				var below = level.getBlockState(ground).getBlock();
				if (below != Blocks.GRASS_BLOCK && below != Blocks.DIRT) continue;
				if (!level.getBlockState(ground.above()).isAir() || !level.getBlockState(ground.above(2)).isAir() || !level.getBlockState(ground.above(3)).isAir()) continue;
				set(level, ground.above(), Blocks.OAK_FENCE.defaultBlockState());
				set(level, ground.above(2), Blocks.OAK_FENCE.defaultBlockState());
				set(level, ground.above(3), Blocks.TORCH.defaultBlockState());
				placed++;
			}
		}
		VillageHeart.LOG.info("[sim] lamps: {} posts at spacing {}", placed, spacing);
	}

	/**
	 * Job-site and meeting blocks of the vanilla village. Removed when they stand outside the ring so unhired villagers
	 * stop walking out to them. Beds are handled apart (both halves). Blocks inside the ring, including the scenario's
	 * own buildings, are left alone.
	 */
	private static final Set<Block> OUTSIDE_POI = Set.of(Blocks.COMPOSTER, Blocks.LECTERN, Blocks.BARREL, Blocks.LOOM, Blocks.FLETCHING_TABLE,
			Blocks.CARTOGRAPHY_TABLE, Blocks.BREWING_STAND, Blocks.BLAST_FURNACE, Blocks.GRINDSTONE, Blocks.SMITHING_TABLE, Blocks.SMOKER,
			Blocks.CAULDRON, Blocks.WATER_CAULDRON, Blocks.STONECUTTER, Blocks.BELL);

	/**
	 * {@code move_in} (the {@code defended_walled} scenario): the ring is built around the new site, and the vanilla village
	 * (bell, beds, job sites, villagers) stands outside it, which is where the scripted zombies used to do all their killing.
	 * This brings that village inside the ring. Villagers are given a bed inside (extra beds on open grass when the houses
	 * do not have enough) and a bell inside, and every bed, job site and bell outside the ring and inside the claim is removed
	 * so nothing calls them back out. The threat still spawns outside the wall.
	 */
	public static void moveIn(Host ctx, ScenarioAction a) {
		ServerLevel level = ctx.level();
		BlockPos site = ctx.site();
		BlockPos heart = ctx.heart();
		if (site == null) throw new IllegalStateException("move_in needs a site action first");
		if (heart == null) throw new IllegalStateException("move_in needs a claim first");
		var size = (java.util.List<?>) a.fields().get("size");
		int w = ((Number) size.get(0)).intValue(), d = ((Number) size.get(1)).intValue();
		WallRing ring = new WallRing(site.getX(), site.getZ(), w, d);
		long r2 = (long) Balance.CLAIM_RADIUS * Balance.CLAIM_RADIUS;
		Vec3 centre = Vec3.atCenterOf(heart);
		List<Villager> villagers = level.getEntitiesOfClass(Villager.class,
				AABB.ofSize(centre, Balance.CLAIM_RADIUS * 2.0, 64, Balance.CLAIM_RADIUS * 2.0),
				v -> v.isAlive() && v.distanceToSqr(centre) <= r2);
		for (Villager v : villagers) forgetVillage(v);
		int already = bedHeads(level, site, ring).size();
		int added = placeBeds(level, site, ring, Math.max(0, villagers.size() - already));
		BlockPos bell = ensureBell(level, site, ring);
		int removed = clearOutside(level, heart, ring, r2);
		List<BlockPos> beds = bedHeads(level, site, ring);
		if (beds.size() < villagers.size()) {
			throw new IllegalStateException("move_in: " + villagers.size() + " villagers but only " + beds.size()
					+ " beds inside the ring (" + added + " added on open grass)");
		}
		PoiManager poi = level.getPoiManager();
		int i = 0;
		for (Villager v : villagers) {
			BlockPos bed = beds.get(i++);
			ensurePoi(level, poi, bed, PoiTypes.HOME); // a bed placed this same tick is not in the POI section yet
			Optional<BlockPos> got = poi.take(t -> t.is(PoiTypes.HOME), (t, p) -> p.equals(bed), bed, 1);
			if (got.isEmpty()) throw new IllegalStateException("move_in: no free bed POI at " + bed.toShortString());
			v.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), bed));
			ensurePoi(level, poi, bell, PoiTypes.MEETING);
			poi.take(t -> t.is(PoiTypes.MEETING), (t, p) -> p.equals(bell), bell, 1); // shared tickets; a miss still sets the memory
			v.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(level.dimension(), bell));
			if (v.isSleeping()) v.stopSleeping();
			v.getNavigation().stop();
			v.teleportTo(bed.getX() + 0.5, bed.getY(), bed.getZ() + 0.5);
			v.setDeltaMovement(Vec3.ZERO);
			if (!WallRing.INSIDE.equals(ring.side(v.blockPosition().getX(), v.blockPosition().getZ()))) {
				throw new IllegalStateException("move_in: " + v.blockPosition().toShortString() + " is not inside the ring");
			}
		}
		VillageHeart.LOG.info("[sim] move_in: {} villagers inside, {} beds ({} already, {} added), {} outside poi blocks removed, bell {}",
				villagers.size(), beds.size(), already, added, removed, bell.toShortString());
	}

	/** Registers a POI when the block was placed in this same tick and the section has not picked it up yet. */
	private static void ensurePoi(ServerLevel level, PoiManager poi, BlockPos pos,
			net.minecraft.resources.ResourceKey<net.minecraft.world.entity.ai.village.poi.PoiType> type) {
		if (poi.exists(pos, t -> t.is(type))) return;
		var holder = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.POINT_OF_INTEREST_TYPE).getOrThrow(type);
		poi.add(pos, holder);
	}

	private static void forgetVillage(Villager v) {
		v.releasePoi(MemoryModuleType.HOME);
		v.releasePoi(MemoryModuleType.JOB_SITE);
		v.releasePoi(MemoryModuleType.POTENTIAL_JOB_SITE);
		v.releasePoi(MemoryModuleType.MEETING_POINT);
		v.getBrain().eraseMemory(MemoryModuleType.HOME);
		v.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
		v.getBrain().eraseMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
		v.getBrain().eraseMemory(MemoryModuleType.MEETING_POINT);
		v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
	}

	/** Heads of beds strictly inside the ring, from the site floor up through a room. */
	private static List<BlockPos> bedHeads(ServerLevel level, BlockPos site, WallRing ring) {
		List<BlockPos> heads = new ArrayList<>();
		for (int x = ring.x(); x < ring.x() + ring.w(); x++) {
			for (int z = ring.z(); z < ring.z() + ring.d(); z++) {
				if (!WallRing.INSIDE.equals(ring.side(x, z))) continue;
				for (int y = site.getY(); y <= site.getY() + 4; y++) {
					BlockPos p = new BlockPos(x, y, z);
					BlockState s = level.getBlockState(p);
					if (s.getBlock() instanceof BedBlock && s.getValue(BedBlock.PART) == BedPart.HEAD) heads.add(p);
				}
			}
		}
		return heads;
	}

	/** Extra red beds on open grass inside the ring, never in front of a door or the gate. Returns how many were placed. */
	private static int placeBeds(ServerLevel level, BlockPos site, WallRing ring, int need) {
		int added = 0;
		for (int x = ring.x() + 1; x < ring.x() + ring.w() - 2 && added < need; x++) {
			for (int z = ring.z() + 1; z < ring.z() + ring.d() - 2 && added < need; z++) {
				BlockPos foot = new BlockPos(x, site.getY() + 1, z), head = foot.east();
				if (!openGrass(level, site, foot) || !openGrass(level, site, head)) continue;
				if (nearPassage(level, foot) || nearPassage(level, head)) continue;
				BlockState bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.EAST);
				set(level, foot, bed.setValue(BedBlock.PART, BedPart.FOOT));
				set(level, head, bed.setValue(BedBlock.PART, BedPart.HEAD));
				added++;
			}
		}
		return added;
	}

	private static boolean openGrass(ServerLevel level, BlockPos site, BlockPos p) {
		Block floor = level.getBlockState(new BlockPos(p.getX(), site.getY(), p.getZ())).getBlock();
		if (floor != Blocks.GRASS_BLOCK && floor != Blocks.DIRT) return false;
		return level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir();
	}

	private static boolean nearPassage(ServerLevel level, BlockPos p) {
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			for (int dy = -1; dy <= 0; dy++) {
				Block b = level.getBlockState(p.relative(dir).above(dy)).getBlock();
				if (b instanceof DoorBlock || b instanceof FenceGateBlock) return true;
			}
		}
		return false;
	}

	/** A floor bell on open grass inside the ring, the one already there or a new one near the centre. */
	private static BlockPos ensureBell(ServerLevel level, BlockPos site, WallRing ring) {
		BlockPos existing = findBell(level, site, ring);
		if (existing != null) return existing;
		int cx = ring.x() + ring.w() / 2, cz = ring.z() + ring.d() / 2, bestD = Integer.MAX_VALUE;
		BlockPos best = null;
		for (int x = ring.x() + 2; x < ring.x() + ring.w() - 2; x++) {
			for (int z = ring.z() + 2; z < ring.z() + ring.d() - 2; z++) {
				BlockPos p = new BlockPos(x, site.getY() + 1, z);
				if (!openGrass(level, site, p) || nearPassage(level, p)) continue;
				int dist = Math.abs(x - cx) + Math.abs(z - cz);
				if (dist < bestD) { bestD = dist; best = p; }
			}
		}
		if (best == null) throw new IllegalStateException("move_in: no open ground inside the ring for a bell");
		set(level, best, Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT, BellAttachType.FLOOR).setValue(BellBlock.FACING, Direction.NORTH));
		return best;
	}

	private static BlockPos findBell(ServerLevel level, BlockPos site, WallRing ring) {
		for (int x = ring.x() + 1; x < ring.x() + ring.w() - 1; x++) {
			for (int z = ring.z() + 1; z < ring.z() + ring.d() - 1; z++) {
				if (!WallRing.INSIDE.equals(ring.side(x, z))) continue;
				for (int y = site.getY(); y <= site.getY() + 4; y++) {
					BlockPos p = new BlockPos(x, y, z);
					if (level.getBlockState(p).is(Blocks.BELL)) return p;
				}
			}
		}
		return null;
	}

	/** Beds (both halves), job sites and bells that are not strictly inside the ring, within the claim, on loaded ground. */
	private static int clearOutside(ServerLevel level, BlockPos heart, WallRing ring, long r2) {
		int removed = 0;
		Set<Long> done = new HashSet<>();
		int r = Balance.CLAIM_RADIUS;
		for (int x = heart.getX() - r; x <= heart.getX() + r; x++) {
			for (int z = heart.getZ() - r; z <= heart.getZ() + r; z++) {
				long dx = (long) x - heart.getX(), dz = (long) z - heart.getZ();
				if (dx * dx + dz * dz > r2) continue;
				if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) continue;
				if (WallRing.INSIDE.equals(ring.side(x, z))) continue;
				int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
				int y0 = Math.max(level.getMinY(), Math.min(top, heart.getY()) - 8);
				int y1 = Math.min(level.getMaxY() - 1, Math.max(top, heart.getY()) + 6);
				for (int y = y0; y <= y1; y++) {
					BlockPos p = new BlockPos(x, y, z);
					if (!done.add(p.asLong())) continue;
					BlockState s = level.getBlockState(p);
					if (s.getBlock() instanceof BedBlock) {
						Direction facing = s.getValue(BedBlock.FACING);
						BlockPos other = s.getValue(BedBlock.PART) == BedPart.FOOT ? p.relative(facing) : p.relative(facing.getOpposite());
						done.add(other.asLong());
						set(level, p, Blocks.AIR.defaultBlockState());
						set(level, other, Blocks.AIR.defaultBlockState());
						removed++;
					} else if (OUTSIDE_POI.contains(s.getBlock())) {
						set(level, p, Blocks.AIR.defaultBlockState());
						removed++;
					}
				}
			}
		}
		return removed;
	}

	/**
	 * {@code count} mobs of type {@code mob}. Without {@code outside}: 12..radius blocks from the Heart. With {@code outside}
	 * (a wall rectangle): in the band just outside that rectangle, so the threat meets the wall instead of the old village.
	 */
	public static void nightSpawn(Host ctx, ScenarioAction a) {
		ServerLevel level = ctx.level();
		BlockPos heart = ctx.heart();
		if (heart == null) throw new IllegalStateException("night_spawn needs a claim first");
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse((String) a.fields().get("mob")));
		int count = num(a, "count", 1), radius = num(a, "radius", 20);
		int placed = 0;
		int[] outside = a.fields().get("outside") instanceof java.util.List<?> o ? new int[] {((Number) o.get(0)).intValue(), ((Number) o.get(1)).intValue()} : null;
		BlockPos site = ctx.site();
		if (outside != null && site == null) throw new IllegalStateException("night_spawn outside needs a site action first");
		for (int attempt = 0; attempt < 400 && placed < count; attempt++) {
			int x, z;
			if (outside != null) {
				long p = OutsideBand.pick(level.getRandom()::nextInt, site.getX(), site.getZ(), outside[0], outside[1]);
				x = OutsideBand.x(p);
				z = OutsideBand.z(p);
			} else {
				double angle = level.getRandom().nextDouble() * Math.PI * 2, dist = 12 + level.getRandom().nextDouble() * Math.max(0, radius - 12);
				x = heart.getX() + (int) Math.round(Math.cos(angle) * dist);
				z = heart.getZ() + (int) Math.round(Math.sin(angle) * dist);
			}
			if (!level.isLoaded(new BlockPos(x, heart.getY(), z))) continue;
			if (outside != null && x >= site.getX() - 2 && x <= site.getX() + outside[0] + 1
					&& z >= site.getZ() - 2 && z <= site.getZ() + outside[1] + 1) continue; // inside or on the wall: not outside it
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos feet = new BlockPos(x, y, z);
			if (!level.getBlockState(feet.below()).isSolidRender() || !level.getBlockState(feet).isAir() || !level.getBlockState(feet.above()).isAir()) continue;
			int refY = outside != null ? site.getY() + 1 : heart.getY();
			if (Math.abs(y - refY) > 6) continue;
			if (!(type.create(level, EntitySpawnReason.COMMAND) instanceof Mob mob)) throw new IllegalStateException("night_spawn: not a mob: " + type);
			mob.snapTo(x + 0.5, y, z + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
			mob.setPersistenceRequired();
			if (mob instanceof Zombie zom) zom.setBaby(false);
			mob.addTag(SimMetricsCollector.TAG_SCRIPTED); // the death log tells scripted attackers from natural ones
			level.addFreshEntity(mob);
			placed++;
		}
		if (placed < count) throw new IllegalStateException("night_spawn: only " + placed + " of " + count + " could be placed");
		VillageHeart.LOG.info("[sim] night_spawn: {} x {} {}", placed, type, outside != null ? "just outside the wall" : "around the heart");
	}
}
