package dev.villageheart.core.guide;

/** One row of the Heart's guide, in the order a new village meets it. Ids are save data: never rename one. */
public enum GuideTask {
	OPEN_HEART("open_heart", Chapter.HAMLET, "Open the Heart", "Right-click the Heart.", null, null,
			"Stand next to the Heart", "Right-click it"),
	BLANK_MARKER("blank_marker", Chapter.HAMLET, "Craft a Blank Marker", "Eight planks around an item frame.",
			"villageheart:blank_marker", null,
			"Item frame in the middle", "Planks in the other eight slots", "Take it into your inventory"),
	HOUSE_MARKER("house_marker", Chapter.HAMLET, "Craft a House Marker", "Blank Marker, planks, and white wool.",
			"villageheart:house_marker", "house",
			"Blank Marker in the middle", "White wool on the edges", "Planks in the corners", "Take it into your inventory"),
	HOUSE("house", Chapter.HAMLET, "Make a House", "A bed and a light. Green means it counts.", null, null,
			"Place the marker above the door", "Put a bed and a light inside", "The plaque turns green"),
	STORAGE_MARKER("storage_marker", Chapter.HAMLET, "Craft a Storage Marker", "Blank Marker, planks, and chests.",
			"villageheart:storage_marker", "storage",
			"Blank Marker in the middle", "Chests on the edges", "Planks in the corners", "Take it into your inventory"),
	STORAGE("storage", Chapter.HAMLET, "Make a Storage", "Two chests inside. Green marker.", null, null,
			"Place the marker above the door", "Put two chests or barrels inside", "The plaque turns green"),
	EMERALDS("emeralds", Chapter.HAMLET, "Put emeralds in the Heart", "Hired villagers do not trade.", null, null,
			"Right-click the Heart", "Put emeralds in the treasury"),
	FARMHOUSE_MARKER("farmhouse_marker", Chapter.HAMLET, "Craft a Farmhouse Marker", "Blank Marker, dirt, and wheat.",
			"villageheart:farmhouse_marker", "farmhouse",
			"Blank Marker in the middle", "Wheat on the edges", "Dirt in the corners", "Take it into your inventory"),
	FARMHOUSE("farmhouse", Chapter.HAMLET, "Make a Farmhouse", "A composter and a chest.", null, null,
			"Place the marker above the door", "Put a composter and a chest inside", "The plaque turns green"),
	FIELD("field", Chapter.HAMLET, "Till and plant a field", "Within 24 blocks of the Farmhouse.", null, null,
			"Finish the Farmhouse first", "Hoe ground within 24 blocks", "Plant a crop"),
	FARMER("farmer", Chapter.HAMLET, "Hire a farmer", "Villagers tab, then the Farmhouse.", null, null,
			"Open the Villagers tab", "Choose a villager", "Send them to the Farmhouse"),
	LUMBER_MARKER("lumber_marker", Chapter.HAMLET, "Craft a Lumber Camp Marker", "Blank Marker, logs, a stone axe, and sticks.",
			"villageheart:lumber_camp_marker", "lumber_camp",
			"Blank Marker in the middle", "Stone axe above it", "Sticks on the other edges", "Logs in the corners"),
	CHOPPING_BLOCK("chopping_block", Chapter.HAMLET, "Craft a Chopping Block", "Any axe on top of a log.",
			"villageheart:chopping_block", "lumber_camp",
			"Put an axe in the grid", "Put a log under it", "Take the block into your inventory"),
	LUMBER_CAMP("lumber_camp", Chapter.HAMLET, "Make a Lumber Camp", "A Chopping Block and a chest.", null, null,
			"Place the marker above the door", "Put the Chopping Block inside", "Add a chest", "The plaque turns green"),
	LUMBERJACK("lumberjack", Chapter.HAMLET, "Hire a lumberjack", "They need an axe, or wood to make one.", null, null,
			"Open the Villagers tab", "Choose a villager", "Send them to the Lumber Camp"),
	KITCHEN_MARKER("kitchen_marker", Chapter.HAMLET, "Craft a Kitchen Marker", "Blank Marker, cobblestone, and coal.",
			"villageheart:kitchen_marker", "kitchen",
			"Blank Marker in the middle", "Coal on the edges", "Cobblestone in the corners", "Take it into your inventory"),
	KITCHEN("kitchen", Chapter.HAMLET, "Make a Kitchen", "A smoker, a chest, and a crafting table.", null, null,
			"Place the marker above the door", "Add a smoker and a chest", "Add a crafting table", "The plaque turns green"),
	COOK("cook", Chapter.HAMLET, "Hire a cook", "Raw food in Storage becomes meals.", null, null,
			"Open the Villagers tab", "Choose a villager", "Send them to the Kitchen"),
	DINNER("dinner", Chapter.HAMLET, "Feed the village at dinner", "Late in the day. Food must be in Storage.", null, null,
			"Put raw food in Storage", "Keep a cook hired", "Wait until late in the day"),
	WAGES("wages", Chapter.HAMLET, "Pay every worker at dawn", "One emerald per worker.", null, null,
			"Count one emerald per worker", "Put them in the Heart", "Leave them there until dawn"),
	VILLAGE_TIER("village_tier", Chapter.VILLAGE, "Reach Village tier", "The goals are on the Overview.", null, null,
			"Open the Overview", "Read the goal list", "Meet each goal"),
	BUILDERS_MARKER("builders_marker", Chapter.VILLAGE, "Craft a Builder's Hut Marker", "Blank Marker, bricks, and ladders.",
			"villageheart:builders_hut_marker", "builders_hut",
			"Blank Marker in the middle", "Ladders on the edges", "Bricks in the corners", "Take it into your inventory"),
	BUILDERS_TABLE("builders_table", Chapter.VILLAGE, "Craft a Builder's Table", "Paper, bricks, and a crafting table.",
			"villageheart:builders_table", "builders_hut",
			"Paper in the top middle", "Brick, crafting table, brick", "Take it into your inventory"),
	BUILDERS_HUT("builders_hut", Chapter.VILLAGE, "Make a Builder's Hut", "A Builder's Table and a chest.", null, null,
			"Place the marker above the door", "Put the Builder's Table inside", "Add a chest", "The plaque turns green"),
	BUILDER("builder", Chapter.VILLAGE, "Hire a builder", "Villagers tab, then the Builder's Hut.", null, null,
			"Open the Villagers tab", "Choose a villager", "Send them to the Builder's Hut"),
	BLUEPRINT_BOOK("blueprint_book", Chapter.VILLAGE, "Craft a Blueprint Book", "A book and two paper. It holds every plan.",
			"villageheart:blueprint_book", null,
			"One book and two paper", "Any shape in the grid", "Take the book into your inventory"),
	HOUSE_MATERIALS("house_materials", Chapter.VILLAGE, "Stock a Small House", "The builder only takes from Storage.", null, null,
			"Open the Blueprint Book", "Read the Small House materials", "Put them in Storage"),
	START_BUILD("start_build", Chapter.VILLAGE, "Start a build", "Green dust is a good spot.", null, null,
			"Open the Blueprint Book", "Pick a plan", "Click green dust on the ground", "Click again to confirm"),
	FINISH_BUILD("finish_build", Chapter.VILLAGE, "Finish a build", "The Buildings tab says what is missing.", null, null,
			"Leave the builder working", "Open Buildings if it pauses", "Restock whatever it names"),
	MINE_MARKER("mine_marker", Chapter.VILLAGE, "Craft a Mine Marker", "Blank Marker, cobblestone, a pickaxe, and rails.",
			"villageheart:mine_marker", "mine",
			"Blank Marker in the middle", "Stone pickaxe above it", "Rails on the other edges", "Cobblestone in the corners"),
	MINE("mine", Chapter.VILLAGE, "Make a Mine", "A stonecutter and a chest.", null, null,
			"Place the marker above the door", "Put a stonecutter inside", "Add a chest", "The plaque turns green"),
	MINESHAFT_MARKER("mineshaft_marker", Chapter.VILLAGE, "Craft a Mineshaft Marker", "Blank Marker, rails, and torches.",
			"villageheart:mineshaft_marker", "mineshaft",
			"Blank Marker in the middle", "Torches on the edges", "Rails in the corners", "Take it into your inventory"),
	MINESHAFT("mineshaft", Chapter.VILLAGE, "Dig a Mineshaft", "Twelve blocks down from the marker.", null, null,
			"Place the marker on the ground", "Dig straight down 12 blocks", "The shaft turns green"),
	MINER("miner", Chapter.VILLAGE, "Hire a miner", "They need a pickaxe.", null, null,
			"Open the Villagers tab", "Choose a villager", "Send them to the Mine"),
	LODGE_MARKER("lodge_marker", Chapter.VILLAGE, "Craft a Lodge Marker", "Blank Marker, leather, and arrows.",
			"villageheart:hunting_lodge_marker", "hunting_lodge",
			"Blank Marker in the middle", "Arrows on the edges", "Leather in the corners", "Take it into your inventory"),
	HUNTING("hunting", Chapter.VILLAGE, "Make a Hunting Lodge", "A fletching table and a chest.", null, null,
			"Place the marker above the door", "Put a fletching table inside", "Add a chest", "The plaque turns green"),
	GROVE_MARKER("grove_marker", Chapter.VILLAGE, "Craft a Grove Marker", "Blank Marker, sticks, and feathers.",
			"villageheart:hunting_ground_marker", "hunting_ground",
			"Blank Marker in the middle", "Feathers on the edges", "Sticks in the corners", "Take it into your inventory"),
	HUNTING_GROUND("hunting_ground", Chapter.VILLAGE, "Make a Hunting Grove", "Logs and leaves beside the lodge.", null, null,
			"Place the marker by the lodge", "Add logs and leaves around it", "The marker turns green"),
	HUNTER("hunter", Chapter.VILLAGE, "Hire a hunter", "They need a bow or a sword.", null, null,
			"Open the Villagers tab", "Choose a villager", "Send them to the lodge"),
	BARRACKS_MARKER("barracks_marker", Chapter.VILLAGE, "Craft a Barracks Marker", "Blank Marker, cobblestone, and iron.",
			"villageheart:barracks_marker", "barracks",
			"Blank Marker in the middle", "Iron ingots on the edges", "Cobblestone in the corners", "Take it into your inventory"),
	BARRACKS("barracks", Chapter.VILLAGE, "Make a Barracks", "A grindstone, beds, and a chest.", null, null,
			"Place the marker above the door", "Add a grindstone and a chest", "Add a bed for each guard", "The plaque turns green"),
	YARD_MARKER("yard_marker", Chapter.VILLAGE, "Craft a Training Yard Marker", "Blank Marker, hay, and a wooden sword.",
			"villageheart:training_yard_marker", "training_yard",
			"Blank Marker in the middle", "Wooden sword on the edges", "Hay bales in the corners", "Take it into your inventory"),
	TRAINING_YARD("training_yard", Chapter.VILLAGE, "Make a Training Yard", "Two targets. The Barracks plan includes them.", null, null,
			"Place the marker beside the Barracks", "Put two targets within 6 blocks", "The marker turns green"),
	GUARD("guard", Chapter.VILLAGE, "Hire a guard", "Hire in daylight. The first guards work nights.", null, null,
			"Open the Villagers tab in daylight", "Choose a villager", "Send them to the Barracks"),
	MARKET_MARKER("market_marker", Chapter.VILLAGE, "Craft a Market Marker", "Blank Marker, white wool, and emeralds.",
			"villageheart:market_marker", "market",
			"Blank Marker in the middle", "Emeralds on the edges", "White wool in the corners", "Take it into your inventory"),
	MARKET_STALL("market_stall", Chapter.VILLAGE, "Craft a Market Stall", "Wool, planks, and an emerald.",
			"villageheart:market_stall", "market",
			"Three wool on the top row", "Plank, emerald, plank under them", "Take it into your inventory"),
	MARKET("market", Chapter.VILLAGE, "Make a Market", "A Market Stall and a chest.", null, null,
			"Place the marker above the door", "Put the Market Stall inside", "Add a chest", "The plaque turns green"),
	MERCHANT("merchant", Chapter.VILLAGE, "Hire a merchant", "Surplus in Storage is sold for emeralds.", null, null,
			"Open the Villagers tab", "Choose a villager", "Send them to the Market"),
	WALL("wall", Chapter.VILLAGE, "Build a wall", "Wall Segment, under Defence in the book.", null, null,
			"Open the Blueprint Book", "Choose Defence, then Wall Segment", "Place the line", "Wait for the builder"),
	GATE("gate", Chapter.VILLAGE, "Build a gate", "Workers can open it. Mobs cannot.", null, null,
			"Open the Blueprint Book", "Choose Defence, then Gate", "Set it in the wall", "Wait for the builder"),
	LAMP("lamp", Chapter.VILLAGE, "Build a lamp post", "It stops monster spawns nearby.", null, null,
			"Open the Blueprint Book", "Choose Defence, then Lamp Post", "Place it inside the wall", "Wait for the builder"),
	TOWN_TIER("town_tier", Chapter.TOWN, "Reach Town tier", "The goals are on the Overview.", null, null,
			"Open the Overview", "Read the Town goals", "Meet each goal"),
	SMITHY_MARKER("smithy_marker", Chapter.TOWN, "Craft a Smithy Marker", "Blank Marker, iron, and smooth stone.",
			"villageheart:smithy_marker", "smithy",
			"Blank Marker in the middle", "Smooth stone on the edges", "Iron ingots in the corners", "Take it into your inventory"),
	SMITHY("smithy", Chapter.TOWN, "Make a Smithy", "A blast furnace, an anvil, and a chest.", null, null,
			"Place the marker above the door", "Add a blast furnace and an anvil", "Add a chest", "The plaque turns green"),
	BLACKSMITH("blacksmith", Chapter.TOWN, "Hire a blacksmith", "They make gear and golems.", null, null,
			"Open the Villagers tab", "Choose a villager", "Send them to the Smithy"),
	GOLEM("golem", Chapter.TOWN, "Have a golem built", "36 iron ingots and a carved pumpkin, in Storage.", null, null,
			"Put 36 iron ingots in Storage", "Add a carved pumpkin", "The blacksmith builds it"),
	WATCHTOWER("watchtower", Chapter.TOWN, "Build a Watchtower", "Guards patrol it.", null, null,
			"Open the Blueprint Book", "Choose Defence, then Watchtower", "Place it on the wall", "Wait for the builder");

