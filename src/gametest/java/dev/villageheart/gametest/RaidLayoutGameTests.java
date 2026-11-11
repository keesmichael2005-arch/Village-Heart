package dev.villageheart.gametest;

import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.defence.DefenceCells;
import dev.villageheart.core.defence.DefenceSite;
import dev.villageheart.core.guard.Shift;
import dev.villageheart.core.sim.Scenario;
import dev.villageheart.core.sim.ScenarioAction;
import dev.villageheart.core.sim.ScenarioParser;
import dev.villageheart.raid.ThreatScheduler;
import dev.villageheart.sim.BuildActions;
import dev.villageheart.sim.GuardActions;
import dev.villageheart.sim.Host;
import dev.villageheart.sim.RaidSim;
import dev.villageheart.sim.StarterActions;
import dev.villageheart.stock.Stockpiles;
import dev.villageheart.world.VillageClaims;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;

/**
 * T-L2-12. The raid scenario's buildings, ring, golem and guards, on a flat yard. The site finder is the sim's job.
 */
public class RaidLayoutGameTests {
	private static final String YARD = "villageheart-gametest:raid_yard";

	/** A time of day on the current day, so the change never looks like a run of missed dawns to another test. */
	private static void atTime(GameTestHelper h, int tick) {
		long now = h.getLevel().getDayTime();
		long base = Math.floorDiv(now, 24000L) * 24000L;
		h.getLevel().setDayTime(base + Math.floorMod(tick, 24000));
	}

	@GameTest(environment = "villageheart-gametest:raid_layout", structure = YARD, skyAccess = true, maxTicks = 4000)
	public void raidLayoutValid(GameTestHelper h) {
		BlockPos[] site = new BlockPos[1];
		BuildingTestSupport.begin(h).thenExecute(() -> {
			StarterActions.resetStanding();
			atTime(h, 1000);
			LumberTestSupport.grass(h, 4, 4, 51, 39);
			site[0] = h.absolutePos(new BlockPos(4, -1, 4));
			ServerLevel level = h.getLevel();
			Host host = new Host() {
				public ServerLevel level() { return level; }
				public net.minecraft.server.MinecraftServer server() { return level.getServer(); }
				public BlockPos heart() { return h.absolutePos(new BlockPos(3, 1, 1)); }
				public BlockPos site() { return site[0]; }
				public void setSite(BlockPos s) { site[0] = s; }
			};
			for (ScenarioAction a : scenario().actions()) run(h, host, a);
		}).thenWaitUntil(() -> assertLayout(h)).thenExecute(() -> {
			VillageTestSupport.done(h);
			WorkerTestSupport.end(h);
		});
	}

	private static void run(GameTestHelper h, Host host, ScenarioAction a) {
		switch (a.type()) {
			case "claim", "site", "raid" -> { }
			case "command" -> command(h, (String) a.fields().get("command"));
			case "tier" -> GuardActions.tier(host, a);
			case "prefab" -> StarterActions.prefab(host, a);
			case "field" -> StarterActions.field(host, a);
			case "grove" -> StarterActions.grove(host, a);
			case "training_yard" -> GuardActions.trainingYard(host, a);
			case "hire" -> StarterActions.hire(host, a);
			case "build" -> BuildActions.build(host, a);
			case "golems" -> RaidSim.setGolems(host.level(), ((Number) a.fields().get("count")).intValue());
			case "top_up_villagers" -> {
				var at = (List<?>) a.fields().get("at");
				RaidSim.topUp(host.level(), ((Number) a.fields().get("adults")).intValue(),
						((Number) at.get(0)).intValue(), ((Number) at.get(1)).intValue());
			}
			case "worker_level" -> RaidSim.setWorkerLevel(host.server(), (String) a.fields().get("job"),
					((Number) a.fields().get("level")).intValue());
			default -> throw new IllegalStateException("raid layout does not run " + a.type());
		}
	}

