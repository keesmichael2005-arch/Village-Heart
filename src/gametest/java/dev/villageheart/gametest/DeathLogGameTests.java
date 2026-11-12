package dev.villageheart.gametest;

import static dev.villageheart.gametest.WorkerTestSupport.ORIGIN_A;
import static dev.villageheart.gametest.WorkerTestSupport.begin;
import static dev.villageheart.gametest.WorkerTestSupport.end;
import static dev.villageheart.gametest.WorkerTestSupport.isHeld;
import static dev.villageheart.gametest.WorkerTestSupport.spawnVillager;

import dev.villageheart.block.MarkerStatus;
import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.death.DeathEntry;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.sim.SimMetricsCollector;
import dev.villageheart.worker.HireResult;
import dev.villageheart.worker.Workers;
import dev.villageheart.world.VillageClaims;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** T-L2-7. The village log and the sim collector record the same loss. */
public class DeathLogGameTests {
	@GameTest(environment = "villageheart-gametest:death_log", structure = BuildingTestSupport.YARD, skyAccess = true, maxTicks = 600)
	public void deathLogRecordsCauseAndDay(GameTestHelper h) {
		begin(h);
		BlockPos[] marker = new BlockPos[1];
		GameTestSequence seq = BuildingTestSupport.begin(h).thenExecute(() -> {
			marker[0] = WorkerTestSupport.farmhouse(h, ORIGIN_A, 1);
			BuildingTestSupport.placeMarker(h, BuildingType.FARMHOUSE, marker[0], Direction.NORTH);
		});
		seq = seq.thenWaitUntil(() -> {
			BuildingTestSupport.assertStatus(h, marker[0], MarkerStatus.VALID);
			h.assertTrue(isHeld(h, BuildingTestSupport.inside(ORIGIN_A, 0, 0, 0)), "the composter is reserved");
		});
		seq.thenExecute(() -> {
			try {
				record(h);
			} finally {
				end(h);
			}
			VillageTestSupport.done(h);
		});
	}

