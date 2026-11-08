package dev.villageheart.world;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.blueprint.BlueprintParams;
import dev.villageheart.core.blueprint.BuildJob;
import dev.villageheart.core.blueprint.BuildPlan;
import dev.villageheart.core.blueprint.Blueprints;
import dev.villageheart.core.blueprint.Placement;
import dev.villageheart.core.blueprint.Rotation4;
import dev.villageheart.core.room.Pos;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** T-L1-10 */
class VillageStateBuildsTest {
	private static final String DIM = "minecraft:overworld";
	private static final HeartLocation HEART = new HeartLocation(DIM, 0, 64, 0);
	private static final UUID PLAYER = new UUID(0, 7);

	private static VillageState base() { return VillageState.founded(HEART, new UUID(0, 1), "o", 0); }

	private static BuildJob job(int n, String blueprint, int cursor) {
		return new BuildJob(new UUID(5, n), blueprint, BlueprintParams.NONE, new HeartLocation(DIM, 10 * n, 64, 3), Rotation4.CLOCKWISE_90,
				100L * n, PLAYER, cursor, Map.of(), null);
	}

	private static VillageState roundTrip(VillageState v) {
		return VillageCodecs.VILLAGE_STATE.parse(JsonOps.INSTANCE, VillageCodecs.VILLAGE_STATE.encodeStart(JsonOps.INSTANCE, v).getOrThrow()).getOrThrow();
	}

	@Test
	void roundTripsAllFields() {
		BuildJob a = job(1, "small_house", 250).withCarried(Map.of("minecraft:oak_planks", 23, "minecraft:cobblestone", 5)).withCarrier(new UUID(9, 9));
		BuildJob b = job(2, "farm_plot", 0);
		VillageState v = base().withBuild(a).withBuild(b);
		VillageState back = roundTrip(v);
		assertEquals(v, back);
		assertEquals(List.of(a, b), back.builds());
		assertNull(back.builds().get(1).carrier());
		assertTrue(back.builds().get(1).carried().isEmpty());
		assertEquals(23, back.build(a.id()).orElseThrow().carriedOf("minecraft:oak_planks"));
	}

	@Test
	void pinnedKeys() {
		JsonObject o = VillageCodecs.VILLAGE_STATE.encodeStart(JsonOps.INSTANCE, base().withBuild(job(1, "small_house", 3)
				.withCarrier(new UUID(1, 1)).withCarried(Map.of("minecraft:torch", 1)))).getOrThrow().getAsJsonObject();
		JsonObject b = o.getAsJsonArray("builds").get(0).getAsJsonObject();
		assertEquals(java.util.Set.of("id", "blueprint", "ground", "rotation", "created_at", "started_by", "cursor", "carried", "carrier"),
				b.keySet());
		assertEquals("clockwise_90", b.get("rotation").getAsString());
		assertEquals(1, b.getAsJsonObject("carried").get("minecraft:torch").getAsInt());
	}

	@Test
	void oldDataWithoutBuildsLoadsEmpty() {
		JsonObject o = VillageCodecs.VILLAGE_STATE.encodeStart(JsonOps.INSTANCE, base().withBuild(job(1, "small_house", 0))).getOrThrow().getAsJsonObject();
		o.remove("builds");
		VillageState back = VillageCodecs.VILLAGE_STATE.parse(JsonOps.INSTANCE, o).getOrThrow();
		assertTrue(back.builds().isEmpty());
	}

	@Test
	void anUnknownBlueprintOrACursorPastTheEndIsDroppedAndTheRestLoads() {
		int steps = BuildPlan.of(Blueprints.SMALL_HOUSE, new Placement(new Pos(10, 64, 3), Rotation4.CLOCKWISE_90)).size();
		BuildJob good = job(1, "small_house", 12);
		BuildJob atEnd = job(2, "small_house", steps);
		VillageState v = base().withBuild(good).withBuild(atEnd).withBuild(job(3, "small_house", 0)).withBuild(job(4, "farm_plot", 0));
		JsonObject o = VillageCodecs.VILLAGE_STATE.encodeStart(JsonOps.INSTANCE, v).getOrThrow().getAsJsonObject();
		JsonArray arr = o.getAsJsonArray("builds");
		arr.get(2).getAsJsonObject().addProperty("blueprint", "castle");
		arr.get(3).getAsJsonObject().addProperty("cursor", 99999);
		VillageState back = VillageCodecs.VILLAGE_STATE.parse(JsonOps.INSTANCE, o).getOrThrow();
		assertEquals(List.of(good, atEnd), back.builds());
	}

	@Test
	void opsKeepOrderAndReplaceById() {
		BuildJob a = job(1, "small_house", 0), b = job(2, "storage_shed", 0), c = job(3, "farm_plot", 0);
		VillageState v = base().withBuild(a).withBuild(b).withBuild(c);
		assertEquals(List.of(a, b, c), v.builds());
		VillageState moved = v.withBuild(b.withCursor(40));
		assertEquals(List.of(a, b.withCursor(40), c), moved.builds());
		assertEquals(List.of(a, c), moved.withoutBuild(b.id()).builds());
		assertSame(moved, moved.withoutBuild(new UUID(0, 0)));
		assertTrue(v.build(new UUID(0, 0)).isEmpty());
		assertThrows(IllegalArgumentException.class, () -> new VillageState(HEART, new UUID(0, 1), "o", 0, Map.of(), List.of(), 0,
				dev.villageheart.core.Tier.HAMLET, List.of(), List.of(), List.of(), List.of(a, a)));
		// the other record parts survive a build change
		VillageState withTier = v.withTier(dev.villageheart.core.Tier.VILLAGE);
		assertEquals(3, withTier.builds().size());
		assertEquals(3, withTier.depositTreasury(5).builds().size());
	}

	@Test
	void carriedZeroesAreDropped() {
		BuildJob a = job(1, "small_house", 0).withCarried(Map.of("minecraft:oak_planks", 0, "minecraft:cobblestone", 2));
		assertEquals(Map.of("minecraft:cobblestone", 2), a.carried());
		assertThrows(IllegalArgumentException.class, () -> job(1, "small_house", 0).withCarried(Map.of("x", -1)));
	}
}
