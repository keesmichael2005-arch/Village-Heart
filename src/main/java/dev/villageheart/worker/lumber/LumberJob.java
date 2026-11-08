package dev.villageheart.worker.lumber;

import dev.villageheart.VillageHeart;
import dev.villageheart.core.Balance;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.TreeTask;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.WorkerRecord;
import dev.villageheart.core.job.ToolRequirement;
import dev.villageheart.core.job.WorkSpeed;
import dev.villageheart.core.lumber.LumberjackPlanner;
import dev.villageheart.core.lumber.LumberjackPlanner.Action;
import dev.villageheart.core.lumber.LumberjackPlanner.Decision;
import dev.villageheart.core.lumber.LumberjackPlanner.FinderState;
import dev.villageheart.core.lumber.LumberjackPlanner.Snapshot;
import dev.villageheart.core.lumber.LumberjackPlanner.ToolState;
import dev.villageheart.core.lumber.ReplantRules;
import dev.villageheart.core.room.Pos;
import dev.villageheart.core.tree.Species;
import dev.villageheart.core.tree.TreeFinder;
import dev.villageheart.core.tree.TreeScan;
import dev.villageheart.core.tree.TreeScanner;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.core.worker.WorkerData;
import dev.villageheart.core.worker.WorkerStatus;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.worker.WorkerAttachments;
import dev.villageheart.worker.Workers;
import dev.villageheart.worker.job.CampContainers;
import dev.villageheart.worker.job.CampTools;
import dev.villageheart.worker.job.ItemPickup;
import dev.villageheart.worker.job.WalkTo;
import dev.villageheart.worker.job.WorkStats;
import dev.villageheart.worker.job.WorkerStatuses;
import dev.villageheart.worker.lumber.LumberRuntime.CampState;
import dev.villageheart.worker.lumber.LumberRuntime.ReplantPlan;
import dev.villageheart.worker.lumber.LumberRuntime.WorkerState;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * The lumberjack's work, one call per tick while WORK is active (M9 plan, sections 3.5-3.7). {@link LumberjackPlanner} decides; this
 * class carries it out against the world and keeps the saved tree tasks. All of it runs inside {@code VillageTicker.section()}.
 */
public final class LumberJob {
	private static final String JOB = "lumberjack";
	private static final int PLAN_INTERVAL = 20;
	private static final int ITEM_TIMEOUT = 400;
	private static final double COLLECT_TAKE = 2.2;
	private static final double CHEST_REACH = 2.0;

	/** {@code -Dvillageheart.lumber.trace=true} logs every decision (for debugging a stuck worker). */
	private static final boolean TRACE = Boolean.getBoolean("villageheart.lumber.trace");

	private static void trace(String fmt, Object... args) {
		if (TRACE) VillageHeart.LOG.info("[lumber] " + fmt, args);
	}

	private LumberJob() {}

	// ---- entry points ------------------------------------------------------------------------------

	public static void tick(ServerLevel level, Villager v, long now) {
		WorkerData data = v.getAttached(WorkerAttachments.WORKER);
		if (data == null || data.job() != JobType.LUMBERJACK) return;
		MinecraftServer server = level.getServer();
		LumberRuntime rt = LumberRuntime.get(server);
		WorkerState ws = rt.state(v.getUUID());
		ws.lastTick = now;
		if (Workers.tripRunning(v)) return; // a deposit trip owns the walk target
		guard(level, v, ws, now);
		if (ws.decision == null || ws.needsPlan || (now - ws.lastPlan >= PLAN_INTERVAL && ws.action != Action.CONTINUE_FELL)) plan(level, v, data, rt, ws, now);
		run(level, v, data, rt, ws, now);
		backgroundSearch(level, v, data, rt, ws, now);
	}

	/**
	 * The tree search goes on every tick whatever the worker is doing (it is a few thousand cell reads in all): a worker that always has a
	 * drop to collect or a spot to replant would otherwise never get past the nearest trees and miss the rest of the day's trees.
	 */
	private static void backgroundSearch(ServerLevel level, Villager v, WorkerData data, LumberRuntime rt, WorkerState ws, long now) {
		if (ws.action == Action.SEARCH) return; // run() just did it
		LumberRuntime.CampState camp = rt.camps.get(data.building());
		if (camp == null || camp.finder == null || camp.exhausted || level.getDayTime() % Balance.DAY_TICKS >= Balance.LUMBER_LAST_FELL) return;
		search(level, v, data, rt, now);
	}

	/** The worker was fired or died: its claims and running task are forgotten (the saved tree task stays for the next lumberjack). */
	public static void forget(MinecraftServer server, UUID villager) {
		LumberRuntime rt = LumberRuntime.get(server);
		rt.claims.values().removeIf(villager::equals);
		rt.workers.remove(villager);
	}