	public enum Chapter { HAMLET, VILLAGE, TOWN }

	private final String id;
	private final Chapter chapter;
	private final String title;
	private final String hint;
	/** Item that completes this step as soon as it is in an inventory. Null when the step is not a craft. */
	private final String itemId;
	/** A building of this type also completes a craft step, once the item has been used. */
	private final String countsAs;
	private final String[] steps;

	GuideTask(String id, Chapter chapter, String title, String hint, String itemId, String countsAs, String... steps) {
		this.id = id;
		this.chapter = chapter;
		this.title = title;
		this.hint = hint;
		this.itemId = itemId;
		this.countsAs = countsAs;
		this.steps = steps.clone();
	}

	public String id() { return id; }
	public Chapter chapter() { return chapter; }
	public String title() { return title; }
	public String hint() { return hint; }
	public String itemId() { return itemId; }
	public String countsAs() { return countsAs; }
	public String[] steps() { return steps.clone(); }

	/** Numbered steps for the Heart's detail box. */
	public String detail() {
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < steps.length; i++) {
			if (i > 0) out.append('\n');
			out.append(i + 1).append(". ").append(steps[i]);
		}
		return out.toString();
	}

	public static java.util.Optional<GuideTask> byId(String id) {
		for (GuideTask t : values()) if (t.id.equals(id)) return java.util.Optional.of(t);
		return java.util.Optional.empty();
	}

	public static boolean isGuideItem(String itemId) {
		for (GuideTask t : values()) if (itemId.equals(t.itemId)) return true;
		return false;
	}
}
