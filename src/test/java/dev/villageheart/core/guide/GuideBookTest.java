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
				false, Set.of(), Tier.HAMLET, 0, false, Set.of(), Set.of());
		GuideProgress done = GuideBook.advance(GuideProgress.EMPTY, house);
		assertTrue(done.has(GuideTask.OPEN_HEART.id()));
		assertTrue(done.has(GuideTask.BLANK_MARKER.id()));
		assertTrue(done.has(GuideTask.HOUSE_MARKER.id()));
		assertTrue(done.has(GuideTask.HOUSE.id()));
		assertEquals(GuideTask.STORAGE_MARKER.id(), GuideBook.current(done));
		GuideProgress kept = GuideBook.advance(done, GuideSignals.none());
		assertEquals(done, kept, "a checked task stays checked");
	}

	@Test void theMineAndTheShaftAreSeparateSteps() {
		GuideSignals onlyMine = new GuideSignals(false, false, Set.of("mine"), Set.of(), false, false, false, false, false, false, false,
				false, Set.of(), Tier.VILLAGE, 0, false, Set.of(), Set.of());
		assertTrue(GuideBook.advance(GuideProgress.EMPTY, onlyMine).has(GuideTask.MINE.id()));
		assertFalse(GuideBook.advance(GuideProgress.EMPTY, onlyMine).has(GuideTask.MINESHAFT.id()));
		GuideSignals both = new GuideSignals(false, false, Set.of("mine", "mineshaft"), Set.of(), false, false, false, false, false, false,
				false, false, Set.of(), Tier.VILLAGE, 0, false, Set.of(), Set.of());
		assertTrue(GuideBook.advance(GuideProgress.EMPTY, both).has(GuideTask.MINESHAFT.id()));
	}

	@Test void needsComeAndGoWithoutCheckingATask() {
		GuideSignals shortPaid = new GuideSignals(false, false, Set.of("farmhouse"), Set.of("farmer", "guard"), false, false, false, false,
				false, false, false, false, Set.of(), Tier.VILLAGE, 3, false, Set.of(), Set.of());
		assertEquals(List.of(GuideBook.NEED_EMERALDS, GuideBook.NEED_FIELD, GuideBook.NEED_YARD), GuideBook.needs(shortPaid));
		GuideProgress hired = GuideBook.advance(GuideProgress.EMPTY, shortPaid);
		assertTrue(hired.has(GuideTask.FARMER.id()));
		assertTrue(hired.has(GuideTask.GUARD.id()));
		assertFalse(hired.has(GuideTask.FIELD.id()));
		assertFalse(hired.has(GuideTask.TRAINING_YARD.id()));
		GuideSignals clear = new GuideSignals(false, false, Set.of("farmhouse", "training_yard"), Set.of("farmer", "guard"), false, true,
				false, false, false, false, false, false, Set.of(), Tier.VILLAGE, 0, false, Set.of(), Set.of());
		assertTrue(GuideBook.needs(clear).isEmpty());
	}

	@Test void aBlueprintBookInHandChecksThatTask() {
		GuideSignals held = new GuideSignals(false, false, Set.of(), Set.of(), false, false, false, false, false, false, false,
				false, Set.of(), Tier.VILLAGE, 0, true, Set.of(), Set.of());
		assertTrue(GuideBook.advance(GuideProgress.EMPTY, held).has(GuideTask.BLUEPRINT_BOOK.id()));
		assertFalse(GuideBook.advance(GuideProgress.EMPTY, GuideSignals.none()).has(GuideTask.BLUEPRINT_BOOK.id()));
	}

	@Test void aCraftedMarkerInInventoryChecksThatStep() {
		GuideSignals held = new GuideSignals(false, false, Set.of(), Set.of(), false, false, false, false, false, false, false,
				false, Set.of(), Tier.HAMLET, 0, false, Set.of(), Set.of("villageheart:blank_marker"));
		GuideProgress done = GuideBook.advance(GuideProgress.EMPTY, held);
		assertTrue(done.has(GuideTask.BLANK_MARKER.id()));
		assertFalse(done.has(GuideTask.HOUSE.id()));
		assertTrue(GuideTask.FARMHOUSE.ordinal() < GuideTask.FIELD.ordinal());
		assertTrue(GuideTask.HOUSE_MARKER.ordinal() < GuideTask.HOUSE.ordinal());
		assertTrue(GuideTask.values().length <= dev.villageheart.core.heart.HeartLimits.MAX_GUIDE);
	}

	@Test void theNextThreeTasksAreTheOnesStillOpen() {
		GuideProgress house = GuideProgress.parse(List.of(GuideTask.OPEN_HEART.id(), GuideTask.BLANK_MARKER.id(),
				GuideTask.HOUSE_MARKER.id(), GuideTask.HOUSE.id()));
		assertEquals(List.of(GuideTask.STORAGE_MARKER.id(), GuideTask.STORAGE.id(), GuideTask.EMERALDS.id()),
				GuideBook.upcoming(house, 3));
		assertEquals(List.of(GuideTask.BLANK_MARKER.id(), GuideTask.HOUSE_MARKER.id(), GuideTask.HOUSE.id()),
				GuideBook.newlyDone(GuideProgress.parse(List.of(GuideTask.OPEN_HEART.id())), house));
	}

	@Test void aMarkerRecipeShowsInTheDetail() {
		assertEquals("minecraft:item_frame", GuideRecipes.grid(GuideTask.BLANK_MARKER).get(4));
		assertEquals("#minecraft:planks", GuideRecipes.grid(GuideTask.BLANK_MARKER).get(0));
		assertEquals("villageheart:blank_marker", GuideRecipes.grid(GuideTask.HOUSE_MARKER).get(4));
		assertEquals("minecraft:book", GuideRecipes.grid(GuideTask.BLUEPRINT_BOOK).get(0));
		assertTrue(GuideRecipes.grid(GuideTask.FIELD).isEmpty());
		assertTrue(GuideTask.BLANK_MARKER.detail().startsWith("1. "));
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
