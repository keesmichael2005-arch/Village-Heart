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
				List.of(new dev.villageheart.core.KnownPlayer(STEW, "stew", 7), new dev.villageheart.core.KnownPlayer(RES, "res", 9)),
				1234, Tier.TOWN, List.of(new BuildingRecord("farm", loc)), List.of(new WorkerRecord(RES, "farmer", loc, new HeartLocation("minecraft:the_nether", -10, -5, -300))));
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
		assertEquals(Set.of("heart", "owner", "owner_name", "claimed_at", "ranks", "known_players", "treasury", "tier", "buildings", "workers"), keys(e));
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

	static final HeartLocation M1 = new HeartLocation("minecraft:overworld", 10, 64, 10);
	static final HeartLocation M2 = new HeartLocation("minecraft:overworld", 20, 64, 10);
	static final HeartLocation M3 = new HeartLocation("minecraft:overworld", 30, 64, 10);

	@Test void buildingsRoundTripWithStatusAndLink() {
		VillageState v = VillageState.founded(M1, OWNER, "a", 1)
				.withBuilding(new BuildingRecord("mineshaft", M2, dev.villageheart.core.building.BuildingStatus.VALID, M3))
				.withBuilding(new BuildingRecord("house", M1, dev.villageheart.core.building.BuildingStatus.INVALID, null))
				.withBuilding(new BuildingRecord("storage", M3));
		assertEquals(v, dec(VillageCodecs.VILLAGE_STATE, enc(VillageCodecs.VILLAGE_STATE, v)));
	}

	@Test void oldBuildingShapeDecodesAsUnchecked() {
		VillageState v = dec(VillageCodecs.VILLAGE_STATE, json("{" + BASE + ",\"buildings\":[{\"type\":\"storage\","
				+ "\"marker\":{\"dim\":\"minecraft:overworld\",\"x\":1,\"y\":2,\"z\":3}}]}"));
		BuildingRecord b = v.buildings().get(0);
		assertEquals(dev.villageheart.core.building.BuildingStatus.UNCHECKED, b.status());
		assertNull(b.link());
	}

	@Test void duplicateMarkersDecodeToTheFirst() {
		String m = "\"marker\":{\"dim\":\"minecraft:overworld\",\"x\":1,\"y\":2,\"z\":3}";
		VillageState v = dec(VillageCodecs.VILLAGE_STATE, json("{" + BASE + ",\"buildings\":[{\"type\":\"storage\"," + m
				+ "},{\"type\":\"house\"," + m + "}]}"));
		assertEquals(1, v.buildings().size());
		assertEquals("storage", v.buildings().get(0).typeId());
	}

	@Test void pinnedBuildingKeys() {
		VillageState v = VillageState.founded(M1, OWNER, "a", 1)
				.withBuilding(new BuildingRecord("mineshaft", M2, dev.villageheart.core.building.BuildingStatus.VALID, M3));
		JsonObject b = enc(VillageCodecs.VILLAGE_STATE, v).getAsJsonObject().getAsJsonArray("buildings").get(0).getAsJsonObject();
		assertEquals(Set.of("type", "marker", "status", "link"), b.keySet());
		assertEquals("valid", b.get("status").getAsString());
	}

	static final UUID W1 = UUID.fromString("44444444-4444-4444-4444-444444444444");
	static final UUID W2 = UUID.fromString("55555555-5555-5555-5555-555555555555");
	static final UUID W3 = UUID.fromString("66666666-6666-6666-6666-666666666666");
	static final HeartLocation J1 = new HeartLocation("minecraft:overworld", 11, 65, 11);
	static final HeartLocation J2 = new HeartLocation("minecraft:overworld", 12, 65, 11);
	static final HeartLocation J3 = new HeartLocation("minecraft:overworld", 21, 65, 11);

	@Test void workersRoundTripKeepOrder() {
		VillageState v = VillageState.founded(M1, OWNER, "a", 1)
				.withWorker(new WorkerRecord(W3, "farmer", M1, J2))
				.withWorker(new WorkerRecord(W1, "lumberjack", M2, J3))
				.withWorker(new WorkerRecord(W2, "farmer", M1, J1));
		VillageState back = dec(VillageCodecs.VILLAGE_STATE, enc(VillageCodecs.VILLAGE_STATE, v));
		assertEquals(v, back);
		assertEquals(List.of(W3, W1, W2), back.workers().stream().map(WorkerRecord::villager).toList());
	}

	@Test void pinnedWorkerKeys() {
		VillageState v = VillageState.founded(M1, OWNER, "a", 1).withWorker(new WorkerRecord(W1, "farmer", M1, J1));
		JsonObject w = enc(VillageCodecs.VILLAGE_STATE, v).getAsJsonObject().getAsJsonArray("workers").get(0).getAsJsonObject();
		assertEquals(Set.of("villager", "job", "building", "job_block"), w.keySet());
	}

	static final String LOCJ = "{\"dim\":\"minecraft:overworld\",\"x\":1,\"y\":2,\"z\":3}";

	@Test void workerWithoutBuildingOrJobBlockIsADecodeError() {
		for (String entry : List.of("{\"villager\":\"" + W1 + "\",\"job\":\"farmer\"}",
				"{\"villager\":\"" + W1 + "\",\"job\":\"farmer\",\"building\":" + LOCJ + "}",
				"{\"villager\":\"" + W1 + "\",\"job\":\"farmer\",\"job_block\":" + LOCJ + "}")) {
			assertTrue(VillageCodecs.VILLAGE_STATE.parse(JsonOps.INSTANCE, json("{" + BASE + ",\"workers\":[" + entry + "]}")).result().isEmpty(), entry);
		}
	}

	@Test void twoWorkersOnOneJobBlockOrOneVillagerTwiceIsAnError() {
		String a = "{\"villager\":\"" + W1 + "\",\"job\":\"farmer\",\"building\":" + LOCJ + ",\"job_block\":" + LOCJ + "}";
		String b = "{\"villager\":\"" + W2 + "\",\"job\":\"farmer\",\"building\":" + LOCJ + ",\"job_block\":" + LOCJ + "}";
		assertTrue(VillageCodecs.VILLAGE_STATE.parse(JsonOps.INSTANCE, json("{" + BASE + ",\"workers\":[" + a + "," + b + "]}")).result().isEmpty());
		String c = "{\"villager\":\"" + W1 + "\",\"job\":\"farmer\",\"building\":" + LOCJ + ",\"job_block\":"
				+ "{\"dim\":\"minecraft:overworld\",\"x\":9,\"y\":2,\"z\":3}}";
		assertTrue(VillageCodecs.VILLAGE_STATE.parse(JsonOps.INSTANCE, json("{" + BASE + ",\"workers\":[" + a + "," + c + "]}")).result().isEmpty());
	}

	@Test void reservedJobBlocksRoundTripAndDefaultEmpty() {
		WorldClaim w = new WorldClaim(java.util.Optional.of(full()), Set.of(J2, J1, J3));
		WorldClaim back = dec(VillageCodecs.WORLD_CLAIM, enc(VillageCodecs.WORLD_CLAIM, w));
		assertEquals(w, back);
		assertEquals(Set.of(J1, J2, J3), back.reservedJobBlocks());
		JsonObject o = enc(VillageCodecs.WORLD_CLAIM, w).getAsJsonObject();
		assertEquals(Set.of("schema", "village", "reserved_job_blocks"), o.keySet());
		WorldClaim legacy = dec(VillageCodecs.WORLD_CLAIM, json("{\"schema\":1}"));
		assertTrue(legacy.reservedJobBlocks().isEmpty());
	}

	@Test void lastDinnerDayRoundTripsAndDefaultsAbsent() {
		WorldClaim w = new WorldClaim(java.util.Optional.of(full()));
		assertTrue(w.lastDinnerDay().isEmpty());
		assertFalse(enc(VillageCodecs.WORLD_CLAIM, w).getAsJsonObject().has("last_dinner_day"));
		w.setLastDinnerDay(37);
		JsonObject o = enc(VillageCodecs.WORLD_CLAIM, w).getAsJsonObject();
		assertEquals(37, o.get("last_dinner_day").getAsLong());
		WorldClaim back = dec(VillageCodecs.WORLD_CLAIM, o);
		assertEquals(37, back.lastDinnerDay().getAsLong());
		assertEquals(w, back);
		assertTrue(dec(VillageCodecs.WORLD_CLAIM, json("{\"schema\":1}")).lastDinnerDay().isEmpty());
		w.releaseAt(full().heart());
		assertTrue(w.lastDinnerDay().isEmpty());
	}

	@Test void releasingTheClaimForgetsReservations() {
		WorldClaim w = new WorldClaim(java.util.Optional.of(VillageState.founded(M1, OWNER, "a", 1)), Set.of(J1));
		w.releaseAt(M1);
		assertTrue(w.reservedJobBlocks().isEmpty());
	}

	@Test void pendingTicketReleasesSurviveReleaseAndRoundTrip() {
		WorldClaim w = new WorldClaim(java.util.Optional.of(VillageState.founded(M1, OWNER, "a", 1)), Set.of(J1), Set.of(J2));
		w.releaseAt(M1);
		assertTrue(w.reservedJobBlocks().isEmpty());
		assertEquals(Set.of(J2), w.pendingTicketReleases());
		WorldClaim back = dec(VillageCodecs.WORLD_CLAIM, enc(VillageCodecs.WORLD_CLAIM, w));
		assertEquals(w, back);
		assertEquals(Set.of("schema", "pending_ticket_releases"), enc(VillageCodecs.WORLD_CLAIM, w).getAsJsonObject().keySet());
		assertTrue(dec(VillageCodecs.WORLD_CLAIM, json("{\"schema\":1}")).pendingTicketReleases().isEmpty());
	}

	// ---- M12: payroll and worker levels ----------------------------------------------------------------------

	@Test void workerLevelRoundTripsAndAnOldWorkerReadsLevelOne() {
		VillageState v = VillageState.founded(M1, OWNER, "a", 1).withWorker(new WorkerRecord(W1, "farmer", M1, J1, 3))
				.withWorker(new WorkerRecord(W2, "farmer", M1, J2));
		VillageState back = dec(VillageCodecs.VILLAGE_STATE, enc(VillageCodecs.VILLAGE_STATE, v));
		assertEquals(v, back);
		assertEquals(List.of(3, 1), back.workers().stream().map(WorkerRecord::level).toList());
		JsonObject w = enc(VillageCodecs.VILLAGE_STATE, v).getAsJsonObject().getAsJsonArray("workers").get(0).getAsJsonObject();
		assertEquals(Set.of("villager", "job", "building", "job_block", "level"), w.keySet());
		String old = "{\"villager\":\"" + W1 + "\",\"job\":\"farmer\",\"building\":" + LOCJ + ",\"job_block\":" + LOCJ + "}";
		VillageState legacy = dec(VillageCodecs.VILLAGE_STATE, json("{" + BASE + ",\"workers\":[" + old + "]}"));
		assertEquals(1, legacy.workers().get(0).level());
		String bad = "{\"villager\":\"" + W1 + "\",\"job\":\"farmer\",\"building\":" + LOCJ + ",\"job_block\":" + LOCJ + ",\"level\":0}";
		assertEquals(1, dec(VillageCodecs.VILLAGE_STATE, json("{" + BASE + ",\"workers\":[" + bad + "]}")).workers().get(0).level(), "a bad level reads as 1");
	}

	@Test void payrollRoundTripsWithPinnedKeys() {
		dev.villageheart.core.economy.Payroll p = new dev.villageheart.core.economy.Payroll(12, Set.of(W1),
				List.of(new dev.villageheart.core.economy.Payee(W2, 2)));
		WorldClaim w = new WorldClaim(java.util.Optional.of(full()), Set.of(), Set.of(), java.util.OptionalLong.empty(), java.util.Optional.of(p));
		JsonObject o = enc(VillageCodecs.WORLD_CLAIM, w).getAsJsonObject();
		JsonObject pj = o.getAsJsonObject("payroll");
		assertEquals(Set.of("last_wage_day", "paid_today", "unpaid"), pj.keySet());
		assertEquals(12, pj.get("last_wage_day").getAsLong());
		assertEquals(Set.of("villager", "wage"), pj.getAsJsonArray("unpaid").get(0).getAsJsonObject().keySet());
		WorldClaim back = dec(VillageCodecs.WORLD_CLAIM, o);
		assertEquals(w, back);
		assertEquals(p, back.payroll().orElseThrow());
	}

	@Test void aPreM12SaveHasNoPayroll() {
		assertTrue(dec(VillageCodecs.WORLD_CLAIM, json("{\"schema\":1}")).payroll().isEmpty());
		assertFalse(enc(VillageCodecs.WORLD_CLAIM, new WorldClaim(java.util.Optional.of(full()))).getAsJsonObject().has("payroll"));
	}

	@Test void oneBadUuidInUnpaidDropsThatEntryOnlyAndTheVillageSurvives() {
		String claim = "{\"schema\":1,\"village\":{" + BASE + "},\"payroll\":{\"last_wage_day\":3,\"paid_today\":[\"" + W1 + "\",\"nope\"],"
				+ "\"unpaid\":[{\"villager\":\"" + W2 + "\",\"wage\":1},{\"villager\":\"not-a-uuid\",\"wage\":1},{\"villager\":\"" + W3 + "\"}]}}";
		WorldClaim w = dec(VillageCodecs.WORLD_CLAIM, json(claim));
		assertTrue(w.village().isPresent());
		dev.villageheart.core.economy.Payroll p = w.payroll().orElseThrow();
		assertEquals(3, p.lastWageDay());
		assertEquals(Set.of(W1), p.paidToday());
		assertEquals(List.of(new dev.villageheart.core.economy.Payee(W2, 1)), p.unpaid());
	}

	@Test void aBrokenPayrollReadsAsAbsentAndNeverLosesTheClaim() {
		String claim = "{\"schema\":1,\"village\":{" + BASE + "},\"payroll\":{\"last_wage_day\":\"x\"}}";
		WorldClaim w = dec(VillageCodecs.WORLD_CLAIM, json(claim));
		assertTrue(w.village().isPresent());
		assertTrue(w.payroll().isEmpty());
		String both = "{\"schema\":1,\"village\":{" + BASE + "},\"payroll\":{\"last_wage_day\":3,\"paid_today\":[\"" + W1 + "\"],"
				+ "\"unpaid\":[{\"villager\":\"" + W1 + "\",\"wage\":1}]}}";
		dev.villageheart.core.economy.Payroll p = dec(VillageCodecs.WORLD_CLAIM, json(both)).payroll().orElseThrow();
		assertTrue(p.unpaid().isEmpty(), "a villager both paid and unpaid counts as paid");
		assertEquals(Set.of(W1), p.paidToday());
	}

	// ---- M22: market ------------------------------------------------------------------------------------------------

	private static dev.villageheart.core.market.MarketState sampleMarket() {
		dev.villageheart.core.market.SalesLedger l = dev.villageheart.core.market.SalesLedger.empty(7).record(W1, M1, 3).record(W2, J1, 2);
		return new dev.villageheart.core.market.MarketState(new dev.villageheart.core.market.Reserves(5, 128), l);
	}

	@Test void marketRoundTripsWithPinnedKeys() {
		dev.villageheart.core.market.MarketState m = sampleMarket();
		WorldClaim w = new WorldClaim(java.util.Optional.of(full()), Set.of(), Set.of(), java.util.OptionalLong.empty(), java.util.Optional.empty(), m);
		JsonObject o = enc(VillageCodecs.WORLD_CLAIM, w).getAsJsonObject();
		JsonObject mj = o.getAsJsonObject("market");
		assertEquals(Set.of("food_reserve_days", "item_reserve", "ledger"), mj.keySet());
		JsonObject lj = mj.getAsJsonObject("ledger");
		assertEquals(Set.of("day", "by_merchant", "by_stall"), lj.keySet());
		assertEquals(Set.of("villager", "sold"), lj.getAsJsonArray("by_merchant").get(0).getAsJsonObject().keySet());
		assertEquals(Set.of("stall", "sold"), lj.getAsJsonArray("by_stall").get(0).getAsJsonObject().keySet());
		assertEquals(2, lj.getAsJsonArray("by_merchant").size());
		assertEquals(2, lj.getAsJsonArray("by_stall").size());
		WorldClaim back = dec(VillageCodecs.WORLD_CLAIM, o);
		assertEquals(w, back);
		assertEquals(m, back.market());
	}

	@Test void aPreM22SaveHasDefaultMarketAndDefaultsAreNotWritten() {
		assertEquals(dev.villageheart.core.market.MarketState.initial(), dec(VillageCodecs.WORLD_CLAIM, json("{\"schema\":1}")).market());
		assertFalse(enc(VillageCodecs.WORLD_CLAIM, new WorldClaim(java.util.Optional.of(full()))).getAsJsonObject().has("market"));
	}

	@Test void marketReservesOutOfRangeAreClamped() {
		String claim = "{\"schema\":1,\"village\":{" + BASE + "},\"market\":{\"food_reserve_days\":99,\"item_reserve\":9999}}";
		WorldClaim w = dec(VillageCodecs.WORLD_CLAIM, json(claim));
		assertTrue(w.village().isPresent());
		assertEquals(new dev.villageheart.core.market.Reserves(30, 1024), w.market().reserves());
	}

	@Test void oneBadLedgerEntryDropsThatEntryOnly() {
		String claim = "{\"schema\":1,\"village\":{" + BASE + "},\"market\":{\"ledger\":{\"day\":4,\"by_merchant\":[{\"villager\":\"" + W1 + "\",\"sold\":3},"
				+ "{\"villager\":\"not-a-uuid\",\"sold\":1},{\"villager\":\"" + W2 + "\",\"sold\":-1}]}}}";
		WorldClaim w = dec(VillageCodecs.WORLD_CLAIM, json(claim));
		assertTrue(w.village().isPresent());
		assertEquals(Map.of(W1, 3), w.market().ledger().byMerchant());
		assertEquals(4, w.market().ledger().day());
	}

	@Test void aBrokenMarketReadsAsAbsentAndNeverLosesTheClaim() {
		String claim = "{\"schema\":1,\"village\":{" + BASE + "},\"market\":{\"ledger\":{\"day\":\"x\"}}}";
		WorldClaim w = dec(VillageCodecs.WORLD_CLAIM, json(claim));
		assertTrue(w.village().isPresent());
		assertEquals(dev.villageheart.core.market.MarketState.initial(), w.market());
		WorldClaim w2 = dec(VillageCodecs.WORLD_CLAIM, json("{\"schema\":1,\"village\":{" + BASE + "},\"market\":5}"));
		assertTrue(w2.village().isPresent());
	}

	// ---- self-crafts (M19a) ----------------------------------------------------------------------------------------------

	static final String CRAFT_BUILDING = "{\"dim\":\"minecraft:overworld\",\"x\":1,\"y\":2,\"z\":3}";

	static String craft(String recipe, String need, int progress) {
		return "{\"worker\":\"22222222-2222-2222-2222-222222222222\",\"recipe\":\"" + recipe + "\",\"building\":" + CRAFT_BUILDING
				+ ",\"need\":\"" + need + "\",\"slot\":1,\"progress\":" + progress + ",\"committed\":[{\"id\":\"minecraft:cobblestone\",\"count\":3},"
				+ "{\"id\":\"minecraft:stick\",\"count\":2}]}";
	}

	static dev.villageheart.core.gear.SelfCraft loadCraft(String json) {
		return dec(VillageCodecs.SELF_CRAFT, json(json));
	}

	@Test void selfCraftsRoundTripWithNamesNotOrdinals() {
		VillageState v = full().withSelfCraft(loadCraft(craft("minecraft:stone_pickaxe", "pickaxe", 1000)));
		assertEquals(v, dec(VillageCodecs.VILLAGE_STATE, enc(VillageCodecs.VILLAGE_STATE, v)));
		JsonObject c = enc(VillageCodecs.SELF_CRAFT, v.selfCrafts().get(0)).getAsJsonObject();
		assertEquals("pickaxe", c.get("need").getAsString());
		assertEquals("minecraft:stone_pickaxe", c.get("recipe").getAsString());
		assertEquals(1000, c.get("progress").getAsInt());
		assertEquals(1, c.get("slot").getAsInt());
		assertEquals(2, c.get("committed").getAsJsonArray().size());
		assertTrue(keys(enc(VillageCodecs.VILLAGE_STATE, v)).contains("self_crafts"));
	}

	@Test void progressAboveTheRecipeIsClamped() {
		dev.villageheart.core.gear.SelfCraft s = loadCraft(craft("minecraft:stone_pickaxe", "pickaxe", 99_999));
		assertEquals(4800, s.craft().progress());
		assertFalse(s.refundPending());
	}

	@Test void negativeProgressOrAnUnknownRecipeIsRefundedNotDropped() {
		dev.villageheart.core.gear.SelfCraft neg = loadCraft(craft("minecraft:stone_pickaxe", "pickaxe", -1));
		assertTrue(neg.refundPending());
		assertEquals(0, neg.craft().progress());
		assertEquals(2, neg.craft().committed().size());
		dev.villageheart.core.gear.SelfCraft unknown = loadCraft(craft("minecraft:netherite_axe", "axe", 10));
		assertTrue(unknown.refundPending());
		assertEquals(3, unknown.craft().committed().get(0).count());
		assertTrue(loadCraft(craft("minecraft:stone_pickaxe", "banana", 10)).refundPending());
	}

	@Test void oldSavesWithoutSelfCraftsLoadEmpty() {
		assertTrue(dec(VillageCodecs.VILLAGE_STATE, json("{" + BASE + "}")).selfCrafts().isEmpty());
	}

	@Test void twoRecordsForOneWorkerKeepTheFirst() {
		VillageState v = dec(VillageCodecs.VILLAGE_STATE, json("{" + BASE + ",\"self_crafts\":[" + craft("minecraft:stone_axe", "axe", 5) + ","
				+ craft("minecraft:wooden_axe", "axe", 9) + "]}"));
		assertEquals(1, v.selfCrafts().size());
		assertEquals("minecraft:stone_axe", v.selfCrafts().get(0).craft().recipeId());
	}

	/** Death log (M25, the save half of T-L1-7). A missing key is empty; 40 rows on disk keep the newest 32; a bad value keeps the village. */
	@Test void deathLogRoundTripCapAndABadValue() {
		assertEquals(dev.villageheart.core.death.DeathLog.EMPTY, dec(VillageCodecs.VILLAGE_STATE, json("{" + BASE + "}")).deaths());
		UUID who = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
		dev.villageheart.core.death.DeathLog log = dev.villageheart.core.death.DeathLog.EMPTY
				.append(new dev.villageheart.core.death.DeathEntry(0, who, "Ada", "farmer", "minecraft:zombie", true, 100, 1, true, true, false));
		VillageState saved = full().withDeaths(log);
		assertEquals(saved, dec(VillageCodecs.VILLAGE_STATE, enc(VillageCodecs.VILLAGE_STATE, saved)));
		assertTrue(keys(enc(VillageCodecs.VILLAGE_STATE, saved)).contains("death_log"));
		assertFalse(keys(enc(VillageCodecs.VILLAGE_STATE, full())).contains("death_log"));

		StringBuilder entries = new StringBuilder();
		for (int i = 0; i < 40; i++) {
			if (i > 0) entries.append(',');
			entries.append("{\"seq\":").append(i).append(",\"villager\":\"").append(who)
					.append("\",\"name\":\"n\",\"job\":\"farmer\",\"cause\":\"minecraft:fall\",\"by_mob\":false,\"game_time\":")
					.append(i).append(",\"day\":1,\"in_claim\":true}");
		}
		VillageState capped = dec(VillageCodecs.VILLAGE_STATE, json("{" + BASE + ",\"death_log\":{\"entries\":[" + entries
				+ "],\"next_seq\":40,\"in_claim_logged\":40}}"));
		assertEquals(32, capped.deaths().entries().size());
		assertEquals(8, capped.deaths().entries().get(0).seq());
		assertEquals(39, capped.deaths().entries().get(31).seq());
		assertEquals(40, capped.deaths().nextSeq());
		assertEquals(40, capped.deaths().inClaimLogged());
		assertEquals(OWNER, capped.owner());

		VillageState dropped = dec(VillageCodecs.VILLAGE_STATE, json("{" + BASE + ",\"death_log\":[]}"));
		assertEquals(dev.villageheart.core.death.DeathLog.EMPTY, dropped.deaths());
		assertEquals("a", dropped.ownerName());

		VillageState oneBad = dec(VillageCodecs.VILLAGE_STATE, json("{" + BASE + ",\"death_log\":{\"entries\":["
				+ "{\"seq\":\"nope\"},{\"seq\":3,\"villager\":\"" + who + "\",\"cause\":\"minecraft:fall\",\"game_time\":9}"
				+ "],\"next_seq\":4,\"in_claim_logged\":1}}"));
		assertEquals(1, oneBad.deaths().entries().size());
		assertEquals(3, oneBad.deaths().entries().get(0).seq());
		assertEquals(4, oneBad.deaths().nextSeq());
	}

	/** Raid and last raid (M25, T-L1-7). A missing key is empty; a bad raid drops the raid and keeps the village. */
	@Test void raidRoundTripAndABadRaid() {
		assertEquals(dev.villageheart.core.raid.ThreatState.EMPTY, dec(VillageCodecs.VILLAGE_STATE, json("{" + BASE + "}")).threats());
		assertFalse(keys(enc(VillageCodecs.VILLAGE_STATE, full())).contains("threats"));

		var plan = new dev.villageheart.core.raid.RaidPlan(2, 4, 1, 0, false);
		var record = new dev.villageheart.core.raid.RaidRecord(7, dev.villageheart.core.raid.RaidOrigin.BAD_OMEN, plan, 500L, 3, 12,
				4, 4, 1, 0, 6, 2, 4, false, 1, 5, 4, 0, 80L);
		var summary = new dev.villageheart.core.raid.RaidSummary(3, 500L, 900L, dev.villageheart.core.raid.RaidOutcome.VICTORY,
				dev.villageheart.core.raid.RaidOrigin.RANDOM, 2, 2, 12, 11, 1, 9, 8);
		VillageState saved = full().withRaid(java.util.Optional.of(record)).withLastRaid(java.util.Optional.of(summary));
		assertEquals(saved, dec(VillageCodecs.VILLAGE_STATE, enc(VillageCodecs.VILLAGE_STATE, saved)));
		com.google.gson.JsonObject encoded = enc(VillageCodecs.VILLAGE_STATE, saved).getAsJsonObject();
		assertTrue(encoded.has("threats"), "threats is a top-level key");
		com.google.gson.JsonObject raid = encoded.getAsJsonObject("threats").getAsJsonObject("raid");
		assertEquals(7, raid.get("vanilla_id").getAsInt());
		assertEquals("bad_omen", raid.get("origin").getAsString());
		assertEquals(2, raid.getAsJsonObject("plan").get("omen").getAsInt());
		assertEquals(4, raid.getAsJsonObject("plan").get("extra_vindicators").getAsInt());
		assertTrue(raid.has("waves_total"), "the raid object is flat, not split into two compounds");
		assertEquals("victory", encoded.getAsJsonObject("threats").getAsJsonObject("last_raid").get("outcome").getAsString());

		VillageState dropped = dec(VillageCodecs.VILLAGE_STATE, json("{" + BASE + ",\"threats\":{\"raid\":[],\"last_raid\":{\"outcome\":\"victory\",\"origin\":\"random\"}}}"));
		assertTrue(dropped.threats().raid().isEmpty(), "a bad raid is dropped");
		assertEquals(dev.villageheart.core.raid.RaidOutcome.VICTORY, dropped.threats().lastRaid().orElseThrow().outcome());
		assertEquals("a", dropped.ownerName());

		VillageState badOutcome = dec(VillageCodecs.VILLAGE_STATE, json("{" + BASE + ",\"threats\":{\"last_raid\":{\"outcome\":\"drawn\",\"origin\":\"test\"}}}"));
		assertTrue(badOutcome.threats().lastRaid().isEmpty());
		assertEquals("a", badOutcome.ownerName());

		VillageState badThreats = dec(VillageCodecs.VILLAGE_STATE, json("{" + BASE + ",\"threats\":[]}"));
		assertEquals(dev.villageheart.core.raid.ThreatState.EMPTY, badThreats.threats());
		assertEquals("a", badThreats.ownerName());

		VillageState badPlan = dec(VillageCodecs.VILLAGE_STATE, json("{" + BASE
				+ ",\"threats\":{\"raid\":{\"vanilla_id\":1,\"origin\":\"test\",\"plan\":{\"omen\":9}}}}"));
		assertTrue(badPlan.threats().raid().isEmpty());
		assertEquals("a", badPlan.ownerName());
	}
}
