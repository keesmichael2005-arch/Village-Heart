package dev.villageheart.worker.build;

import dev.villageheart.core.blueprint.BuilderPlanner;
import dev.villageheart.core.blueprint.FetchBatch;
import dev.villageheart.core.blueprint.StandPoints;
import dev.villageheart.core.job.WorkClock;
import dev.villageheart.core.Balance;
import dev.villageheart.core.room.Pos;
import dev.villageheart.worker.job.WalkTo;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/** One builder's transient state (never saved): its claim, the cached decision, the walk, the work clock, the stand points it gave up on. */
public final class BuilderState {
	/** The build this builder works on (its claim is in the build runtime); null when it has none. */
	@Nullable
	public UUID build;
	final WalkTo walk = new WalkTo();
	final WorkClock clock = new WorkClock(Balance.BUILD_PLACE_TICKS);
	/** The step index this builder is working, when it is not the saved cursor (another builder holds that one). */
	public int step = -1;
	BuilderPlanner.Action action = new BuilderPlanner.Action(BuilderPlanner.Type.IDLE, null);
	boolean needsPlan = true;
	long nextPlanAt;
	long idleBackoffUntil;
	/** Stand points that could not be reached: until when they are skipped. */
	final Map<Pos, Long> failed = new HashMap<>();
	@Nullable
	Pos standTarget;
	/** Walks that ended near the stand point but not in reach of the cell since the last action: after a few the stand point is given up. */
	int arrivals;
	@Nullable
	StandPoints.Standable standable;
	long unreachableUntil;
	/** The ring of stand points of the current build, recomputed every 100 ticks. */
	List<Pos> ring = List.of();
	long ringAt = Long.MIN_VALUE;
	/** A fetch in progress. */
	List<FetchBatch.Take> batch = List.of();
	@Nullable
	BlockPos fetchTarget;
	long fetchRetryAt;
	/** How long the cell of the current step has held an entity (IN_THE_WAY is reported after Balance.BUILD_IN_THE_WAY_TICKS). */
	long entitySince = -1;
	long blockedUntil;

	public void reset() {
		build = null;
		walk.clear();
		clock.reset();
		action = new BuilderPlanner.Action(BuilderPlanner.Type.IDLE, null);
		needsPlan = true;
		failed.clear();
		standTarget = null;
		arrivals = 0;
		unreachableUntil = 0;
		ring = List.of();
		ringAt = Long.MIN_VALUE;
		batch = List.of();
		fetchTarget = null;
		entitySince = -1;
		blockedUntil = 0;
		step = -1;
	}
}
