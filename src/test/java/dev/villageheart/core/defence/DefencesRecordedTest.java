package dev.villageheart.core.defence;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Balance;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.blueprint.Rotation4;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** T-L1-14. Walls and gates are recorded, and they still have no patrol point. */
class DefencesRecordedTest {
	private static final HeartLocation GROUND = new HeartLocation("minecraft:overworld", 4, 64, 8);

	@Test
	void everyDefenceBlueprintIsRecordedAndWallsHaveNoPatrolPoint() {
		for (String id : new String[] {"wall_segment", "gate", "lamp_post", "watchtower"}) assertTrue(Defences.recorded(id), id);
		assertFalse(Defences.recorded("small_house"));
		assertTrue(DefenceSpecs.of("wall_segment").isEmpty());
		assertTrue(DefenceSpecs.of("gate").isEmpty());
		assertEquals(128, Balance.MAX_DEFENCE_SITES);

		DefenceSite wall = new DefenceSite(new UUID(3, 1), "wall_segment", GROUND, Rotation4.NONE, 1, 10, java.util.Set.of(), java.util.Set.of());
		VillageState v = VillageState.founded(GROUND, new UUID(0, 1), "o", 0).withDefence(wall);
		assertEquals(10, v.defences().get(0).length());
		assertEquals(wall, v.defence(wall.id()).orElseThrow());
		assertTrue(Defences.patrolPoints(v.defences(), p -> true).isEmpty());
		assertTrue(Defences.keys(wall).isEmpty());

		VillageState full = v;
		for (int i = 0; i < Balance.MAX_DEFENCE_SITES - 1; i++) full = full.withDefence(new DefenceSite(new UUID(4, i), "lamp_post", GROUND, Rotation4.NONE, i));
		assertEquals(Balance.MAX_DEFENCE_SITES, full.defences().size());
		assertSame(full, full.withDefence(new DefenceSite(new UUID(4, 999), "wall_segment", GROUND, Rotation4.NONE, 9, 8, java.util.Set.of(), java.util.Set.of())));
		assertEquals(10, full.defence(wall.id()).orElseThrow().length());
	}
}
