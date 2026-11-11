package dev.villageheart.sim;

import dev.villageheart.VillageHeart;
import dev.villageheart.core.Balance;
import dev.villageheart.core.sim.ActionSchedule;
import dev.villageheart.core.sim.Assertion;
import dev.villageheart.core.sim.AssertionResult;
import dev.villageheart.core.sim.DayMetrics;
import dev.villageheart.core.sim.Json;
import dev.villageheart.core.sim.MetricsJson;
import dev.villageheart.core.sim.MetricsJson.Pos;
import dev.villageheart.core.sim.MetricsJson.Report;
import dev.villageheart.core.sim.MetricsJson.VillagePositions;
import dev.villageheart.core.sim.MetricsTable;
import dev.villageheart.core.sim.Scenario;
import dev.villageheart.core.sim.ScenarioAction;
import dev.villageheart.core.sim.ScenarioEvaluator;
import dev.villageheart.core.sim.ScenarioParser;
import dev.villageheart.core.sim.ScenarioResult;
import dev.villageheart.core.sim.SimClock;
import dev.villageheart.core.sim.SimRequest;
import dev.villageheart.core.sim.SimStatus;
import dev.villageheart.core.sim.TickStats;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.world.VillageTicker;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/**
 * The long-simulation state machine: LOCATE (on server start) -> WAIT_BELL -> SETUP -> RUNNING -> DONE.
 * Every callback is guarded: a failure becomes status ERROR with the stack trace in the report, then the server halts.
 * The report on disk is the only channel back to scripts/simulate.sh.
 */
public final class SimulationRun implements Host {
	private enum State { PRE_START, WAIT_BELL, WAIT_RESUME, RUNNING, DONE }

	private static final int BELL_POLL_TICKS = 20;
	private static final int BELL_MAX_TICKS = 600;
	private static final int HEARTBEAT_TICKS = 6000;

	// request / scenario
	private SimRequest request;
	private Scenario scenario;
	private List<Assertion> assertions = List.of();
	private ActionSchedule schedule;
	private Path fallbackPath;
	private String startupError;

	// report state
	private SimStatus status = SimStatus.RUNNING;
	private String error;
	private boolean cleanShutdown;
	private long heartbeat;
	private Integer stoppedAfterDay;
	private long seed = SimWorld.SEED;
	private String difficulty = "normal";
	private BlockPos structure, bell, heart, site;
	private long g0;
	private final List<Integer> reloads = new ArrayList<>();
	private DayMetrics baseline;
	private final List<DayMetrics> days = new ArrayList<>();
	private List<AssertionResult> assertionResults = List.of();

	// live state
	private State state = State.PRE_START;
	private MinecraftServer server;
	private ServerLevel level;
	private SimPlayer player;
	private SimTickets tickets;
	private SimMetricsCollector collector;
	private SimClock clock;
	private int stateTicks;
	private long dayWallStart;
	private long dayStartTick;
	private boolean finishing;
	private long resumeLogAt = -1;

	private SimulationRun() {}

	// ---- accessors for actions ----
	@Override public ServerLevel level() { return level; }
	@Override public MinecraftServer server() { return server; }
	BlockPos bell() { return bell; }
	@Override public BlockPos heart() { return heart; }
	SimPlayer player() { return player; }
	@Override public BlockPos site() { return site; }
	@Override public void setSite(BlockPos site) { this.site = site; }

	/** The scenario's {@code wall_ring} on the ground, or null (no ring, or no site yet): the death log says which side of it a villager fell. */
	private dev.villageheart.core.sim.WallRing wallRing() {
		if (site == null) return null;
		for (ScenarioAction a : scenario.actions()) {
			if (a.type().equals("wall_ring") && a.fields().get("size") instanceof List<?> size) {
				return new dev.villageheart.core.sim.WallRing(site.getX(), site.getZ(), ((Number) size.get(0)).intValue(), ((Number) size.get(1)).intValue());
			}
		}
		return null;
	}

	void onClaimed(BlockPos heartPos) {
		this.heart = heartPos;
		collector.setCentre(Vec3.atCenterOf(heartPos));
		// the owner moves from the bell to the Heart; the siege gate follows so "inside the claim" stays the claim
		if (scenario != null && !scenario.villageSiege()) SiegeGate.moveCentre(level, heartPos);
	}

	// ---- startup (onInitialize): never throws ----

	private dev.villageheart.core.sim.MetricsTable baselineTable;
	private MetricsJson.Provenance provenance;

