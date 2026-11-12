package dev.villageheart.world;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.villageheart.core.WorldClaim;
import dev.villageheart.core.growth.BreedingPair;
import dev.villageheart.core.growth.FailedPair;
import dev.villageheart.core.growth.GrowthState;
import dev.villageheart.core.growth.LapseReason;
import dev.villageheart.core.growth.License;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** T-L1-6 (M23 plan 5.1). */
class GrowthCodecsTest {
	static final UUID A = new UUID(1, 1), B = new UUID(1, 2), C = new UUID(1, 3), CHILD = new UUID(9, 9);
	static final BreedingPair PAIR = BreedingPair.of(A, B);

	static JsonElement enc(GrowthState g) { return GrowthCodecs.GROWTH.encodeStart(JsonOps.INSTANCE, g).getOrThrow(); }

	static GrowthState dec(JsonElement e) { return GrowthCodecs.GROWTH.parse(JsonOps.INSTANCE, e).getOrThrow(); }

	static GrowthState full() {
		License l = License.issue(12, PAIR, OptionalLong.of(4242)).approaching().seeded(4242, 7, 9).handOver().aborted().seeded(4243, 12, 12);
		return new GrowthState(Optional.of(l), OptionalLong.of(11), 5, List.of(new FailedPair(BreedingPair.of(A, C), 10)));
	}

	@Test void fullStateRoundTrips() {
		GrowthState g = full();
		assertEquals(g, dec(enc(g)));
		License born = full().licence().get().born(CHILD);
		GrowthState b = full().afterBirth(12, born);
		assertEquals(b, dec(enc(b)));
		assertEquals(OptionalLong.of(12), dec(enc(b)).lastBirthDay());
	}

	@Test void emptyStateRoundTrips() {
		assertEquals(GrowthState.EMPTY, dec(enc(GrowthState.EMPTY)));
	}

	@Test void absentKeysAreAnEmptyState() {
		assertEquals(GrowthState.EMPTY, dec(new JsonObject()));
	}

	@Test void pinnedKeys() {
		JsonObject o = enc(full()).getAsJsonObject();
		assertEquals(Set.of("licence", "last_birth_day", "births_total", "failed_pairs"), o.keySet());
		// defaults (no hand-over, 0 approach ticks) are not written
		assertEquals(Set.of("day", "a", "b", "state", "seeds", "aborts", "bed", "prior_food_a", "prior_food_b"), o.getAsJsonObject("licence").keySet());
		License l = License.issue(1, PAIR, OptionalLong.of(5)).approaching().tickApproach().seeded(5, 1, 2).handOver();
		assertEquals(Set.of("day", "a", "b", "state", "seeds", "bed", "bed_handed_over", "prior_food_a", "prior_food_b", "approach_ticks"),
				enc(GrowthState.EMPTY.withLicence(l)).getAsJsonObject().getAsJsonObject("licence").keySet());
		assertEquals("SEEDED", o.getAsJsonObject("licence").get("state").getAsString());
	}

	@Test void inconsistentLicenceIsDroppedAndTheRestKept() {
		JsonObject o = enc(full()).getAsJsonObject();
		o.getAsJsonObject("licence").addProperty("state", "BORN"); // BORN without a child
		GrowthState g = dec(o);
		assertTrue(g.licence().isEmpty());
		assertEquals(5, g.birthsTotal());
		assertEquals(OptionalLong.of(11), g.lastBirthDay());
		assertEquals(1, g.failedPairs().size());
	}

	@Test void unknownStateIsDropped() {
		JsonObject o = enc(full()).getAsJsonObject();
		o.getAsJsonObject("licence").addProperty("state", "DANCING");
		assertTrue(dec(o).licence().isEmpty());
	}

	@Test void lapsedLicenceKeepsItsReason() {
		License l = License.issue(3, PAIR, OptionalLong.empty()).lapsed(LapseReason.CANNOT_MEET);
		GrowthState g = GrowthState.EMPTY.withLicence(l);
		assertEquals(g, dec(enc(g)));
	}

	@Test void worldClaimWritesGrowthOnlyWhenThereIsSome() {
		WorldClaim w = new WorldClaim();
		JsonObject o = VillageCodecs.WORLD_CLAIM.encodeStart(JsonOps.INSTANCE, w).getOrThrow().getAsJsonObject();
		assertFalse(o.has("growth"), "an old-looking save stays as it was");
		w.setGrowth(full());
		JsonElement e = VillageCodecs.WORLD_CLAIM.encodeStart(JsonOps.INSTANCE, w).getOrThrow();
		assertTrue(e.getAsJsonObject().has("growth"));
		WorldClaim back = VillageCodecs.WORLD_CLAIM.parse(JsonOps.INSTANCE, e).getOrThrow();
		assertEquals(full(), back.growth());
		assertEquals(w, back);
		// a save from before M23 reads as no growth
		assertEquals(GrowthState.EMPTY, VillageCodecs.WORLD_CLAIM.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"schema\":1}")).getOrThrow().growth());
	}

	@Test void releasingTheClaimForgetsGrowth() {
		WorldClaim w = new WorldClaim();
		w.setGrowth(full());
		w.clear();
		assertEquals(GrowthState.EMPTY, w.growth());
	}
}
