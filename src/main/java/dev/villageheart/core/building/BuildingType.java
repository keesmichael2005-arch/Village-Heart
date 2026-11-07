package dev.villageheart.core.building;

import dev.villageheart.core.Tier;

import java.util.Optional;

import static dev.villageheart.core.Tier.HAMLET;
import static dev.villageheart.core.Tier.TOWN;
import static dev.villageheart.core.Tier.VILLAGE;
import static dev.villageheart.core.building.BuildingKind.OUTDOOR_SITE;
import static dev.villageheart.core.building.BuildingKind.ROOM;

/** Every building and outdoor site. The single source for registration, assets, recipes and (later) validation. */
public enum BuildingType {
	HOUSE("house", "House", HAMLET, ROOM, Ring.uniform("#minecraft:planks", "minecraft:white_wool"), null),
	STORAGE("storage", "Storage", HAMLET, ROOM, Ring.uniform("#minecraft:planks", "minecraft:chest"), null),
	FARMHOUSE("farmhouse", "Farmhouse", HAMLET, ROOM, Ring.uniform("minecraft:dirt", "minecraft:wheat"), null),
	LUMBER_CAMP("lumber_camp", "Lumber Camp", HAMLET, ROOM, Ring.withTool("#minecraft:logs", "minecraft:stone_axe", "minecraft:stick"), null),
	KITCHEN("kitchen", "Kitchen", HAMLET, ROOM, Ring.uniform("minecraft:cobblestone", "minecraft:coal"), null),
	BUILDERS_HUT("builders_hut", "Builder's Hut", VILLAGE, ROOM, Ring.uniform("minecraft:brick", "minecraft:ladder"), null),
	MINE("mine", "Mine", VILLAGE, ROOM, Ring.withTool("minecraft:cobblestone", "minecraft:stone_pickaxe", "minecraft:rail"), null),
	HUNTING_LODGE("hunting_lodge", "Hunting Lodge", VILLAGE, ROOM, Ring.uniform("minecraft:leather", "minecraft:arrow"), null),
	BARRACKS("barracks", "Barracks", VILLAGE, ROOM, Ring.uniform("minecraft:cobblestone", "minecraft:iron_ingot"), null),
	MARKET("market", "Market", VILLAGE, ROOM, Ring.uniform("minecraft:white_wool", "minecraft:emerald"), null),
	SMITHY("smithy", "Smithy", TOWN, ROOM, Ring.uniform("minecraft:iron_ingot", "minecraft:smooth_stone"), null),
	MINESHAFT("mineshaft", "Mineshaft", VILLAGE, OUTDOOR_SITE, Ring.uniform("minecraft:rail", "minecraft:torch"), "mine"),
	HUNTING_GROUND("hunting_ground", "Hunting Ground", VILLAGE, OUTDOOR_SITE, Ring.uniform("minecraft:stick", "minecraft:feather"), "hunting_lodge"),
	TRAINING_YARD("training_yard", "Training Yard", VILLAGE, OUTDOOR_SITE, Ring.uniform("minecraft:hay_block", "minecraft:wooden_sword"), "barracks");

	private final String id;
	private final String displayName;
	private final Tier tier;
	private final BuildingKind kind;
	private final Ring ring;
	private final String parentId;

	BuildingType(String id, String displayName, Tier tier, BuildingKind kind, Ring ring, String parentId) {
		this.id = id;
		this.displayName = displayName;
		this.tier = tier;
		this.kind = kind;
		this.ring = ring;
		this.parentId = parentId;
	}

	/** Snake_case and stable: part of save data and of every asset path. */
	public String id() { return id; }
	public String displayName() { return displayName; }
	public Tier tier() { return tier; }
	public BuildingKind kind() { return kind; }
	public Ring ring() { return ring; }
	public Optional<BuildingType> parent() { return parentId == null ? Optional.empty() : byId(parentId); }
	public String parentId() { return parentId; }
	/** Block id, item id, recipe id and texture base name, e.g. {@code lumber_camp_marker}. */
	public String markerId() { return id + "_marker"; }

	public static Optional<BuildingType> byId(String id) {
		for (BuildingType t : values()) if (t.id.equals(id)) return Optional.of(t);
		return Optional.empty();
	}
}