	/** The behaviour ended (night, panic, raid, fired): the crack overlay goes, the claim is released, the saved task stays. */
	public static void stop(ServerLevel level, Villager v) {
		LumberRuntime rt = LumberRuntime.get(level.getServer());
		WorkerState ws = rt.workers.get(v.getUUID());
		if (ws == null) return;
		clearCrack(level, v, ws);
		releaseTree(rt, v.getUUID(), ws);
		ws.walk.release(v);
		ws.needsPlan = true;
		ws.replant = null;
		ws.item = null;
		ws.action = Action.IDLE_AT_CAMP;
	}

	/** While a tree is being felled the planner is not on a timer, but the camp going invalid or a full inventory still interrupt it. */
	private static void guard(ServerLevel level, Villager v, WorkerState ws, long now) {
		if (ws.tree == null || ws.needsPlan || (now + v.getId()) % PLAN_INTERVAL != 0) return;
		if (!campValid(level, v) || dev.villageheart.worker.DepositTrip.wanted(level, v)) ws.needsPlan = true;
		WorkerData data = v.getAttached(WorkerAttachments.WORKER);
		if (data != null && data.needs().speed() <= 0) ws.needsPlan = true; // too hungry (or unpaid): stop felling at once
	}

	// ---- planning ----------------------------------------------------------------------------------

	private static boolean campValid(ServerLevel level, Villager v) {
		Optional<VillageState> village = VillageClaims.current(level.getServer());
		if (village.isEmpty()) return false;
		Optional<WorkerRecord> rec = village.get().worker(v.getUUID());
		return rec.isPresent() && Workers.status(level.getServer(), rec.get()) != WorkerStatus.WORKPLACE_INVALID;
	}

	private static HeartLocation loc(ServerLevel level, BlockPos p) { return VillageClaims.locate(level, p); }

	private static BlockPos pos(HeartLocation l) { return new BlockPos(l.x(), l.y(), l.z()); }

	private static void plan(ServerLevel level, Villager v, WorkerData data, LumberRuntime rt, WorkerState ws, long now) {
		MinecraftServer server = level.getServer();
		ws.lastPlan = now;
		ws.needsPlan = false;
		boolean valid = campValid(level, v);
		if (TRACE && !valid) trace("{} @{} camp counts as invalid", v.getId(), now);
		boolean deposit = valid && dev.villageheart.worker.DepositTrip.wanted(level, v);
		CampTools.Result lease = valid ? CampTools.lease(server, v, JobType.LUMBERJACK.tool().orElseThrow().kind()) : CampTools.Missing.UNKNOWN;
		ToolState tool = lease instanceof CampTools.Held ? ToolState.PRESENT : lease == CampTools.Missing.NONE ? ToolState.NONE : ToolState.UNKNOWN;
		ws.lease = lease instanceof CampTools.Held h ? h : null;
		ReplantPlan replant = valid ? findReplant(level, v, data, rt, now) : null;
		ws.replant = replant;
		ItemEntity item = valid ? findItem(level, v, data, rt, ws, now) : null;
		boolean task = valid && tool == ToolState.PRESENT && pickTask(level, v, data, rt, now) != null;
		FinderState finder = valid ? finderState(level, v, data, rt, now) : FinderState.EXHAUSTED;
		boolean fell = ws.tree != null;
		Snapshot snap = new Snapshot(valid, deposit, fell, tool, replant != null, item != null, task,
				level.getDayTime() % Balance.DAY_TICKS, finder, data.needs().speed() <= 0);
		Decision d = LumberjackPlanner.plan(snap);
		applyStatus(v.getUUID(), d.status());
		Action before = ws.action;
		if (TRACE && (ws.decision == null || ws.decision.action() != d.action())) {
			trace("{} @{} plan {} (tool {}, finder {}, replant {}, item {}, task {}, tree {})", v.getId(), now, d, tool, finder, replant != null,
					item != null, task, ws.tree != null);
		}
		ws.decision = d;
		ws.action = d.action();
		if (before == Action.CONTINUE_FELL && d.action() != Action.CONTINUE_FELL) clearCrack(level, v, ws);
		if (d.action() != Action.REPLANT) ws.replant = null;
		switch (d.action()) {
			case FELL_TASK -> {
				if (startTask(level, v, data, rt, ws, now)) ws.action = Action.CONTINUE_FELL;
				else ws.action = Action.IDLE_AT_CAMP;
			}
			case FELL_NEW -> {
				if (startNew(level, v, data, rt, ws, now)) ws.action = Action.CONTINUE_FELL;
				else {
					ws.action = Action.IDLE_AT_CAMP;
					ws.needsPlan = true;
				}
			}
			case COLLECT -> {
				if (!item.getUUID().equals(ws.item)) {
					ws.item = item.getUUID();
					ws.itemSince = now;
				}
			}
			default -> { }
		}
	}

