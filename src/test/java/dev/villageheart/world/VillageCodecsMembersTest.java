package dev.villageheart.world;

import static dev.villageheart.world.VillageCodecsTest.*;
import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.villageheart.core.Balance;
import dev.villageheart.core.KnownPlayer;
import dev.villageheart.core.Rank;
import dev.villageheart.core.VillageState;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The name book in the save format (M24). */
class VillageCodecsMembersTest {
	static UUID uuid(int n) { return new UUID(0L, n); }

	@Test void knownPlayersPinnedKeysAndRoundTrip() {
		JsonObject root = enc(VillageCodecs.VILLAGE_STATE, full()).getAsJsonObject();
		JsonObject first = root.getAsJsonArray("known_players").get(0).getAsJsonObject();
		assertEquals(Set.of("id", "name", "last_seen"), first.keySet());
		assertEquals("stew", first.get("name").getAsString());
		assertEquals(full(), dec(VillageCodecs.VILLAGE_STATE, root));
	}

	@Test void preM24SaveGetsPlaceholderNames() {
		VillageState v = dec(VillageCodecs.VILLAGE_STATE, json("{" + BASE
				+ ",\"ranks\":{\"22222222-2222-2222-2222-222222222222\":\"steward\"}}"));
		assertEquals(Rank.STEWARD, v.rankOf(STEW));
		assertEquals(1, v.known().size());
		assertEquals("", v.name(STEW));
		assertEquals(0, v.known().get(0).lastSeen());
	}

	@Test void oneBadKnownEntryDoesNotLoseTheVillage() {
		VillageState v = dec(VillageCodecs.VILLAGE_STATE, json("{" + BASE + ",\"known_players\":["
				+ "{\"id\":\"nope\",\"name\":\"x\",\"last_seen\":1},"
				+ "{\"id\":\"33333333-3333-3333-3333-333333333333\",\"name\":\"ok\",\"last_seen\":5}]}"));
		assertEquals(List.of(new KnownPlayer(RES, "ok", 5)), v.known());
	}

	@Test void ownerAndDuplicateEntriesAreDropped() {
		VillageState v = dec(VillageCodecs.VILLAGE_STATE, json("{" + BASE + ",\"known_players\":["
				+ "{\"id\":\"11111111-1111-1111-1111-111111111111\",\"name\":\"me\",\"last_seen\":1},"
				+ "{\"id\":\"33333333-3333-3333-3333-333333333333\",\"name\":\"first\",\"last_seen\":5},"
				+ "{\"id\":\"33333333-3333-3333-3333-333333333333\",\"name\":\"second\",\"last_seen\":9}]}"));
		assertEquals(List.of(new KnownPlayer(RES, "first", 5)), v.known());
	}

	private static String knownJson(int count) {
		StringBuilder known = new StringBuilder("[");
		for (int i = 1; i <= count; i++) {
			if (i > 1) known.append(',');
			known.append("{\"id\":\"").append(uuid(i)).append("\",\"name\":\"p").append(i).append("\",\"last_seen\":").append(i).append('}');
		}
		return known.append(']').toString();
	}

	@Test void seventyEntriesLoadTrimmedToTheCapKeepingRankedOnes() {
		// player 1 is the oldest and ranked: it must survive although it is the oldest entry
		String json = "{" + BASE + ",\"ranks\":{\"" + uuid(1) + "\":\"resident\"},\"known_players\":" + knownJson(70) + "}";
		VillageState v = dec(VillageCodecs.VILLAGE_STATE, json(json));
		assertEquals(Balance.MAX_KNOWN_PLAYERS, v.known().size());
		assertTrue(v.knownPlayer(uuid(1)).isPresent());
		assertTrue(v.knownPlayer(uuid(70)).isPresent(), "newest unranked kept");
		assertTrue(v.knownPlayer(uuid(2)).isEmpty(), "oldest unranked dropped");
		assertEquals(Rank.RESIDENT, v.rankOf(uuid(1)));
	}

	@Test void seventyRankedEntriesKeepTheSameSixtyThreeEveryRun() {
		StringBuilder ranks = new StringBuilder("{");
		for (int i = 1; i <= 70; i++) {
			if (i > 1) ranks.append(',');
			// the five oldest are Stewards, the rest Residents
			ranks.append('"').append(uuid(i)).append("\":\"").append(i <= 5 ? "steward" : "resident").append('"');
		}
		ranks.append('}');
		String json = "{" + BASE + ",\"ranks\":" + ranks + ",\"known_players\":" + knownJson(70) + "}";
		VillageState a = dec(VillageCodecs.VILLAGE_STATE, JsonParser.parseString(json));
		VillageState b = dec(VillageCodecs.VILLAGE_STATE, JsonParser.parseString(json));
		assertEquals(a, b);
		assertEquals(63, a.ranks().size());
		for (int i = 1; i <= 5; i++) assertEquals(Rank.STEWARD, a.rankOf(uuid(i)), "stewards first although oldest");
		// of the 65 residents the 58 newest are kept: 70 down to 13
		assertEquals(Rank.RESIDENT, a.rankOf(uuid(70)));
		assertEquals(Rank.RESIDENT, a.rankOf(uuid(13)));
		assertEquals(Rank.VISITOR, a.rankOf(uuid(12)));
		assertEquals(64, a.known().size(), "one demoted player stays in the book while there is room");
	}

	@Test void formattingCodesAreStrippedFromNames() {
		VillageState v = dec(VillageCodecs.VILLAGE_STATE, json("{" + BASE + ",\"known_players\":["
				+ "{\"id\":\"33333333-3333-3333-3333-333333333333\",\"name\":\"§cRed§r\",\"last_seen\":5}]}"));
		assertEquals("cRedr", v.name(RES));
	}
}
