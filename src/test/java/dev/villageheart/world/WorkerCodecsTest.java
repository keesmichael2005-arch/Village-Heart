package dev.villageheart.world;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.core.worker.Needs;
import dev.villageheart.core.worker.WorkerData;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WorkerCodecsTest {
	static final HeartLocation B = new HeartLocation("minecraft:overworld", 0, 64, 0);
	static final HeartLocation J = new HeartLocation("minecraft:overworld", 1, 65, 1);
	static final String LOC = "{\"dim\":\"minecraft:overworld\",\"x\":1,\"y\":2,\"z\":3}";

	static JsonElement enc(WorkerData d) { return WorkerCodecs.WORKER_DATA.encodeStart(JsonOps.INSTANCE, d).getOrThrow(); }
	static com.mojang.serialization.DataResult<WorkerData> parse(String json) {
		return WorkerCodecs.WORKER_DATA.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
	}

	@Test
	void roundTripWithNonDefaultNeeds() {
		WorkerData d = new WorkerData("miner", B, J, 3, 120, new Needs(false, 2, false, false));
		assertEquals(d, WorkerCodecs.WORKER_DATA.parse(JsonOps.INSTANCE, enc(d)).getOrThrow());
	}

	@Test
	void pinnedKeys() {
		JsonObject o = enc(new WorkerData("miner", B, J, 3, 120, new Needs(false, 2, false, false))).getAsJsonObject();
		assertEquals(Set.of("job", "building", "job_block", "level", "xp", "needs"), o.keySet());
		assertEquals(Set.of("fed", "hungry_days", "housed", "paid"), o.getAsJsonObject("needs").keySet());
	}

	@Test
	void minimalShapeDecodesToDefaults() {
		WorkerData d = parse("{\"job\":\"farmer\",\"building\":" + LOC + ",\"job_block\":" + LOC + "}").getOrThrow();
		assertEquals(1, d.level());
		assertEquals(0, d.xp());
		assertEquals(Needs.DEFAULT, d.needs());
		assertEquals(JobType.FARMER, d.job());
	}

	@Test
	void levelAndXpAboveTheTableAreClamped() {
		String base = "\"building\":" + LOC + ",\"job_block\":" + LOC;
		WorkerData a = parse("{\"job\":\"farmer\"," + base + ",\"level\":7,\"xp\":10000}").getOrThrow();
		assertEquals(3, a.level());
		assertEquals(300, a.xp());
		WorkerData b = parse("{\"job\":\"farmer\"," + base + ",\"level\":2,\"xp\":150}").getOrThrow();
		assertEquals(2, b.level());
		assertEquals(150, b.xp());
		assertEquals(b, WorkerCodecs.WORKER_DATA.parse(JsonOps.INSTANCE, enc(b)).getOrThrow());
		WorkerData c = parse("{\"job\":\"farmer\"," + base + "}").getOrThrow();
		assertEquals(1, c.level());
		assertEquals(0, c.xp());
	}

	@Test
	void badValuesAreErrorsNotExceptions() {
		String base = "\"building\":" + LOC + ",\"job_block\":" + LOC;
		assertTrue(parse("{\"job\":\"farmer\"," + base + ",\"level\":0}").result().isEmpty());
		assertTrue(parse("{\"job\":\"farmer\"," + base + ",\"xp\":-1}").result().isEmpty());
		assertTrue(parse("{\"job\":\"wizard\"," + base + "}").result().isEmpty());
		assertTrue(parse("{\"job\":\"farmer\"," + base + ",\"needs\":{\"hungry_days\":-1}}").result().isEmpty());
		assertTrue(parse("{\"job\":\"farmer\",\"building\":" + LOC + "}").result().isEmpty());
	}
}
