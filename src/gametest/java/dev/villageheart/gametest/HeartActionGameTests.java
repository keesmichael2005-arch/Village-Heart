package dev.villageheart.gametest;

import static dev.villageheart.gametest.BuildingTestSupport.*;
import static dev.villageheart.gametest.HeartTestSupport.*;

import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.Rank;
import dev.villageheart.core.heart.HeartSnapshot;
import dev.villageheart.core.heart.Rejection;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.heart.HeartActions;
import dev.villageheart.heart.net.HeartActionResultPayload;
import dev.villageheart.heart.net.HeartSnapshotPayload;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.worker.HireResult;
import dev.villageheart.worker.Workers;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.Blocks;

/**
 * Layer 2: the Heart screen actions (M8): ranks, the gate, rate limits, bad packets. Rules as in {@link HeartScreenGameTests}.
 * Players have random UUIDs, so tests never share rate-limit buckets.
 */
public class HeartActionGameTests {
	private static MinecraftServer server(GameTestHelper h) { return h.getLevel().getServer(); }

	private static BlockPos farmMarker(GameTestHelper h) { return h.absolutePos(markerPos(FARMHOUSE, 5)); }

	private static BlockPos storageMarker(GameTestHelper h) { return h.absolutePos(markerPos(STORAGE, 5)); }

	private static void assertRejected(GameTestHelper h, List<Sent> sent, ServerPlayer p, Rejection why, String what) {
		HeartActionResultPayload r = lastResult(sent, p);
		h.assertFalse(r.ok(), what + ": not ok");
		h.assertValueEqual(why.langKey(), r.key(), what + ": reason");
	}

	private static void assertOk(GameTestHelper h, List<Sent> sent, ServerPlayer p, String key, String what) {
		HeartActionResultPayload r = lastResult(sent, p);
		h.assertTrue(r.ok(), what + ": ok, got " + r);
		h.assertValueEqual(key, r.key(), what + ": key");
	}

	private static void assertUnchanged(GameTestHelper h, Villager v, String what) {
		h.assertFalse(isWorker(h, v), what + ": V is still unemployed");
		h.assertTrue(WorkerTestSupport.isUnemployed(v), what + ": V keeps the vanilla profession");
	}

	// ---- T-L2-1 (roadmap) ---------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:heart_assign", structure = YARD, skyAccess = true, maxTicks = 700)
	public void assignFromVisitorRejectedFromStewardAccepted(GameTestHelper h) {
		Villager[] v = new Villager[1];
		ServerPlayer[] p = new ServerPlayer[2];
		List<Sent>[] sent = new List[1];
		GameTestSequence seq = seed(h).thenExecute(() -> {
			sent[0] = recorder();
			v[0] = villager(h);
			p[0] = player(h, "vis", Rank.VISITOR);
			p[1] = player(h, "stew", Rank.STEWARD);
			open(h, p[0]);
			open(h, p[1]);
			assign(p[0], v[0], "farmer", Optional.empty());
		});
		seq = seq.thenIdle(2).thenExecute(() -> {
			assertRejected(h, sent[0], p[0], Rejection.NO_PERMISSION, "Visitor assign");
			assertUnchanged(h, v[0], "after the Visitor");
			assign(p[1], v[0], "farmer", Optional.empty());
		});
		seq = seq.thenIdle(2).thenExecute(() -> {
			assertOk(h, sent[0], p[1], "message.villageheart.hire.hired", "Steward assign");
			h.assertTrue(isWorker(h, v[0]), "V is a worker");
			h.assertValueEqual("villageheart:farmer", v[0].getVillagerData().profession().unwrapKey().orElseThrow().identifier().toString(),
					"profession");
			h.assertTrue(VillageClaims.current(server(h)).orElseThrow().worker(v[0].getUUID()).isPresent(), "registry record");
		});
		awaitWithin(h, seq, dev.villageheart.core.Balance.HEART_REFRESH_TICKS + 1, "the screen shows V as a farmer", () -> {
			List<HeartSnapshotPayload> got = snapshotsTo(sent[0], p[1]);
			h.assertFalse(got.isEmpty(), "a snapshot was sent");
			HeartSnapshot last = got.get(got.size() - 1).snapshot();
			h.assertTrue(last.villagers().stream().anyMatch(r -> r.id().equals(v[0].getUUID()) && r.jobId().equals("farmer")),
					"row for V says farmer");
		}).thenExecute(() -> done(h));
	}

