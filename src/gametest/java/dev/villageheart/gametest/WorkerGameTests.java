package dev.villageheart.gametest;

import dev.villageheart.core.worker.JobType;
import dev.villageheart.worker.ModProfessions;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * Layer 2: hiring and the worker framework (M7). Rules (docs/TESTING.md, "Worker GameTests"): one environment per test that touches
 * the claim, wait for POIs, schedule every callback from the test body (never runAfterDelay inside a callback), light the area for
 * night tests.
 */
public class WorkerGameTests {
	// ---- T-L2-1 ----------------------------------------------------------------------------------

	@GameTest
	public void professionsRegistered(GameTestHelper h) {
		for (JobType j : JobType.values()) {
			h.assertTrue(BuiltInRegistries.VILLAGER_PROFESSION.containsKey(ModProfessions.key(j)), "profession registered for " + j.id());
			h.assertValueEqual("villageheart:" + j.id(), ModProfessions.key(j).identifier().toString(), "key of " + j.id());
		}
		for (var poi : BuiltInRegistries.POINT_OF_INTEREST_TYPE.listElements().toList()) {
			for (JobType j : JobType.values()) {
				var prof = ModProfessions.holder(j).value();
				h.assertFalse(prof.heldJobSite().test(poi), j.id() + " holds job site " + poi.getRegisteredName());
				h.assertFalse(prof.acquirableJobSite().test(poi), j.id() + " can acquire " + poi.getRegisteredName());
			}
		}
		for (JobType j : JobType.values()) {
			Villager v = h.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
			v.setVillagerData(v.getVillagerData().withProfession(ModProfessions.holder(j)));
			h.assertTrue(v.getOffers().isEmpty(), j.id() + " has no trades");
			v.discard();
		}
		h.succeed();
	}
}
