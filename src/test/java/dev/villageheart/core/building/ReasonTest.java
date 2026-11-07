package dev.villageheart.core.building;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Balance;
import dev.villageheart.core.Tier;
import dev.villageheart.core.room.Pos;
import dev.villageheart.core.room.RoomProblem;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReasonTest {
	@Test void everyCodeHasItsExactMessage() {
		Set<Reason.Code> seen = EnumSet.noneOf(Reason.Code.class);
		check(seen, Reason.requiresTier(Tier.VILLAGE), "Requires Village tier");
		check(seen, Reason.requiresTier(Tier.TOWN), "Requires Town tier");
		check(seen, Reason.outsideClaim(93, Balance.CLAIM_RADIUS), "Outside the village: 93 blocks from the Heart (max 80)");
		check(seen, Reason.otherDimension(), "Not in the village's dimension");
		check(seen, Reason.room(new RoomProblem(RoomProblem.Kind.ROOF_MISSING, new Pos(12, 70, -4))), "Roof missing at (12, 70, -4)");
		check(seen, Reason.room(new RoomProblem(RoomProblem.Kind.SHARED, new Pos(3, 64, 9))), "Room is shared with the marker at (3, 64, 9)");
		check(seen, Reason.floorTooSmall(7, 9), "Room too small: floor 7 of 9");
		check(seen, Reason.needsMore(1, Thing.CHEST), "Needs 1 more chest");
		check(seen, Reason.needsMore(2, Thing.CHEST), "Needs 2 more chests");
		check(seen, Reason.needsMore(1, Thing.LIGHT), "Needs 1 more light source");
		check(seen, Reason.needsLinkedSite(BuildingType.MINESHAFT), "Needs a linked Mineshaft");
		check(seen, Reason.needsLinkedSite(BuildingType.HUNTING_GROUND), "Needs a linked Hunting Ground");
		check(seen, Reason.noParent(BuildingType.MINE, Balance.MINESHAFT_LINK_RANGE), "No Mine within 48 blocks");
		check(seen, Reason.noParent(BuildingType.HUNTING_LODGE, Integer.MAX_VALUE), "No Hunting Lodge in the village");
		check(seen, Reason.noParent(BuildingType.BARRACKS, Integer.MAX_VALUE), "No Barracks in the village");
		check(seen, Reason.shaftTooShallow(9, Balance.MINESHAFT_MIN_DEPTH), "Shaft too shallow: 9 of 12 blocks deep");
		check(seen, Reason.insideHomeRoom(), "Must be outside House and Farmhouse rooms");
		check(seen, Reason.notLoaded("Room reaches an unloaded area at (1, 2, 3)"),
				"Cannot check now: Room reaches an unloaded area at (1, 2, 3)");
		check(seen, Reason.noVillage(), "No village yet: place a Village Heart near a bell, then right-click this marker again");
		assertEquals(EnumSet.allOf(Reason.Code.class), seen, "every Code has an example");
	}

	private static void check(Set<Reason.Code> seen, Reason r, String message) {
		assertEquals(message, r.message());
		seen.add(r.code());
	}

	@Test void thingPhrases() {
		assertEquals("chest", Thing.CHEST.phrase(1));
		assertEquals("chests", Thing.STORAGE_CONTAINER.phrase(2));
		assertEquals("light source", Thing.LIGHT.phrase(1));
		assertEquals("light sources", Thing.LIGHT.phrase(0));
		assertEquals("armor stands or targets", Thing.DUMMY.phrase(2));
		assertEquals("grass, leaf or log block", Thing.NATURAL.phrase(1));
		for (Thing t : Thing.values()) {
			assertFalse(t.phrase(1).isBlank(), t.name());
			assertNotEquals(t.phrase(1), t.phrase(2), t.name());
		}
		assertEquals("blast furnaces", Thing.BLAST_FURNACE.phrase(2));
	}

	@Test void translationKeys() {
		assertEquals("message.villageheart.reason.needs_more", Reason.needsMore(1, Thing.BED).translationKey());
		assertEquals("message.villageheart.reason.no_village", Reason.noVillage().translationKey());
	}

	@Test void storageReadsChest() {
		assertEquals("Needs 1 more chest", Reason.needsMore(1, Thing.STORAGE_CONTAINER).message());
	}
}
