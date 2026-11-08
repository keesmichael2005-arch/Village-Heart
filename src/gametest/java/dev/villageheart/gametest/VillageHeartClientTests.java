package dev.villageheart.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * Layer 4: launches the real game client (on a virtual display in CI / cloud),
 * creates a world, places the Village Heart in front of the player and screenshots it.
 * Screenshots land in build/run/clientGameTest/screenshots/ for agents to inspect.
 */
public class VillageHeartClientTests implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		// debugging aid: -Dvillageheart.l4.only=build runs only the Builder's Table flow (default: everything, as verify.sh does)
		if ("build".equals(System.getProperty("villageheart.l4.only"))) {
			BuildClientTests.builderFlow(context);
			return;
		}
		if ("levels".equals(System.getProperty("villageheart.l4.only"))) {
			LevelClientTests.levelBadges(context);
			LevelClientTests.villagersTabLevels(context);
			return;
		}
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getClientWorld().waitForChunksRender();
			sp.getServer().runCommand("time set day");
			sp.getServer().runCommand("execute as @a at @s run setblock ^ ^1 ^3 villageheart:village_heart");
			sp.getServer().runCommand("execute as @a at @s run setblock ^2 ^1 ^3 minecraft:bell");
			context.waitTicks(40);
			sp.getClientWorld().waitForChunksRender();
			context.takeScreenshot("village_heart_placed");
		}
		refusedPlacementKeepsClientItem(context);
		MarkerClientTests.markerWall(context);
		MarkerClientTests.markerItems(context);
		MarkerClientTests.validationReport(context);
		WorkerClientTests.workersAtJobBlocks(context);
		LumberClientTests.lumberjackFelling(context);
		FarmerClientTests.farmerField(context);
		HeartScreenClientTests.heartTabs(context);
		BuildClientTests.builderFlow(context);
		LumberClientTests.villagersNeedsAxe(context);
		LevelClientTests.levelBadges(context);
		LevelClientTests.villagersTabLevels(context);
		MinerClientTests.villagersTabAwayMining(context);
		RanksClientTests.ranksTab(context);
		MultiplayerClientTests.ownerAndResident(context);
	}

	/** T-L4-1: a survival player right-clicks a Village Heart onto the ground with no bell near. */
	private void refusedPlacementKeepsClientItem(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getClientWorld().waitForChunksRender();
			sp.getServer().runCommand("time set day");
			sp.getServer().runCommand("gamemode survival @a");
			sp.getServer().runCommand("clear @a");
			sp.getServer().runCommand("give @a villageheart:village_heart 1");
			sp.getServer().runCommand("tp @a ~0.5 ~ ~0.5 0 60");
			context.waitTicks(20);
			sp.getClientWorld().waitForChunksRender();
			// Precondition: the client must be able to predict the placement (target cell free of the player's hitbox),
			// otherwise the ghost-item case is never exercised.
			boolean clientCanPredict = context.computeOnClient(mc -> {
				if (!(mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit)
						|| hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) return false;
				var target = hit.getBlockPos().relative(hit.getDirection());
				return mc.level.isUnobstructed(dev.villageheart.block.ModBlocks.VILLAGE_HEART.defaultBlockState(), target,
						net.minecraft.world.phys.shapes.CollisionContext.of(mc.player));
			});
			if (!clientCanPredict) throw new AssertionError("precondition: client could not predict the Heart placement (player in the way or no block targeted)");
			context.getInput().pressMouse(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT);
			context.waitTicks(5);
			context.takeScreenshot("heart_refused_no_bell");
			context.waitTicks(10);

			int count = context.computeOnClient(mc -> mc.player.getMainHandItem().getCount());
			boolean heartNearby = context.computeOnClient(mc -> {
				var origin = mc.player.blockPosition();
				for (var p : net.minecraft.core.BlockPos.betweenClosed(origin.offset(-6, -3, -6), origin.offset(6, 3, 6))) {
					if (mc.level.getBlockState(p).is(dev.villageheart.block.ModBlocks.VILLAGE_HEART)) return true;
				}
				return false;
			});
			boolean serverHeart = sp.getServer().computeOnServer(server -> {
				var pl = server.getPlayerList().getPlayers().get(0);
				var origin = pl.blockPosition();
				for (var p : net.minecraft.core.BlockPos.betweenClosed(origin.offset(-6, -3, -6), origin.offset(6, 3, 6))) {
					if (pl.level().getBlockState(p).is(dev.villageheart.block.ModBlocks.VILLAGE_HEART)) return true;
				}
				return false;
			});
			if (serverHeart) throw new AssertionError("server placed a Heart although no bell is near");
			if (heartNearby) throw new AssertionError("ghost Village Heart stayed on the client");
			if (count != 1) throw new AssertionError("client main hand count after refusal is " + count + ", expected 1");
		}
	}
}
