package dev.villageheart.gametest;

import dev.villageheart.block.ModBlocks;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.core.ClaimRules;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/** Layer 2: real (headless) Minecraft server, real blocks, real POI system. */
public class VillageHeartGameTests {
	@GameTest(environment = "villageheart-gametest:heart_needs_bell")
	public void heartNeedsBellToClaim(GameTestHelper helper) {
		VillageTestSupport.reset(helper);
		BlockPos heart = new BlockPos(2, 1, 2);
		helper.setBlock(heart, ModBlocks.VILLAGE_HEART);
		BlockPos abs = helper.absolutePos(heart);

		helper.assertValueEqual(ClaimRules.Result.NO_BELL_NEARBY,
				VillageClaims.evaluate(helper.getLevel(), abs), "claim without bell");

		helper.setBlock(new BlockPos(4, 1, 2), Blocks.BELL);
		helper.runAfterDelay(2, () -> {
			helper.assertValueEqual(ClaimRules.Result.CLAIMED,
					VillageClaims.evaluate(helper.getLevel(), abs), "claim next to bell");
			VillageTestSupport.done(helper);
		});
	}

	/** T-L2-10: two tests that each hold a claim for 20 ticks. Fails if their batches overlap. */
	private void isolationProbe(GameTestHelper h, String name) {
		VillageTestSupport.reset(h);
		ServerPlayer owner = VillageTestSupport.player(h, name, GameType.SURVIVAL);
		h.setBlock(new BlockPos(4, 1, 2), Blocks.BELL);
		h.runAfterDelay(2, () -> {
			VillageTestSupport.place(h, owner, new BlockPos(2, 0, 2));
			h.runAfterDelay(20, () -> {
				var v = VillageClaims.current(h.getLevel().getServer());
				h.assertTrue(v.isPresent(), "claim still present after 20 ticks (" + name + ")");
				h.assertValueEqual(owner.getUUID(), v.get().owner(), "owner is still " + name);
				h.assertValueEqual(VillageClaims.locate(h.getLevel(), h.absolutePos(new BlockPos(2, 1, 2))), v.get().heart(), "heart location");
				VillageTestSupport.done(h);
			});
		});
	}

	@GameTest(environment = "villageheart-gametest:probe_a", maxTicks = 200)
	public void isolationProbeA(GameTestHelper h) { isolationProbe(h, "probe_a"); }

	@GameTest(environment = "villageheart-gametest:probe_b", maxTicks = 200)
	public void isolationProbeB(GameTestHelper h) { isolationProbe(h, "probe_b"); }
}