	// ---- T-L2-2 ---------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:heart_fire", structure = YARD, skyAccess = true, maxTicks = 700)
	public void firePermissions(GameTestHelper h) {
		Villager[] v = new Villager[1];
		ServerPlayer[] p = new ServerPlayer[3];
		List<Sent>[] sent = new List[1];
		GameTestSequence seq = seed(h).thenExecute(() -> {
			sent[0] = recorder();
			v[0] = villager(h);
			h.assertTrue(Workers.hire(h.getLevel(), v[0], JobType.FARMER, Optional.empty()) instanceof HireResult.Hired, "hired");
			p[0] = player(h, "res", Rank.RESIDENT);
			p[1] = player(h, "stew", Rank.STEWARD);
			p[2] = owner();
			open(h, p[0]);
			open(h, p[1]);
			open(h, p[2]);
			fire(p[0], v[0].getUUID());
			assertRejected(h, sent[0], p[0], Rejection.NO_PERMISSION, "Resident fire");
			h.assertTrue(isWorker(h, v[0]), "V still a worker");
			recheck(p[0], farmMarker(h));
			assertRejected(h, sent[0], p[0], Rejection.NO_PERMISSION, "Resident recheck");

			fire(p[1], v[0].getUUID());
			assertOk(h, sent[0], p[1], "message.villageheart.hire.fired", "Steward fire");
			h.assertFalse(isWorker(h, v[0]), "V is no longer a worker");
			h.assertTrue(WorkerTestSupport.isUnemployed(v[0]), "V is a vanilla villager again");

			fire(p[2], v[0].getUUID());
			assertRejected2(h, sent[0], p[2], "message.villageheart.hire.not_a_worker");

			recheck(p[1], farmMarker(h));
			assertOk(h, sent[0], p[1], "gui.villageheart.heart.checking", "Steward recheck");
			recheck(p[1], farmMarker(h).offset(0, 40, 0));
			assertRejected(h, sent[0], p[1], Rejection.NOT_A_BUILDING, "recheck of a random position");

			assign(p[1], v[0], "farmer", Optional.of(storageMarker(h)));
			HeartActionResultPayload r = lastResult(sent[0], p[1]);
			h.assertFalse(r.ok(), "the Storage has no farmer slot");
			h.assertValueEqual("message.villageheart.hire.not_in_preferred_building", r.key(), "preferred building refusal");
			assign(p[1], v[0], "farmer", Optional.of(farmMarker(h).offset(0, 40, 0)));
			assertRejected(h, sent[0], p[1], Rejection.NOT_A_BUILDING, "assign with a random building");
			assertUnchanged(h, v[0], "after the bad assigns");
			done(h);
		});
	}

	private static void assertRejected2(GameTestHelper h, List<Sent> sent, ServerPlayer p, String key) {
		HeartActionResultPayload r = lastResult(sent, p);
		h.assertFalse(r.ok(), "not ok");
		h.assertValueEqual(key, r.key(), "key");
	}

	// ---- T-L2-4 (action part) ----------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:heart_rank_actions", structure = YARD, skyAccess = true, maxTicks = 700)
	public void rankChangeCountsAtOnce(GameTestHelper h) {
		Villager[] v = new Villager[1];
		ServerPlayer[] s = new ServerPlayer[1];
		List<Sent>[] sent = new List[1];
		seed(h).thenExecute(() -> {
			sent[0] = recorder();
			v[0] = villager(h);
			s[0] = player(h, "stew", Rank.STEWARD);
			open(h, s[0]);
			VillageClaims.setRank(server(h), s[0].getUUID(), Rank.VISITOR);
			assign(s[0], v[0], "farmer", Optional.empty());
			assertRejected(h, sent[0], s[0], Rejection.NO_PERMISSION, "demoted steward");
			assertUnchanged(h, v[0], "after the demoted steward");
			VillageClaims.setRank(server(h), s[0].getUUID(), Rank.STEWARD);
			assign(s[0], v[0], "farmer", Optional.empty());
			assertOk(h, sent[0], s[0], "message.villageheart.hire.hired", "promoted steward");
			done(h);
		});
	}

