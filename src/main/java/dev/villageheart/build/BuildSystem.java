package dev.villageheart.build;

import dev.villageheart.core.Balance;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.blueprint.Blueprint;
import dev.villageheart.core.blueprint.Blueprints;
import dev.villageheart.core.blueprint.BuildJob;
import dev.villageheart.core.blueprint.BuildPlan;
import dev.villageheart.core.blueprint.BuildPlan.Step;
import dev.villageheart.core.blueprint.BuildStatus;
import dev.villageheart.core.blueprint.BuildTexts;
import dev.villageheart.core.blueprint.BuilderPlanner.CellState;
import dev.villageheart.core.blueprint.Material;
import dev.villageheart.core.blueprint.MaterialPlanner;
import dev.villageheart.core.blueprint.MaterialPlanner.BuildNeed;
import dev.villageheart.core.heart.TextLine;
import dev.villageheart.core.heart.VillagerNames;
import dev.villageheart.core.stock.Reservations;
import dev.villageheart.core.stock.StockSnapshot;
import dev.villageheart.stock.StackKey;
import dev.villageheart.stock.Stockpiles;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.world.VillageSystem;
import dev.villageheart.world.VillageTicker;
import dev.villageheart.worker.Workers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

/**
 * Keeps the builds honest, every {@link Balance#BUILD_REFRESH_TICKS} ticks (plan 3.2 #9): releases the claims of builders that are not working,
 * works out what each build still needs and reserves it in the stockpile (oldest build first), and notes what is missing. It also describes
 * every build for the Heart screen and the Builder's Table. Reservations are transient (M6): a restart simply works them out again.
 */
public final class BuildSystem implements VillageSystem {
	public static final BuildSystem INSTANCE = new BuildSystem();

	/** Material id to material, over all blueprints (the same id is the same material everywhere). */
	private static final Map<String, Material> MATERIALS = new LinkedHashMap<>();

	static {
		for (Blueprint bp : Blueprints.all()) for (var l : bp.materials().lines()) MATERIALS.putIfAbsent(l.material().id(), l.material());
	}

	/** A builder's pause counts for this long after it was set: longer than the longest wait between two of its decisions (a blocked cell: 100). */
	private static final int PAUSE_FRESH_TICKS = 150;

	private BuildSystem() {}

	public static Material material(String id) { return MATERIALS.get(id); }

	@Override
	public void tick(MinecraftServer server, VillageState village) {
		if (village.builds().isEmpty() || server.getTickCount() % Balance.BUILD_REFRESH_TICKS != 0) return;
		try (var t = VillageTicker.section()) {
			refresh(server, village);
		}
	}

	/** Test hook (L4): the build is worked by {@code builder} as far as the screens can tell, and the claim is never released. */
	public static void pinClaimForTest(MinecraftServer server, UUID build, UUID builder) {
		BuildRuntime rt = BuildRuntime.get(server);
		rt.claims.put(build, builder);
		rt.pinned.add(build);
	}

	/** Works the reservations out now (a new build, a test). */
	public static void refresh(MinecraftServer server) {
		VillageClaims.current(server).ifPresent(v -> refresh(server, v));
	}

