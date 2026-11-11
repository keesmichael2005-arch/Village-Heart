package dev.villageheart.sim;

import dev.villageheart.build.BuildJobs;
import dev.villageheart.build.SiteValidation;
import dev.villageheart.building.BuildingSystem;
import dev.villageheart.building.Buildings;
import dev.villageheart.core.building.BuildingStatus;
import dev.villageheart.core.Balance;
import dev.villageheart.core.Rank;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.blueprint.Blueprint;
import dev.villageheart.core.blueprint.BlueprintParams;
import dev.villageheart.core.blueprint.Blueprints;
import dev.villageheart.core.blueprint.BuildJob;
import dev.villageheart.core.blueprint.Placement;
import dev.villageheart.core.blueprint.SiteRules.SiteProblem;
import dev.villageheart.core.blueprint.WallLines;
import dev.villageheart.core.room.Pos;
import dev.villageheart.core.sim.ScenarioAction;
import dev.villageheart.world.VillageClaims;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * {@code build} (M25 step 18): one {@code build line} plus {@code build advance all}, with coordinates relative to the site.
 * A scenario file cannot know the site's world position or the build's id, so the action does both.
 */
public final class BuildActions {
	private BuildActions() {}

	public static void build(Host ctx, ScenarioAction a) {
		ServerLevel level = ctx.level();
		BlockPos site = ctx.site();
		if (site == null) throw new IllegalStateException("build needs a site action first");
		settle(ctx.server());
		String id = (String) a.fields().get("blueprint");
		Blueprint unit = Blueprints.byId(id).orElseThrow(() -> new IllegalStateException("unknown blueprint " + id));
		int[] from = pair(a, "from"), to = pair(a, "to");
		int y = site.getY();
		BlockPos start = new BlockPos(site.getX() + from[0], y, site.getZ() + from[1]);
		BlockPos end = new BlockPos(site.getX() + to[0], y, site.getZ() + to[1]);
		WallLines.Result line = WallLines.of(new Pos(start.getX(), y, start.getZ()), new Pos(end.getX(), y, end.getZ()),
				Blueprints.minLength(unit), Balance.MAX_WALL_LENGTH);
		if (!line.ok()) {
			throw new IllegalStateException(id + " " + start.toShortString() + ".." + end.toShortString() + ": " + line.problem().english());
		}
		Blueprint bp = Blueprints.resolve(id, new BlueprintParams(line.line().length()))
				.orElseThrow(() -> new IllegalStateException("cannot resolve " + id + " of " + line.line().length()));
		VillageState village = VillageClaims.current(ctx.server()).orElseThrow(() -> new IllegalStateException("no claim"));
		SiteProblem problem = SiteValidation.check(level, village, bp, new Placement(line.line().ground(), line.line().rotation()), Rank.OWNER);
		if (problem != null) {
			throw new IllegalStateException(id + " of " + line.line().length() + " at " + start.toShortString() + ": " + problem.english());
		}
		BuildJob job = BuildJobs.start(level, bp, start, line.line().rotation(), new BlueprintParams(line.line().length()), village.owner());
		BuildJobs.Advanced advanced = BuildJobs.advance(ctx.server(), job.id(), Integer.MAX_VALUE);
		if (!advanced.finished()) {
			throw new IllegalStateException(id + " of " + line.line().length() + " stopped after " + advanced.steps() + ": " + advanced.stoppedBecause());
		}
	}

	/** Register and validate markers placed earlier in the same setup, so Storage can reserve the wall's cobblestone. */
	private static void settle(MinecraftServer server) {
		for (int i = 0; i < 200; i++) {
			VillageState village = VillageClaims.current(server).orElse(null);
			if (village == null) return;
			BuildingSystem.INSTANCE.tick(server, village);
			boolean waiting = false;
			for (Buildings.BuildingView view : Buildings.all(server)) {
				if (view.status() == BuildingStatus.UNCHECKED) waiting = true;
			}
			if (!waiting && i > 0) return;
		}
	}

	private static int[] pair(ScenarioAction a, String key) {
		List<?> list = (List<?>) a.fields().get(key);
		return new int[] { ((Number) list.get(0)).intValue(), ((Number) list.get(1)).intValue() };
	}
}