	static SimulationRun start(Path requestPath) {
		SimulationRun r = new SimulationRun();
		r.fallbackPath = requestPath.toAbsolutePath().resolveSibling("sim-error.json");
		try {
			r.request = SimRequest.parse(Files.readString(requestPath));
		} catch (Throwable t) {
			r.startupError = "bad request file " + requestPath + ": " + describe(t);
			r.writeFallback();
			return r;
		}
		try {
			String file = Path.of(r.request.scenarioFile()).getFileName().toString();
			r.scenario = ScenarioParser.parse(file.replaceAll("\\.json$", ""), Files.readString(Path.of(r.request.scenarioFile())));
			r.assertions = ScenarioEvaluator.parseAll(r.scenario, r.request.extraAssertions());
			// M13: a scenario may compare with a stored report (x@base); a missing or unreadable baseline is an error at start, never a silent pass
			String scenarioText = Files.readString(Path.of(r.request.scenarioFile()));
			String baselineName = null;
			if (r.scenario.baseline() != null) {
				Path bp = Path.of(r.request.scenarioFile()).toAbsolutePath().resolveSibling(r.scenario.baseline()).normalize();
				r.baselineTable = dev.villageheart.core.sim.BaselineLoader.load(Files.readString(bp));
				baselineName = r.scenario.baseline();
			}
			r.provenance = new MetricsJson.Provenance(baselineName, MetricsJson.scenarioSha256(scenarioText), System.getenv("SIM_GIT_COMMIT"));
			r.schedule = new ActionSchedule(r.scenario.actions());
			if (r.scenario.seed() != null) r.seed = r.scenario.seed();
			for (ScenarioAction a : r.scenario.actions()) {
				if (a.day() > r.request.days()) {
					throw new IllegalArgumentException("action \"" + a.type() + "\" is on day " + a.day() + " but only " + r.request.days() + " days run");
				}
				if (!SimActions.ALL.containsKey(a.type())) throw new IllegalArgumentException("no implementation for action " + a.type());
			}
			if (r.request.resume()) {
				Report old = MetricsJson.read(Files.readString(Path.of(r.request.out())));
				r.restoreFrom(old);
			}
			r.writeReport();
		} catch (Throwable t) {
			r.startupError = describe(t);
			r.status = SimStatus.ERROR;
			r.error = r.startupError;
			r.tryWriteReport();
		}
		return r;
	}

	private void restoreFrom(Report old) {
		if (old.status() != SimStatus.RUNNING || old.stoppedAfterDay() == null) {
			throw new IllegalStateException("cannot resume: previous report is " + old.status() + ", stopped_after_day=" + old.stoppedAfterDay());
		}
		structure = toPos(old.village().structure());
		bell = toPos(old.village().bell());
		heart = toPos(old.village().heart());
		site = toPos(old.village().site());
		g0 = old.g0();
		reloads.addAll(old.reloads());
		reloads.add(old.stoppedAfterDay());
		baseline = old.baseline();
		days.addAll(old.days());
		seed = old.seed();
		heartbeat = old.heartbeat();
		cleanShutdown = false;
		schedule.restore(old.actionsDone());
	}

	private static BlockPos toPos(Pos p) { return p == null ? null : new BlockPos(p.x(), p.y(), p.z()); }
	private static Pos fromPos(BlockPos p) { return p == null ? null : new Pos(p.getX(), p.getY(), p.getZ()); }

	// ---- server lifecycle ----

	void onServerStarted(MinecraftServer server) {
		this.server = server;
		if (startupError != null) {
			VillageHeart.LOG.error("[sim] startup failed: {}", startupError);
			server.halt(false);
			return;
		}
		guarded(() -> {
			level = server.overworld();
			difficulty = server.getWorldData().getDifficulty().getKey();
			seed = level.getSeed();
			collector = new SimMetricsCollector(level, null, Balance.CLAIM_RADIUS);
			collector.register();
			collector.setWallRing(this::wallRing);
			tickets = new SimTickets(level);
			if (request.resume()) {
				SimWorld.forceLoad(level, heart != null ? heart : structure);
				state = State.WAIT_RESUME;
				VillageHeart.LOG.info("[sim] resuming after day {} at {}", stoppedAfterDayOfResume(), heart.toShortString());
			} else {
				structure = SimWorld.locate(level).orElseThrow(() -> new IllegalStateException("no plains village within 100 chunks of spawn"));
				VillageHeart.LOG.info("[sim] village start at {}", structure.toShortString());
				SimWorld.forceLoad(level, structure);
				state = State.WAIT_BELL;
			}
			stateTicks = 0;
			writeReport();
		});
	}

