package dev.villageheart.core.heart;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class DeathCauseTextTest {
	@Test void entityIdsUseTheEntityKey() {
		assertEquals(Optional.of("entity.minecraft.vindicator"), DeathCauseText.translationKey("minecraft:vindicator"));
		assertEquals(Optional.of("entity.minecraft.lightning_bolt"), DeathCauseText.translationKey("minecraft:lightning_bolt"));
	}

	@Test void namedDamageIdsUseTheCauseKey() {
		assertEquals(Optional.of("death.villageheart.cause.fall"), DeathCauseText.translationKey("minecraft:fall"));
		assertEquals(Optional.of("death.villageheart.cause.zombified"), DeathCauseText.translationKey("zombified"));
		assertEquals(Optional.of("death.villageheart.cause.generic_kill"), DeathCauseText.translationKey("minecraft:generic_kill"));
	}

	@Test void unknownIdsStayRaw() {
		assertEquals(Optional.empty(), DeathCauseText.translationKey("mystery"));
		assertEquals(Optional.empty(), DeathCauseText.translationKey(""));
		assertEquals(Optional.of("entity.mod.thing"), DeathCauseText.translationKey("mod:thing"));
	}
}
