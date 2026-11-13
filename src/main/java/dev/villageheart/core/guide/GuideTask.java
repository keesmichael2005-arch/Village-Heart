package dev.villageheart.core.guide;

/** One row of the Heart's guide, in the order a new village meets it. Ids are save data: never rename one. */
public enum GuideTask {
	OPEN_HEART("open_heart", Chapter.HAMLET, "Open the Heart", "Right-click the Heart."),
	BLANK_MARKER("blank_marker", Chapter.HAMLET, "Craft a Blank Marker", "Search the recipe book."),
	HOUSE("house", Chapter.HAMLET, "Make a House", "Place the marker above the door. Green means it counts."),
	STORAGE("storage", Chapter.HAMLET, "Make a Storage", "Two chests inside. Green marker."),
	EMERALDS("emeralds", Chapter.HAMLET, "Put emeralds in the Heart", "Hired villagers do not trade."),
	FIELD("field", Chapter.HAMLET, "Till and plant a field", "Within 24 blocks of the Farmhouse."),
	FARMHOUSE("farmhouse", Chapter.HAMLET, "Make a Farmhouse", "A composter and a chest."),
	FARMER("farmer", Chapter.HAMLET, "Hire a farmer", "Villagers tab, then the Farmhouse."),
	LUMBER_CAMP("lumber_camp", Chapter.HAMLET, "Make a Lumber Camp", "A Chopping Block and a chest."),
	LUMBERJACK("lumberjack", Chapter.HAMLET, "Hire a lumberjack", "They need an axe, or wood to make one."),
	KITCHEN("kitchen", Chapter.HAMLET, "Make a Kitchen", "A smoker, a chest, and a crafting table."),
	COOK("cook", Chapter.HAMLET, "Hire a cook", "Raw food in Storage becomes meals."),
	DINNER("dinner", Chapter.HAMLET, "Feed the village at dinner", "Late in the day. Food must be in Storage."),
	WAGES("wages", Chapter.HAMLET, "Pay every worker at dawn", "One emerald per worker."),
	VILLAGE_TIER("village_tier", Chapter.VILLAGE, "Reach Village tier", "The goals are on the Overview."),
	BUILDERS_HUT("builders_hut", Chapter.VILLAGE, "Make a Builder's Hut", "A Builder's Table and a chest."),
	BUILDER("builder", Chapter.VILLAGE, "Hire a builder", "Villagers tab, then the Builder's Hut."),
	BLUEPRINT_BOOK("blueprint_book", Chapter.VILLAGE, "Craft a Blueprint Book", "A book and two paper. It holds every plan."),
	HOUSE_MATERIALS("house_materials", Chapter.VILLAGE, "Stock a Small House", "The builder only takes from Storage."),
	START_BUILD("start_build", Chapter.VILLAGE, "Start a build", "Open the book, pick a plan, then click the ground. Green dust is a good spot."),
	FINISH_BUILD("finish_build", Chapter.VILLAGE, "Finish a build", "If it pauses, the Buildings tab says what is missing."),
	MINE("mine", Chapter.VILLAGE, "Make a Mine and a Mineshaft", "The Mine is a plan. Dig the shaft 12 blocks down."),
	MINER("miner", Chapter.VILLAGE, "Hire a miner", "They need a pickaxe."),
	HUNTING("hunting", Chapter.VILLAGE, "Make a lodge and a hunting ground", "The Hunting Lodge, then the Hunting Grove beside it."),
	HUNTER("hunter", Chapter.VILLAGE, "Hire a hunter", "They need a bow or a sword."),
	BARRACKS("barracks", Chapter.VILLAGE, "Make a Barracks", "The Barracks plan includes the yard beside the door."),
	TRAINING_YARD("training_yard", Chapter.VILLAGE, "Make a Training Yard", "Two targets on that yard. Guards train there."),
	GUARD("guard", Chapter.VILLAGE, "Hire a guard", "Hire in daylight. The first guards work nights."),
	MARKET("market", Chapter.VILLAGE, "Make a Market", "A Market Stall and a chest."),
	MERCHANT("merchant", Chapter.VILLAGE, "Hire a merchant", "Surplus in Storage is sold for emeralds."),
	WALL("wall", Chapter.VILLAGE, "Build a wall", "Wall Segment, under Defence in the book."),
	GATE("gate", Chapter.VILLAGE, "Build a gate", "Workers can open it. Mobs cannot."),
	LAMP("lamp", Chapter.VILLAGE, "Build a lamp post", "It stops monster spawns nearby."),
	TOWN_TIER("town_tier", Chapter.TOWN, "Reach Town tier", "The goals are on the Overview."),
	SMITHY("smithy", Chapter.TOWN, "Make a Smithy", "A blast furnace, an anvil, and a chest."),
	BLACKSMITH("blacksmith", Chapter.TOWN, "Hire a blacksmith", "They make gear and golems."),
	GOLEM("golem", Chapter.TOWN, "Have a golem built", "36 iron ingots and a carved pumpkin, in Storage."),
	WATCHTOWER("watchtower", Chapter.TOWN, "Build a Watchtower", "Guards patrol it.");

	public enum Chapter { HAMLET, VILLAGE, TOWN }

	private final String id;
	private final Chapter chapter;
	private final String title;
	private final String hint;

	GuideTask(String id, Chapter chapter, String title, String hint) {
		this.id = id;
		this.chapter = chapter;
		this.title = title;
		this.hint = hint;
	}

	public String id() { return id; }
	public Chapter chapter() { return chapter; }
	public String title() { return title; }
	public String hint() { return hint; }

	public static java.util.Optional<GuideTask> byId(String id) {
		for (GuideTask t : values()) if (t.id.equals(id)) return java.util.Optional.of(t);
		return java.util.Optional.empty();
	}
}