	private int stoppedAfterDayOfResume() { return reloads.isEmpty() ? 0 : reloads.get(reloads.size() - 1); }

	void onServerStopped(MinecraftServer server) {
		if (request == null && startupError != null && scenario == null) return; // fallback already written
		cleanShutdown = true;
		tryWriteReport();
	}

	// ---- per-tick state machine ----

	void onTick(MinecraftServer server) {
		if (state == State.PRE_START || state == State.DONE || finishing) return;
		guarded(() -> {
			switch (state) {
				case WAIT_BELL -> waitBell();
				case WAIT_RESUME -> waitResume();
				case RUNNING -> running();
				default -> { }
			}
		});
	}

	private void waitBell() {
		stateTicks++;
		if (stateTicks % BELL_POLL_TICKS != 0) return;
		var found = SimWorld.findBell(level, structure);
		if (found.isEmpty()) {
			if (stateTicks >= BELL_MAX_TICKS) throw new IllegalStateException("no bell near village " + structure.toShortString());
			return;
		}
		if (!SimWorld.forcedChunksTicking(level, structure)) {
			if (stateTicks >= 4 * BELL_MAX_TICKS) throw new IllegalStateException("the forced chunks around " + structure.toShortString() + " never became entity-ticking");
			return;
		}
		bell = found.get();
		VillageHeart.LOG.info("[sim] bell at {}", bell.toShortString());
		setup();
	}

	private void setup() {
		// the simulated owner stands near the bell until the Heart exists, then at the Heart
		BlockPos stand = SimWorld.heartSpot(level, bell).orElse(bell.below());
		player = SimPlayer.join(server, level, stand);
		// harness only: there is no vanilla gamerule for VillageSiege. The gate skips it while the owner stands inside the claim.
		if (!scenario.villageSiege()) SiegeGate.suppressInsideClaim(level, bell, Balance.CLAIM_RADIUS);
		for (ScenarioAction a : schedule.setupActions()) runAction(a);
		schedule.markSetupDone();
		if (heart != null) {
			// from now on the static square follows the Heart (the same chunks on seed 1, where the Heart is in the start chunk)
			SimWorld.recentre(level, structure, heart);
			VillageHeart.LOG.info("[sim] forced area: +-{} chunks around the Heart's chunk {}; hired workers carry a +-{}-chunk ticking ticket",
					SimWorld.LOAD_RADIUS_CHUNKS, SimTickets.chunkOf(heart), dev.villageheart.core.sim.SimArea.FOLLOW_TICKING_CHUNKS);
		}
		level.setDayTime(0);
		// dinner of day index 0 must fire whatever the dinner system did during setup (M11 plan 3.2 #3)
		if (VillageClaims.current(server).isPresent()) {
			dev.villageheart.food.Dinner.resetClock(server);
			dev.villageheart.economy.Wages.resetClock(server); // sim day 1 has no wage run: the first is at the start of sim day 2
		}
		g0 = level.getGameTime();
		clock = new SimClock(g0, request.days());
		baseline = collector.collect(server, 0, g0, TickStats.EMPTY, 0, 0);
		resetTimers();
		if (heart != null) VillageHeart.LOG.info("[sim] heart at {}", heart.toShortString());
		if (request.days() == 0) {
			finish(SimStatus.PASS, false);
			return;
		}
		server.tickRateManager().requestGameToSprint((int) (request.days() * SimClock.DAY_TICKS + 200));
		state = State.RUNNING;
		writeReport();
	}