	private static void applyStatus(UUID id, LumberjackPlanner.StatusChange change) {
		switch (change) {
			case CLEAR_ALL -> WorkerStatuses.set(id, null);
			case CLEAR_NEEDS_TOOL -> {
				if (WorkerStatuses.get(id) == WorkerStatus.NEEDS_TOOL) WorkerStatuses.set(id, null);
			}
			case NEEDS_TOOL -> WorkerStatuses.set(id, WorkerStatus.NEEDS_TOOL);
			case NO_WORK -> WorkerStatuses.set(id, WorkerStatus.NO_WORK);
			case KEEP -> { }
		}
	}

	// ---- running -----------------------------------------------------------------------------------

	private static void run(ServerLevel level, Villager v, WorkerData data, LumberRuntime rt, WorkerState ws, long now) {
		switch (ws.action) {
			case CONTINUE_FELL -> fell(level, v, data, rt, ws, now);
			case REPLANT -> replant(level, v, data, rt, ws, now);
			case COLLECT -> collect(level, v, data, rt, ws, now);
			case SEARCH -> {
				search(level, v, data, rt, now);
				ws.needsPlan = true;
			}
			case IDLE_AT_CAMP -> idle(level, v, data, ws, now);
			case YIELD, FELL_TASK, FELL_NEW -> { }
		}
	}

	private static void idle(ServerLevel level, Villager v, WorkerData data, WorkerState ws, long now) {
		BlockPos jb = pos(data.jobBlock());
		if (now < ws.idleBackoffUntil || !data.jobBlock().dimension().equals(level.dimension().identifier().toString())) return;
		if (WalkTo.distance(v, jb) <= Balance.JOB_BLOCK_REACH) {
			ws.walk.release(v);
			return;
		}
		if (!ws.walk.active() || !ws.walk.target().equals(jb)) ws.walk.begin(jb, Balance.JOB_BLOCK_REACH, now);
		if (ws.walk.tick(v, now) == WalkTo.State.UNREACHABLE) {
			ws.walk.clear();
			ws.idleBackoffUntil = now + 100;
		}
	}

	// ---- felling -----------------------------------------------------------------------------------

