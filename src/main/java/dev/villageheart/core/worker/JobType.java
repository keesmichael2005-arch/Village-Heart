package dev.villageheart.core.worker;

import dev.villageheart.core.Tier;
import dev.villageheart.core.building.BuildingRequirements;
import dev.villageheart.core.building.BuildingType;
import java.util.Optional;

/** The nine jobs of DESIGN section 3.4: id, display name, the building that offers it and the content key of its job block. */
public enum JobType {
	FARMER("farmer", "Farmer", BuildingType.FARMHOUSE, BuildingRequirements.COMPOSTER),
	LUMBERJACK("lumberjack", "Lumberjack", BuildingType.LUMBER_CAMP, BuildingRequirements.CHOPPING_BLOCK),
	COOK("cook", "Cook", BuildingType.KITCHEN, BuildingRequirements.SMOKER),
	BUILDER("builder", "Builder", BuildingType.BUILDERS_HUT, BuildingRequirements.BUILDERS_TABLE),
	MINER("miner", "Miner", BuildingType.MINE, BuildingRequirements.STONECUTTER),
	HUNTER("hunter", "Hunter", BuildingType.HUNTING_LODGE, BuildingRequirements.FLETCHING_TABLE),
	GUARD("guard", "Guard", BuildingType.BARRACKS, BuildingRequirements.GRINDSTONE),
	MERCHANT("merchant", "Merchant", BuildingType.MARKET, BuildingRequirements.MARKET_STALL),
	BLACKSMITH("blacksmith", "Blacksmith", BuildingType.SMITHY, BuildingRequirements.BLAST_FURNACE);

	private final String id;
	private final String displayName;
	private final BuildingType building;
	private final String jobBlockKey;

	JobType(String id, String displayName, BuildingType building, String jobBlockKey) {
		this.id = id;
		this.displayName = displayName;
		this.building = building;
		this.jobBlockKey = jobBlockKey;
	}

	/** Snake_case and stable: part of save data, profession ids and asset paths. */
	public String id() { return id; }
	public String displayName() { return displayName; }
	public BuildingType building() { return building; }
	/** Namespaced block id of the job block, the room scanner's content key. */
	public String jobBlockKey() { return jobBlockKey; }
	public Tier tier() { return building.tier(); }

	public static Optional<JobType> byId(String id) {
		for (JobType t : values()) if (t.id.equals(id)) return Optional.of(t);
		return Optional.empty();
	}
}
