package dev.villageheart.world;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.blueprint.BlueprintParams;
import dev.villageheart.core.blueprint.BuildJob;
import dev.villageheart.core.blueprint.Rotation4;
import dev.villageheart.core.defence.DefenceSite;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** T-L1-7 */
class VillageStateDefencesTest {
	private static final String DIM = "minecraft:overworld";
	private static final HeartLocation HEART = new HeartLocation(DIM, 0, 64, 0);
	private static final UUID PLAYER = new UUID(0, 7);

	private static VillageState base() { return VillageState.founded(HEART, new UUID(0, 1), "o", 0); }

	private static BuildJob wall(int n, int length, int cursor) {
		return new BuildJob(new UUID(5, n), "wall_segment", new BlueprintParams(length), new HeartLocation(DIM, 10 * n, 64, 3), Rotation4.CLOCKWISE_180,
				100L * n, PLAYER, cursor, Map.of(), null);
	}

	private static VillageState roundTrip(VillageState v) {
		return VillageCodecs.VILLAGE_STATE.parse(JsonOps.INSTANCE, VillageCodecs.VILLAGE_STATE.encodeStart(JsonOps.INSTANCE, v).getOrThrow()).getOrThrow();
	}

	@Test
	void aWallBuildAndTwoDefenceSitesRoundTrip() {
		DefenceSite lamp = new DefenceSite(new UUID(1, 1), "lamp_post", new HeartLocation(DIM, 5, 64, 6), Rotation4.CLOCKWISE_90, 77L);
		DefenceSite tower = new DefenceSite(new UUID(1, 2), "watchtower", new HeartLocation(DIM, 9, 64, 6), Rotation4.NONE, 99L);
		VillageState v = base().withBuild(wall(1, 20, 70)).withDefence(lamp).withDefence(tower);
		VillageState back = roundTrip(v);
		assertEquals(v, back);
		assertEquals(20, back.builds().get(0).params().length());
		assertEquals(70, back.builds().get(0).cursor());
		assertEquals(List.of(lamp, tower), back.defences());
		JsonObject o = VillageCodecs.VILLAGE_STATE.encodeStart(JsonOps.INSTANCE, v).getOrThrow().getAsJsonObject();
		assertEquals(20, o.getAsJsonArray("builds").get(0).getAsJsonObject().get("length").getAsInt());
		assertEquals(java.util.Set.of("id", "blueprint", "ground", "rotation", "built_at"),
				o.getAsJsonArray("defences").get(0).getAsJsonObject().keySet());
	}

	@Test
	void aBadLengthIsDroppedAndTheVillageLoads() {
		VillageState v = base().withBuild(wall(1, 20, 0)).withBuild(wall(2, 20, 0)).withBuild(wall(3, 20, 0));
		JsonObject o = VillageCodecs.VILLAGE_STATE.encodeStart(JsonOps.INSTANCE, v).getOrThrow().getAsJsonObject();
		JsonArray arr = o.getAsJsonArray("builds");
		arr.get(0).getAsJsonObject().addProperty("length", 33);
		arr.get(1).getAsJsonObject().remove("length"); // 0
		VillageState back = VillageCodecs.VILLAGE_STATE.parse(JsonOps.INSTANCE, o).getOrThrow();
		assertEquals(List.of(wall(3, 20, 0)), back.builds());
	}

	@Test
	void aVillageSavedBeforeM21HasNoDefences() {
		DefenceSite lamp = new DefenceSite(new UUID(1, 1), "lamp_post", new HeartLocation(DIM, 5, 64, 6), Rotation4.NONE, 1L);
		JsonObject o = VillageCodecs.VILLAGE_STATE.encodeStart(JsonOps.INSTANCE, base().withDefence(lamp)).getOrThrow().getAsJsonObject();
		assertTrue(o.has("defences"));
		o.remove("defences");
		VillageState back = VillageCodecs.VILLAGE_STATE.parse(JsonOps.INSTANCE, o).getOrThrow();
		assertTrue(back.defences().isEmpty());
		assertFalse(VillageCodecs.VILLAGE_STATE.encodeStart(JsonOps.INSTANCE, base()).getOrThrow().getAsJsonObject().has("defences"));
	}

	@Test
	void aBrokenSiteIsDroppedNotFatal() {
		DefenceSite lamp = new DefenceSite(new UUID(1, 1), "lamp_post", new HeartLocation(DIM, 5, 64, 6), Rotation4.NONE, 1L);
		JsonObject o = VillageCodecs.VILLAGE_STATE.encodeStart(JsonOps.INSTANCE, base().withDefence(lamp)).getOrThrow().getAsJsonObject();
		o.getAsJsonArray("defences").get(0).getAsJsonObject().remove("ground");
		assertTrue(VillageCodecs.VILLAGE_STATE.parse(JsonOps.INSTANCE, o).getOrThrow().defences().isEmpty());
	}
}