	private void waitResume() {
		stateTicks++;
		if (!level.isLoaded(heart)) {
			if (stateTicks > BELL_MAX_TICKS) throw new IllegalStateException("village chunks did not load on resume");
			return;
		}
		int done = days.size();
		var claim = VillageClaims.current(server);
		if (claim.isEmpty()) throw new IllegalStateException("resume: the claim is gone after reload");
		if (!claim.get().owner().equals(SimWorld.OWNER_UUID)) throw new IllegalStateException("resume: claim owner changed");
		if (level.getGameTime() < g0 + done * SimClock.DAY_TICKS) {
			throw new IllegalStateException("resume: game time " + level.getGameTime() + " is before day " + done + " end");
		}
		player = SimPlayer.join(server, level, heart.below());
		collector.setCentre(Vec3.atCenterOf(heart));
		resumeLogAt = level.getGameTime() + 400; // the entities of the force-loaded chunks are there a moment later
		StarterActions.resetStanding();
		for (ScenarioAction a : scenario.actions()) {
			if (a.type().equals("hire") && a.fields().get("replace") instanceof Boolean b && b) StarterActions.registerStanding(a);
		}
		clock = new SimClock(g0, request.days(), done);
		resetTimers();
		server.tickRateManager().requestGameToSprint((int) (clock.remainingTicks(level.getGameTime()) + 200));
		state = State.RUNNING;
		writeReport();
		VillageHeart.LOG.info("[sim] resumed at game time {}, {} days recorded, {} to go", level.getGameTime(), done, clock.remainingTicks(level.getGameTime()));
	}

	/** Day records and wall clock start fresh (the setup/boot ticks must not leak into the next day). */
	private void resetTimers() {
		VillageTicker.TIMER.snapshotAndReset();
		dayWallStart = System.nanoTime();
		dayStartTick = level.getGameTime();
	}

	private void running() {
		long now = level.getGameTime();
		if (now % 20 == 0) player.drain();
		if (now % dev.villageheart.core.sim.SimArea.FOLLOW_INTERVAL_TICKS == 0) StarterActions.followWorkers(this, tickets);
		if (now % 100 == 0) {
			StarterActions.maintain(this);
			collector.watchRing();
		}
		if (resumeLogAt >= 0 && now >= resumeLogAt) {
			resumeLogAt = -1;
			logNeeds("resume");
		}
		// Fixed order (docs/plans/M2.md 3.4): record ended days, run due actions, write the report, then maybe finish.
		boolean wrote = false;
		for (OptionalInt d = clock.dayEnded(now); d.isPresent(); d = clock.dayEnded(now)) {
			recordDay(d.getAsInt(), now);
			wrote = true;
		}
		for (ScenarioAction a : schedule.due(clock.elapsed(now))) runAction(a);
		boolean lastDay = clock.finished();
		boolean stopNow = request.stopAfterDay() != null && clock.completedDays() >= request.stopAfterDay();
		if (wrote || now - heartbeat >= HEARTBEAT_TICKS) {
			heartbeat = now;
			writeReport();
		}
		if (lastDay) {
			finish(null, false);
		} else if (stopNow) {
			stoppedAfterDay = request.stopAfterDay();
			logNeeds("halt");
			finish(SimStatus.RUNNING, true);
		}
	}

	/** Every census villager's diet and bed, and each worker's needs, one line each, sorted: the halt and the resume lists must match (M11 T-L3-2). */
	private void logNeeds(String when) {
		var village = VillageClaims.current(server);
		if (village.isEmpty()) return;
		List<String> lines = new ArrayList<>();
		for (var v : dev.villageheart.world.VillageCensus.of(server, village.get())) {
			String home = dev.villageheart.needs.Housing.home(v).map(g -> g.pos().toShortString()).orElse("-");
			String needs = dev.villageheart.worker.WorkerAttachments.get(v).map(w -> w.needs().toString()).orElse("-");
			lines.add(v.getUUID() + " hungry_days=" + dev.villageheart.food.DietAttachments.read(v, village.get().claimedAtGameTime()).hungryDays()
					+ " home=" + home + " needs=" + needs);
		}
		java.util.Collections.sort(lines);
		for (String l : lines) VillageHeart.LOG.info("[sim-needs] {} {}", when, l);
	}

	private void recordDay(int day, long now) {
		double wall = (System.nanoTime() - dayWallStart) / 1e9;
		long ticks = now - dayStartTick;
		TickStats ms = VillageTicker.TIMER.snapshotAndReset();
		DayMetrics m = collector.collect(server, day, now, ms, wall, ticks);
		days.add(m);
		dayWallStart = System.nanoTime();
		dayStartTick = now;
		VillageHeart.LOG.info("[sim] day {} done: population {}, village {} ms/tick, {} tps", day, m.population(),
				String.format("%.4f", ms.meanMs()), String.format("%.0f", m.serverTps()));
	}

	private void runAction(ScenarioAction a) {
		VillageHeart.LOG.info("[sim] action {} (day {} tick {})", a.type(), a.day(), a.tick());
		try {
			SimActions.ALL.get(a.type()).run(this, a);
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalStateException("action " + a.type() + " failed", e);
		}
	}

