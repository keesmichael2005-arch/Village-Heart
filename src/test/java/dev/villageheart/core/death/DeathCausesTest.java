package dev.villageheart.core.death;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/** T-L1-2. The death log and the sim collector share this table. */
class DeathCausesTest {
	@Test void sourceEntityBeatsTheDamageType() {
		DeathCauses.Cause zombie = DeathCauses.of(Optional.of("minecraft:zombie"), true, "minecraft:mob_attack");
		assertEquals("minecraft:zombie", zombie.cause());
		assertTrue(zombie.byMob());

		DeathCauses.Cause player = DeathCauses.of(Optional.of("minecraft:player"), false, "minecraft:player_attack");
		assertEquals("minecraft:player", player.cause());
		assertFalse(player.byMob());

		DeathCauses.Cause vindicator = DeathCauses.of(Optional.of("minecraft:vindicator"), true, "minecraft:mob_attack");
		assertEquals("minecraft:vindicator", vindicator.cause());
		assertTrue(vindicator.byMob());
	}

	@Test void noSourceUsesTheDamageType() {
		DeathCauses.Cause fall = DeathCauses.of(Optional.empty(), false, "minecraft:fall");
		assertEquals("minecraft:fall", fall.cause());
		assertFalse(fall.byMob());
		assertFalse(DeathCauses.of(Optional.empty(), true, "minecraft:fall").byMob(), "a flag without a source is not a mob");
	}

	@Test void conversions() {
		assertEquals(Optional.of("zombified"), DeathCauses.conversion("minecraft:villager", "minecraft:zombie_villager"));
		assertEquals(Optional.of("minecraft:lightning_bolt"), DeathCauses.conversion("minecraft:villager", "minecraft:witch"));
		assertEquals(Optional.empty(), DeathCauses.conversion("minecraft:zombie_villager", "minecraft:villager"));
		assertEquals(Optional.empty(), DeathCauses.conversion("minecraft:villager", "minecraft:villager"));
		assertTrue(DeathCauses.conversionCountsAsMob("zombified"));
		assertFalse(DeathCauses.conversionCountsAsMob("minecraft:lightning_bolt"));
	}

	@Test void sameLossIsAtMostOneTickAfterTheDeath() {
		assertTrue(DeathCauses.sameLoss(100, 100));
		assertTrue(DeathCauses.sameLoss(100, 101));
		assertFalse(DeathCauses.sameLoss(100, 102));
		assertTrue(DeathCauses.sameLoss(100, 90), "an earlier conversion still merges, as the sim collector always did");
	}
}
