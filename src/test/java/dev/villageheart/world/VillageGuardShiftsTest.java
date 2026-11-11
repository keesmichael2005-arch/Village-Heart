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
		assertEquals(Shift.NIGHT, v.guardShift(G2).orElseThrow().shift(), "up to three guards all work nights");
		assertEquals(List.of(G1, G2), v.guardIds());
		VillageState four = v.withWorker(guard(G3, 3)).withWorker(guard(new UUID(7, 4), 4));
		assertEquals(Shift.DAY, four.guardShift(new UUID(7, 4)).orElseThrow().shift(), "the 4th guard takes the one day slot");
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
		assertEquals(Shift.NIGHT, loaded.guardShift(G2).orElseThrow().shift());
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
		calls.put("withSmokerHeat", s -> s.withSmokerHeat(BARRACKS, 3));
		calls.put("withoutSmokerHeat", s -> s.withSmokerHeat(BARRACKS, 3).withoutSmokerHeat(BARRACKS::equals));
		calls.put("withGuardShifts", s -> s.withGuardShifts(s.guardShifts()));
		calls.put("withTierProgress", s -> s.withTierProgress(new dev.villageheart.core.tier.TierProgress(2, 5L, 7))); // M14
		calls.put("withThreats", s -> s.withThreats(s.threats().withPending(Optional.of(new dev.villageheart.core.siege.PendingRaid(3, 4000))))); // M25
		// the names that rebuild the guard map on purpose are tested above
		Set<String> special = Set.of("withWorker", "withoutWorker", "withGuardPin", "withGuardSeniority");
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
	void splitCodecKeepsEveryKeyFlatAndReadsOldSaves() {
		// a state with every optional key non-default: all keys are top level (the split into two map codecs does not nest or rename anything)
		VillageState v = withGuards().withTier(Tier.VILLAGE).withTreasury(5).withSmokerHeat(BARRACKS, 4)
				.withTierProgress(new dev.villageheart.core.tier.TierProgress(2, 7L, 9));
		JsonObject json = VillageCodecs.VILLAGE_STATE.encodeStart(JsonOps.INSTANCE, v).getOrThrow().getAsJsonObject();
		for (String key : List.of("heart", "owner", "owner_name", "claimed_at", "treasury", "tier", "workers", "tier_progress", "smoker_heat", "guard_shifts")) {
			assertTrue(json.has(key), "top-level key " + key + " in " + json.keySet());
		}
		assertEquals(v, VillageCodecs.VILLAGE_STATE.parse(JsonOps.INSTANCE, json).getOrThrow(), "a save with all keys loads field for field");
		// a save written before M14, M13 and M18: only the four required keys
		JsonObject old = new JsonObject();
		for (String key : List.of("heart", "owner", "owner_name", "claimed_at")) old.add(key, json.get(key));
		VillageState loaded = VillageCodecs.VILLAGE_STATE.parse(JsonOps.INSTANCE, old).getOrThrow();
		assertTrue(loaded.workers().isEmpty() && loaded.guardShifts().isEmpty());
		assertEquals(dev.villageheart.core.tier.TierProgress.INITIAL, loaded.tierProgress());
		assertEquals(0, loaded.smokerHeat(BARRACKS));
	}

	@Test
	void seniorityMovesTheShiftsWithXpAndNothingElse() {
		UUID g4 = new UUID(7, 4);
		VillageState v = withGuards().withWorker(guard(G3, 3)).withWorker(guard(g4, 4)); // G1-G3 nights, G4 the day guard
		assertEquals(Shift.DAY, v.guardShift(g4).orElseThrow().shift());
		assertSame(v, v.withGuardSeniority(java.util.Map.of()), "no XP news: nothing moves, same instance");
		VillageState up = v.withGuardSeniority(java.util.Map.of(g4, 50, G1, 10, G2, 20, G3, 30));
		assertEquals(Shift.NIGHT, up.guardShift(g4).orElseThrow().shift(), "the guard with the XP takes the night");
		assertEquals(Shift.DAY, up.guardShift(G1).orElseThrow().shift(), "and the least senior night guard goes to days");
		assertEquals(v.workers(), up.workers());
	}

	@Test
	void guardDataRoundTrip() {
		for (boolean w : new boolean[] {true, false}) {
			GuardData d = new GuardData(w, w ? 36000L : 0L, w ? 7 : 0);
			assertEquals(d, GuardCodecs.GUARD_DATA.parse(JsonOps.INSTANCE, GuardCodecs.GUARD_DATA.encodeStart(JsonOps.INSTANCE, d).getOrThrow()).getOrThrow());
		}
		assertEquals(GuardData.FRESH, GuardCodecs.GUARD_DATA.parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow(), "absent fields: not wounded, qualified, no ration");
		JsonObject oldSave = new JsonObject(); // a save from before the ration and training fields
		oldSave.addProperty("wounded", true);
		assertEquals(new GuardData(true, 0, 0), GuardCodecs.GUARD_DATA.parse(JsonOps.INSTANCE, oldSave).getOrThrow());
	}
}
