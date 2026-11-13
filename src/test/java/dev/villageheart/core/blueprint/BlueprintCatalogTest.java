package dev.villageheart.core.blueprint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BlueprintCatalogTest {
	@Test
	void everyPlanIsInOneGroupInMenuOrder() {
		Set<String> seen = new HashSet<>();
		List<String> flat = new ArrayList<>();
		for (BlueprintGroup group : BlueprintGroup.values()) {
			List<Blueprint> plans = BlueprintCatalog.in(group);
			assertTrue(!plans.isEmpty(), group.name());
			for (Blueprint bp : plans) {
				assertTrue(seen.add(bp.id()), bp.id());
				assertEquals(group, BlueprintCatalog.groupOf(bp));
				flat.add(bp.id());
			}
		}
		assertEquals(Blueprints.all().size(), seen.size());
		assertEquals(flat.size(), seen.size());
		for (BlueprintGroup group : BlueprintGroup.values()) {
			List<String> fromMenu = Blueprints.all().stream().filter(b -> BlueprintCatalog.groupOf(b) == group).map(Blueprint::id).toList();
			assertEquals(fromMenu, BlueprintCatalog.in(group).stream().map(Blueprint::id).toList(), group.name());
		}
	}

	@Test
	void theThreeGroupsMatchTheBuildings() {
		assertEquals(List.of("small_house", "storage_shed", "farmhouse", "lumber_camp", "kitchen", "builders_hut", "mine",
				"hunting_lodge", "barracks", "market", "smithy"), ids(BlueprintGroup.BUILDINGS));
		assertEquals(List.of("farm_plot", "hunting_grove"), ids(BlueprintGroup.PRODUCTION));
		assertEquals(List.of("wall_segment", "gate", "lamp_post", "watchtower"), ids(BlueprintGroup.DEFENCE));
	}

	private static List<String> ids(BlueprintGroup group) {
		return BlueprintCatalog.in(group).stream().map(Blueprint::id).toList();
	}
}