	static void refresh(MinecraftServer server, VillageState village) {
		BuildRuntime rt = BuildRuntime.get(server);
		releaseClaims(server, village, rt);
		rt.plans.keySet().removeIf(id -> village.build(id).isEmpty());
		rt.touched.keySet().removeIf(id -> village.build(id).isEmpty());
		rt.missing.keySet().removeIf(id -> village.build(id).isEmpty());
		rt.reservedKeys.keySet().removeIf(id -> village.build(id).isEmpty());
		rt.pause.keySet().removeIf(id -> village.build(id).isEmpty());
		List<BuildJob> builds = village.builds();
		if (builds.isEmpty()) return;
		Reservations<StackKey> res = Stockpiles.reservations(server);
		StockSnapshot<StackKey> snap = Stockpiles.snapshot(server);
		// what the builds may reserve: the stock minus what other consumers reserved
		Map<StackKey, Integer> stock = new LinkedHashMap<>();
		for (StockSnapshot.Total<StackKey> t : snap.totals()) {
			StackKey k = t.key();
			if (!k.patch().isEmpty()) continue;
			int others = res.reserved(k);
			for (BuildJob b : builds) others -= res.reservedBy(b.id(), k);
			long avail = Math.max(0, t.count() - Math.max(0, others));
			if (avail > 0) stock.put(k, (int) Math.min(Integer.MAX_VALUE, avail));
		}
		List<BuildNeed<StackKey, UUID>> needs = new ArrayList<>();
		for (BuildJob b : builds) {
			Blueprint bp = BuildJobs.blueprint(b);
			ServerLevel level = Workers.levelOf(server, b.ground());
			Map<String, Integer> need = new LinkedHashMap<>();
			for (String m : stepMaterials(server, level, bp, b)) need.merge(m, 1, Integer::sum);
			Map<StackKey, Integer> held = new HashMap<>();
			for (StackKey k : stock.keySet()) {
				int h = res.reservedBy(b.id(), k);
				if (h > 0) held.put(k, h);
			}
			needs.add(new BuildNeed<>(b.id(), need, carriedByMaterial(bp, b), held));
		}
		MaterialPlanner.Result<StackKey, UUID> r = MaterialPlanner.plan(needs, stock, (k, mid) -> BuildJobs.matches(k, MATERIALS.get(mid)),
				StackKey::id);
		for (BuildJob b : builds) {
			res.release(b.id());
			Map<StackKey, Integer> mine = r.reserve().get(b.id());
			for (Map.Entry<StackKey, Integer> e : mine.entrySet()) res.set(b.id(), e.getKey(), e.getValue());
			rt.reservedKeys.put(b.id(), new HashMap<>(mine));
			List<BuildRuntime.Missing> miss = new ArrayList<>();
			for (Map.Entry<String, Integer> e : r.missing().get(b.id()).entrySet()) miss.add(new BuildRuntime.Missing(MATERIALS.get(e.getKey()), e.getValue()));
			rt.missing.put(b.id(), miss);
		}
	}

	/** A claim stays only while its builder is loaded, a worker and in its WORK activity. */
	private static void releaseClaims(MinecraftServer server, VillageState village, BuildRuntime rt) {
		rt.claims.entrySet().removeIf(e -> {
			if (village.build(e.getKey()).isEmpty()) return true;
			if (rt.pinned.contains(e.getKey())) return false;
			Optional<Villager> v = Workers.findLoaded(server, e.getValue());
			boolean release = v.isEmpty() || village.worker(e.getValue()).isEmpty() || !v.get().getBrain().isActive(Activity.WORK);
			if (release && Boolean.getBoolean("villageheart.build.trace")) {
				dev.villageheart.VillageHeart.LOG.info("[build] claim of {} released (loaded {}, worker {}, working {})", e.getValue(), v.isPresent(),
						village.worker(e.getValue()).isPresent(), v.isPresent() && v.get().getBrain().isActive(Activity.WORK));
			}
			return release;
		});
	}

	// ---- what a build still needs ----

	/**
	 * The material id of each step that still has to place a block, in step order, from the cursor: the PLACE steps; while the check pass runs
	 * (the cursor is at or past its first CHECK step) the CHECK steps whose cell no longer holds the block, then the marker.
	 */
	public static List<String> stepMaterials(MinecraftServer server, @Nullable ServerLevel level, Blueprint bp, BuildJob job) {
		List<Step> steps = BuildJobs.steps(server, job);
		int firstCheck = steps.size();
		for (int i = 0; i < steps.size(); i++) {
			if (steps.get(i).kind() == BuildPlan.Kind.CHECK) {
				firstCheck = i;
				break;
			}
		}
		List<String> out = new ArrayList<>();
		for (int i = job.cursor(); i < steps.size(); i++) {
			Step s = steps.get(i);
			if (s.kind() == BuildPlan.Kind.PLACE) {
				out.add(s.materialId());
			} else if (s.kind() == BuildPlan.Kind.CHECK && job.cursor() >= firstCheck && level != null) {
				CellState cs = BuildExecutor.cellState(level, s, BuildJobs.touched(server, job));
				if (cs == CellState.FREE || cs == CellState.CLEARABLE || cs == CellState.ENTITY) out.add(s.materialId());
			}
		}
		return out;
	}