	private static void fell(ServerLevel level, Villager v, WorkerData data, LumberRuntime rt, WorkerState ws, long now) {
		if (ws.tree == null || ws.key == null) {
			ws.needsPlan = true;
			return;
		}
		BlockPos base0 = pos(ws.key);
		if (!ws.walk.active() || !ws.walk.target().equals(base0)) ws.walk.begin(base0, Balance.CHOP_REACH, Balance.CHOP_CLOSEST, now);
		WalkTo.State st = ws.walk.tick(v, now);
		if (st == WalkTo.State.UNREACHABLE) {
			giveUpTree(level, v, data, rt, ws, now);
			return;
		}
		if (st == WalkTo.State.WALKING) {
			clearCrack(level, v, ws);
			return;
		}
		if (!sameKey(ws)) {
			ws.clock.reset();
			ws.clockKey = ws.key;
		}
		BlockPos next = nextLog(level, ws);
		if (next == null) {
			if (ws.remaining.isEmpty()) finishTree(level, v, data, rt, ws, now);
			return;
		}
		v.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(next));
		if (ws.lease == null) {
			ws.needsPlan = true;
			return;
		}
		ToolRequirement req = JobType.LUMBERJACK.tool().orElseThrow();
		double mult = WorkSpeed.multiplier(Optional.of(ws.lease.lease().tier()), req, data.level(), data.needs());
		int units = ws.clock.tick(mult);
		showCrack(level, v, ws, next, units > 0 ? -1 : (int) (ws.clock.progress() * 10));
		if (units <= 0) return;
		// re-read the tool right before the cut: the player may have taken it out
		CampTools.Result r = CampTools.lease(level.getServer(), v, req.kind());
		if (!(r instanceof CampTools.Held held)) {
			ws.lease = null;
			ws.clock.reset();
			clearCrack(level, v, ws);
			ws.needsPlan = true;
			return;
		}
		ws.lease = held;
		cut(level, v, data, rt, ws, next, held, now);
	}

	private static boolean sameKey(WorkerState ws) { return ws.key != null && ws.key.equals(ws.clockKey); }

	/** The next log still standing in the fell order, or null (wait: its chunk is not loaded, or nothing is left). Skips logs already gone. */
	private static BlockPos nextLog(ServerLevel level, WorkerState ws) {
		while (!ws.remaining.isEmpty()) {
			Pos p = ws.remaining.peekFirst();
			BlockPos bp = new BlockPos(p.x(), p.y(), p.z());
			if (level.getChunkSource().getChunkNow(bp.getX() >> 4, bp.getZ() >> 4) == null) return null;
			if (level.getBlockState(bp).is(BlockTags.OVERWORLD_NATURAL_LOGS)) return bp;
			ws.remaining.pollFirst();
		}
		return null;
	}

	private static void cut(ServerLevel level, Villager v, WorkerData data, LumberRuntime rt, WorkerState ws, BlockPos bp, CampTools.Held held, long now) {
		BlockState state = level.getBlockState(bp);
		ws.remaining.pollFirst();
		clearCrack(level, v, ws);
		trace("{} cut {} at {} ({} left)", v.getId(), state.getBlock(), bp, ws.remaining.size());
		if (level.getGameRules().get(GameRules.BLOCK_DROPS)) {
			for (ItemStack drop : Block.getDrops(state, level, bp, null, v, held.lease().stack())) {
				ItemStack rest = v.getInventory().addItem(drop.copy());
				if (!rest.isEmpty()) Block.popResource(level, bp, rest);
			}
		}
		level.destroyBlock(bp, false, v);
		CampTools.use(level, held.lease(), 1);
		WorkStats.add(JOB, "logs", 1);
		v.getBrain().setMemory(MemoryModuleType.LAST_WORKED_AT_POI, now);
		ws.needsPlan = true;
		if (nextLog(level, ws) == null && ws.remaining.isEmpty()) finishTree(level, v, data, rt, ws, now);
	}

	private static void showCrack(ServerLevel level, Villager v, WorkerState ws, BlockPos bp, int stage) {
		if (stage < 0) return;
		stage = Math.min(9, stage);
		if (bp.equals(ws.crackPos) && stage == ws.crackStage) return;
		if (ws.crackPos != null && !ws.crackPos.equals(bp)) level.destroyBlockProgress(v.getId(), ws.crackPos, -1);
		level.destroyBlockProgress(v.getId(), bp, stage);
		ws.crackPos = bp;
		ws.crackStage = stage;
	}

	private static void clearCrack(ServerLevel level, Villager v, WorkerState ws) {
		if (ws.crackPos != null) level.destroyBlockProgress(v.getId(), ws.crackPos, -1);
		ws.crackPos = null;
		ws.crackStage = -1;
	}

	private static void releaseTree(LumberRuntime rt, UUID me, WorkerState ws) {
		if (ws.key != null && me.equals(rt.claims.get(ws.key))) rt.claims.remove(ws.key);
		ws.clearTree();
	}

	/** All logs are down: the task becomes REPLANT with every base cell, the claim is released. */
	private static void finishTree(ServerLevel level, Villager v, WorkerData data, LumberRuntime rt, WorkerState ws, long now) {
		TreeScan.Tree tree = ws.tree;
		HeartLocation key = ws.key;
		if (tree == null || key == null) return;
		trace("{} finished the tree at {}", v.getId(), key);
		clearCrack(level, v, ws);
		VillageClaims.data(level.getServer()).update(s -> {
			Optional<TreeTask> t = s.treeTask(key);
			if (t.isEmpty()) return s;
			List<HeartLocation> base = new ArrayList<>();
			for (Pos p : tree.base()) base.add(loc(level, new BlockPos(p.x(), p.y(), p.z())));
			return s.withoutTreeTask(key).withTreeTask(t.get().withStage(TreeTask.Stage.REPLANT, base).withSince(now));
		});
		WorkStats.add(JOB, "trees", 1);
		releaseTree(rt, v.getUUID(), ws);
		ws.clock.reset();
		ws.walk.clear();
		ws.needsPlan = true;
	}

	/** The tree cannot be reached: it is left alone for 6000 ticks, a task it had is dropped when nothing was cut yet. */
	private static void giveUpTree(ServerLevel level, Villager v, WorkerData data, LumberRuntime rt, WorkerState ws, long now) {
		HeartLocation key = ws.key;
		dev.villageheart.worker.job.LeafEscape.free(level, v); // boxed in by a canopy it cut into: leaves touching it are broken
		if (key != null) {
			rt.camp(data.building()).giveUp(key, now);
			rt.giveUps.add(key + ": " + ws.walk.reason());
			VillageHeart.LOG.info("Lumberjack {} cannot reach the tree at {} ({}): skipped for {} ticks", v.getUUID(), key, ws.walk.reason(),
					Balance.TREE_BLACKLIST_TICKS);
			boolean untouched = ws.tree != null && ws.remaining.size() == ws.tree.fellOrder().size();
			if (untouched) VillageClaims.data(level.getServer()).update(s -> s.withoutTreeTask(key));
		}
		clearCrack(level, v, ws);
		releaseTree(rt, v.getUUID(), ws);
		ws.walk.clear();
		ws.clock.reset();
		ws.needsPlan = true;
	}

	// ---- choosing a tree ---------------------------------------------------------------------------

	private static TreeTask pickTask(ServerLevel level, Villager v, WorkerData data, LumberRuntime rt, long now) {
		Optional<VillageState> village = VillageClaims.current(level.getServer());
		if (village.isEmpty()) return null;
		TreeTask best = null;
		double bestD = Double.MAX_VALUE;
		for (TreeTask t : village.get().treeTasksAt(data.building())) {
			if (t.stage() != TreeTask.Stage.FELLING) continue;
			if (rt.claimedByOther(t.key(), v.getUUID(), now)) continue;
			if (rt.taskRetryAt.getOrDefault(t.key(), 0L) > now) continue;
			double d = WalkTo.distance(v, pos(t.key()));
			if (d < bestD) {
				bestD = d;
				best = t;
			}
		}
		return best;
	}

	private static boolean startTask(ServerLevel level, Villager v, WorkerData data, LumberRuntime rt, WorkerState ws, long now) {
		TreeTask t = pickTask(level, v, data, rt, now);
		if (t == null) return false;
		LevelTreeView view = new LevelTreeView(level);
		List<Pos> base = new ArrayList<>();
		for (HeartLocation b : t.base()) base.add(new Pos(b.x(), b.y(), b.z()));
		TreeScan scan = TreeScanner.resume(view, base, t.boxMin(), t.boxMax());
		if (scan instanceof TreeScan.Tree tree) {
			setTree(rt, v, ws, tree, t.key(), now);
			return true;
		}
		TreeScan.NotATree n = (TreeScan.NotATree) scan;
		switch (n.why()) {
			case GONE -> VillageClaims.data(level.getServer()).update(s -> {
				Optional<TreeTask> cur = s.treeTask(t.key());
				return cur.isEmpty() ? s : s.withoutTreeTask(t.key()).withTreeTask(cur.get().withStage(TreeTask.Stage.REPLANT, t.base()).withSince(now));
			});
			case UNKNOWN -> rt.taskRetryAt.put(t.key(), now + 100);
			default -> {
				VillageHeart.LOG.info("Dropped the felling task at {}: {}", t.key(), n.why());
				VillageClaims.data(level.getServer()).update(s -> s.withoutTreeTask(t.key()));
			}
		}
		return false;
	}

	private static boolean startNew(ServerLevel level, Villager v, WorkerData data, LumberRuntime rt, WorkerState ws, long now) {
		CampState camp = rt.camp(data.building());
		String dim = level.dimension().identifier().toString();
		var skip = rt.skipFor(data.building(), dim, now, v.getUUID());
		LevelTreeView view = null;
		while (!camp.found.isEmpty()) {
			TreeScan.Tree cand = camp.found.pollFirst();
			Pos b0 = cand.base().get(0);
			if (skip.test(b0)) continue;
			if (view == null) view = new LevelTreeView(level);
			TreeScan fresh = TreeScanner.scan(view, b0);
			if (!(fresh instanceof TreeScan.Tree tree)) continue;
			HeartLocation key = new HeartLocation(dim, b0.x(), b0.y(), b0.z());
			Pos[] box = TreeScanner.box(tree.fellOrder());
			List<HeartLocation> base = new ArrayList<>();
			for (Pos p : tree.base()) base.add(new HeartLocation(dim, p.x(), p.y(), p.z()));
			TreeTask task = new TreeTask(data.building(), TreeTask.Stage.FELLING, base, ReplantRules.saplingFor(tree.species()), now, box[0], box[1]);
			boolean[] ok = {true};
			VillageClaims.data(level.getServer()).update(s -> {
				try {
					return s.withTreeTask(task);
				} catch (IllegalArgumentException e) {
					ok[0] = false;
					return s;
				}
			});
			if (!ok[0]) continue;
			setTree(rt, v, ws, tree, key, now);
			return true;
		}
		return false;
	}

	private static void setTree(LumberRuntime rt, Villager v, WorkerState ws, TreeScan.Tree tree, HeartLocation key, long now) {
		trace("{} takes the tree at {} ({} logs)", v.getUUID(), key, tree.fellOrder().size());
		ws.tree = tree;
		ws.key = key;
		ws.remaining = new ArrayDeque<>(tree.fellOrder());
		ws.walk.clear();
		rt.claims.put(key, v.getUUID());
		if (!key.equals(ws.clockKey)) {
			ws.clock.reset();
			ws.clockKey = key;
		}
	}

	// ---- searching ---------------------------------------------------------------------------------

	private static FinderState finderState(ServerLevel level, Villager v, WorkerData data, LumberRuntime rt, long now) {
		CampState camp = rt.camp(data.building());
		BlockPos centre = pos(data.jobBlock());
		if (camp.finder == null || (camp.exhausted && now - camp.sweepDoneAt >= Balance.TREE_INDEX_TTL)) {
			camp.finder = new TreeFinder(new Pos(centre.getX(), centre.getY(), centre.getZ()), Balance.LUMBER_RADIUS, Balance.LUMBER_DY);
			camp.finderCreatedAt = now;
			camp.exhausted = false;
			camp.exhaustedUnknown = false;
			camp.found.clear();
		}
		String dim = level.dimension().identifier().toString();
		var skip = rt.skipFor(data.building(), dim, now, v.getUUID());
		camp.found.removeIf(t -> skip.test(t.base().get(0)));
		if (!camp.found.isEmpty()) return FinderState.HAS_TREE;
		if (!camp.exhausted) return FinderState.PAUSED;
		return camp.exhaustedUnknown ? FinderState.EXHAUSTED_UNKNOWN : FinderState.EXHAUSTED;
	}

	private static void search(ServerLevel level, Villager v, WorkerData data, LumberRuntime rt, long now) {
		CampState camp = rt.camp(data.building());
		if (camp.finder == null || camp.exhausted) return;
		String dim = level.dimension().identifier().toString();
		var skip = rt.skipFor(data.building(), dim, now, v.getUUID());
		TreeFinder.Result r = camp.finder.step(new LevelTreeView(level), Balance.TREE_SEARCH_READS_PER_TICK, skip);
		if (r instanceof TreeFinder.Found f) {
			if (isTickable(level, f.tree().base().get(0))) camp.found.add(f.tree());
			else trace("tree at {} skipped: its chunk is not entity-ticking / block-ticking", f.tree().base().get(0));
		} else if (r == TreeFinder.Progress.EXHAUSTED) {
			camp.exhausted = true;
			camp.exhaustedUnknown = camp.finder.sawUnknown();
			camp.sweepDoneAt = now;
		}
	}

	/** A tree is a target only where the worker can walk and the leaves decay (entity-ticking chunk, block-ticking chunk). */
	private static boolean isTickable(ServerLevel level, Pos p) {
		BlockPos bp = new BlockPos(p.x(), p.y(), p.z());
		return level.isPositionEntityTicking(bp) && level.shouldTickBlocksAt(bp);
	}

	// ---- replanting --------------------------------------------------------------------------------

	private static int countInInventory(Villager v, String itemId) {
		Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(itemId));
		return v.getInventory().countItem(item);
	}

	private static boolean isSapling(ItemStack s) { return s.is(ItemTags.SAPLINGS); }

	private static boolean twoByTwoSapling(ItemStack s) {
		String id = BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
		for (Species sp : Species.values()) if (sp.needsTwoByTwo() && sp.saplingId().equals(id)) return true;
		return false;
	}

	private static Species speciesOfSapling(String id) {
		for (Species sp : Species.values()) if (sp.saplingId().equals(id)) return sp;
		return Species.OAK;
	}

	private static ReplantPlan findReplant(ServerLevel level, Villager v, WorkerData data, LumberRuntime rt, long now) {
		Optional<VillageState> village = VillageClaims.current(level.getServer());
		if (village.isEmpty()) return null;
		List<TreeTask> tasks = new ArrayList<>();
		for (TreeTask t : village.get().treeTasksAt(data.building())) if (t.stage() == TreeTask.Stage.REPLANT) tasks.add(t);
		if (tasks.isEmpty()) return null;
		Optional<List<CampContainers.Chest>> chests = null;
		tasks.sort(Comparator.comparingDouble(t -> WalkTo.distance(v, pos(t.base().get(0)))));
		for (TreeTask t : tasks) {
			Species sp = speciesOfSapling(t.sapling());
			boolean fallback = ReplantRules.fallbackAllowed(now - t.since(), sp);
			BlockPos cell = null;
			double bestD = Double.MAX_VALUE;
			for (HeartLocation c : t.base()) {
				double d = WalkTo.distance(v, pos(c));
				if (d < bestD) {
					bestD = d;
					cell = pos(c);
				}
			}
			if (cell == null) continue;
			String item = null;
			boolean fromChest = false;
			BlockPos chestPos = null;
			if (countInInventory(v, t.sapling()) > 0) item = t.sapling();
			else if (fallback) item = anyInventorySapling(v);
			if (item == null) {
				if (chests == null) chests = CampContainers.of(level.getServer(), data.building());
				if (chests.isPresent()) {
					for (CampContainers.Chest ch : chests.get()) {
						if (containerCount(ch.container(), t.sapling()) > 0) {
							item = t.sapling();
							fromChest = true;
							chestPos = ch.pos();
							break;
						}
					}
					if (item == null && fallback) {
						for (CampContainers.Chest ch : chests.get()) {
							String any = anyContainerSapling(ch.container());
							if (any != null) {
								item = any;
								fromChest = true;
								chestPos = ch.pos();
								break;
							}
						}
					}
				}
			}
			if (item != null) return new ReplantPlan(t.key(), cell, item, fromChest, chestPos);
		}
		return null;
	}

	private static String anyInventorySapling(Villager v) {
		SimpleContainer inv = v.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (!s.isEmpty() && isSapling(s) && !twoByTwoSapling(s)) return BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
		}
		return null;
	}

	private static String anyContainerSapling(net.minecraft.world.Container c) {
		for (int i = 0; i < c.getContainerSize(); i++) {
			ItemStack s = c.getItem(i);
			if (!s.isEmpty() && isSapling(s) && !twoByTwoSapling(s)) return BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
		}
		return null;
	}

	private static int containerCount(net.minecraft.world.Container c, String itemId) {
		Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(itemId));
		int n = 0;
		for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).is(item)) n += c.getItem(i).getCount();
		return n;
	}

	private static void replant(ServerLevel level, Villager v, WorkerData data, LumberRuntime rt, WorkerState ws, long now) {
		ReplantPlan plan = ws.replant;
		if (plan == null) {
			ws.needsPlan = true;
			return;
		}
		Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(plan.itemId()));
		if (TRACE && now % 20 == 0) trace("{} replant {} at {} from {} (chest {}), carrying {}", v.getId(), plan.itemId(), plan.cell(), plan.fromChest() ? "chest" : "inventory",
				plan.chest(), v.getInventory().countItem(item));
		if (v.getInventory().countItem(item) <= 0) {
			// FETCH: walk to the camp chest and take what the open cells of this task need
			BlockPos chest = plan.chest();
			if (chest == null) {
				ws.needsPlan = true;
				return;
			}
			if (!ws.walk.active() || !ws.walk.target().equals(chest)) ws.walk.begin(chest, CHEST_REACH, now);
			WalkTo.State st = ws.walk.tick(v, now);
			if (st == WalkTo.State.UNREACHABLE) {
				ws.walk.clear();
				ws.needsPlan = true;
				ws.idleBackoffUntil = now + 100;
				return;
			}
			if (st == WalkTo.State.WALKING) return;
			Optional<List<CampContainers.Chest>> chests = CampContainers.of(level.getServer(), data.building());
			int want = openCells(level, plan);
			if (chests.isPresent()) {
				for (CampContainers.Chest ch : chests.get()) {
					for (int i = 0; i < ch.container().getContainerSize() && want > 0; i++) {
						ItemStack s = ch.container().getItem(i);
						if (!s.is(item)) continue;
						int n = Math.min(want, s.getCount());
						ItemStack moving = s.copyWithCount(n);
						ItemStack rest = v.getInventory().addItem(moving);
						int moved = n - rest.getCount();
						s.shrink(moved);
						want -= moved;
						ch.container().setChanged();
					}
				}
			}
			ws.walk.clear();
			ws.needsPlan = true;
			return;
		}
		BlockPos cell = plan.cell();
		if (!ws.walk.active() || !ws.walk.target().equals(cell)) ws.walk.begin(cell, Balance.CHOP_REACH, Balance.CHOP_CLOSEST, now);
		WalkTo.State st = ws.walk.tick(v, now);
		if (st == WalkTo.State.UNREACHABLE) {
			ws.walk.clear();
			ws.needsPlan = true;
			ws.idleBackoffUntil = now + 100;
			return;
		}
		if (st == WalkTo.State.WALKING) return;
		v.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(cell));
		plant(level, v, rt, ws, plan, item, now);
	}

	private static int openCells(ServerLevel level, ReplantPlan plan) {
		return VillageClaims.current(level.getServer()).flatMap(s -> s.treeTask(plan.taskKey())).map(t -> t.base().size()).orElse(1);
	}

	private static void plant(ServerLevel level, Villager v, LumberRuntime rt, WorkerState ws, ReplantPlan plan, Item item, long now) {
		BlockPos cell = plan.cell();
		ws.walk.clear();
		ws.needsPlan = true;
		if (level.getChunkSource().getChunkNow(cell.getX() >> 4, cell.getZ() >> 4) == null) return;
		Block block = Block.byItem(item);
		BlockState sapling = block.defaultBlockState();
		BlockState here = level.getBlockState(cell);
		boolean planted = false;
		if (here.canBeReplaced() && sapling.canSurvive(level, cell)) {
			planted = level.setBlock(cell, sapling, Block.UPDATE_ALL);
			if (planted) {
				v.getInventory().removeItemType(item, 1);
				WorkStats.add(JOB, "saplings_planted", 1);
				v.getBrain().setMemory(MemoryModuleType.LAST_WORKED_AT_POI, now);
			}
		}
		trace("{} plants {} at {}: {}", v.getId(), item, cell, planted);
		if (!planted) VillageHeart.LOG.info("Lumberjack {} dropped the replant cell {}: {}", v.getUUID(), cell, here);
		HeartLocation cellLoc = loc(level, cell);
		VillageClaims.data(level.getServer()).update(s -> {
			Optional<TreeTask> t = s.treeTask(plan.taskKey());
			if (t.isEmpty()) return s;
			List<HeartLocation> left = new ArrayList<>(t.get().base());
			left.remove(cellLoc);
			VillageState without = s.withoutTreeTask(plan.taskKey());
			return left.isEmpty() ? without : without.withTreeTask(t.get().withStage(TreeTask.Stage.REPLANT, left));
		});
	}

	// ---- collecting --------------------------------------------------------------------------------

	private static boolean wanted(ItemStack s) {
		return s.is(ItemTags.LOGS) || s.is(ItemTags.SAPLINGS) || s.is(Items.STICK) || s.is(Items.APPLE);
	}

	/**
	 * The nearest wanted drop in the work area (within {@link Balance#LUMBER_RADIUS} of the Chopping Block, whatever its distance from the
	 * worker: a lumberjack idling at the camp must still see the saplings that fall from a canopy 25 blocks away).
	 */
	private static ItemEntity findItem(ServerLevel level, Villager v, WorkerData data, LumberRuntime rt, WorkerState ws, long now) {
		BlockPos camp = pos(data.jobBlock());
		double r = Balance.LUMBER_RADIUS;
		net.minecraft.world.phys.AABB area = new net.minecraft.world.phys.AABB(camp).inflate(r, Balance.LUMBER_DY + 8, r);
		ItemEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, area, x -> true)) {
			if (e.isRemoved() || e.hasPickUpDelay() || !wanted(e.getItem()) || !ItemPickup.fits(v, e)) continue;
			double dx = e.getX() - (camp.getX() + 0.5), dz = e.getZ() - (camp.getZ() + 0.5);
			if (dx * dx + dz * dz > r * r) continue;
			Long until = rt.itemBlacklist.get(e.getUUID());
			if (until != null && until > now) continue;
			// out of reach by construction: lying on a canopy (it falls when the leaves decay) or buried in a block
			BlockPos ip = e.blockPosition();
			BlockState at = level.getBlockState(ip);
			if (level.getBlockState(ip.below()).is(BlockTags.LEAVES) || !(at.isAir() || at.canBeReplaced())) continue;
			double d = e.distanceTo(v);
			if (d < bestD) {
				bestD = d;
				best = e;
			}
		}
		return best;
	}

	private static void collect(ServerLevel level, Villager v, WorkerData data, LumberRuntime rt, WorkerState ws, long now) {
		if (ws.item == null || !(level.getEntity(ws.item) instanceof ItemEntity e) || e.isRemoved()) {
			ws.item = null;
			ws.needsPlan = true;
			return;
		}
		if (now - ws.itemSince > ITEM_TIMEOUT) {
			rt.itemBlacklist.put(e.getUUID(), now + 6000);
			ws.item = null;
			ws.needsPlan = true;
			return;
		}
		double dx = e.getX() - v.getX(), dz = e.getZ() - v.getZ();
		if (TRACE && now % 20 == 0) trace("{} collect {} at {} (dx {} dz {} dy {} delay {})", v.getId(), e.getItem(), e.position(), dx, dz, e.getY() - v.getY(), e.hasPickUpDelay());
		if (Math.sqrt(dx * dx + dz * dz) <= COLLECT_TAKE && Math.abs(e.getY() - v.getY()) < 2.5) {
			int got = ItemPickup.collect(v, e);
			trace("{} took {} of {}", v.getId(), got, e.getItem());
			ws.item = null;
			ws.walk.clear();
			ws.needsPlan = true;
			return;
		}
		BlockPos ip = e.blockPosition();
		if (!ws.walk.active()) ws.walk.begin(ip, 0.8, now);
		else ws.walk.retarget(ip);
		WalkTo.State st = ws.walk.tick(v, now);
		if (st == WalkTo.State.UNREACHABLE) {
			rt.itemBlacklist.put(e.getUUID(), now + 6000);
			ws.item = null;
			ws.walk.clear();
			ws.needsPlan = true;
		}
	}

	// ---- queries for the keep rule and tests -------------------------------------------------------

	/** Saplings of {@code item} the camp's open replant spots still need (the keep rule adds the reserve). */
	public static int saplingsNeeded(MinecraftServer server, WorkerData data, Item item) {
		Optional<VillageState> village = VillageClaims.current(server);
		if (village.isEmpty()) return 0;
		String id = BuiltInRegistries.ITEM.getKey(item).toString();
		int n = 0;
		for (TreeTask t : village.get().treeTasksAt(data.building())) {
			if (t.stage() == TreeTask.Stage.REPLANT && t.sapling().equals(id)) n += t.base().size();
		}
		return n;
	}
}
