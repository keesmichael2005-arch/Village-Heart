package dev.villageheart.gametest;

import dev.villageheart.VillageHeart;
import dev.villageheart.build.DesignWorld;
import dev.villageheart.build.VillageHouses;
import dev.villageheart.core.blueprint.Blueprints;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** The vanilla village scan: every template is classified, and only real houses are offered. */
public class VanillaHouseGameTests {
	@GameTest(maxTicks = 100)
	public void everyVillageTemplateIsClassified(GameTestHelper h) {
		var rows = VillageHouses.rows();
		h.assertTrue(rows.size() >= 400, "scanned " + rows.size());
		int pass = 0;
		StringBuilder report = new StringBuilder();
		for (var row : rows) {
			h.assertFalse(row.reason() == null || row.reason().isBlank(), row.id());
			if (row.offered()) {
				pass++;
				h.assertTrue(row.id().contains("/houses/") && !row.id().contains("/zombie/"), row.id());
				String slug = row.id().substring(row.id().lastIndexOf('/') + 1);
				h.assertTrue(Blueprints.byId("vanilla_" + slug).isPresent(), "offered " + row.id());
			} else {
				h.assertTrue(!row.id().contains("/zombie/") || !row.offered(), row.id());
			}
			report.append(row.offered() ? "PASS " : "FAIL ").append(row.id()).append(" — ").append(row.reason()).append('\n');
		}
		VillageHeart.LOG.info("VANILLA_SCAN {} pass of {}\n{}", pass, rows.size(), report);
		h.assertTrue(pass > 0, "no vanilla house passed the room check");
		var server = h.getLevel().getServer();
		VillageHeart.LOG.info("DESIGN_DIMENSION {} levels {}", DesignWorld.available(server) ? "present" : "missing", server.levelKeys());
		h.succeed();
	}
}