	// ---- T-L2-5 ---------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:heart_stale", structure = YARD, skyAccess = true, maxTicks = 700)
	public void staleOrForeignMenuRejected(GameTestHelper h) {
		Villager[] v = new Villager[1];
		ServerPlayer[] s = new ServerPlayer[1];
		List<Sent>[] sent = new List[1];
		GameTestSequence seq = seed(h).thenExecute(() -> {
			sent[0] = recorder();
			v[0] = villager(h);
			s[0] = player(h, "stew", Rank.STEWARD);
			int id = open(h, s[0]).containerId;
			// (a) a container id that is not open
			send(s[0], dev.villageheart.heart.net.AssignJobPayload.CODEC,
					new dev.villageheart.heart.net.AssignJobPayload(id + 1, v[0].getUUID(), "farmer", Optional.empty()));
			assertRejected(h, sent[0], s[0], Rejection.NOT_OPEN, "wrong container id");
			assertUnchanged(h, v[0], "after (a)");
			// (b) closed, then the old id
			s[0].closeContainer();
			send(s[0], dev.villageheart.heart.net.AssignJobPayload.CODEC,
					new dev.villageheart.heart.net.AssignJobPayload(id, v[0].getUUID(), "farmer", Optional.empty()));
			assertRejected(h, sent[0], s[0], Rejection.NOT_OPEN, "closed screen");
			assertUnchanged(h, v[0], "after (b)");
			// (c) open again, the owner breaks the Heart and claims again: the epoch changed
			open(h, s[0]);
			BlockPos old = heartPos(h);
			h.getLevel().setBlock(old, Blocks.AIR.defaultBlockState(), 3);
		});
		seq = seq.thenIdle(2).thenExecute(() -> {
			h.assertTrue(s[0].containerMenu == s[0].inventoryMenu, "the screen closed with the village");
			VillageTestSupport.place(h, owner(), new BlockPos(3, 0, 1));
			h.assertTrue(VillageClaims.current(server(h)).isPresent(), "claimed again");
			send(s[0], dev.villageheart.heart.net.AssignJobPayload.CODEC,
					new dev.villageheart.heart.net.AssignJobPayload(1, v[0].getUUID(), "farmer", Optional.empty()));
			assertRejected(h, sent[0], s[0], Rejection.NOT_OPEN, "after the re-claim");
			assertUnchanged(h, v[0], "after (c)");
			done(h);
		});
	}

	// ---- T-L2-9 ---------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:heart_bad", structure = YARD, skyAccess = true, maxTicks = 700)
	public void badActionsAreHarmless(GameTestHelper h) {
		seed(h).thenExecute(() -> {
			List<Sent> sent = recorder();
			Villager v = villager(h);
			ServerPlayer s = player(h, "stew", Rank.STEWARD);
			open(h, s);
			send(s, dev.villageheart.heart.net.AssignJobPayload.CODEC, new dev.villageheart.heart.net.AssignJobPayload(
					s.containerMenu.containerId, v.getUUID(), "wizard", Optional.empty()));
			assertRejected(h, sent, s, Rejection.UNKNOWN_JOB, "unknown job");
			send(s, dev.villageheart.heart.net.AssignJobPayload.CODEC, new dev.villageheart.heart.net.AssignJobPayload(
					s.containerMenu.containerId, java.util.UUID.randomUUID(), "farmer", Optional.empty()));
			assertRejected(h, sent, s, Rejection.VILLAGER_NOT_FOUND, "random uuid");
			assign(s, v, "farmer", Optional.empty());
			assertOk(h, sent, s, "message.villageheart.hire.hired", "first hire");
			assign(s, v, "farmer", Optional.empty());
			assertRejected2(h, sent, s, "message.villageheart.hire.already_employed");
			var trader = h.spawn(EntityType.WANDERING_TRADER, inside(FARMHOUSE, 2, 0, 1));
			send(s, dev.villageheart.heart.net.AssignJobPayload.CODEC, new dev.villageheart.heart.net.AssignJobPayload(
					s.containerMenu.containerId, trader.getUUID(), "farmer", Optional.empty()));
			assertRejected2(h, sent, s, "message.villageheart.hire.not_a_villager");
			trader.discard();
			h.assertTrue(isWorker(h, v), "V's job is unharmed");
			done(h);
		});
	}

