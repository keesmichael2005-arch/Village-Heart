package dev.villageheart.worker.build;

import dev.villageheart.VillageHeart;
import dev.villageheart.build.BuildExecutor;
import dev.villageheart.build.BuildJobs;
import dev.villageheart.build.BuildRuntime;
import dev.villageheart.build.BuildSystem;
import dev.villageheart.build.LevelSiteView;
import dev.villageheart.build.PaletteStates;
import dev.villageheart.build.SiteSupport;
import dev.villageheart.build.SiteValidation;
import dev.villageheart.core.Balance;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.WorkerRecord;
import dev.villageheart.core.blueprint.Blueprint;
import dev.villageheart.core.blueprint.BuildJob;
import dev.villageheart.core.blueprint.BuildPlan;
import dev.villageheart.core.blueprint.SiteRules;
import dev.villageheart.core.blueprint.BuildPlan.Step;
import dev.villageheart.core.blueprint.BuildStatus;
import dev.villageheart.core.blueprint.BuildTexts;
import dev.villageheart.core.blueprint.BuilderPlanner;
import dev.villageheart.core.blueprint.BuilderPlanner.Action;
import dev.villageheart.core.blueprint.BuilderPlanner.CellState;
import dev.villageheart.core.blueprint.BuilderPlanner.Facts;
import dev.villageheart.core.blueprint.BuilderPlanner.Type;
import dev.villageheart.core.blueprint.FetchBatch;
import dev.villageheart.core.blueprint.Material;
import dev.villageheart.core.blueprint.Placement;
import dev.villageheart.core.blueprint.StandPoints;
import dev.villageheart.core.job.WorkSpeed;
import dev.villageheart.core.job.WorkUnit;
import dev.villageheart.core.room.Pos;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.core.worker.WorkerData;
import dev.villageheart.core.worker.WorkerStatus;
import dev.villageheart.stock.StackKey;
import dev.villageheart.stock.Stockpiles;
import dev.villageheart.worker.WorkXp;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.worker.DepositTrip;
import dev.villageheart.worker.WorkerAttachments;
import dev.villageheart.worker.Workers;
import dev.villageheart.worker.job.WalkTo;
import dev.villageheart.worker.job.WorkStats;
import dev.villageheart.worker.job.WorkerStatuses;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The builder's work, one call per tick while WORK is active (M20 plan, 3.2 and 3.6). {@link BuilderPlanner} decides; this class carries it
 * out against the world and keeps the saved build records. The builder claims the oldest build nobody works on, fetches the materials the
 * build reserved (a batch of about a stack), walks to a stand point on the ring around the site (never inside it), and clears or places one
 * cell per {@link Balance#BUILD_TICKS_PER_BLOCK} work-clock units. All of it runs inside {@code VillageTicker.section()}.
 */
public final class BuilderJob {
	private static final String JOB = "builder";
	private static final int PLAN_INTERVAL = 20;
	private static final int SKIP_LIMIT = 400;
	/** The pathfinder accepts a node within 1 block of the target as "reached" (accuracy 1) and the villager may stop at 1.2 from its centre. */
	private static final double ARRIVE = 1.4;
	/** The builder acts on a cell this far (feet to centre) at most: {@link Balance#BUILD_REACH} plus the 1 block a walk may end short. */
	private static final double ACT_REACH = Balance.BUILD_REACH + ARRIVE;
	private static final double CHEST_REACH = 2.0;
	private static final int RING_TTL = 100;

	private static final boolean TRACE = Boolean.getBoolean("villageheart.build.trace");

	private static void trace(String fmt, Object... args) {
		if (TRACE) VillageHeart.LOG.info("[build] " + fmt, args);
	}

	/** Test hook: called at every clear and place with where the builder stood (T-L2-7: always outside the box). */
	public interface ActionListener {
		void acted(Villager builder, BuildPlan.Kind kind, Pos cell, boolean outsideBox);
	}

	public static volatile ActionListener listener;

	private BuilderJob() {}

	// ---- entry points ------------------------------------------------------------------------------

	public static void tick(ServerLevel level, Villager v, long now) {
		WorkerData data = v.getAttached(WorkerAttachments.WORKER);
		if (data == null || data.job() != JobType.BUILDER) return;
		MinecraftServer server = level.getServer();
		BuildRuntime rt = BuildRuntime.get(server);
		BuilderState ws = rt.builder(v.getUUID());
		if (Workers.tripRunning(v)) return; // a deposit trip owns the walking
		Optional<VillageState> village = VillageClaims.current(server);
		if (village.isEmpty() || !campValid(server, village.get(), v)) return;
		BuildJob job = currentJob(rt, village.get(), ws, v);
		if (job == null) {
			idle(level, v, data, ws, now);
			return;
		}
		Blueprint bp = BuildJobs.blueprint(job);
		List<Step> steps = BuildJobs.steps(server, job);
		boolean fetching = ws.action.type() == Type.FETCH && ws.fetchTarget != null; // a fetch is carried out, not re-planned on the way
		if (ws.needsPlan || (now >= ws.nextPlanAt && !fetching)) {
			job = plan(level, v, data, rt, ws, job, bp, steps, now);
			if (job == null) return; // finished or in a state that needs the next tick
		}
		run(level, v, data, rt, ws, job, bp, steps, now);
	}

	/** The behaviour ended (night, panic, raid, fired): the claim is released, the walk forgotten; the build and what the builder carries stay. */
	public static void stop(ServerLevel level, Villager v) {
		BuildRuntime rt = BuildRuntime.get(level.getServer());
		BuilderState ws = rt.builders.get(v.getUUID());
		if (ws == null) return;
		if (ws.build != null) {
			rt.claims.remove(ws.build, v.getUUID());
			rt.pause.remove(ws.build);
		}
		ws.reset();
		WorkerStatuses.set(v.getUUID(), null);
	}

	private static boolean campValid(MinecraftServer server, VillageState village, Villager v) {
		Optional<WorkerRecord> rec = village.worker(v.getUUID());
		return rec.isPresent() && Workers.status(server, rec.get()) != WorkerStatus.WORKPLACE_INVALID;
	}

	// ---- claiming ------------------------------------------------------------------------------------

	/** The build this builder works on: its claim, or the oldest build nobody works on whose carrier is nobody, it, or gone. */
	@Nullable
	private static BuildJob currentJob(BuildRuntime rt, VillageState village, BuilderState ws, Villager v) {
		UUID me = v.getUUID();
		if (ws.build != null) {
			Optional<BuildJob> j = village.build(ws.build);
			if (j.isPresent() && me.equals(rt.claims.get(ws.build)) && (j.get().carrier() == null || me.equals(j.get().carrier()))) return j.get();
			if (ws.build != null && me.equals(rt.claims.get(ws.build))) rt.claims.remove(ws.build);
			ws.reset();
		}
		for (BuildJob j : village.builds()) {
			if (rt.claims.containsKey(j.id())) continue;
			UUID carrier = j.carrier();
			if (carrier != null && !carrier.equals(me) && village.worker(carrier).isPresent()) continue; // waits for the builder that holds its materials
			rt.claims.put(j.id(), me);
			ws.reset();
			ws.build = j.id();
			trace("{} claims build {} ({})", v.getId(), j.id(), j.blueprintId());
			return j;
		}
		return null;
	}

	private static void idle(ServerLevel level, Villager v, WorkerData data, BuilderState ws, long now) {
		WorkerStatuses.set(v.getUUID(), WorkerStatus.NO_WORK);
		BlockPos jb = new BlockPos(data.jobBlock().x(), data.jobBlock().y(), data.jobBlock().z());
		if (now < ws.idleBackoffUntil || !data.jobBlock().dimension().equals(level.dimension().identifier().toString())) return;
		if (dev.villageheart.worker.job.WorkstationStand.at(v, jb)) {
			ws.walk.clear();
			return;
		}
		BlockPos spot = dev.villageheart.worker.job.WorkstationStand.spot(level, jb, v.position());
		if (!ws.walk.active() || !ws.walk.target().equals(spot)) ws.walk.begin(spot, spot.equals(jb) ? Balance.JOB_BLOCK_REACH : dev.villageheart.worker.job.WorkstationStand.REACH, now);
		if (ws.walk.tick(v, now) == WalkTo.State.UNREACHABLE) {
			ws.walk.clear();
			ws.idleBackoffUntil = now + 100;
		}
	}

	// ---- planning ------------------------------------------------------------------------------------

	/** Returns the build (its record may have changed) or null when the tick is over. */
	@Nullable
	private static BuildJob plan(ServerLevel level, Villager v, WorkerData data, BuildRuntime rt, BuilderState ws, BuildJob job, Blueprint bp,
			List<Step> steps, long now) {
		MinecraftServer server = level.getServer();
		ws.needsPlan = false;
		ws.nextPlanAt = now + PLAN_INTERVAL;
		job = reconcile(v, job, server);
		// free steps (cells that already hold what is wanted) cost nothing
		java.util.Set<Long> touched = BuildJobs.touched(server, job);
		int cursor = job.cursor(), start = cursor;
		Step step = null;
		CellState cs = CellState.ALREADY_RIGHT;
		while (cursor < steps.size() && cursor - start < SKIP_LIMIT) {
			step = steps.get(cursor);
			cs = BuildExecutor.cellState(level, step, touched);
			if (cs != CellState.ALREADY_RIGHT) break;
			cursor++;
		}
		if (cursor != start) job = save(server, job, cursor, job.carried(), job.carrier());
		if (cursor >= steps.size()) {
			finish(server, v, rt, ws, job);
			return null;
		}
		if (cs == CellState.ALREADY_RIGHT) { // the skip limit: go on next tick
			ws.needsPlan = true;
			return null;
		}
		Material m = step.kind() == BuildPlan.Kind.CLEAR ? null : step.entry().material();
		boolean clearing = step.kind() == BuildPlan.Kind.CLEAR || cs == CellState.CLEARABLE;
		boolean inHand = m == null || carriedAvailable(v, job, m) > 0;
		boolean reserved = !inHand && BuildSystem.reservedByMaterial(server, job).getOrDefault(m.id(), 0) > 0;
		boolean deposit = DepositTrip.wanted(level, v);
		boolean inside = insideBox(v, bp, job);
		boolean reach = distanceTo(v, step.pos()) <= ACT_REACH;
		boolean unreachable = now < ws.unreachableUntil;
		Action a = BuilderPlanner.next(new Facts(true, false, step.kind(), cs, inHand, reserved, deposit, inside, reach, unreachable));
		trace("{} plans {} at cursor {} ({} {}): {}", v.getId(), a.type(), cursor, step.kind(), step.pos(), cs);
		ws.action = a;
		if (a.type() != Type.WAIT) {
			rt.pause.remove(job.id());
			WorkerStatuses.set(v.getUUID(), null);
		}
		switch (a.type()) {
			case WAIT -> wait(level, v, rt, ws, job, step, a.reason(), now);
			case YIELD -> ws.nextPlanAt = now + 10;
			case FETCH -> beginFetch(level, v, rt, ws, job, bp, now);
			case WALK -> { }
			case CLEAR, PLACE -> { }
			default -> { }
		}
		return job;
	}

	/** The carried map never claims more than the builder holds (a dropped or stolen item, a stack a player took). */
	private static BuildJob reconcile(Villager v, BuildJob job, MinecraftServer server) {
		if (!v.getUUID().equals(job.carrier()) || job.carried().isEmpty()) return job;
		Map<String, Integer> fixed = new HashMap<>(job.carried());
		boolean changed = false;
		for (Map.Entry<String, Integer> e : job.carried().entrySet()) {
			Item item = BuiltInRegistries.ITEM.getOptional(Identifier.parse(e.getKey())).orElse(null);
			int have = item == null ? 0 : v.getInventory().countItem(item);
			if (have < e.getValue()) {
				fixed.put(e.getKey(), have);
				changed = true;
			}
		}
		return changed ? save(server, job, job.cursor(), fixed, job.carrier()) : job;
	}

	private static BuildJob save(MinecraftServer server, BuildJob job, int cursor, Map<String, Integer> carried, @Nullable UUID carrier) {
		BuildJob next = job.withCursor(cursor).withCarried(carried).withCarrier(carrier);
		VillageClaims.data(server).update(v -> v.build(job.id()).isPresent() ? v.withBuild(next) : v);
		return next;
	}

	private static void finish(MinecraftServer server, Villager v, BuildRuntime rt, BuilderState ws, BuildJob job) {
		BuildJobs.finish(server, job);
		WorkStats.add(JOB, "builds", 1);
		WorkerStatuses.set(v.getUUID(), null);
		ws.reset();
	}

	private static void wait(ServerLevel level, Villager v, BuildRuntime rt, BuilderState ws, BuildJob job, Step step, BuilderPlanner.WaitReason reason,
			long now) {
		BuildStatus status;
		List<String> args = List.of();
		String cell = step.pos().toString();
		switch (reason) {
			case NEEDS_MATERIALS -> status = BuildStatus.NEEDS_MATERIALS;
			case BLOCKED -> {
				status = BuildStatus.BLOCKED;
				BlockState st = BuildExecutor.read(level, step.pos());
				String id = st == null ? "block" : BuildTexts.blockName(BuiltInRegistries.BLOCK.getKey(st.getBlock()).toString());
				args = List.of(id, cell);
				// a natural cell that a block of a player rests on: name that block (it is the one in the way)
				if (st != null && LevelSiteView.classify(st) == SiteRules.SiteCell.CLEARABLE) {
					var dep = SiteSupport.dependent(level, step.pos(), BuildJobs.touched(level.getServer(), job));
					if (dep.isPresent()) args = List.of(BuildTexts.blockName(dep.get().id()), dep.get().at().toString());
				}
				ws.nextPlanAt = now + Balance.BUILD_BLOCKED_RETRY_TICKS;
			}
			case IN_THE_WAY -> {
				if (ws.entitySince < 0) ws.entitySince = now;
				if (now - ws.entitySince < Balance.BUILD_IN_THE_WAY_TICKS) {
					rt.pause.remove(job.id());
					return;
				}
				status = BuildStatus.IN_THE_WAY;
				args = List.of(cell);
			}
			case UNREACHABLE -> {
				status = BuildStatus.UNREACHABLE;
				args = List.of(cell);
			}
			default -> status = BuildStatus.NOT_LOADED;
		}
		if (reason != BuilderPlanner.WaitReason.IN_THE_WAY) ws.entitySince = -1;
		rt.pause.put(job.id(), new BuildRuntime.Pause(status, args, now));
		trace("{} waits: {} {}", v.getId(), status, args);
		WorkerStatuses.set(v.getUUID(), switch (status) {
			case NEEDS_MATERIALS -> WorkerStatus.NEEDS_MATERIALS;
			case BLOCKED, IN_THE_WAY, UNREACHABLE -> WorkerStatus.BLOCKED;
			default -> null;
		});
	}

	// ---- running --------------------------------------------------------------------------------------

	private static void run(ServerLevel level, Villager v, WorkerData data, BuildRuntime rt, BuilderState ws, BuildJob job, Blueprint bp,
			List<Step> steps, long now) {
		if (job.cursor() >= steps.size()) {
			ws.needsPlan = true;
			return;
		}
		Step step = steps.get(job.cursor());
		switch (ws.action.type()) {
			case FETCH -> fetch(level, v, rt, ws, job, bp, now);
			case WALK -> walk(level, v, ws, job, bp, step, now);
			case CLEAR, PLACE -> act(level, v, data, rt, ws, job, bp, steps, step, now);
			default -> { }
		}
	}

	// ---- fetching -------------------------------------------------------------------------------------

	private static void beginFetch(ServerLevel level, Villager v, BuildRuntime rt, BuilderState ws, BuildJob job, Blueprint bp, long now) {
		MinecraftServer server = level.getServer();
		if (now < ws.fetchRetryAt) {
			ws.action = new Action(Type.YIELD, null);
			return;
		}
		List<String> mats = BuildSystem.stepMaterials(server, level, bp, job);
		List<FetchBatch.Take> batch = FetchBatch.plan(mats, BuildSystem.carriedByMaterial(bp, job), BuildSystem.reservedByMaterial(server, job),
				id -> stackSize(BuildSystem.material(id)));
		if (batch.isEmpty()) {
			ws.action = new Action(Type.WAIT, BuilderPlanner.WaitReason.NEEDS_MATERIALS);
			rt.pause.put(job.id(), new BuildRuntime.Pause(BuildStatus.NEEDS_MATERIALS, List.of(), now));
			WorkerStatuses.set(v.getUUID(), WorkerStatus.NEEDS_MATERIALS);
			return;
		}
		Material first = BuildSystem.material(batch.get(0).material());
		Optional<BlockPos> target = Stockpiles.nearestHolding(level, v.blockPosition(), k -> BuildJobs.matches(k, first));
		if (target.isEmpty()) {
			ws.fetchRetryAt = now + 100;
			ws.action = new Action(Type.WAIT, BuilderPlanner.WaitReason.NEEDS_MATERIALS);
			rt.pause.put(job.id(), new BuildRuntime.Pause(BuildStatus.NEEDS_MATERIALS, List.of(), now));
			WorkerStatuses.set(v.getUUID(), WorkerStatus.NEEDS_MATERIALS);
			return;
		}
		ws.batch = batch;
		ws.fetchTarget = target.get();
		ws.walk.begin(target.get(), CHEST_REACH, now);
		trace("{} fetches {} from {}", v.getId(), batch, target.get());
	}

	private static int stackSize(Material m) {
		Item item = PaletteStates.displayItem(m);
		return item == null ? 64 : item.getDefaultMaxStackSize();
	}

	private static void fetch(ServerLevel level, Villager v, BuildRuntime rt, BuilderState ws, BuildJob job, Blueprint bp, long now) {
		if (ws.fetchTarget == null) {
			ws.needsPlan = true;
			return;
		}
		WalkTo.State st = ws.walk.tick(v, now);
		if (st == WalkTo.State.WALKING) return;
		if (st == WalkTo.State.UNREACHABLE) {
			ws.walk.clear();
			ws.fetchTarget = null;
			ws.fetchRetryAt = now + 200;
			ws.needsPlan = true;
			return;
		}
		MinecraftServer server = level.getServer();
		var reservations = Stockpiles.reservations(server);
		Map<String, Integer> took = new HashMap<>(job.carried());
		boolean any = false;
		SimpleContainer inv = v.getInventory();
		outer:
		for (FetchBatch.Take take : ws.batch) {
			Material m = BuildSystem.material(take.material());
			int left = take.n();
			for (StackKey key : new ArrayList<>(rt.reservedKeys(job.id()))) {
				if (left <= 0) break;
				if (!BuildJobs.matches(key, m)) continue;
				while (left > 0) {
					int ask = Math.min(left, reservations.reservedBy(job.id(), key));
					if (ask <= 0) break;
					ItemStack got = Stockpiles.withdraw(level, v.blockPosition(), key, ask, job.id());
					if (got.isEmpty()) break;
					int n = got.getCount();
					ItemStack rest = inv.addItem(got.copy());
					if (!rest.isEmpty()) {
						n -= rest.getCount();
						Stockpiles.deposit(level, v.blockPosition(), rest);
						left = 0;
					}
					if (n > 0) {
						took.merge(BuiltInRegistries.ITEM.getKey(key.item()).toString(), n, Integer::sum);
						any = true;
					}
					left -= n;
					if (!rest.isEmpty()) break outer;
				}
			}
		}
		ws.walk.clear();
		ws.fetchTarget = null;
		ws.batch = List.of();
		if (any) {
			save(server, job, job.cursor(), took, v.getUUID());
			WorkStats.add(JOB, "fetches", 1);
		} else {
			ws.fetchRetryAt = now + 40;
		}
		ws.needsPlan = true;
	}

	// ---- walking --------------------------------------------------------------------------------------

	/** The column one block further out than a ring column (null: it is not standable). Walking aims there: the walk ends within a block of it, so never inside the box. */
	@Nullable
	private static Pos outward(BuilderState ws, Pos min, Pos max, Pos p) {
		int dx = p.x() < min.x() ? -1 : p.x() > max.x() ? 1 : 0;
		int dz = p.z() < min.z() ? -1 : p.z() > max.z() ? 1 : 0;
		if (ws.standable == null) return null;
		var y = ws.standable.feetY(p.x() + dx, p.z() + dz);
		return y.isPresent() ? new Pos(p.x() + dx, y.getAsInt(), p.z() + dz) : null;
	}

	private static void walk(ServerLevel level, Villager v, BuilderState ws, BuildJob job, Blueprint bp, Step step, long now) {
		Placement pl = job.placement();
		Pos min = pl.min(bp), max = pl.max(bp);
		if (ws.ringAt == Long.MIN_VALUE || now - ws.ringAt >= RING_TTL) {
			ws.standable = SiteValidation.standableFor(level, job);
			ws.ring = StandPoints.ring(min, max, ws.standable);
			ws.ringAt = now;
		}
		ws.failed.values().removeIf(t -> t <= now);
		Pos vp = new Pos(v.getBlockX(), v.getBlockY(), v.getBlockZ());
		Pos cell = step.pos();
		Pos stand = ws.standTarget;
		if (stand == null || ws.failed.containsKey(stand) || !ws.ring.contains(stand) || StandPoints.distance(stand, cell) > Balance.BUILD_REACH) {
			List<Pos> c = new ArrayList<>(StandPoints.within(cell, ws.ring, Balance.BUILD_REACH, vp));
			c.removeIf(ws.failed::containsKey);
			if (c.isEmpty()) {
				ws.unreachableUntil = now + Balance.BUILD_UNREACHABLE_RETRY_TICKS;
				ws.failed.clear();
				ws.walk.clear();
				ws.needsPlan = true;
				trace("{} finds no stand point near {}", v.getId(), cell);
				return;
			}
			stand = c.get(0);
			ws.standTarget = stand;
			ws.arrivals = 0;
			ws.walk.clear();
		}
		Pos goal = stand;
		Pos out = outward(ws, min, max, stand);
		if (out != null && StandPoints.distance(out, cell) <= Balance.BUILD_REACH) goal = out;
		Pos aim = goal;
		Pos way = StandPoints.waypoint(min, max, ws.ring, vp, stand).orElse(null);
		if (way != null) {
			Pos wo = outward(ws, min, max, way);
			aim = wo != null ? wo : way;
		}
		BlockPos target = BuildExecutor.at(aim);
		if (!ws.walk.active() || !ws.walk.target().equals(target)) ws.walk.begin(target, ARRIVE, now);
		WalkTo.State st = ws.walk.tick(v, now);
		if (st == WalkTo.State.UNREACHABLE) {
			trace("{} cannot reach {}: {}", v.getId(), target, ws.walk.reason());
			ws.failed.put(stand, now + Balance.BUILD_UNREACHABLE_RETRY_TICKS);
			ws.standTarget = null;
			ws.walk.clear();
			ws.needsPlan = true;
		} else if (st == WalkTo.State.ARRIVED) {
			ws.walk.clear();
			if (way == null && ++ws.arrivals > 3) { // arrived, yet not in reach of the cell: this stand point does not work
				trace("{} gives up the stand point {}", v.getId(), stand);
				ws.failed.put(stand, now + Balance.BUILD_UNREACHABLE_RETRY_TICKS);
				ws.standTarget = null;
				ws.arrivals = 0;
			}
			ws.needsPlan = true;
		}
	}

	// ---- clearing and placing -------------------------------------------------------------------------

	private static void act(ServerLevel level, Villager v, WorkerData data, BuildRuntime rt, BuilderState ws, BuildJob job, Blueprint bp,
			List<Step> steps, Step step, long now) {
		MinecraftServer server = level.getServer();
		boolean outside = !insideBox(v, bp, job);
		if (!outside || distanceTo(v, step.pos()) > ACT_REACH) {
			ws.needsPlan = true;
			return;
		}
		ws.arrivals = 0;
		v.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(BuildExecutor.at(step.pos())));
		int units = ws.clock.tick(WorkSpeed.untooled(data.level(), data.needs()));
		if (units <= 0) return;
		// look again right before acting: a player may have changed the cell
		CellState cs = BuildExecutor.cellState(level, step, BuildJobs.touched(server, job));
		if (cs != CellState.FREE && cs != CellState.CLEARABLE) {
			ws.needsPlan = true;
			return;
		}
		v.swing(InteractionHand.MAIN_HAND);
		v.getBrain().setMemory(MemoryModuleType.LAST_WORKED_AT_POI, now);
		ActionListener l = listener;
		Material m = step.kind() == BuildPlan.Kind.CLEAR ? null : step.entry().material();
		Map<String, Integer> carried = new HashMap<>(job.carried());
		Item item = null;
		if (cs == CellState.CLEARABLE && m != null) {
			// a natural block under a PLACE or CHECK step goes and the new block comes in ONE action: in the gap between two actions water
			// could flow into the cell (a farmland dried to dirt next to the water)
			item = takeOne(v, carried, m);
			if (item == null) {
				ws.needsPlan = true;
				return;
			}
		}
		if (cs == CellState.CLEARABLE) {
			for (ItemStack drop : BuildExecutor.clear(level, step.pos(), v)) {
				ItemStack rest = v.getInventory().addItem(drop);
				if (!rest.isEmpty()) BuildJobs.store(level, new Pos(v.getBlockX(), v.getBlockY(), v.getBlockZ()), rest);
			}
			WorkStats.add(JOB, "blocks_cleared", 1);
			if (l != null) l.acted(v, BuildPlan.Kind.CLEAR, step.pos(), outside);
			if (step.kind() == BuildPlan.Kind.CLEAR) {
				save(server, job, job.cursor() + 1, job.carried(), job.carrier());
				ws.needsPlan = true;
				return;
			}
		} else {
			item = takeOne(v, carried, m);
		}
		if (item == null) {
			ws.needsPlan = true;
			return;
		}
		BuildExecutor.place(level, bp, job.placement(), step, item, v);
		if (m.returns() != null) {
			BuiltInRegistries.ITEM.getOptional(Identifier.parse(m.returns())).ifPresent(r -> {
				ItemStack rest = v.getInventory().addItem(new ItemStack(r));
				if (!rest.isEmpty()) BuildJobs.store(level, new Pos(v.getBlockX(), v.getBlockY(), v.getBlockZ()), rest);
			});
		}
		WorkStats.add(JOB, "blocks_placed", 1);
		WorkXp.award(v, WorkUnit.BLOCK_PLACED, 1);
		if (l != null) l.acted(v, BuildPlan.Kind.PLACE, step.pos(), outside);
		BuildJob next = save(server, job, job.cursor() + 1, carried, job.carrier());
		BuildJobs.afterPlace(server, level, bp, step);
		trace("{} placed {} at {} (cursor {})", v.getId(), step.entry().blockId(), step.pos(), next.cursor());
		ws.needsPlan = true;
	}

	/** Takes one item that serves {@code m} out of the builder's inventory, from the carried amounts; null when there is none. */
	@Nullable
	private static Item takeOne(Villager v, Map<String, Integer> carried, Material m) {
		SimpleContainer inv = v.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.isEmpty() || !s.getComponentsPatch().isEmpty() || !PaletteStates.satisfies(m, s.getItem())) continue;
			String id = BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
			int c = carried.getOrDefault(id, 0);
			if (c <= 0) continue;
			Item item = s.getItem();
			s.shrink(1);
			inv.setChanged();
			if (c == 1) carried.remove(id);
			else carried.put(id, c - 1);
			return item;
		}
		return null;
	}

	// ---- helpers --------------------------------------------------------------------------------------

	/** How many items serving {@code m} the builder holds for this build (the carried amounts, capped by what it really has). */
	private static int carriedAvailable(Villager v, BuildJob job, Material m) {
		int n = 0;
		for (Map.Entry<String, Integer> e : job.carried().entrySet()) {
			Item item = BuiltInRegistries.ITEM.getOptional(Identifier.parse(e.getKey())).orElse(null);
			if (item == null || !PaletteStates.satisfies(m, item)) continue;
			n += Math.min(e.getValue(), v.getInventory().countItem(item));
		}
		return n;
	}

	/** True when the builder's feet are in a column of the site box (the ring around it is outside), not clearly above or below it. */
	private static boolean insideBox(Villager v, Blueprint bp, BuildJob job) {
		Placement pl = job.placement();
		Pos min = pl.min(bp), max = pl.max(bp);
		BlockPos b = v.blockPosition();
		return b.getX() >= min.x() && b.getX() <= max.x() && b.getZ() >= min.z() && b.getZ() <= max.z() && v.getY() < max.y() + 1.0
				&& v.getY() > min.y() - 2.0;
	}

	/** 3D distance from the builder's feet to the centre of a cell. */
	private static double distanceTo(Villager v, Pos cell) {
		double dx = v.getX() - (cell.x() + 0.5), dy = v.getY() - (cell.y() + 0.5), dz = v.getZ() - (cell.z() + 0.5);
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}
}
