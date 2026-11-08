package dev.villageheart.world;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.mine.AwayProgress;
import dev.villageheart.core.mine.AwayState;
import dev.villageheart.core.mine.AwayState.AwayTrip;
import dev.villageheart.core.mine.AwayState.PriorFlags;
import dev.villageheart.core.mine.SurfaceReason;
import dev.villageheart.core.room.Pos;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AwayStateCodecTest {
	static final HeartLocation SITE = new HeartLocation("minecraft:overworld", 10, 64, 20);
	static final AwayTrip TRIP = new AwayTrip("miner", SITE, new Pos(1, 64, 2), new Pos(3, 52, 4), 12345L, new PriorFlags(false, false, true, false, true));

	static JsonElement enc(AwayState s) { return AwayCodecs.AWAY_STATE.encodeStart(JsonOps.INSTANCE, s).getOrThrow(); }

	static AwayState dec(JsonElement e) { return AwayCodecs.AWAY_STATE.parse(JsonOps.INSTANCE, e).getOrThrow(); }

	@Test
	void roundTripWithATripAndPending() {
		AwayState s = new AwayState(Optional.of(TRIP), new AwayProgress(1234.5), Optional.of(SITE), 7, Map.of("minecraft:coal", 2, "minecraft:cobblestone", 16),
				Optional.of(SurfaceReason.DAY_END), 3, false);
		assertEquals(s, dec(enc(s)));
	}

	@Test
	void roundTripWithoutATrip() {
		AwayState s = new AwayState(Optional.empty(), new AwayProgress(10), Optional.empty(), 2, Map.of(), Optional.of(SurfaceReason.HAUL), 0, false);
		assertEquals(s, dec(enc(s)));
		assertEquals(AwayState.NONE, dec(enc(AwayState.NONE)));
	}

	@Test
	void pinnedFieldNames() {
		AwayTrip allSet = new AwayTrip("miner", SITE, new Pos(1, 64, 2), new Pos(3, 52, 4), 12345L, new PriorFlags(true, true, true, true, true));
		JsonObject o = enc(new AwayState(Optional.of(allSet), new AwayProgress(1), Optional.of(SITE), 1, Map.of("minecraft:coal", 1),
				Optional.of(SurfaceReason.NO_TOOL), 1, false)).getAsJsonObject();
		assertEquals(Set.of("trip", "progress", "progress_shaft", "rolls", "pending", "last_reason", "unknown_steps"), o.keySet());
		JsonObject trip = o.getAsJsonObject("trip");
		assertEquals(Set.of("job", "site", "surface", "hold", "since", "prior"), trip.keySet());
		assertEquals(Set.of("invisible", "invulnerable", "silent", "no_ai", "custom_name_visible"), trip.getAsJsonObject("prior").keySet());
		assertEquals("no_tool", o.get("last_reason").getAsString());
	}

	@Test
	void anEmptyObjectDecodesToTheDefaults() {
		assertEquals(AwayState.NONE, dec(JsonParser.parseString("{}")));
	}

	@Test
	void aBrokenTripDecodesAsNoTripWithTheBrokenMarker() {
		AwayState s = dec(JsonParser.parseString("{\"trip\":{\"job\":\"miner\",\"site\":\"nonsense\"},\"rolls\":4,\"progress\":12.0}"));
		assertTrue(s.trip().isEmpty());
		assertTrue(s.broken());
		assertEquals(4, s.rolls(), "the rest of the record survives");
		assertEquals(12.0, s.progress().units());
		assertEquals(PriorFlags.VANILLA, new PriorFlags(false, false, false, false, false));
		// the marker is never saved
		assertFalse(enc(s).getAsJsonObject().has("broken"));
		assertFalse(dec(enc(s)).broken());
	}

	@Test
	void negativeNumbersAreClampedNotFatal() {
		AwayState s = dec(JsonParser.parseString("{\"rolls\":-5,\"progress\":-1.0,\"unknown_steps\":-2}"));
		assertEquals(0, s.rolls());
		assertEquals(0.0, s.progress().units());
		assertEquals(0, s.unknownSteps());
	}

	@Test
	void anUnknownReasonFailsTheRecordLeniently() {
		// a wrong last_reason is an optional field: the whole record still must not throw
		assertDoesNotThrow(() -> AwayCodecs.AWAY_STATE.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"last_reason\":\"zzz\"}")));
	}
}
