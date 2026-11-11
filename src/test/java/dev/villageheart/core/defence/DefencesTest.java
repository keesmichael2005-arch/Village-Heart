package dev.villageheart.core.defence;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Balance;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.blueprint.Rotation4;
import dev.villageheart.core.room.Pos;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** T-L1-6 */
class DefencesTest {
	private static final String DIM = "minecraft:overworld";
	private static final HeartLocation GROUND = new HeartLocation(DIM, 100, 64, 100);

	private static DefenceSite lamp(int n, Rotation4 r) { return new DefenceSite(new UUID(1, n), "lamp_post", GROUND, r, n); }

	private static DefenceSite tower(int n, Rotation4 r) { return new DefenceSite(new UUID(2, n), "watchtower", GROUND, r, n); }

	@Test
	void lampPatrolPointIsInFrontOfThePost() {
		// the blueprint's front is NORTH (-z); a rotation turns it
		Pos[] front = {new Pos(100, 65, 99), new Pos(101, 65, 100), new Pos(100, 65, 101), new Pos(99, 65, 100)};
		for (Rotation4 r : Rotation4.values()) {
			DefenceSite s = lamp(1, r);
			assertEquals(Optional.of(front[r.ordinal()]), Defences.patrolPoint(s, p -> true), r.name());
			List<Defences.Key> keys = Defences.keys(s);
			assertEquals(List.of(new Defences.Key(new Pos(100, 67, 100), "minecraft:lantern")), keys);
		}
	}

	@Test
	void lampBesideAWallTakesTheNextOpenSide() {
		DefenceSite s = lamp(1, Rotation4.NONE);
		Pos blocked = new Pos(100, 65, 99);
		assertEquals(Optional.of(new Pos(100, 65, 101)), Defences.patrolPoint(s, p -> !p.equals(blocked)));
		assertTrue(Defences.patrolPoint(s, p -> false).isEmpty());
	}

	@Test
	void towerPatrolPointIsInFrontOfTheEntranceAndKeysAreTheLadders() {
		// anchor (2, 2): local (2, 0, 0) is two blocks north of the ground column
		Pos[] front = {new Pos(100, 65, 98), new Pos(102, 65, 100), new Pos(100, 65, 102), new Pos(98, 65, 100)};
		for (Rotation4 r : Rotation4.values()) {
			DefenceSite s = tower(1, r);
			assertEquals(Optional.of(front[r.ordinal()]), Defences.patrolPoint(s, p -> true), r.name());
			assertEquals(List.of(new Defences.Key(new Pos(100, 65, 100), "minecraft:ladder"), new Defences.Key(new Pos(100, 70, 100), "minecraft:ladder")),
					Defences.keys(s));
		}
	}

	@Test
	void wallsAndGatesAreRecordedButHaveNoPatrolPoint() {
		// M25 records every finished defence. A wall still has no key cell and no patrol point (T-L1-14).
		assertTrue(DefenceSpecs.of("wall_segment").isEmpty());
		assertTrue(DefenceSpecs.of("gate").isEmpty());
		assertTrue(DefenceSpecs.of("small_house").isEmpty());
		assertTrue(Defences.recorded("wall_segment"));
		assertTrue(Defences.recorded("gate"));
		assertTrue(Defences.recorded("lamp_post"));
		assertTrue(Defences.recorded("watchtower"));
		assertFalse(Defences.recorded("small_house"));
		DefenceSite wall = new DefenceSite(new UUID(3, 1), "wall_segment", GROUND, Rotation4.NONE, 1, 10, java.util.Set.of(), java.util.Set.of());
		assertTrue(Defences.patrolPoint(wall, p -> true).isEmpty());
		assertTrue(Defences.keys(wall).isEmpty());
	}

	@Test
	void patrolPointsKeepRecordOrderAndTheCapHolds() {
		VillageState v = VillageState.founded(GROUND, new UUID(0, 1), "o", 0);
		DefenceSite a = lamp(1, Rotation4.NONE), b = tower(2, Rotation4.NONE), c = lamp(3, Rotation4.CLOCKWISE_90);
		v = v.withDefence(a).withDefence(b).withDefence(c);
		assertEquals(List.of(a, b, c), v.defences());
		assertEquals(List.of(new Pos(100, 65, 99), new Pos(100, 65, 98), new Pos(101, 65, 100)), Defences.patrolPoints(v.defences(), p -> true));
		assertSame(v, v.withDefence(a)); // a known id
		assertEquals(List.of(a, c), v.withoutDefence(b.id()).defences());
		assertSame(v, v.withoutDefence(new UUID(0, 0)));
		VillageState full = VillageState.founded(GROUND, new UUID(0, 1), "o", 0);
		List<DefenceSite> sites = new ArrayList<>();
		for (int i = 0; i < Balance.MAX_DEFENCE_SITES; i++) {
			sites.add(lamp(i + 10, Rotation4.NONE));
			full = full.withDefence(sites.get(i));
		}
		assertEquals(Balance.MAX_DEFENCE_SITES, full.defences().size());
		assertSame(full, full.withDefence(lamp(999, Rotation4.NONE)));
		// the other record parts survive a defence change
		assertEquals(3, v.withTier(dev.villageheart.core.Tier.TOWN).defences().size());
	}
}
