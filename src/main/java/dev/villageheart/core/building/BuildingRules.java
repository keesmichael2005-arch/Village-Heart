package dev.villageheart.core.building;

import dev.villageheart.core.Balance;
import dev.villageheart.core.Tier;
import dev.villageheart.core.building.Requirements.ContentRule;
import dev.villageheart.core.room.Pos;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/** The pure rules: a survey of a building plus the village context in, a {@link Verdict} out (DESIGN section 3.3). */
public final class BuildingRules {
	private BuildingRules() {}

	/**
	 * What the rules need to know about the village around one building.
	 *
	 * @param claimArea       OUTSIDE_CLAIM / OTHER_DIMENSION reason, or null when the marker is in the claim
	 * @param parentLink      sites: the nearest parent building in range, null when there is none
	 * @param validLinkedSites Mine / Hunting Lodge: valid sites linked to it
	 */
	public record CheckContext(Tier villageTier, @Nullable Reason claimArea, @Nullable Pos parentLink, int validLinkedSites) {}

	/** What a site survey found (see ShaftScanner / AreaCounter). */
	public record SiteFacts(int depth, int rails, int lights, int natural, int targets, int armorStands, boolean insideHomeRoom) {}

	/**
	 * Reason order: claim area (then stop), tier gate, structure (then stop: contents of a leaking fill are partial), floor,
	 * each missing content in table order, linked site. VALID iff there are no reasons.
	 */
	public static Verdict checkRoom(BuildingType t, RoomFacts f, CheckContext c) {
		Requirements req = BuildingRequirements.of(t);
		int slots = slots(req, f);
		List<Reason> reasons = new ArrayList<>();
		if (c.claimArea() != null) return Verdict.of(List.of(c.claimArea()), slots, null);
		tierGate(t, c, reasons);
		if (!f.enclosed() || !f.structural().isEmpty()) {
			reasons.addAll(f.structural());
			return Verdict.of(reasons, slots, null);
		}
		if (f.floorArea() < req.minFloor()) reasons.add(Reason.floorTooSmall(f.floorArea(), req.minFloor()));
		for (ContentRule rule : req.contents()) {
			int need = rule.min();
			if (rule.scaleKey() != null) need = Math.max(need, f.count(rule.scaleKey()));
			int have = 0;
			for (String key : rule.keys()) have += f.count(key);
			if (have < need) reasons.add(Reason.needsMore(need - have, rule.thing()));
		}
		if (req.requiredSite() != null && c.validLinkedSites() == 0) reasons.add(Reason.needsLinkedSite(req.requiredSite()));
		return Verdict.of(reasons, slots, null);
	}

	private static int slots(Requirements req, RoomFacts f) {
		return switch (req.slots()) {
			case NONE -> 0;
			case JOB_BLOCKS -> f.count(req.jobKey());
			case BEDS -> f.count(BuildingRequirements.BED);
			case CONTAINERS -> f.count(BuildingRequirements.CHEST) + f.count(BuildingRequirements.BARREL);
			case MIN_JOB_BLOCKS_AND_BEDS -> Math.min(f.count(req.jobKey()), f.count(BuildingRequirements.BED));
		};
	}

	private static void tierGate(BuildingType t, CheckContext c, List<Reason> reasons) {
		if (t.tier().compareTo(c.villageTier()) > 0) reasons.add(Reason.requiresTier(t.tier()));
	}

	/** Outdoor sites: claim area, tier gate, the site's own checks, then the parent link. */
	public static Verdict checkSite(BuildingType t, SiteFacts f, CheckContext c) {
		Pos link = c.parentLink();
		List<Reason> reasons = new ArrayList<>();
		if (c.claimArea() != null) return Verdict.of(List.of(c.claimArea()), 0, null);
		tierGate(t, c, reasons);
		int range = Integer.MAX_VALUE;
		switch (t) {
			case MINESHAFT -> {
				range = Balance.MINESHAFT_LINK_RANGE;
				if (f.depth() < Balance.MINESHAFT_MIN_DEPTH) reasons.add(Reason.shaftTooShallow(f.depth(), Balance.MINESHAFT_MIN_DEPTH));
				if (f.rails() < Balance.MINESHAFT_MIN_RAILS) reasons.add(Reason.needsMore(Balance.MINESHAFT_MIN_RAILS - f.rails(), Thing.RAIL));
				if (f.lights() < 1) reasons.add(Reason.needsMore(1 - f.lights(), Thing.LIGHT));
			}
			case HUNTING_GROUND -> {
				if (f.insideHomeRoom()) reasons.add(Reason.insideHomeRoom());
				if (f.natural() < Balance.HUNTING_GROUND_MIN_NATURAL) {
					reasons.add(Reason.needsMore(Balance.HUNTING_GROUND_MIN_NATURAL - f.natural(), Thing.NATURAL));
				}
			}
			case TRAINING_YARD -> {
				int dummies = f.targets() + f.armorStands();
				if (dummies < Balance.TRAINING_YARD_MIN_DUMMIES) reasons.add(Reason.needsMore(Balance.TRAINING_YARD_MIN_DUMMIES - dummies, Thing.DUMMY));
			}
			default -> throw new IllegalArgumentException(t + " is not an outdoor site");
		}
		if (link == null) reasons.add(Reason.noParent(t.parent().orElseThrow(), range));
		return Verdict.of(reasons, 0, link);
	}
}