	// ---- finishing ----

	/** @param forced non-null to set the status directly; null = evaluate the assertions. */
	private void finish(SimStatus forced, boolean phase1) {
		finishing = true;
		try {
			server.tickRateManager().stopSprinting();
			if (forced != null) {
				status = forced;
			} else {
				ScenarioResult r = ScenarioEvaluator.evaluate(assertions, new MetricsTable(baseline, days), baselineTable);
				status = r.status();
				assertionResults = r.results();
			}
			state = State.DONE;
			writeReport();
			writeSummary();
		} finally {
			server.halt(false);
		}
	}

	private void guarded(Runnable r) {
		try {
			r.run();
		} catch (Throwable t) {
			VillageHeart.LOG.error("[sim] harness failure", t);
			fail(t);
		}
	}

	private void fail(Throwable t) {
		if (state == State.DONE && status == SimStatus.ERROR) return;
		status = SimStatus.ERROR;
		error = describe(t);
		state = State.DONE;
		finishing = true;
		tryWriteReport();
		if (server != null) server.halt(false);
	}

	private static String describe(Throwable t) {
		StringWriter sw = new StringWriter();
		t.printStackTrace(new PrintWriter(sw));
		String s = sw.toString();
		return s.length() > 6000 ? s.substring(0, 6000) + "..." : s;
	}

	// ---- report files ----

	private void tryWriteReport() {
		try {
			writeReport();
		} catch (Throwable t) {
			VillageHeart.LOG.error("[sim] could not write report", t);
		}
	}

	private Report buildReport() {
		String mc = FabricLoader.getInstance().getModContainer("minecraft")
				.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
		return new Report(scenario != null ? scenario.name() : request.scenario(), status, error, cleanShutdown, heartbeat,
				schedule != null ? schedule.actionsDone() : 0, stoppedAfterDay, seed, difficulty, request.days(), mc,
				new VillagePositions(fromPos(structure), fromPos(bell), fromPos(heart), fromPos(site)), g0, reloads, baseline, days, assertionResults, provenance);
	}

	private void writeReport() {
		try {
			Path out = Path.of(request.out());
			Files.createDirectories(out.getParent());
			Path tmp = out.resolveSibling(out.getFileName() + ".tmp");
			Files.writeString(tmp, MetricsJson.write(buildReport()), StandardCharsets.UTF_8);
			Files.move(tmp, out, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (java.io.IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
	}

	private void writeFallback() {
		try {
			Files.createDirectories(fallbackPath.getParent());
			Files.writeString(fallbackPath, Json.write(Map.of("status", "ERROR", "error", startupError, "clean_shutdown", false), true));
		} catch (Throwable t) {
			VillageHeart.LOG.error("[sim] could not write {}", fallbackPath, t);
		}
	}

	private void writeSummary() {
		try {
			Path out = Path.of(request.out());
			Path md = out.resolveSibling(out.getFileName().toString().replaceAll("\\.json$", ".summary.md"));
			StringBuilder sb = new StringBuilder();
			sb.append("# Simulation ").append(scenario.name()).append(": ").append(status).append("\n\n");
			sb.append("Seed ").append(seed).append(", ").append(difficulty).append(", ").append(days.size()).append(" of ")
					.append(request.days()).append(" days");
			if (!reloads.isEmpty()) sb.append(", reloaded after day ").append(reloads);
			sb.append("\n\n");
			for (AssertionResult a : assertionResults) {
				sb.append(a.passed() ? "- PASS " : "- FAIL ").append(a.text()).append(" (").append(a.detail()).append(")\n");
			}
			sb.append("\n| day | population | deaths | village ms mean | village ms p95 | server tps | heap MB |\n|---|---|---|---|---|---|---|\n");
			for (DayMetrics d : days) {
				sb.append(String.format("| %d | %d | %d | %.4f | %.4f | %.0f | %d |%n", d.day(), d.population(), d.deaths().size(),
						d.villageMs().meanMs(), d.villageMs().p95Ms(), d.serverTps(), d.heapUsedMb()));
			}
			sb.append(dev.villageheart.core.sim.DeathLog.markdown(days));
			Files.writeString(md, sb.toString(), StandardCharsets.UTF_8);
		} catch (Throwable t) {
			VillageHeart.LOG.error("[sim] could not write summary", t);
		}
	}
}
