package dev.villageheart.core.building;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Tier;
import dev.villageheart.core.building.BuildingRules.CheckContext;
import dev.villageheart.core.building.BuildingRules.SiteFacts;
import dev.villageheart.core.room.Pos;
import java.util.List;
import org.junit.jupiter.api.Test;

class SiteRulesTest {
	static final Pos PARENT = new Pos(40, 70, 12);
	static final CheckContext LINKED = new CheckContext(Tier.VILLAGE, null, PARENT, 0);
	static final CheckContext ALONE = new CheckContext(Tier.VILLAGE, null, null, 0);

	static SiteFacts shaft(int depth, int rails, int lights) { return new SiteFacts(depth, rails, lights, 0, 0, 0, false); }
	static SiteFacts ground(int natural, boolean home) { return new SiteFacts(0, 0, 0, natural, 0, 0, home); }
	static SiteFacts yard(int targets, int stands) { return new SiteFacts(0, 0, 0, 0, targets, stands, false); }

	static List<String> messages(Verdict v) { return v.reasons().stream().map(Reason::message).toList(); }

	@Test void mineshaft() {
		Verdict ok = BuildingRules.checkSite(BuildingType.MINESHAFT, shaft(12, 8, 1), LINKED);
		assertEquals(BuildingStatus.VALID, ok.status());
		assertEquals(PARENT, ok.link());
		assertEquals(List.of("Shaft too shallow: 11 of 12 blocks deep"),
				messages(BuildingRules.checkSite(BuildingType.MINESHAFT, shaft(11, 8, 1), LINKED)));
		assertEquals(List.of("Needs 2 more rails"), messages(BuildingRules.checkSite(BuildingType.MINESHAFT, shaft(12, 6, 1), LINKED)));
		assertEquals(List.of("Needs 1 more light source"), messages(BuildingRules.checkSite(BuildingType.MINESHAFT, shaft(12, 8, 0), LINKED)));
		Verdict noMine = BuildingRules.checkSite(BuildingType.MINESHAFT, shaft(12, 8, 1), ALONE);
		assertEquals(List.of("No Mine within 48 blocks"), messages(noMine));
		assertNull(noMine.link());
	}

	@Test void huntingGround() {
		assertEquals(BuildingStatus.VALID, BuildingRules.checkSite(BuildingType.HUNTING_GROUND, ground(40, false), LINKED).status());
		assertEquals(List.of("Needs 1 more grass, leaf or log block"),
				messages(BuildingRules.checkSite(BuildingType.HUNTING_GROUND, ground(39, false), LINKED)));
		assertEquals(List.of("Must be outside House and Farmhouse rooms"),
				messages(BuildingRules.checkSite(BuildingType.HUNTING_GROUND, ground(40, true), LINKED)));
		assertEquals(List.of("No Hunting Lodge in the village"),
				messages(BuildingRules.checkSite(BuildingType.HUNTING_GROUND, ground(40, false), ALONE)));
	}

	@Test void trainingYard() {
		assertEquals(BuildingStatus.VALID, BuildingRules.checkSite(BuildingType.TRAINING_YARD, yard(1, 1), LINKED).status());
		assertEquals(BuildingStatus.VALID, BuildingRules.checkSite(BuildingType.TRAINING_YARD, yard(0, 2), LINKED).status());
		assertEquals(List.of("Needs 1 more armor stand or target"), messages(BuildingRules.checkSite(BuildingType.TRAINING_YARD, yard(0, 1), LINKED)));
		assertEquals(List.of("Needs 2 more armor stands or targets"), messages(BuildingRules.checkSite(BuildingType.TRAINING_YARD, yard(0, 0), LINKED)));
		assertEquals(List.of("No Barracks in the village"), messages(BuildingRules.checkSite(BuildingType.TRAINING_YARD, yard(1, 1), ALONE)));
	}

	@Test void tierGateComesFirst() {
		CheckContext hamlet = new CheckContext(Tier.HAMLET, null, PARENT, 0);
		for (BuildingType t : new BuildingType[] {BuildingType.MINESHAFT, BuildingType.HUNTING_GROUND, BuildingType.TRAINING_YARD}) {
			Verdict v = BuildingRules.checkSite(t, new SiteFacts(99, 99, 99, 99, 99, 99, false), hamlet);
			assertEquals(List.of("Requires Village tier"), messages(v), t.toString());
		}
	}

	@Test void siteLinkIsKeptWhileInvalid() {
		Verdict v = BuildingRules.checkSite(BuildingType.MINESHAFT, shaft(12, 6, 1), LINKED);
		assertEquals(BuildingStatus.INVALID, v.status());
		assertEquals(PARENT, v.link());
	}

	@Test void claimAreaReasonIsAlone() {
		Reason out = Reason.outsideClaim(200, 128);
		Verdict v = BuildingRules.checkSite(BuildingType.MINESHAFT, shaft(0, 0, 0), new CheckContext(Tier.VILLAGE, out, null, 0));
		assertEquals(List.of(out), v.reasons());
	}

	@Test void roomTypeIsNotASite() {
		assertThrows(IllegalArgumentException.class, () -> BuildingRules.checkSite(BuildingType.HOUSE, shaft(0, 0, 0), LINKED));
	}
}
