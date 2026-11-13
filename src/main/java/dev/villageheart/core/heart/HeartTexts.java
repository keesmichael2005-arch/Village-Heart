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
		EN.put(PREFIX + "overview.next_tier_ready", "Next tier: %s (at dawn)");
		EN.put(PREFIX + "goal.population", "Population %s / %s");
		EN.put(PREFIX + "goal.workers", "Workers %s / %s");
		EN.put(PREFIX + "goal.guards", "Guards %s / %s");
		EN.put(PREFIX + "goal.storage", "A valid Storage");
		EN.put(PREFIX + "goal.farmhouse", "A valid Farmhouse");
		EN.put(PREFIX + "goal.kitchen", "A valid Kitchen");
		EN.put(PREFIX + "goal.fed_days", "Fed days in a row %s / %s");
		EN.put(PREFIX + "goal.dawn_treasury", "Treasury at a dawn %s / %s");
		EN.put(PREFIX + "overview.population", "Population %s (beds %s)");
		EN.put(PREFIX + "overview.population_tip", "Unemployed villagers count only while nearby.");
		EN.put(PREFIX + "overview.workers", "Workers %s / %s slots");
		EN.put(PREFIX + "overview.food", "Food: %s days");
		EN.put(PREFIX + "overview.food_none", "Food: -");
		EN.put(PREFIX + "overview.treasury", "Treasury: %s");
		EN.put(PREFIX + "overview.wages", "Wages tomorrow: %s");
		EN.put(PREFIX + "overview.wages_none", "Wages tomorrow: -");
		EN.put(PREFIX + "deposit_all", "Deposit all");
		EN.put(PREFIX + "deposit_all.tip", "Deposit every emerald you carry");
		EN.put(PREFIX + "withdraw_one", "Withdraw 1");
		EN.put(PREFIX + "withdraw_stack", "Withdraw 64");
		EN.put(PREFIX + "withdraw.tip", "Only the Owner can take emeralds out");
		EN.put(PREFIX + "overview.no_alerts", "No alerts");
		EN.put(PREFIX + "overview.more_alerts", "(+%s more)");
		EN.put(PREFIX + "overview.recent_deaths", "Recent deaths");
		EN.put(PREFIX + "overview.death_row", "%s (%s): %s, day %s");
		EN.put(PREFIX + "overview.death_raid", "%s (raid)");
		EN.put(PREFIX + "overview.death_no_job", "Villager");
		EN.put(PREFIX + "villagers.none", "No villagers nearby.");
		EN.put(PREFIX + "villagers.more", "+%s more villagers");
		EN.put(PREFIX + "villagers.unemployed", "Unemployed");
		EN.put(PREFIX + "villagers.level", "%s L%s");
		EN.put(PREFIX + "villagers.level_capped", "Needs a %s to reach level %s");
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
		EN.put(PREFIX + "detail.none", "");
		EN.put(PREFIX + "reasons.more", "(+%s more)");
		EN.put(PREFIX + "reasons.none", "No problems found.");
		EN.put(PREFIX + "stock.none", "Nothing stored.");
		EN.put(PREFIX + "stock.line1", "%s items in %s Storage, %s of %s slots free");
		EN.put(PREFIX + "stock.line2", "Food: %s nutrition (%s days)");
		EN.put(PREFIX + "stock.line2_none", "Food: %s nutrition");
		EN.put(PREFIX + "stock.more", "+%s more kinds");
		EN.put(PREFIX + "stock.count", "\u00d7 %s");
		EN.put(PREFIX + "stock.market_sold", "Market: sold %s today");
		EN.put(PREFIX + "stock.market_none", "No merchant: nothing is sold");
		EN.put(PREFIX + "stock.for_sale", "Up to %s for sale above the reserve");
		EN.put(PREFIX + "reserve.label_food", "Keep: food");
		EN.put(PREFIX + "reserve.days", "%s days");
		EN.put(PREFIX + "reserve.label_items", "other items");
		EN.put(PREFIX + "reserve.each", "%s each");
		EN.put(PREFIX + "reserve.food_less", "-");
		EN.put(PREFIX + "reserve.food_more", "+");
		EN.put(PREFIX + "reserve.items_less", "-16");
		EN.put(PREFIX + "reserve.items_more", "+16");
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
		EN.put(PREFIX + "activity.workplace_invalid", "No workstation");
		EN.put(PREFIX + "activity.needs_tool", "Needs tool");
		EN.put(PREFIX + "activity.needs_tool.axe", "Needs axe");
		EN.put(PREFIX + "activity.needs_tool.pickaxe", "Needs pickaxe");
		EN.put(PREFIX + "activity.needs_tool.hoe", "Needs hoe");
		EN.put(PREFIX + "activity.needs_tool.bow_or_sword", "Needs bow or sword");
		EN.put(PREFIX + "activity.too_hungry", "Too hungry to work");
		EN.put(PREFIX + "activity.on_strike", "On strike");
		// M18: guards
		EN.put(PREFIX + "activity.patrolling", "Patrolling");
		EN.put(PREFIX + "activity.fighting", "Fighting");
		EN.put(PREFIX + "activity.equipping", "Fetching gear");
		EN.put(PREFIX + "activity.training", "Training");
		EN.put(PREFIX + "activity.wounded", "Wounded, resting");
		EN.put(PREFIX + "shift.day_auto", "Day shift (auto)");
		EN.put(PREFIX + "shift.night_auto", "Night shift (auto)");
		EN.put(PREFIX + "shift.day_pinned", "Day shift");
		EN.put(PREFIX + "shift.night_pinned", "Night shift");
		EN.put(PREFIX + "shift.button", "Shift: %s");
		EN.put(PREFIX + "shift.choice.0", "Auto");
		EN.put(PREFIX + "shift.choice.1", "Day");
		EN.put(PREFIX + "shift.choice.2", "Night");
		// M19a: a worker making its own tool, and why it cannot
		EN.put(PREFIX + "activity.making_wooden_tool", "Making a wooden tool");
		EN.put(PREFIX + "activity.making_wooden_tool.axe", "Making a wooden axe");
		EN.put(PREFIX + "activity.making_wooden_tool.pickaxe", "Making a wooden pickaxe");
		EN.put(PREFIX + "activity.making_wooden_tool.hoe", "Making a wooden hoe");
		EN.put(PREFIX + "activity.making_wooden_tool.bow_or_sword", "Making a wooden sword");
		EN.put(PREFIX + "activity.making_stone_tool", "Making a stone tool");
		EN.put(PREFIX + "activity.making_stone_tool.axe", "Making a stone axe");
		EN.put(PREFIX + "activity.making_stone_tool.pickaxe", "Making a stone pickaxe");
		EN.put(PREFIX + "activity.making_stone_tool.hoe", "Making a stone hoe");
		EN.put(PREFIX + "activity.making_stone_tool.bow_or_sword", "Making a stone sword");
		EN.put(PREFIX + "activity.tool_no_materials", "Needs a tool (no wood)");
		EN.put(PREFIX + "activity.tool_no_materials.axe", "Needs axe (no wood)");
		EN.put(PREFIX + "activity.tool_no_materials.pickaxe", "Needs pickaxe (no wood)");
		EN.put(PREFIX + "activity.tool_no_materials.hoe", "Needs hoe (no wood)");
		EN.put(PREFIX + "activity.tool_no_materials.bow_or_sword", "Needs sword (no wood)");
		EN.put(PREFIX + "activity.tool_no_space", "Needs a tool (no room)");
		EN.put(PREFIX + "activity.tool_no_space.axe", "Needs axe (no room)");
		EN.put(PREFIX + "activity.tool_no_space.pickaxe", "Needs pickaxe (no room)");
		EN.put(PREFIX + "activity.tool_no_space.hoe", "Needs hoe (no room)");
		EN.put(PREFIX + "activity.tool_no_space.bow_or_sword", "Needs sword (no room)");
		EN.put(PREFIX + "activity.no_work", "Nothing to do");
		EN.put(PREFIX + "activity.no_work.lumberjack", "No trees within 32 blocks");
		EN.put(PREFIX + "activity.no_work.builder", "No blueprint to build");
		EN.put(PREFIX + "buildings.builds", "Builds");
		EN.put(PREFIX + "builds.builder", "Builder: %s");
		EN.put(PREFIX + "activity.needs_materials", "Needs materials");
		EN.put(PREFIX + "activity.blocked", "Build blocked");
		EN.put(PREFIX + "activity.no_work.farmer", "No farmland within 24 blocks");
		EN.put(PREFIX + "activity.no_work.miner", "No valid Mineshaft");
		EN.put(PREFIX + "activity.away_working", "Away (working)");
		EN.put(PREFIX + "activity.away_working.miner", "Away (mining)");
		EN.put(PREFIX + "activity.site_unreachable", "Can't reach its site");
		EN.put(PREFIX + "activity.site_unreachable.miner", "Can't reach the Mineshaft");
		EN.put(PREFIX + "activity.no_work.hunter", "No valid Hunting Ground");
		EN.put(PREFIX + "activity.needs_fuel", "Needs fuel");
		EN.put(PREFIX + "activity.no_work.cook", "Nothing to cook");
		EN.put(PREFIX + "activity.away_working.hunter", "Away (hunting)");
		EN.put(PREFIX + "activity.site_unreachable.hunter", "Can't reach the Hunting Ground");
		EN.put(PREFIX + "activity.no_work.merchant", "Nothing to sell (all stock is within the reserve)");
		EN.put(PREFIX + "activity.site_unreachable.merchant", "Can't reach its stall");
		EN.put(PREFIX + "activity.daily_limit", "Sold today's limit");
		EN.put(PREFIX + "activity.treasury_full", "Treasury full");
		EN.put(PREFIX + "activity.smithing", "Smithing");
		EN.put(PREFIX + "activity.building_golem", "Building an iron golem");
		EN.put(PREFIX + "activity.needs_material", "Needs material");
		EN.put(PREFIX + "activity.needs_villagers", "Too few villagers for a golem");
		EN.put(PREFIX + "activity.no_work.blacksmith", "Nothing to smith");
		EN.put(PREFIX + "activity.sheltering", "Sheltering");
		EN.put(PREFIX + "activity.no_shelter", "No shelter");
		EN.put(PREFIX + "activity.repairing", "Repairing");
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
		if (activity == ActivityKind.NEEDS_TOOL || activity == ActivityKind.MAKING_WOODEN_TOOL || activity == ActivityKind.MAKING_STONE_TOOL
				|| activity == ActivityKind.TOOL_NO_MATERIALS || activity == ActivityKind.TOOL_NO_SPACE) {
			String tool = dev.villageheart.core.worker.JobType.byId(jobId).flatMap(dev.villageheart.core.worker.JobType::tool)
					.map(t -> t.kind().id()).orElse("");
			String specific = "activity." + name + "." + tool;
			return EN.containsKey(PREFIX + specific) ? PREFIX + specific : key("activity." + name);
		}
		if (activity == ActivityKind.NO_WORK || activity == ActivityKind.AWAY_WORKING || activity == ActivityKind.SITE_UNREACHABLE) {
			String specific = "activity." + name + "." + jobId;
			return EN.containsKey(PREFIX + specific) ? PREFIX + specific : key("activity." + name);
		}
		return key("activity." + name);
	}
}