	private static void record(GameTestHelper h) {
		ServerLevel level = h.getLevel();
		Difficulty was = level.getDifficulty();
		BlockPos heart = h.absolutePos(new BlockPos(3, 1, 1));
		SimMetricsCollector collector = new SimMetricsCollector(level, Vec3.atCenterOf(heart), 48);
		collector.register();
		collector.setEnabled(false);
		Zombie zombie = h.spawn(EntityType.ZOMBIE, BuildingTestSupport.inside(ORIGIN_A, 2, 0, 2));
		zombie.setNoAi(true);
		try {
			level.getServer().setDifficulty(Difficulty.HARD, true);
			collector.setEnabled(true);
			Villager converted = hire(h);
			converted.setCustomName(net.minecraft.network.chat.Component.literal("A".repeat(40)));
			converted.hurtServer(level, level.damageSources().mobAttack(zombie), 1000.0F);
			collector.setEnabled(false);
			var sim = collector.drainDeaths();
			h.assertValueEqual(1, sim.size(), "the collector counts the conversion once");
			h.assertValueEqual("zombified", sim.get(0).cause(), "collector cause");
			h.assertTrue(sim.get(0).byMob(), "a conversion counts as a mob death");
			DeathEntry hard = only(h, "the hard conversion");
			h.assertValueEqual("farmer", hard.jobId(), "job");
			h.assertValueEqual("zombified", hard.cause(), "one zombified row");
			h.assertTrue(hard.byMob() && hard.inClaim() && !hard.duringRaid() && !hard.duringSiege(), "flags");
			h.assertValueEqual(day(level), hard.day(), "day");
			h.assertValueEqual(hard.cause(), sim.get(0).cause(), "the sim row copies the village log");
			h.assertTrue(sim.get(0).detail() != null && hard.jobId().equals(sim.get(0).detail().job()), "the sim row keeps the logged job");
			h.assertValueEqual("A".repeat(32), hard.name(), "the log cuts a long name");
			h.assertValueEqual(hard.name(), sim.get(0).detail().name(), "the sim name is the cut log name");
			discardConverted(h);

			level.getServer().setDifficulty(Difficulty.NORMAL, true);
			DeathEntry plain = null;
			for (int i = 0; i < 24 && plain == null; i++) {
				Villager farmer = hire(h);
				farmer.hurtServer(level, level.damageSources().mobAttack(zombie), 1000.0F);
				DeathEntry row = rowFor(h, farmer.getUUID());
				h.assertTrue(row != null, "the farmer was logged");
				if ("minecraft:zombie".equals(row.cause())) plain = row;
				discardConverted(h);
			}
			h.assertTrue(plain != null, "a farmer on normal died without converting");
			h.assertValueEqual("farmer", plain.jobId(), "job");
			h.assertTrue(plain.byMob() && plain.inClaim(), "a zombie's hit");
			h.assertValueEqual(day(level), plain.day(), "day");

			int beforeIdle = size(h);
			Villager idle = spawnVillager(h, BuildingTestSupport.inside(ORIGIN_A, 3, 0, 1));
			idle.kill(level);
			DeathEntry killed = rowFor(h, idle.getUUID());
			h.assertTrue(killed != null && size(h) == beforeIdle + 1, "the unemployed villager was logged");
			h.assertValueEqual("minecraft:generic_kill", killed.cause(), "kill()");
			h.assertValueEqual("", killed.jobId(), "no job");
			h.assertTrue(!killed.byMob() && killed.inClaim(), "not a mob, inside the claim");

			int beforeCure = size(h);
			ZombieVillager sick = h.spawn(EntityType.ZOMBIE_VILLAGER, BuildingTestSupport.inside(ORIGIN_A, 3, 0, 2));
			Villager cured = sick.convertTo(EntityType.VILLAGER, ConversionParams.single(sick, false, false), v -> { });
			h.assertTrue(cured != null, "the cure produced a villager");
			h.assertValueEqual(beforeCure, size(h), "a cure logs nothing");
			cured.discard();

			collector.setEnabled(true);
			int beforeWitch = size(h);
			Villager hexed = spawnVillager(h, BuildingTestSupport.inside(ORIGIN_A, 3, 0, 1));
			var witch = hexed.convertTo(EntityType.WITCH, ConversionParams.single(hexed, false, false), w -> { });
			collector.setEnabled(false);
			h.assertTrue(witch != null, "the villager became a witch");
			DeathEntry witchRow = rowFor(h, hexed.getUUID());
			h.assertTrue(witchRow != null && size(h) == beforeWitch + 1, "one log row for the witch");
			h.assertValueEqual("minecraft:lightning_bolt", witchRow.cause(), "a witch is lightning, not zombified");
			h.assertTrue(!witchRow.byMob(), "lightning is not a mob death");
			var simWitch = collector.drainDeaths();
			h.assertValueEqual(1, simWitch.size(), "the sim records the witch once");
			h.assertValueEqual(witchRow.cause(), simWitch.get(0).cause(), "the sim follows the log and does not call a witch zombified");
			h.assertTrue(!simWitch.get(0).byMob(), "the sim agrees lightning is not a mob death");
			witch.discard();

			int beforeOut = size(h);
			Villager outsider = spawnVillager(h, BuildingTestSupport.inside(ORIGIN_A, 3, 0, 1));
			outsider.setPos(heart.getX() + 0.5, heart.getY(), heart.getZ() + 90.5);
			outsider.kill(level);
			h.assertValueEqual(beforeOut, size(h), "an unemployed villager outside the claim is not logged");
			h.assertTrue(rowFor(h, outsider.getUUID()) == null, "no row for the outsider");

			Villager traveller = hire(h);
			traveller.setPos(heart.getX() + 90.5, heart.getY(), heart.getZ() + 0.5);
			traveller.kill(level);
			DeathEntry away = rowFor(h, traveller.getUUID());
			h.assertTrue(away != null && !away.inClaim() && "farmer".equals(away.jobId()), "a hired worker outside is logged, not in the claim");

			List<DeathEntry> recent = VillageClaims.current(level.getServer()).orElseThrow().deaths().recent(size(h));
			h.assertTrue(recent.size() >= 2, "several rows");
			for (int i = 1; i < recent.size(); i++) {
				h.assertTrue(recent.get(i - 1).seq() > recent.get(i).seq(), "newest first");
			}
		} finally {
			collector.setEnabled(false);
			level.getServer().setDifficulty(was, true);
			zombie.discard();
			discardConverted(h);
		}
	}

	private static Villager hire(GameTestHelper h) {
		Villager v = spawnVillager(h, BuildingTestSupport.inside(ORIGIN_A, 2, 0, 1));
		HireResult r = Workers.hire(h.getLevel(), v, JobType.FARMER, Optional.empty());
		h.assertTrue(r instanceof HireResult.Hired, "hired a farmer: " + r);
		return v;
	}

	private static DeathEntry only(GameTestHelper h, String what) {
		List<DeathEntry> rows = VillageClaims.current(h.getLevel().getServer()).orElseThrow().deaths().entries();
		h.assertValueEqual(1, rows.size(), what);
		return rows.get(0);
	}

	private static DeathEntry rowFor(GameTestHelper h, java.util.UUID id) {
		for (DeathEntry e : VillageClaims.current(h.getLevel().getServer()).orElseThrow().deaths().entries()) {
			if (e.villager().equals(id)) return e;
		}
		return null;
	}

	private static int size(GameTestHelper h) {
		return VillageClaims.current(h.getLevel().getServer()).orElseThrow().deaths().entries().size();
	}

	private static int day(Level level) { return (int) (level.getDayTime() / 24000L + 1); }

	private static void discardConverted(GameTestHelper h) {
		for (ZombieVillager z : h.getEntities(EntityType.ZOMBIE_VILLAGER)) z.discard();
	}
}