	/** Material id to the amount the builder carries for this build (its carried items, by the materials they serve). */
	public static Map<String, Integer> carriedByMaterial(Blueprint bp, BuildJob job) {
		Map<String, Integer> out = new LinkedHashMap<>();
		for (Map.Entry<String, Integer> e : job.carried().entrySet()) {
			Item item = BuiltInRegistries.ITEM.getOptional(Identifier.parse(e.getKey())).orElse(null);
			if (item == null) continue;
			for (var l : bp.materials().lines()) {
				if (PaletteStates.satisfies(l.material(), item)) {
					out.merge(l.material().id(), e.getValue(), Integer::sum);
					break;
				}
			}
		}
		return out;
	}

	/** Material id to the amount reserved for the build in Storage (live amounts of the keys reserved at the last refresh). */
	public static Map<String, Integer> reservedByMaterial(MinecraftServer server, BuildJob job) {
		BuildRuntime rt = BuildRuntime.get(server);
		Reservations<StackKey> res = Stockpiles.reservations(server);
		Map<String, Integer> out = new LinkedHashMap<>();
		for (StackKey k : rt.reservedKeys.getOrDefault(job.id(), Map.of()).keySet()) {
			int n = res.reservedBy(job.id(), k);
			if (n <= 0) continue;
			for (Material m : MATERIALS.values()) {
				if (BuildJobs.matches(k, m)) {
					out.merge(m.id(), n, Integer::sum);
					break;
				}
			}
		}
		return out;
	}

	// ---- describing a build ----

	/** One build as the Heart screen and the table show it. {@code missing} lists the materials still short, first one first. */
	public record BuildView(BuildJob job, Blueprint blueprint, int placed, int total, BuildStatus status, TextLine line, List<TextLine> missing,
			String builderName, boolean claimed) {
		public BuildView {
			missing = List.copyOf(missing);
		}
	}

	public static List<BuildView> views(MinecraftServer server) {
		List<BuildView> out = new ArrayList<>();
		for (BuildJob j : BuildJobs.list(server)) out.add(view(server, j));
		return out;
	}

	public static BuildView view(MinecraftServer server, BuildJob job) {
		BuildRuntime rt = BuildRuntime.get(server);
		Blueprint bp = BuildJobs.blueprint(job);
		List<Step> steps = BuildJobs.steps(server, job);
		long now = server.overworld().getGameTime();
		int percent = BuildPlan.percent(steps, job.cursor());
		List<TextLine> missing = new ArrayList<>();
		for (BuildRuntime.Missing m : rt.missing(job.id())) missing.add(BuildTexts.missing(m.material(), m.n()));
		UUID builder = rt.claimant(job.id());
		BuildRuntime.Pause pause = rt.pause(job.id());
		BuildStatus status;
		TextLine line;
		if (pause != null && now - pause.at() <= PAUSE_FRESH_TICKS && builder != null) {
			status = pause.status();
			line = status == BuildStatus.NEEDS_MATERIALS && !missing.isEmpty() ? missing.get(0)
					: status == BuildStatus.NEEDS_MATERIALS && pause.args().size() < 2 ? new TextLine("build.villageheart.needs_unknown", "Needs materials")
					: BuildTexts.status(status, pause.args());
		} else if (builder == null) {
			status = BuildStatus.WAITING_FOR_BUILDER;
			line = BuildTexts.status(status, List.of());
		} else {
			Step s = job.cursor() < steps.size() ? steps.get(job.cursor()) : null;
			status = s == null || s.kind() == BuildPlan.Kind.PLACE ? BuildStatus.BUILDING
					: s.kind() == BuildPlan.Kind.CLEAR ? BuildStatus.CLEARING : BuildStatus.CHECKING;
			line = status == BuildStatus.BUILDING ? BuildTexts.building(percent) : BuildTexts.status(status, List.of());
		}
		String name = "";
		if (builder != null) {
			name = Workers.findLoaded(server, builder).map(v -> v.hasCustomName() ? v.getCustomName().getString() : VillagerNames.of(builder)).orElse("");
		}
		return new BuildView(job, bp, BuildPlan.placedBefore(steps, job.cursor()), BuildPlan.placeCount(steps), status, line, missing, name,
				builder != null);
	}
}
