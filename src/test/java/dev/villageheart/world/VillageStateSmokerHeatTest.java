package dev.villageheart.world;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.VillageState;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** M13 T-L1-7: the heat banked at a smoker is saved with the village. */
class VillageStateSmokerHeatTest {
	private static final String DIM = "minecraft:overworld";
	private static final HeartLocation HEART = new HeartLocation(DIM, 0, 64, 0);
	private static final HeartLocation S1 = new HeartLocation(DIM, 5, 64, 6);
	private static final HeartLocation S2 = new HeartLocation(DIM, 9, 64, 6);

	private static VillageState base() { return VillageState.founded(HEART, new UUID(0, 1), "o", 0); }

	private static VillageState roundTrip(VillageState v) {
		return VillageCodecs.VILLAGE_STATE.parse(JsonOps.INSTANCE, VillageCodecs.VILLAGE_STATE.encodeStart(JsonOps.INSTANCE, v).getOrThrow()).getOrThrow();
	}

	@Test
	void heatRoundTripsAndOtherSmokersKeepTheirs() {
		VillageState v = base().withSmokerHeat(S1, 600).withSmokerHeat(S2, 1400);
		assertEquals(600, v.smokerHeat(S1));
		assertEquals(v, roundTrip(v));
		assertEquals(Map.of(S1, 600, S2, 1400), roundTrip(v).smokerHeat());
		VillageState changed = v.withSmokerHeat(S1, 200);
		assertEquals(200, changed.smokerHeat(S1));
		assertEquals(1400, changed.smokerHeat(S2), "the other smoker is untouched");
		JsonObject o = VillageCodecs.VILLAGE_STATE.encodeStart(JsonOps.INSTANCE, v).getOrThrow().getAsJsonObject();
		assertEquals(java.util.Set.of("smoker", "heat"), o.getAsJsonArray("smoker_heat").get(0).getAsJsonObject().keySet());
	}

	@Test
	void zeroRemovesTheEntryAndOtherEditsKeepTheHeat() {
		VillageState v = base().withSmokerHeat(S1, 600);
		assertEquals(0, v.withSmokerHeat(S1, 0).smokerHeat(S1));
		assertTrue(v.withSmokerHeat(S1, 0).smokerHeat().isEmpty());
		assertEquals(600, v.withTreasury(5).smokerHeat(S1), "an unrelated edit keeps the heat");
		assertEquals(600, v.depositTreasury(3).withTier(dev.villageheart.core.Tier.HAMLET).smokerHeat(S1));
		assertThrows(IllegalArgumentException.class, () -> v.withSmokerHeat(S1, -1));
	}

	@Test
	void absentKeyReadsAsEmpty() {
		JsonObject o = VillageCodecs.VILLAGE_STATE.encodeStart(JsonOps.INSTANCE, base()).getOrThrow().getAsJsonObject();
		o.remove("smoker_heat");
		assertTrue(VillageCodecs.VILLAGE_STATE.parse(JsonOps.INSTANCE, o).getOrThrow().smokerHeat().isEmpty());
	}

	@Test
	void aNegativeEntryIsDroppedAndTheVillageLoads() {
		VillageState v = base().withSmokerHeat(S1, 600).withSmokerHeat(S2, 400);
		JsonObject o = VillageCodecs.VILLAGE_STATE.encodeStart(JsonOps.INSTANCE, v).getOrThrow().getAsJsonObject();
		JsonArray arr = o.getAsJsonArray("smoker_heat");
		for (var e : arr) {
			JsonObject entry = e.getAsJsonObject();
			if (entry.getAsJsonObject("smoker").get("x").getAsInt() == 5) entry.addProperty("heat", -7);
		}
		VillageState back = VillageCodecs.VILLAGE_STATE.parse(JsonOps.INSTANCE, o).getOrThrow();
		assertEquals(Map.of(S2, 400), back.smokerHeat(), "the bad entry is dropped, the other kept, the village loads");
	}

	@Test
	void withoutSmokerHeatDropsOnlyTheMatching() {
		VillageState v = base().withSmokerHeat(S1, 600).withSmokerHeat(S2, 400);
		assertEquals(Map.of(S2, 400), v.withoutSmokerHeat(S1::equals).smokerHeat());
		assertSame(v, v.withoutSmokerHeat(l -> false));
	}
}