	private static void command(GameTestHelper h, String cmd) {
		var level = h.getLevel();
		boolean[] fired = {false};
		boolean[] ok = {false};
		var src = level.getServer().createCommandSourceStack().withLevel(level)
				.withPosition(net.minecraft.world.phys.Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 1, 1))))
				.withCallback((success, result) -> { fired[0] = true; ok[0] = success; });
		level.getServer().getCommands().performPrefixedCommand(src, cmd);
		h.assertTrue(fired[0] && ok[0], "command failed: " + cmd);
	}

	private static void assertLayout(GameTestHelper h) {
		var server = h.getLevel().getServer();
		var village = VillageClaims.current(server).orElseThrow();
		valid(h, BuildingType.HOUSE, 4);
		valid(h, BuildingType.STORAGE, 1);
		valid(h, BuildingType.KITCHEN, 1);
		valid(h, BuildingType.FARMHOUSE, 1);
		valid(h, BuildingType.LUMBER_CAMP, 1);
		valid(h, BuildingType.SMITHY, 1);
		valid(h, BuildingType.BUILDERS_HUT, 1);
		valid(h, BuildingType.TRAINING_YARD, 1);
		var barracks = dev.villageheart.building.Buildings.valid(server, BuildingType.BARRACKS);
		h.assertValueEqual(1, barracks.size(), "barracks");
		h.assertValueEqual(4, barracks.get(0).verdict().orElseThrow().slots(), "guard slots");
		Map<String, List<Integer>> lengths = new TreeMap<>();
		int cells = 0, unseen = 0;
		for (DefenceSite site : village.defences()) {
			lengths.computeIfAbsent(site.blueprintId(), k -> new ArrayList<>()).add(site.length());
			for (var placed : DefenceCells.of(site)) {
				cells++;
				if (!site.seen().contains(DefenceCells.relative(site.ground(), placed.pos()))) unseen++;
			}
		}
		for (List<Integer> list : lengths.values()) list.sort(Integer::compareTo);
		h.assertTrue(List.of(2, 10, 10, 16, 16, 32, 32, 32).equals(lengths.get("wall_segment")), "wall lengths " + lengths);
		h.assertTrue(List.of(7, 7).equals(lengths.get("gate")), "gate lengths " + lengths);
		h.assertValueEqual(486, cells, "defence cells");
		h.assertValueEqual(0, unseen, "unseen cells");
		long builders = village.workers().stream().filter(w -> w.jobId().equals("builder")).count();
		long guards = village.workers().stream().filter(w -> w.jobId().equals("guard")).count();
		long day = 0, night = 0;
		for (var w : village.workers()) {
			if (!w.jobId().equals("guard")) continue;
			var shift = village.guardShift(w.villager()).orElseThrow();
			if (shift.shift() == Shift.DAY) day++;
			else night++;
		}
		h.assertValueEqual(1L, builders, "builder");
		h.assertValueEqual(4L, guards, "guards");
		h.assertValueEqual(1L, day, "day shift");
		h.assertValueEqual(3L, night, "night shift");
		HeartLocation heart = village.heart();
		h.assertValueEqual(1, RaidSim.countGolems(h.getLevel(), heart), "golems");
		h.assertValueEqual(0L, Stockpiles.count(server, Items.IRON_INGOT), "iron ingots");
		h.assertValueEqual(0L, Stockpiles.count(server, Items.CARVED_PUMPKIN), "carved pumpkin");
		int score = ThreatScheduler.scoreHundred(server);
		dev.villageheart.VillageHeart.LOG.info("[raid] defence cells {} threat score {}", cells, score);
		h.assertTrue(score >= 400 && score <= 800, "threat score " + score + " for " + cells + " cells");
	}

	private static void valid(GameTestHelper h, BuildingType type, int n) {
		h.assertValueEqual(n, dev.villageheart.building.Buildings.valid(h.getLevel().getServer(), type).size(), type.id());
	}

	private static Scenario scenario() {
		try {
			Path p = Path.of("sim/scenarios/raid.json");
			if (!Files.exists(p)) p = Path.of("../../../sim/scenarios/raid.json");
			return ScenarioParser.parse("raid", Files.readString(p));
		} catch (java.io.IOException e) {
			throw new IllegalStateException(e);
		}
	}
}
