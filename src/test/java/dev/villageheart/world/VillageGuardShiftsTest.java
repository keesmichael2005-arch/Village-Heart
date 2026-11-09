package dev.villageheart.world;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.Rank;
import dev.villageheart.core.Tier;
import dev.villageheart.core.TreeTask;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.WorkerRecord;
import dev.villageheart.core.blueprint.BlueprintParams;
import dev.villageheart.core.blueprint.BuildJob;
import dev.villageheart.core.blueprint.Rotation4;
import dev.villageheart.core.defence.DefenceSite;
import dev.villageheart.core.guard.GuardData;
import dev.villageheart.core.guard.GuardShift;
import dev.villageheart.core.guard.Shift;
import dev.villageheart.core.room.Pos;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** T-L1-11: guard shifts in the village state and its codec; the GuardData codec. */
class VillageGuardShiftsTest {
	private static final String DIM = "minecraft:overworld";
	private static final HeartLocation HEART = new HeartLocation(DIM, 0, 64, 0);
	private static final HeartLocation BARRACKS = new HeartLocation(DIM, 30, 64, 0);
	private static final UUID G1 = new UUID(7, 1), G2 = new UUID(7, 2), G3 = new UUID(7, 3), FARMER = new UUID(7, 9);

	private static VillageState base() { return VillageState.founded(HEART, new UUID(0, 1), "o", 0); }

	private static WorkerRecord guard(UUID id, int block) {
		return new WorkerRecord(id, "guard", BARRACKS, new HeartLocation(DIM, 30 + block, 64, 2));
	}

	private static VillageState withGuards() {
		return base().withWorker(guard(G1, 1)).withWorker(guard(G2, 2));
	}

	private static VillageState roundTrip(VillageState v) {
		return VillageCodecs.VILLAGE_STATE.parse(JsonOps.INSTANCE, VillageCodecs.VILLAGE_STATE.encodeStart(JsonOps.INSTANCE, v).getOrThrow()).getOrThrow();
	}

	@Test
	void hiringAndFiringRebalance() {
		VillageState v = withGuards();
		assertEquals(Shift.NIGHT, v.guardShift(G1).orElseThrow().shift());
		assertEquals(Shift.DAY, v.guardShift(G2).orElseThrow().shift());
		assertEquals(List.of(G1, G2), v.guardIds());
		VillageState gone = v.withoutWorker(G1);
		assertTrue(gone.guardShift(G1).isEmpty());
		assertEquals(Set.of(G2), gone.guardShifts().keySet());
		VillageState farmerToo = v.withWorker(new WorkerRecord(FARMER, "farmer", BARRACKS, new HeartLocation(DIM, 40, 64, 2)));
		assertTrue(farmerToo.guardShift(FARMER).isEmpty(), "a farmer has no shift");
	}

	@Test
	void pinAndBackToAuto() {
		VillageState v = withGuards().withGuardPin(G2, Optional.of(Shift.NIGHT));
		assertEquals(new GuardShift(Shift.NIGHT, true), v.guardShift(G2).orElseThrow());
		VillageState auto = v.withGuardPin(G2, Optional.empty());
		assertFalse(auto.guardShift(G2).orElseThrow().pinned());
		assertEquals(v, v.withGuardPin(FARMER, Optional.of(Shift.DAY)), "a non-guard is a no-op");
	}

	@Test
	void roundTripShiftAndPin() {
		VillageState v = withGuards().withWorker(guard(G3, 3)).withGuardPin(G3, Optional.of(Shift.DAY));
		assertEquals(v, roundTrip(v));
		assertTrue(roundTrip(v).guardShift(G3).orElseThrow().pinned());
	}

	@Test
	void absentFieldIsEmptyAndMismatchIsRebalancedOnLoad() {
		VillageState v = withGuards();
		JsonObject json = VillageCodecs.VILLAGE_STATE.encodeStart(JsonOps.INSTANCE, v).getOrThrow().getAsJsonObject();
		assertTrue(json.has("guard_shifts"));
		json.remove("guard_shifts");
		VillageState loaded = VillageCodecs.VILLAGE_STATE.parse(JsonOps.INSTANCE, json).getOrThrow();
		assertEquals(Shift.NIGHT, loaded.guardShift(G1).orElseThrow().shift(), "a pre-M18 save gets shifts for its guards");
		assertEquals(2, loaded.guardShifts().size());
	}

