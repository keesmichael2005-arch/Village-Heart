package dev.villageheart.gametest;

import dev.villageheart.block.ModBlocks;
import dev.villageheart.block.VillageHeartBlock;
import dev.villageheart.core.ClaimRules;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

/** Layer 2: real (headless) Minecraft server, real blocks, real POI system. */
public class VillageHeartGameTests {
	@GameTest
	public void heartNeedsBellToClaim(GameTestHelper helper) {
		BlockPos heart = new BlockPos(2, 1, 2);
		helper.setBlock(heart, ModBlocks.VILLAGE_HEART);
		BlockPos abs = helper.absolutePos(heart);

		helper.assertValueEqual(ClaimRules.Result.NO_BELL_NEARBY,
				VillageHeartBlock.tryClaim(helper.getLevel(), abs), "claim without bell");

		helper.setBlock(new BlockPos(4, 1, 2), Blocks.BELL);
		helper.runAfterDelay(2, () -> {
			helper.assertValueEqual(ClaimRules.Result.CLAIMED,
					VillageHeartBlock.tryClaim(helper.getLevel(), abs), "claim next to bell");
			helper.succeed();
		});
	}
}
