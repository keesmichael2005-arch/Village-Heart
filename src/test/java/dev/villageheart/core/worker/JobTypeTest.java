package dev.villageheart.core.worker;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Tier;
import dev.villageheart.core.building.BuildingKind;
import dev.villageheart.core.building.BuildingRequirements;
import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.building.JobBlockType;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class JobTypeTest {
	@Test
	void matchesDesignTable() {
		assertEquals(9, JobType.values().length);
		check(JobType.FARMER, "farmer", BuildingType.FARMHOUSE, "minecraft:composter");
		check(JobType.LUMBERJACK, "lumberjack", BuildingType.LUMBER_CAMP, "villageheart:chopping_block");
		check(JobType.COOK, "cook", BuildingType.KITCHEN, "minecraft:smoker");
		check(JobType.BUILDER, "builder", BuildingType.BUILDERS_HUT, "villageheart:builders_table");
		check(JobType.MINER, "miner", BuildingType.MINE, "minecraft:stonecutter");
		check(JobType.HUNTER, "hunter", BuildingType.HUNTING_LODGE, "minecraft:fletching_table");
		check(JobType.GUARD, "guard", BuildingType.BARRACKS, "minecraft:grindstone");
		check(JobType.MERCHANT, "merchant", BuildingType.MARKET, "villageheart:market_stall");
		check(JobType.BLACKSMITH, "blacksmith", BuildingType.SMITHY, "minecraft:blast_furnace");
	}

	private static void check(JobType j, String id, BuildingType b, String key) {
		assertEquals(id, j.id());
		assertEquals(b, j.building());
		assertEquals(key, j.jobBlockKey());
	}

	@Test
	void everyJobIsARoomBuildingWhoseSlotKeyIsTheJobBlock() {
		for (JobType j : JobType.values()) {
			assertEquals(BuildingKind.ROOM, j.building().kind(), j.id());
			assertEquals(j.jobBlockKey(), BuildingRequirements.of(j.building()).jobKey(), j.id());
		}
	}

	@Test
	void modJobBlockKeysComeFromJobBlockType() {
		assertEquals("villageheart:" + JobBlockType.CHOPPING_BLOCK.id(), JobType.LUMBERJACK.jobBlockKey());
		assertEquals("villageheart:" + JobBlockType.BUILDERS_TABLE.id(), JobType.BUILDER.jobBlockKey());
		assertEquals("villageheart:" + JobBlockType.MARKET_STALL.id(), JobType.MERCHANT.jobBlockKey());
		for (JobBlockType t : JobBlockType.values()) {
			assertTrue(java.util.Arrays.stream(JobType.values()).anyMatch(j -> j.building() == t.building()), t.id());
		}
	}

	@Test
	void tiersFollowTheBuilding() {
		assertEquals(Tier.HAMLET, JobType.FARMER.tier());
		assertEquals(Tier.VILLAGE, JobType.MINER.tier());
		assertEquals(Tier.TOWN, JobType.BLACKSMITH.tier());
	}

	@Test
	void byIdRoundTripsAndUnknownIsEmpty() {
		for (JobType j : JobType.values()) assertEquals(Optional.of(j), JobType.byId(j.id()));
		assertEquals(Optional.empty(), JobType.byId("wizard"));
		assertEquals(Optional.empty(), JobType.byId("FARMER"));
	}
}
