package dev.villageheart.core.smith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.WorkerRecord;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GolemRulesTest {
	@Test
	void ingotsAndTheHalfHealthLine() {
		assertEquals(3, GolemRules.ingotsFor(40, 100));
		assertEquals(2, GolemRules.ingotsFor(49, 100));
		assertEquals(1, GolemRules.ingotsFor(89, 100));
		assertEquals(0, GolemRules.ingotsFor(91, 100));
		assertEquals(0, GolemRules.ingotsFor(100, 100));
		assertTrue(GolemRules.wantsRepair(49.9f, 100));
		assertFalse(GolemRules.wantsRepair(50, 100));
	}

	@Test
	void oneGolemPerFiveVillagers() {
		assertFalse(GolemRules.allowed(4, 0, 0));
		assertTrue(GolemRules.allowed(5, 0, 0));
		assertFalse(GolemRules.allowed(5, 1, 0));
		assertFalse(GolemRules.allowed(10, 1, 1));
		assertTrue(GolemRules.allowed(10, 1, 0));
	}

	@Test
	void populationCountsUnloadedWorkers() {
		UUID loaded = new UUID(0, 1), away = new UUID(0, 2);
		HeartLocation at = new HeartLocation("minecraft:overworld", 1, 64, 1);
		WorkerRecord home = new WorkerRecord(loaded, "farmer", at, at);
		WorkerRecord unloaded = new WorkerRecord(away, "guard", at, at);
		assertEquals(2, GolemRules.population(Set.of(loaded), List.of(home, unloaded)));
	}
}
