package dev.villageheart.core.heart;

import java.util.LinkedHashMap;
import java.util.Map;

/** The client texts of the Heart screen (key to English). en_us.json must hold the same text (LangCoverageTest). */
public final class HeartTexts {
	private HeartTexts() {}

	public static final String PREFIX = "gui.villageheart.heart.";

	public static final Map<String, String> EN = new LinkedHashMap<>();

	static {
		EN.put(PREFIX + "tab.overview", "Overview");
		EN.put(PREFIX + "tab.villagers", "Villagers");
		EN.put(PREFIX + "tab.buildings", "Buildings");
		EN.put(PREFIX + "tab.stockpile", "Stockpile");
		EN.put(PREFIX + "tab.ranks", "Ranks");
		EN.put(PREFIX + "tab.locked", "Residents and above can see this");
		EN.put(PREFIX + "tab.ranks_locked", "Only the Owner can manage ranks");
		EN.put(PREFIX + "locked.body", "Residents and above can see this tab.");
		EN.put(PREFIX + "overview.title", "%s's village");
		EN.put(PREFIX + "overview.tier", "Tier: %s");
		EN.put(PREFIX + "overview.next_tier", "Next tier: %s");
		EN.put(PREFIX + "overview.top_tier", "Highest tier reached");
		EN.put(PREFIX + "overview.population", "Population %s (beds %s)");
		EN.put(PREFIX + "overview.population_tip", "Unemployed villagers count only while nearby.");
		EN.put(PREFIX + "overview.workers", "Workers %s / %s slots");
		EN.put(PREFIX + "overview.food", "Food: %s days");
		EN.put(PREFIX + "overview.food_none", "Food: -");
		EN.put(PREFIX + "overview.treasury", "Treasury: %s emeralds");
		EN.put(PREFIX + "overview.wages", "Wages tomorrow: -");
		EN.put(PREFIX + "overview.no_alerts", "No alerts");
		EN.put(PREFIX + "overview.more_alerts", "(+%s more)");
		EN.put(PREFIX + "villagers.none", "No villagers nearby.");
		EN.put(PREFIX + "villagers.more", "+%s more villagers");
		EN.put(PREFIX + "villagers.unemployed", "Unemployed");
		EN.put(PREFIX + "villagers.level", "%s L%s");
		EN.put(PREFIX + "villagers.nitwit", "Nitwit");
		EN.put(PREFIX + "villagers.nitwit_note", "Nitwits cannot work.");
		EN.put(PREFIX + "villagers.job", "Job: %s (%s free)");
		EN.put(PREFIX + "villagers.job_none", "No jobs available");
		EN.put(PREFIX + "fire", "Fire");
		EN.put(PREFIX + "assign", "Assign");
		EN.put(PREFIX + "confirm_trades", "Confirm: loses its trades");
		EN.put(PREFIX + "check_now", "Check now");
		EN.put(PREFIX + "assign.tier_locked", "The village is too small for that job.");
		EN.put(PREFIX + "assign.no_slot", "No free job block for that job.");
		EN.put(PREFIX + "buildings.none", "No buildings yet.");
		EN.put(PREFIX + "buildings.more", "+%s more buildings");
		EN.put(PREFIX + "buildings.coords", "(%s, %s, %s)");
		EN.put(PREFIX + "status.valid", "Valid");
		EN.put(PREFIX + "status.invalid", "Invalid");
		EN.put(PREFIX + "status.unchecked", "Not checked");
		EN.put(PREFIX + "status.deferred", "Cannot check now");
		EN.put(PREFIX + "detail.slots", "Slots %s/%s");
		EN.put(PREFIX + "detail.homes", "Homes %s");
		EN.put(PREFIX + "detail.containers", "Containers %s");
		EN.put(PREFIX + "reasons.more", "(+%s more)");
		EN.put(PREFIX + "reasons.none", "No problems found.");
		EN.put(PREFIX + "stock.none", "Nothing stored.");
		EN.put(PREFIX + "stock.line1", "%s items in %s Storage, %s of %s slots free");
		EN.put(PREFIX + "stock.line2", "Food: %s nutrition (%s days)");
		EN.put(PREFIX + "stock.line2_none", "Food: %s nutrition");
		EN.put(PREFIX + "stock.more", "+%s more kinds");
		EN.put(PREFIX + "stock.count", "\u00d7 %s");
		EN.put(PREFIX + "activity.working", "Working");
		EN.put(PREFIX + "activity.idle", "Idle");
		EN.put(PREFIX + "activity.going_home", "Going home");
		EN.put(PREFIX + "activity.sleeping", "Sleeping");
		EN.put(PREFIX + "activity.resting", "Resting");
		EN.put(PREFIX + "activity.meeting", "Meeting");
		EN.put(PREFIX + "activity.panicking", "Panicking");
		EN.put(PREFIX + "activity.hiding", "Hiding");
		EN.put(PREFIX + "activity.away", "Away");
		EN.put(PREFIX + "activity.other", "Busy");
		EN.put(PREFIX + "activity.storage_full", "Storage full");
		EN.put(PREFIX + "activity.storage_unreachable", "Storage unreachable");
		EN.put(PREFIX + "activity.workplace_invalid", "Workplace invalid");
		EN.put(PREFIX + "activity.needs_tool", "Needs tool");
		EN.put(PREFIX + "activity.needs_tool.axe", "Needs axe");
		EN.put(PREFIX + "activity.needs_tool.pickaxe", "Needs pickaxe");
		EN.put(PREFIX + "activity.needs_tool.hoe", "Needs hoe");
		EN.put(PREFIX + "activity.needs_tool.bow_or_sword", "Needs bow or sword");
		EN.put(PREFIX + "activity.too_hungry", "Too hungry to work");
		EN.put(PREFIX + "activity.no_work", "Nothing to do");
		EN.put(PREFIX + "activity.no_work.lumberjack", "No trees within 32 blocks");
		EN.put(PREFIX + "activity.no_work.builder", "No blueprint to build");
		EN.put(PREFIX + "buildings.builds", "Builds");
		EN.put(PREFIX + "builds.builder", "Builder: %s");
		EN.put(PREFIX + "activity.needs_materials", "Needs materials");
		EN.put(PREFIX + "activity.blocked", "Build blocked");
		EN.put(PREFIX + "activity.no_work.farmer", "No farmland within 24 blocks");
		EN.put(PREFIX + "rank.owner", "Owner");
		EN.put(PREFIX + "rank.steward", "Steward");
		EN.put(PREFIX + "rank.resident", "Resident");
		EN.put(PREFIX + "rank.visitor", "Visitor");
		EN.put(PREFIX + "ranks.header", "%s players \u00b7 Ranks apply at once");
		EN.put(PREFIX + "ranks.make_steward", "Steward");
		EN.put(PREFIX + "ranks.make_resident", "Resident");
		EN.put(PREFIX + "ranks.make_visitor", "Visitor");
		EN.put(PREFIX + "ranks.transfer", "Transfer ownership");
		EN.put(PREFIX + "ranks.transfer_confirm", "Confirm: give the village to %s");
		EN.put(PREFIX + "ranks.transfer_needs_member", "Make them a Resident or Steward first");
		EN.put(PREFIX + "ranks.online", "online");
		EN.put(PREFIX + "ranks.offline", "offline");
		EN.put(PREFIX + "ranks.more", "+%s more players");
		EN.put(PREFIX + "building.house", "House");
		EN.put(PREFIX + "building.storage", "Storage");
		EN.put(PREFIX + "building.farmhouse", "Farmhouse");
		EN.put(PREFIX + "building.lumber_camp", "Lumber Camp");
		EN.put(PREFIX + "building.kitchen", "Kitchen");
		EN.put(PREFIX + "building.builders_hut", "Builder's Hut");
		EN.put(PREFIX + "building.mine", "Mine");
		EN.put(PREFIX + "building.hunting_lodge", "Hunting Lodge");
		EN.put(PREFIX + "building.barracks", "Barracks");
		EN.put(PREFIX + "building.market", "Market");
		EN.put(PREFIX + "building.smithy", "Smithy");
		EN.put(PREFIX + "building.mineshaft", "Mineshaft");
		EN.put(PREFIX + "building.hunting_ground", "Hunting Ground");
		EN.put(PREFIX + "building.training_yard", "Training Yard");
	}

