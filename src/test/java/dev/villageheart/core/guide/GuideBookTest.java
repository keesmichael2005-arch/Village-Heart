package dev.villageheart.core.guide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import dev.villageheart.core.Tier;
import dev.villageheart.core.WorldClaim;
import dev.villageheart.world.VillageCodecs;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GuideBookTest {
	@Test void aNewVillageStartsOnOpeningTheHeart() {
		GuideProgress none = GuideBook.advance(GuideProgress.EMPTY, GuideSignals.none());
		assertTrue(none.isEmpty());
		assertEquals(GuideTask.OPEN_HEART.id(), GuideBook.current(none));
	}

	@Test void doingALaterThingChecksItAndLeavesTheGap() {
		GuideSignals house = new GuideSignals(true, true, Set.of("house"), Set.of(), false, false, false, false, false, false, false,
				false, Set.of(), Tier.HAMLET, 0);
		GuideProgress done = GuideBook.advance(GuideProgress.EMPTY, house);
		assertTrue(done.has(GuideTask.OPEN_HEART.id()));
		assertTrue(done.has(GuideTask.BLANK_MARKER.id()));
		assertTrue(done.has(GuideTask.HOUSE.id()));
		assertEquals(GuideTask.STORAGE.id(), GuideBook.current(done));
		GuideProgress kept = GuideBook.advance(done, GuideSignals.none());
		assertEquals(done, kept, "a checked task stays checked");
	}

	@Test void aMineNeedsBothTheBuildingAndTheShaft() {
		GuideSignals onlyMine = new GuideSignals(false, false, Set.of("mine"), Set.of(), false, false, false, false, false, false, false,
				false, Set.of(), Tier.VILLAGE, 0);
		assertFalse(GuideBook.advance(GuideProgress.EMPTY, onlyMine).has(GuideTask.MINE.id()));
		GuideSignals both = new GuideSignals(false, false, Set.of("mine", "mineshaft"), Set.of(), false, false, false, false, false, false,
				false, false, Set.of(), Tier.VILLAGE, 0);
		assertTrue(GuideBook.advance(GuideProgress.EMPTY, both).has(GuideTask.MINE.id()));
	}

	@Test void needsComeAndGoWithoutCheckingATask() {
		GuideSignals shortPaid = new GuideSignals(false, false, Set.of("farmhouse"), Set.of("farmer", "guard"), false, false, false, false,
				false, false, false, false, Set.of(), Tier.VILLAGE, 3);
		assertEquals(List.of(GuideBook.NEED_EMERALDS, GuideBook.NEED_FIELD, GuideBook.NEED_YARD), GuideBook.needs(shortPaid));
		GuideProgress hired = GuideBook.advance(GuideProgress.EMPTY, shortPaid);
		assertTrue(hired.has(GuideTask.FARMER.id()));
		assertTrue(hired.has(GuideTask.GUARD.id()));
		assertFalse(hired.has(GuideTask.FIELD.id()));
		assertFalse(hired.has(GuideTask.TRAINING_YARD.id()));
		GuideSignals clear = new GuideSignals(false, false, Set.of("farmhouse", "training_yard"), Set.of("farmer", "guard"), false, true,
				false, false, false, false, false, false, Set.of(), Tier.VILLAGE, 0);
		assertTrue(GuideBook.needs(clear).isEmpty());
	}

	@Test void anUnknownSavedIdIsDroppedAndARealOneRoundTrips() {
		assertTrue(GuideProgress.parse(List.of("not_a_task", GuideTask.HOUSE.id())).has(GuideTask.HOUSE.id()));
		assertEquals(1, GuideProgress.parse(List.of("not_a_task", GuideTask.HOUSE.id())).done().size());
		WorldClaim claim = new WorldClaim();
		claim.setGuide(GuideProgress.parse(List.of(GuideTask.STORAGE.id())));
		WorldClaim back = VillageCodecs.WORLD_CLAIM.parse(JsonOps.INSTANCE,
				VillageCodecs.WORLD_CLAIM.encodeStart(JsonOps.INSTANCE, claim).getOrThrow()).getOrThrow();
		assertTrue(back.guide().has(GuideTask.STORAGE.id()));
		WorldClaim old = VillageCodecs.WORLD_CLAIM.parse(JsonOps.INSTANCE,
				VillageCodecs.WORLD_CLAIM.encodeStart(JsonOps.INSTANCE, new WorldClaim()).getOrThrow()).getOrThrow();
		assertTrue(old.guide().isEmpty());
	}
}
