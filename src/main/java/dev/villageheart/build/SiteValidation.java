package dev.villageheart.build;

import dev.villageheart.block.MarkerBlock;
import dev.villageheart.building.Buildings;
import dev.villageheart.core.Rank;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.blueprint.Blueprint;
import dev.villageheart.core.blueprint.BuildJob;
import dev.villageheart.core.blueprint.Placement;
import dev.villageheart.core.blueprint.SiteRules;
import dev.villageheart.core.blueprint.SiteRules.Box;
import dev.villageheart.core.blueprint.SiteRules.Code;
import dev.villageheart.core.blueprint.SiteRules.Protected;
import dev.villageheart.core.blueprint.SiteRules.SiteFacts;
import dev.villageheart.core.blueprint.SiteRules.SiteProblem;
import dev.villageheart.core.blueprint.StandPoints;
import dev.villageheart.core.building.BuildingKind;
import dev.villageheart.core.room.Pos;
import dev.villageheart.core.room.RoomScan;
import dev.villageheart.world.VillageClaims;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Builds the {@link SiteFacts} of a placement from the world and the village and asks {@link SiteRules} (plan 3.5 and 3.7). Read-only: it
 * never changes the world (it may ask the building system for a re-check of a room it has no scan of).
 */
public final class SiteValidation {
	private SiteValidation() {}

	/** A registered room that has no scan yet (after a restart) is assumed to reach at most this far from its marker (a 4096-cell room). */
	private static final int UNKNOWN_ROOM_RADIUS = 64;

	/** Null when the site is fine, else the first problem. */
	public static SiteProblem check(ServerLevel level, VillageState village, Blueprint bp, Placement pl, Rank rank) {
		LevelSiteView view = new LevelSiteView(level);
		List<Protected> prot = new ArrayList<>();
		Pos min = pl.min(bp), max = pl.max(bp);
		SiteProblem unchecked = null;
		for (Buildings.BuildingView b : Buildings.all(level.getServer())) {
			if (b.level() != level) continue;
			Pos marker = new Pos(b.marker().getX(), b.marker().getY(), b.marker().getZ());
			Set<Long> cells = new HashSet<>();
			cells.add(marker.pack());
			BlockState ms = level.getBlockState(b.marker());
			if (ms.getBlock() instanceof MarkerBlock) {
				BlockPos support = b.marker().relative(ms.getValue(MarkerBlock.FACING).getOpposite());
				cells.add(Pos.pack(support.getX(), support.getY(), support.getZ()));
			}
			if (b.type().kind() == BuildingKind.ROOM) {
				Optional<RoomScan> scan = b.latestScan();
				if (scan.isPresent()) {
					cells.addAll(scan.get().shell());
					if (scan.get().enclosed()) cells.addAll(scan.get().interior());
				} else if (unchecked == null && near(marker, min, max)) {
					unchecked = SiteRules.unchecked(b.type().displayName(), marker);
					Buildings.request(level.getServer(), VillageClaims.locate(level, b.marker()));
				}
			}
			prot.add(new Protected(b.type().displayName(), marker, cells));
		}
		List<Box> others = new ArrayList<>();
		for (BuildJob j : village.builds()) {
			if (!j.ground().dimension().equals(level.dimension().identifier().toString())) continue;
			Blueprint obp = BuildJobs.blueprint(j);
			others.add(SiteRules.box(obp, j.placement()));
		}
		SiteFacts f = new SiteFacts(bp, pl, village.heart(), level.dimension().identifier().toString(), village.tier(), rank,
				level.getMinY(), level.getMaxY(), view, prot, others, village.builds().size(), standable(view, pl.ground().y()));
		Optional<SiteProblem> p = SiteRules.check(f);
		if (unchecked != null && (p.isEmpty() || p.get().code().ordinal() >= Code.OVERLAP.ordinal())) return unchecked;
		return p.orElse(null);
	}

	private static boolean near(Pos marker, Pos min, Pos max) {
		return marker.x() >= min.x() - UNKNOWN_ROOM_RADIUS && marker.x() <= max.x() + UNKNOWN_ROOM_RADIUS
				&& marker.z() >= min.z() - UNKNOWN_ROOM_RADIUS && marker.z() <= max.z() + UNKNOWN_ROOM_RADIUS;
	}

	/**
	 * Feet y of a standable spot in a column (solid below, two passable cells that hold no fluid), scanning from three above the ground
	 * block to two below it. A column in an unloaded chunk has none.
	 */
	public static StandPoints.Standable standable(LevelSiteView view, int groundY) {
		return (x, z) -> {
			for (int y = groundY + 3; y >= groundY - 2; y--) {
				BlockState below = view.state(new Pos(x, y - 1, z));
				BlockState feet = view.state(new Pos(x, y, z));
				BlockState head = view.state(new Pos(x, y + 1, z));
				if (below == null || feet == null || head == null) return OptionalInt.empty();
				if (solid(below) && free(feet) && free(head)) return OptionalInt.of(y);
			}
			return OptionalInt.empty();
		};
	}

	private static boolean solid(BlockState s) {
		return s.getFluidState().isEmpty() && !s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
	}

	private static boolean free(BlockState s) {
		return s.getFluidState().isEmpty() && s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
	}
}