	// ---- T-L2-10 --------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:heart_rate", structure = YARD, skyAccess = true, maxTicks = 700)
	public void rateLimited(GameTestHelper h) {
		ServerPlayer[] p = new ServerPlayer[2];
		List<Sent>[] sent = new List[1];
		GameTestSequence seq = seed(h).thenExecute(() -> {
			sent[0] = recorder();
			Villager target = villager(h);
			p[0] = player(h, "stew", Rank.STEWARD);
			p[1] = player(h, "vis", Rank.VISITOR);
			open(h, p[0]);
			open(h, p[1]);
			for (int i = 0; i < 20; i++) recheck(p[0], farmMarker(h));
			List<HeartActionResultPayload> res = resultsTo(sent[0], p[0]);
			h.assertValueEqual(8L, res.stream().filter(HeartActionResultPayload::ok).count(), "ok replies");
			h.assertValueEqual(1L, res.stream().filter(r -> r.key().equals(Rejection.RATE_LIMITED.langKey())).count(),
					"one rate_limited reply (throttled)");
			h.assertValueEqual(9, res.size(), "no other replies");

			for (int i = 0; i < 50; i++) assign(p[1], target, "farmer", Optional.empty());
		});
		seq.thenIdle(6).thenExecute(() -> {
			h.assertValueEqual(1, HeartActions.warnCount, "one WARN for 50 forbidden requests");
			List<HeartActionResultPayload> vis = resultsTo(sent[0], p[1]);
			h.assertTrue(vis.stream().filter(r -> r.key().equals(Rejection.NO_PERMISSION.langKey())).count() <= 8, "at most 8 NO_PERMISSION replies");
			int before = resultsTo(sent[0], p[0]).size();
			recheck(p[0], farmMarker(h));
			List<HeartActionResultPayload> res = resultsTo(sent[0], p[0]);
			h.assertValueEqual(before + 1, res.size(), "one more reply after the refill");
			h.assertTrue(res.get(res.size() - 1).ok(), "and it is ok");
			done(h);
		});
	}

	// ---- T-L2-12 --------------------------------------------------------------------------------

	/**
	 * A worker that is not loaded: simulated by discarding the entity while its registry record stays (the same state the village sees
	 * when the chunk is unloaded; the "restored when it loads" half belongs to M7's orphan sweep tests).
	 */
	@GameTest(environment = "villageheart-gametest:heart_unloaded", structure = YARD, skyAccess = true, maxTicks = 700)
	public void fireUnloadedWorker(GameTestHelper h) {
		java.util.UUID[] id = new java.util.UUID[1];
		ServerPlayer[] s = new ServerPlayer[1];
		List<Sent>[] sent = new List[1];
		seed(h).thenExecute(() -> {
			sent[0] = recorder();
			Villager v = villager(h);
			h.assertTrue(Workers.hire(h.getLevel(), v, JobType.FARMER, Optional.empty()) instanceof HireResult.Hired, "hired");
			id[0] = v.getUUID();
			dev.villageheart.worker.WorkerSystem.exemptFromOrphanSweepForTest(id[0]);
			v.discard();
			HeartSnapshot snap = snapshot(h, Rank.OWNER);
			var row = snap.villagers().stream().filter(r -> r.id().equals(id[0])).findFirst().orElseThrow();
			h.assertFalse(row.loaded(), "row says not loaded");
			h.assertValueEqual(dev.villageheart.core.heart.ActivityKind.AWAY, row.activity(), "activity");
			h.assertValueEqual(1, snap.overview().population(), "the population still counts it");
			s[0] = player(h, "stew", Rank.STEWARD);
			open(h, s[0]);
			fire(s[0], id[0]);
			assertOk(h, sent[0], s[0], "message.villageheart.hire.fired", "fire by id");
			h.assertTrue(VillageClaims.current(server(h)).orElseThrow().worker(id[0]).isEmpty(), "record gone");
			h.assertTrue(snapshot(h, Rank.OWNER).villagers().isEmpty(), "row gone");
			done(h);
		});
	}

	// ---- T-L2-13 --------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:heart_dimension", structure = YARD, skyAccess = true, maxTicks = 700)
	public void villagerInOtherDimension(GameTestHelper h) {
		seed(h).thenExecute(() -> {
			List<Sent> sent = recorder();
			ServerPlayer s = player(h, "stew", Rank.STEWARD);
			open(h, s);
			BlockPos hp = heartPos(h);
			var nether = server(h).getLevel(net.minecraft.world.level.Level.NETHER);
			h.assertTrue(nether != null, "the Nether exists");
			Villager v = EntityType.VILLAGER.create(nether, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
			v.setPos(hp.getX() + 0.5, 70, hp.getZ() + 0.5);
			nether.addFreshEntity(v);
			try {
				send(s, dev.villageheart.heart.net.AssignJobPayload.CODEC, new dev.villageheart.heart.net.AssignJobPayload(
						s.containerMenu.containerId, v.getUUID(), "farmer", Optional.empty()));
				assertRejected(h, sent, s, Rejection.VILLAGER_NOT_FOUND, "villager in the Nether");
				h.assertFalse(isWorker(h, v), "nothing changed");
			} finally {
				v.discard();
			}
			done(h);
		});
	}
}
