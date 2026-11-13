package dev.villageheart.core.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.WorkerRecord;
import dev.villageheart.core.worker.Needs;
import dev.villageheart.core.worker.WorkerData;
import dev.villageheart.world.VillageCodecs;
import dev.villageheart.world.WorkerCodecs;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The chest link is an optional field. A save from before pockets still loads, and a linked chest round-trips. */
class WorkerChestSaveTest {
	static final HeartLocation B = new HeartLocation("minecraft:overworld", 0, 64, 0);
	static final HeartLocation J = new HeartLocation("minecraft:overworld", 1, 65, 1);
	static final HeartLocation C = new HeartLocation("minecraft:overworld", 4, 64, 3);
	static final String OLD = "{\"job\":\"farmer\",\"building\":{\"dim\":\"minecraft:overworld\",\"x\":0,\"y\":64,\"z\":0},"
			+ "\"job_block\":{\"dim\":\"minecraft:overworld\",\"x\":1,\"y\":65,\"z\":1}}";

	@Test
	void anOldAttachmentLoadsWithNoChest() {
		WorkerData d = WorkerCodecs.WORKER_DATA.parse(JsonOps.INSTANCE, JsonParser.parseString(OLD)).getOrThrow();
		assertEquals(Optional.empty(), d.chest());
		assertEquals(1, d.level());
		JsonObject saved = WorkerCodecs.WORKER_DATA.encodeStart(JsonOps.INSTANCE, d).getOrThrow().getAsJsonObject();
		assertFalse(saved.has("chest"), "an empty chest is left out, so the old key set stays");
	}

	@Test
	void aLinkedChestRoundTripsOnTheAttachmentAndTheRecord() {
		WorkerData d = new WorkerData("lumberjack", B, J, 2, 40, Needs.DEFAULT).withChest(C);
		assertEquals(d, WorkerCodecs.WORKER_DATA.parse(JsonOps.INSTANCE, WorkerCodecs.WORKER_DATA.encodeStart(JsonOps.INSTANCE, d).getOrThrow()).getOrThrow());
		assertTrue(WorkerCodecs.WORKER_DATA.encodeStart(JsonOps.INSTANCE, d).getOrThrow().getAsJsonObject().has("chest"));

		WorkerRecord r = new WorkerRecord(new UUID(0, 7), "lumberjack", B, J, 2).withChest(C);
		WorkerRecord back = VillageCodecs.WORKER.parse(JsonOps.INSTANCE, VillageCodecs.WORKER.encodeStart(JsonOps.INSTANCE, r).getOrThrow()).getOrThrow();
		assertEquals(Optional.of(C), back.chest());
		assertEquals(r, back);

		String oldRecord = "{\"villager\":\"" + r.villager() + "\",\"job\":\"lumberjack\","
				+ "\"building\":{\"dim\":\"minecraft:overworld\",\"x\":0,\"y\":64,\"z\":0},"
				+ "\"job_block\":{\"dim\":\"minecraft:overworld\",\"x\":1,\"y\":65,\"z\":1}}";
		WorkerRecord loaded = VillageCodecs.WORKER.parse(JsonOps.INSTANCE, JsonParser.parseString(oldRecord)).getOrThrow();
		assertEquals(Optional.empty(), loaded.chest());
		assertEquals(1, loaded.level());
	}
}
