package dev.villageheart.world;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.defence.GateLedger;
import org.junit.jupiter.api.Test;

/** The saved ledger of gates a villager opened (so a gate open at shutdown is closed after the reload). */
class GateLedgerTest {
	@Test
	void roundTripKeepsEveryGate() {
		GateLedger l = new GateLedger();
		l.add(new HeartLocation("minecraft:overworld", 14, 64, 13));
		l.add(new HeartLocation("minecraft:overworld", 15, 64, 13));
		l.add(new HeartLocation("minecraft:the_nether", -3, 70, 9));
		GateLedger back = GateLedger.CODEC.parse(JsonOps.INSTANCE, GateLedger.CODEC.encodeStart(JsonOps.INSTANCE, l).getOrThrow()).getOrThrow();
		assertEquals(l.gates(), back.gates());
	}

	@Test
	void absentFieldIsEmptyAndAddRemoveWork() {
		GateLedger l = GateLedger.CODEC.parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow();
		assertTrue(l.gates().isEmpty());
		HeartLocation g = new HeartLocation("minecraft:overworld", 1, 2, 3);
		l.add(g);
		l.add(g);
		assertEquals(1, l.gates().size());
		l.remove(g);
		assertTrue(l.gates().isEmpty());
	}
}
