package dev.villageheart.world;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import dev.villageheart.core.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class VillageCodecsTest {
	static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");
	static final UUID STEW = UUID.fromString("22222222-2222-2222-2222-222222222222");
	static final UUID RES = UUID.fromString("33333333-3333-3333-3333-333333333333");

	static VillageState full() {
		HeartLocation loc = new HeartLocation("minecraft:the_nether", -12, -5, -300);
		return new VillageState(loc, OWNER, "alice", 5_000_000_000L, Map.of(STEW, Rank.STEWARD, RES, Rank.RESIDENT),
				1234, Tier.TOWN, List.of(new BuildingRecord("farm", loc)), List.of(new WorkerRecord(RES, "farmer")));
	}

	static <T> JsonElement enc(Codec<T> c, T v) { return c.encodeStart(JsonOps.INSTANCE, v).getOrThrow(); }
	static <T> T dec(Codec<T> c, JsonElement j) { return c.parse(JsonOps.INSTANCE, j).getOrThrow(); }
	static JsonElement json(String s) { return JsonParser.parseString(s); }
	static Set<String> keys(JsonElement e) { return e.getAsJsonObject().keySet(); }

	@Test void roundTripFullState() {
		VillageState v = full();
		assertEquals(v, dec(VillageCodecs.VILLAGE_STATE, enc(VillageCodecs.VILLAGE_STATE, v)));
	}

	@Test void roundTripEmptyWorldClaim() {
		WorldClaim w = dec(VillageCodecs.WORLD_CLAIM, enc(VillageCodecs.WORLD_CLAIM, new WorldClaim()));
		assertTrue(w.village().isEmpty());
	}

	@Test void roundTripFoundedState() {
		VillageState v = VillageState.founded(new HeartLocation("minecraft:overworld", 1, 2, 3), OWNER, "bob", 7);
		assertEquals(v, dec(VillageCodecs.VILLAGE_STATE, enc(VillageCodecs.VILLAGE_STATE, v)));
	}

	@Test void roundTripWorldClaimWithVillage() {
		WorldClaim w = new WorldClaim(java.util.Optional.of(full()));
		assertEquals(w, dec(VillageCodecs.WORLD_CLAIM, enc(VillageCodecs.WORLD_CLAIM, w)));
	}

	@Test void pinnedFieldNames() {
		JsonElement e = enc(VillageCodecs.VILLAGE_STATE, full());
		assertEquals(Set.of("heart", "owner", "owner_name", "claimed_at", "ranks", "treasury", "tier", "buildings", "workers"), keys(e));
		JsonObject world = enc(VillageCodecs.WORLD_CLAIM, new WorldClaim(java.util.Optional.of(full()))).getAsJsonObject();
		assertEquals(Set.of("schema", "village"), world.keySet());
		assertEquals(1, world.get("schema").getAsInt());
	}

	@Test void foundedStateOmitsDefaults() {
		VillageState v = VillageState.founded(new HeartLocation("minecraft:overworld", 1, 2, 3), OWNER, "bob", 7);
		assertEquals(Set.of("heart", "owner", "owner_name", "claimed_at"), keys(enc(VillageCodecs.VILLAGE_STATE, v)));
		assertEquals(Set.of("schema"), keys(enc(VillageCodecs.WORLD_CLAIM, new WorldClaim())));
	}

	static final String BASE = "\"heart\":{\"dim\":\"minecraft:overworld\",\"x\":1,\"y\":2,\"z\":3},"
			+ "\"owner\":\"11111111-1111-1111-1111-111111111111\",\"owner_name\":\"a\",\"claimed_at\":4";

	@Test void missingOptionalFieldsDefault() {
		VillageState v = dec(VillageCodecs.VILLAGE_STATE, json("{" + BASE + "}"));
		assertEquals(0, v.treasury());
		assertEquals(Tier.HAMLET, v.tier());
		assertTrue(v.ranks().isEmpty() && v.buildings().isEmpty() && v.workers().isEmpty());
	}

	@Test void badValuesAreErrorsNotExceptions() {
		for (String extra : List.of(",\"tier\":\"metropolis\"", ",\"treasury\":-5",
				",\"ranks\":{\"22222222-2222-2222-2222-222222222222\":\"king\"}",
				",\"ranks\":{\"22222222-2222-2222-2222-222222222222\":\"owner\"}")) {
			assertTrue(VillageCodecs.VILLAGE_STATE.parse(JsonOps.INSTANCE, json("{" + BASE + extra + "}")).result().isEmpty(), extra);
		}
		String badOwner = BASE.replace("11111111-1111-1111-1111-111111111111", "not-a-uuid");
		assertTrue(VillageCodecs.VILLAGE_STATE.parse(JsonOps.INSTANCE, json("{" + badOwner + "}")).result().isEmpty());
	}
}
