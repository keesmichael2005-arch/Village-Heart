package dev.villageheart.worker.hunt;

import dev.villageheart.building.Buildings;
import dev.villageheart.core.Balance;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.building.BuildingStatus;
import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.hunt.MeatWeights;
import dev.villageheart.core.mine.ShaftChoice;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.worker.job.AwayTrips;
import dev.villageheart.worker.job.CampContainers;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Which Hunting Ground a Lodge's hunters use, what meat grows around it, and where a hunter is held while it is out (M17 plan 3.2). */
public final class HuntSites {
	private HuntSites() {}

	/** A Hunting Ground: its saved location, marker and level. */
	public record GroundSite(HeartLocation loc, BlockPos marker, ServerLevel level) {}

	/** The nearest VALID Hunting Ground linked to the Lodge at {@code lodge}, or empty. */
	public static Optional<GroundSite> groundFor(MinecraftServer server, HeartLocation lodge) {
		Optional<Buildings.BuildingView> lodgeView = CampContainers.building(server, lodge);
		if (lodgeView.isEmpty() || lodgeView.get().type() != BuildingType.HUNTING_LODGE) return Optional.empty();
		BlockPos lodgeMarker = lodgeView.get().marker();
		List<GroundSite> linked = new ArrayList<>();
		for (Buildings.BuildingView b : Buildings.all(server)) {
			if (b.type() != BuildingType.HUNTING_GROUND || b.status() != BuildingStatus.VALID || b.link() == null) continue;
			if (!b.link().equals(lodgeMarker) || b.level() != lodgeView.get().level()) continue;
			linked.add(new GroundSite(VillageClaims.locate(b.level(), b.marker()), b.marker(), b.level()));
		}
		List<HeartLocation> locs = linked.stream().map(GroundSite::loc).toList();
		Optional<HeartLocation> pick = ShaftChoice.pick(lodge, locs);
		if (pick.isEmpty()) return Optional.empty();
		return linked.stream().filter(s -> s.loc().equals(pick.get())).findFirst();
	}

	/** The ground at {@code loc} when it is VALID and still linked to the Lodge at {@code lodge} (a trip's ground is checked every step). */
	public static Optional<GroundSite> validLinkedGround(MinecraftServer server, HeartLocation loc, HeartLocation lodge) {
		for (Buildings.BuildingView b : Buildings.all(server)) {
			if (b.type() != BuildingType.HUNTING_GROUND || b.status() != BuildingStatus.VALID || b.link() == null) continue;
			HeartLocation bl = VillageClaims.locate(b.level(), b.marker());
			if (!bl.equals(loc)) continue;
			HeartLocation link = VillageClaims.locate(b.level(), b.link());
			return link.equals(lodge) ? Optional.of(new GroundSite(bl, b.marker(), b.level())) : Optional.empty();
		}
		return Optional.empty();
	}

	// ---- meat ---------------------------------------------------------------------------------------

	/**
	 * The meat weights around a ground: the vanilla creature spawn weights of the biomes at the marker and at the eight compass points
	 * {@link Balance#HUNTING_GROUND_RADIUS} blocks away. A sample in an unloaded chunk is skipped; the result is cached only when all
	 * nine were read.
	 */
	public static MeatWeights meatWeights(ServerLevel level, HeartLocation ground, BlockPos marker) {
		MeatWeights cached = HunterRuntime.weights(ground);
		if (cached != null) return cached;
		Map<String, Integer> spawns = new LinkedHashMap<>();
		int r = Balance.HUNTING_GROUND_RADIUS;
		int loaded = 0;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				BlockPos p = marker.offset(dx * r, 0, dz * r);
				if (!level.hasChunkAt(p)) continue;
				loaded++;
				for (var w : level.getBiome(p).value().getMobSettings().getMobs(MobCategory.CREATURE).unwrap()) {
					MobSpawnSettings.SpawnerData data = w.value();
					spawns.merge(BuiltInRegistries.ENTITY_TYPE.getKey(data.type()).toString(), w.weight(), Integer::sum);
				}
			}
		}
		MeatWeights w = MeatWeights.fromSpawns(spawns);
		if (loaded == 9) HunterRuntime.cacheWeights(ground, w);
		return w;
	}

	// ---- hold cell ----------------------------------------------------------------------------------

	/**
	 * Where a hunter is held while it is out: the cell it stands on. If another away worker already holds that cell (or it is a marker
	 * cell), the nearest free standable cell within 2 blocks that nobody holds, else the arrival cell after all.
	 */
	public static BlockPos holdCell(ServerLevel level, Villager v, BlockPos marker) {
		BlockPos arrival = v.blockPosition();
		List<BlockPos> held = new ArrayList<>();
		for (Villager o : level.getEntitiesOfClass(Villager.class, new AABB(arrival).inflate(6), x -> x != v && AwayTrips.isAway(x))) {
			AwayTrips.state(o).trip().ifPresent(t -> held.add(new BlockPos(t.hold().x(), t.hold().y(), t.hold().z())));
		}
		if (usable(level, v, arrival, marker, held)) return arrival;
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				for (int dy = -1; dy <= 1; dy++) {
					BlockPos c = arrival.offset(dx, dy, dz);
					if (!usable(level, v, c, marker, held)) continue;
					double d = c.distSqr(arrival);
					if (d < bestD) {
						bestD = d;
						best = c;
					}
				}
			}
		}
		return best == null ? arrival : best;
	}

	private static boolean usable(ServerLevel level, Villager v, BlockPos c, BlockPos marker, List<BlockPos> held) {
		if (c.equals(marker) || c.above().equals(marker) || held.contains(c)) return false;
		if (level.getChunkSource().getChunkNow(c.getX() >> 4, c.getZ() >> 4) == null) return false;
		BlockPos below = c.below();
		if (level.getBlockState(below).getCollisionShape(level, below).isEmpty()) return false;
		return level.noCollision(v.getDimensions(v.getPose()).makeBoundingBox(Vec3.atBottomCenterOf(c)));
	}
}
