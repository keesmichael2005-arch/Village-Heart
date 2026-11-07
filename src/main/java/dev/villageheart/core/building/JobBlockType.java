package dev.villageheart.core.building;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** The three new job blocks (the other jobs reuse vanilla workstations, DESIGN section 3.4). */
public enum JobBlockType {
	CHOPPING_BLOCK("chopping_block", "Chopping Block", BuildingType.LUMBER_CAMP),
	BUILDERS_TABLE("builders_table", "Builder's Table", BuildingType.BUILDERS_HUT),
	MARKET_STALL("market_stall", "Market Stall", BuildingType.MARKET);

	private final String id;
	private final String displayName;
	private final BuildingType building;

	JobBlockType(String id, String displayName, BuildingType building) {
		this.id = id;
		this.displayName = displayName;
		this.building = building;
	}

	public String id() { return id; }
	public String displayName() { return displayName; }
	public BuildingType building() { return building; }

	public static Optional<JobBlockType> byId(String id) {
		for (JobBlockType t : values()) if (t.id.equals(id)) return Optional.of(t);
		return Optional.empty();
	}

	public ShapedSpec recipe() {
		Map<Character, String> k = new LinkedHashMap<>();
		switch (this) {
			case CHOPPING_BLOCK -> {
				k.put('A', "#minecraft:axes");
				k.put('L', "#minecraft:logs");
				return new ShapedSpec(java.util.List.of("A", "L"), k, "villageheart:" + id, 1);
			}
			case BUILDERS_TABLE -> {
				k.put('P', "minecraft:paper");
				k.put('B', "minecraft:brick");
				k.put('C', "minecraft:crafting_table");
				return new ShapedSpec(java.util.List.of(" P ", "BCB"), k, "villageheart:" + id, 1);
			}
			default -> {
				k.put('W', "#minecraft:wool");
				k.put('P', "#minecraft:planks");
				k.put('E', "minecraft:emerald");
				return new ShapedSpec(java.util.List.of("WWW", "PEP"), k, "villageheart:" + id, 1);
			}
		}
	}
}