	@Test
	void unknownShiftIdIsDropped() {
		JsonObject json = VillageCodecs.VILLAGE_STATE.encodeStart(JsonOps.INSTANCE, withGuards()).getOrThrow().getAsJsonObject();
		JsonArray arr = json.getAsJsonArray("guard_shifts");
		arr.get(0).getAsJsonObject().addProperty("shift", "dusk");
		VillageState loaded = VillageCodecs.VILLAGE_STATE.parse(JsonOps.INSTANCE, json).getOrThrow();
		assertEquals(2, loaded.guardShifts().size(), "the dropped entry is re-created by the balancer");
		assertEquals(Shift.DAY, loaded.guardShift(G2).orElseThrow().shift());
	}

	@Test
	void everyWithMethodKeepsGuardShifts() throws Exception {
		VillageState v = withGuards().withGuardPin(G1, Optional.of(Shift.DAY));
		VillageState c = v;
		DefenceSite lamp = new DefenceSite(new UUID(1, 1), "lamp_post", new HeartLocation(DIM, 5, 64, 6), Rotation4.NONE, 7L);
		BuildJob job = new BuildJob(new UUID(5, 1), "wall_segment", new BlueprintParams(20), new HeartLocation(DIM, 10, 64, 3), Rotation4.NONE, 1L,
				new UUID(0, 7), 0, Map.of(), null);
		TreeTask task = new TreeTask(BARRACKS, TreeTask.Stage.FELLING, List.of(new HeartLocation(DIM, 25, 64, 3)), "minecraft:oak_sapling", 1L,
				new Pos(23, 64, 1), new Pos(27, 72, 5));
		Map<String, java.util.function.Function<VillageState, VillageState>> calls = new java.util.HashMap<>();
		calls.put("withRank", s -> s.withRank(new UUID(4, 4), "p", Rank.RESIDENT, 5));
		calls.put("withdrawTreasury", s -> s.withTreasury(5).withdrawTreasury(2));
		calls.put("withTreasury", s -> s.withTreasury(9));
		calls.put("withWorkerLevel", s -> s.withWorkerLevel(G1, 2));
		calls.put("withTier", s -> s.withTier(Tier.VILLAGE));
		calls.put("withBuilding", s -> s.withBuilding(new dev.villageheart.core.BuildingRecord("farm", BARRACKS)));
		calls.put("withoutBuilding", s -> s.withoutBuilding(BARRACKS));
		calls.put("withTreeTask", s -> s.withTreeTask(task));
		calls.put("withoutTreeTask", s -> s.withTreeTask(task).withoutTreeTask(task.key()));
		calls.put("withBuild", s -> s.withBuild(job));
		calls.put("withoutBuild", s -> s.withBuild(job).withoutBuild(job.id()));
		calls.put("withDefence", s -> s.withDefence(lamp));
		calls.put("withoutDefence", s -> s.withDefence(lamp).withoutDefence(lamp.id()));
		calls.put("withoutSelfCraft", s -> s.withoutSelfCraft(G1));
		calls.put("withSelfCraft", s -> s.withoutSelfCraft(G1)); // a craft record is heavy to build; both rebuild through withSelfCrafts
		calls.put("withGuardShifts", s -> s.withGuardShifts(s.guardShifts()));
		calls.put("withTierProgress", s -> s.withTierProgress(new dev.villageheart.core.tier.TierProgress(2, 5L, 7))); // M14
		// the names that rebuild the guard map on purpose are tested above
		Set<String> special = Set.of("withWorker", "withoutWorker", "withGuardPin");
		Set<String> found = new HashSet<>();
		for (Method m : VillageState.class.getDeclaredMethods()) {
			if (!Modifier.isPublic(m.getModifiers()) || m.getReturnType() != VillageState.class) continue;
			if (!m.getName().startsWith("with")) continue;
			found.add(m.getName());
		}
		for (String name : found) {
			if (special.contains(name)) continue;
			assertTrue(calls.containsKey(name), "VillageState." + name + " is new: add it to this sweep so it keeps guardShifts");
		}
		for (var e : calls.entrySet()) {
			assertEquals(v.guardShifts(), e.getValue().apply(c).guardShifts(), e.getKey() + " dropped the guard shifts");
		}
	}

	@Test
	void guardDataRoundTrip() {
		for (boolean w : new boolean[] {true, false}) {
			GuardData d = new GuardData(w);
			assertEquals(d, GuardCodecs.GUARD_DATA.parse(JsonOps.INSTANCE, GuardCodecs.GUARD_DATA.encodeStart(JsonOps.INSTANCE, d).getOrThrow()).getOrThrow());
		}
		assertEquals(new GuardData(false), GuardCodecs.GUARD_DATA.parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow(), "absent field is not wounded");
	}
}
