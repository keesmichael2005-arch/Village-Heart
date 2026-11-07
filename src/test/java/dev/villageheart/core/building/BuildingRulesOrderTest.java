package dev.villageheart.core.building;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Tier;
import dev.villageheart.core.building.BuildingRules.CheckContext;
import dev.villageheart.core.room.Pos;
import dev.villageheart.core.room.RoomProblem;
import dev.villageheart.core.room.RoomProblem.Kind;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BuildingRulesOrderTest {
	static CheckContext ctx(Tier t, int linked) { return new CheckContext(t, null, null, linked); }

	@Test void mineAtHamletListsEverythingInOrder() {
		Verdict v = BuildingRules.checkRoom(BuildingType.MINE, new RoomFacts(true, List.of(), 5, Map.of()), ctx(Tier.HAMLET, 0));
		assertEquals(List.of(Reason.requiresTier(Tier.VILLAGE), Reason.floorTooSmall(5, 9), Reason.needsMore(1, Thing.STONECUTTER),
				Reason.needsMore(1, Thing.CHEST), Reason.needsLinkedSite(BuildingType.MINESHAFT)), v.reasons());
		assertEquals(BuildingStatus.INVALID, v.status());
	}

	@Test void leakingRoomShowsOnlyTheLeak() {
		RoomFacts f = new RoomFacts(false, List.of(Reason.room(new RoomProblem(Kind.ROOF_MISSING, new Pos(1, 2, 3)))), 0, Map.of());
		Verdict v = BuildingRules.checkRoom(BuildingType.STORAGE, f, ctx(Tier.TOWN, 0));
		assertEquals(List.of("Roof missing at (1, 2, 3)"), v.reasons().stream().map(Reason::message).toList());
		assertEquals(Kind.ROOF_MISSING, v.reasons().get(0).roomKind());
	}

	@Test void tierGateComesBeforeStructure() {
		RoomFacts f = new RoomFacts(false, List.of(Reason.room(new RoomProblem(Kind.NO_DOOR, null))), 0, Map.of());
		Verdict v = BuildingRules.checkRoom(BuildingType.MARKET, f, ctx(Tier.HAMLET, 0));
		assertEquals(List.of("Requires Village tier", "No door below or beside the marker"), v.reasons().stream().map(Reason::message).toList());
	}

	@Test void sharedAloneIsInvalid() {
		RoomFacts f = new RoomFacts(true, List.of(Reason.room(new RoomProblem(Kind.SHARED, new Pos(3, 64, 9)))), 20,
				Map.of("#chest", 2));
		Verdict v = BuildingRules.checkRoom(BuildingType.STORAGE, f, ctx(Tier.HAMLET, 0));
		assertEquals(BuildingStatus.INVALID, v.status());
		assertEquals(List.of("Room is shared with the marker at (3, 64, 9)"), v.reasons().stream().map(Reason::message).toList());
	}

	@Test void claimAreaReasonIsAlone() {
		Reason out = Reason.outsideClaim(93, 80);
		Verdict v = BuildingRules.checkRoom(BuildingType.STORAGE, new RoomFacts(true, List.of(), 1, Map.of()),
				new CheckContext(Tier.HAMLET, out, null, 0));
		assertEquals(List.of(out), v.reasons());
	}

	@Test void smithyAtVillageTierOnlyNeedsTown() {
		RoomFacts f = new RoomFacts(true, List.of(), 12, Map.of("minecraft:blast_furnace", 1, "#anvil", 1, "#chest", 1));
		Verdict v = BuildingRules.checkRoom(BuildingType.SMITHY, f, ctx(Tier.VILLAGE, 0));
		assertEquals(List.of(Reason.requiresTier(Tier.TOWN)), v.reasons());
		assertEquals("Requires Town tier", v.reasons().get(0).message());
		assertEquals(BuildingStatus.VALID, BuildingRules.checkRoom(BuildingType.SMITHY, f, ctx(Tier.TOWN, 0)).status(), "equal tier: no gate");
	}

	@Test void notEnclosedWithoutReasonIsRejected() {
		assertThrows(IllegalArgumentException.class, () -> new RoomFacts(false, List.of(), 0, Map.of()));
	}
}