	/** The full lang key for a suffix, checked against the table. */
	public static String key(String suffix) {
		if (!EN.containsKey(PREFIX + suffix)) throw new IllegalArgumentException("unknown Heart text: " + suffix);
		return PREFIX + suffix;
	}

	public static String english(String suffix) { return EN.get(key(suffix)); }

	/**
	 * The key of what a villager is doing, for the Villagers tab and for every other place that shows a worker's status (one set of words):
	 * a missing tool names the tool of the job ("Needs axe"), "no work" may have a job-specific reason ("No trees within 32 blocks").
	 */
	public static String activityKey(ActivityKind activity, String jobId) {
		String name = activity.name().toLowerCase(java.util.Locale.ROOT);
		if (activity == ActivityKind.NEEDS_TOOL) {
			String tool = dev.villageheart.core.worker.JobType.byId(jobId).flatMap(dev.villageheart.core.worker.JobType::tool)
					.map(t -> t.kind().id()).orElse("");
			String specific = "activity.needs_tool." + tool;
			return EN.containsKey(PREFIX + specific) ? PREFIX + specific : key("activity.needs_tool");
		}
		if (activity == ActivityKind.NO_WORK) {
			String specific = "activity.no_work." + jobId;
			return EN.containsKey(PREFIX + specific) ? PREFIX + specific : key("activity.no_work");
		}
		return key("activity." + name);
	}
}
