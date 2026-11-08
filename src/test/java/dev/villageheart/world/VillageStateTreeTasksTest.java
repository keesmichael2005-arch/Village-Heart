package dev.villageheart.world;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.TreeTask;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.room.Pos;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VillageStateTreeTasksTest {
	static final String DIM = "minecraft:overworld";
	static final HeartLocation HEART = new HeartLocation(DIM, 0, 64, 0);
	static final HeartLocation CAMP = new HeartLocation(DIM, 20, 64, 0);
	static final HeartLocation CAMP2 = new HeartLocation(DIM, 40, 64, 0);

	static HeartLocation cell(int x, int z) { return new HeartLocation(DIM, x, 64, z); }

	static TreeTask felling(HeartLocation camp, int x, int z) {
		return new TreeTask(camp, TreeTask.Stage.FELLING, List.of(cell(x, z)), "minecraft:oak_sapling", 100L, new Pos(x - 2, 64, z - 2), new Pos(x + 2, 72, z + 2));
	}

	static TreeTask replant(HeartLocation camp, int x, int z) {
		return new TreeTask(camp, TreeTask.Stage.REPLANT, List.of(cell(x, z), cell(x + 1, z), cell(x, z + 1), cell(x + 1, z + 1)),
				"minecraft:dark_oak_sapling", 200L, new Pos(x - 2, 64, z - 2), new Pos(x + 3, 72, z + 3));
	}

	static VillageState base() { return VillageState.founded(HEART, new UUID(0, 1), "o", 0); }

	@Test
	void roundTripBothStages() {
		VillageState v = base().withTreeTask(felling(CAMP, 25, 3)).withTreeTask(replant(CAMP, 30, 8));
		VillageState back = VillageCodecs.VILLAGE_STATE.parse(JsonOps.INSTANCE, VillageCodecs.VILLAGE_STATE.encodeStart(JsonOps.INSTANCE, v).getOrThrow()).getOrThrow();
		assertEquals(v, back);
		assertEquals(2, back.treeTasks().size());
	}

	@Test
	void pinnedKeys() {
		VillageState v = base().withTreeTask(felling(CAMP, 25, 3));
		JsonObject o = VillageCodecs.VILLAGE_STATE.encodeStart(JsonOps.INSTANCE, v).getOrThrow().getAsJsonObject();
		assertTrue(o.has("tree_tasks"));
		JsonObject t = o.getAsJsonArray("tree_tasks").get(0).getAsJsonObject();
		assertEquals(java.util.Set.of("building", "stage", "base", "sapling", "since", "box_min", "box_max"), t.keySet());
		assertEquals("felling", t.get("stage").getAsString());
	}

	@Test
	void aVillageWithoutTreeTasksReadsEmpty() {
		VillageState v = base();
		JsonObject o = VillageCodecs.VILLAGE_STATE.encodeStart(JsonOps.INSTANCE, v).getOrThrow().getAsJsonObject();
		o.remove("tree_tasks");
		VillageState back = VillageCodecs.VILLAGE_STATE.parse(JsonOps.INSTANCE, o).getOrThrow();
		assertEquals(List.of(), back.treeTasks());
	}

	@Test
	void anUnknownStageIsADataError() {
		VillageState v = base().withTreeTask(felling(CAMP, 25, 3));
		JsonObject o = VillageCodecs.VILLAGE_STATE.encodeStart(JsonOps.INSTANCE, v).getOrThrow().getAsJsonObject();
		o.getAsJsonArray("tree_tasks").get(0).getAsJsonObject().addProperty("stage", "chopping");
		assertTrue(VillageCodecs.VILLAGE_STATE.parse(JsonOps.INSTANCE, o).error().isPresent());
	}

	@Test
	void twoSavedTasksSharingACellKeepTheFirstAndTheVillageStillLoads() {
		VillageState v = base().withTreeTask(felling(CAMP, 25, 3));
		JsonObject o = VillageCodecs.VILLAGE_STATE.encodeStart(JsonOps.INSTANCE, v).getOrThrow().getAsJsonObject();
		JsonElement dup = o.getAsJsonArray("tree_tasks").get(0).deepCopy();
		dup.getAsJsonObject().addProperty("sapling", "minecraft:birch_sapling");
		o.getAsJsonArray("tree_tasks").add(dup);
		VillageState back = VillageCodecs.VILLAGE_STATE.parse(JsonOps.INSTANCE, o).getOrThrow();
		assertEquals(1, back.treeTasks().size());
		assertEquals("minecraft:oak_sapling", back.treeTasks().get(0).sapling());
	}

	@Test
	void withTreeTaskRefusesASharedCell() {
		VillageState v = base().withTreeTask(felling(CAMP, 25, 3));
		assertThrows(IllegalArgumentException.class, () -> v.withTreeTask(felling(CAMP2, 25, 3)));
		assertThrows(IllegalArgumentException.class, () -> v.withTreeTask(replant(CAMP, 24, 2)));
		assertEquals(1, v.withTreeTask(felling(CAMP, 26, 3)).treeTasks().size() - 1);
	}

	@Test
	void opsKeepOrderAndFindByKeyAndBuilding() {
		VillageState v = base().withTreeTask(felling(CAMP, 25, 3)).withTreeTask(felling(CAMP2, 45, 3)).withTreeTask(felling(CAMP, 30, 3));
		assertEquals(List.of(cell(25, 3), cell(45, 3), cell(30, 3)), v.treeTasks().stream().map(TreeTask::key).toList());
		assertEquals(2, v.treeTasksAt(CAMP).size());
		assertEquals(Optional.of(felling(CAMP2, 45, 3)), v.treeTask(cell(45, 3)));
		VillageState w = v.withoutTreeTask(cell(45, 3));
		assertEquals(List.of(cell(25, 3), cell(30, 3)), w.treeTasks().stream().map(TreeTask::key).toList());
		assertSame(w, w.withoutTreeTask(cell(45, 3)));
	}

	@Test
	void otherOpsKeepTheTasks() {
		VillageState v = base().withTreeTask(felling(CAMP, 25, 3));
		assertEquals(1, v.withTier(dev.villageheart.core.Tier.VILLAGE).treeTasks().size());
		assertEquals(1, v.depositTreasury(5).treeTasks().size());
		assertEquals(1, v.withRank(new UUID(0, 9), dev.villageheart.core.Rank.RESIDENT).treeTasks().size());
	}

	@Test
	void aTaskNeedsABaseCell() {
		assertThrows(IllegalArgumentException.class, () -> new TreeTask(CAMP, TreeTask.Stage.REPLANT, List.of(), "x", 0, new Pos(0, 0, 0), new Pos(1, 1, 1)));
	}
}
