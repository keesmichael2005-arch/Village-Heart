package dev.villageheart.core.building;

import static dev.villageheart.core.building.Requirements.ContentRule.need;

import dev.villageheart.core.building.Requirements.ContentRule;
import dev.villageheart.core.building.Requirements.DetailLine;
import dev.villageheart.core.building.Requirements.SlotRule;
import dev.villageheart.core.room.RoomContents;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The requirement table of DESIGN section 3.3: one entry per ROOM building type. */
public final class BuildingRequirements {
	public static final String CHEST = RoomContents.CHEST;
	public static final String BED = RoomContents.BED;
	public static final String LIGHT = RoomContents.LIGHT;
	public static final String BARREL = "minecraft:barrel";
	public static final String COMPOSTER = "minecraft:composter";
	public static final String SMOKER = "minecraft:smoker";
	public static final String CRAFTING_TABLE = "minecraft:crafting_table";
	public static final String STONECUTTER = "minecraft:stonecutter";
	public static final String FLETCHING_TABLE = "minecraft:fletching_table";
	public static final String GRINDSTONE = "minecraft:grindstone";
	public static final String BLAST_FURNACE = "minecraft:blast_furnace";
	public static final String TARGET = "minecraft:target";
	public static final String ANVIL = "#anvil";
	public static final String RAIL = "#rail";
	public static final String NATURAL = "#natural";
	public static final String CHOPPING_BLOCK = "villageheart:" + JobBlockType.CHOPPING_BLOCK.id();
	public static final String BUILDERS_TABLE = "villageheart:" + JobBlockType.BUILDERS_TABLE.id();
	public static final String MARKET_STALL = "villageheart:" + JobBlockType.MARKET_STALL.id();

	private static final Map<BuildingType, Requirements> TABLE = new EnumMap<>(BuildingType.class);

	static {
		put(BuildingType.HOUSE, new Requirements(12, List.of(need(Thing.BED, 1, BED), need(Thing.LIGHT, 1, LIGHT)),
				null, SlotRule.BEDS, null, DetailLine.HOMES));
		put(BuildingType.STORAGE, new Requirements(9, List.of(need(Thing.STORAGE_CONTAINER, 2, CHEST, BARREL)),
				null, SlotRule.CONTAINERS, null, DetailLine.CONTAINERS));
		put(BuildingType.FARMHOUSE, job(9, COMPOSTER, need(Thing.COMPOSTER, 1, COMPOSTER), need(Thing.CHEST, 1, CHEST)));
		put(BuildingType.LUMBER_CAMP, job(9, CHOPPING_BLOCK, need(Thing.CHOPPING_BLOCK, 1, CHOPPING_BLOCK), need(Thing.CHEST, 1, CHEST)));
		put(BuildingType.KITCHEN, job(9, SMOKER, need(Thing.SMOKER, 1, SMOKER), need(Thing.CHEST, 1, CHEST),
				need(Thing.CRAFTING_TABLE, 1, CRAFTING_TABLE)));
		put(BuildingType.BUILDERS_HUT, job(9, BUILDERS_TABLE, need(Thing.BUILDERS_TABLE, 1, BUILDERS_TABLE), need(Thing.CHEST, 1, CHEST)));
		put(BuildingType.MARKET, job(9, MARKET_STALL, need(Thing.MARKET_STALL, 1, MARKET_STALL), need(Thing.CHEST, 1, CHEST)));
		put(BuildingType.SMITHY, job(12, BLAST_FURNACE, need(Thing.BLAST_FURNACE, 1, BLAST_FURNACE), need(Thing.ANVIL, 1, ANVIL),
				need(Thing.CHEST, 1, CHEST)));
		put(BuildingType.MINE, new Requirements(9, List.of(need(Thing.STONECUTTER, 1, STONECUTTER), need(Thing.CHEST, 1, CHEST)),
				STONECUTTER, SlotRule.JOB_BLOCKS, BuildingType.MINESHAFT, DetailLine.WORKER_SLOTS));
		put(BuildingType.HUNTING_LODGE, new Requirements(9, List.of(need(Thing.FLETCHING_TABLE, 1, FLETCHING_TABLE), need(Thing.CHEST, 1, CHEST)),
				FLETCHING_TABLE, SlotRule.JOB_BLOCKS, BuildingType.HUNTING_GROUND, DetailLine.WORKER_SLOTS));
		// the only rule whose minimum scales with another count: one bed per guard slot (grindstone)
		put(BuildingType.BARRACKS, new Requirements(16, List.of(need(Thing.GRINDSTONE, 1, GRINDSTONE),
				new ContentRule(Thing.BED, Set.of(BED), 1, GRINDSTONE), need(Thing.CHEST, 1, CHEST)),
				GRINDSTONE, SlotRule.MIN_JOB_BLOCKS_AND_BEDS, null, DetailLine.WORKER_SLOTS));
	}

	private BuildingRequirements() {}

	private static Requirements job(int floor, String jobKey, ContentRule... rules) {
		return new Requirements(floor, List.of(rules), jobKey, SlotRule.JOB_BLOCKS, null, DetailLine.WORKER_SLOTS);
	}

	private static void put(BuildingType t, Requirements r) { TABLE.put(t, r); }

	/** @throws IllegalArgumentException for an outdoor site (they have their own checks) */
	public static Requirements of(BuildingType t) {
		Requirements r = TABLE.get(t);
		if (r == null) throw new IllegalArgumentException(t + " has no room requirements");
		return r;
	}
}
