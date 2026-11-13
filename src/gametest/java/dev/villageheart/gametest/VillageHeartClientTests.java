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
		// M12: the older L4 tests hire workers and fund no treasury, so they run with wages off (as every GameTest does); TreasuryClientTests turns them on
		dev.villageheart.needs.NeedsOverride.wagesEnabled = false;
		// debugging aid: -Dvillageheart.l4.only=build runs only the Builder's Table flow (default: everything, as verify.sh does)
		if ("build".equals(System.getProperty("villageheart.l4.only"))) {
			BuildClientTests.builderFlow(context);
			return;
		}
		if ("mp".equals(System.getProperty("villageheart.l4.only"))) {
			MultiplayerClientTests.ownerAndResident(context);
			return;
		}
		if ("treasury".equals(System.getProperty("villageheart.l4.only"))) {
			TreasuryClientTests.overviewStrike(context);
			return;
		}
		if ("market".equals(System.getProperty("villageheart.l4.only"))) {
			MarketClientTests.stockpileReserves(context);
			return;
		}
		if ("defence".equals(System.getProperty("villageheart.l4.only"))) {
			DefenceClientTests.walledSection(context);
			return;
		}
		if ("raid".equals(System.getProperty("villageheart.l4.only"))) {
			RaidClientTests.raidOverview(context);
			return;
		}
		if ("title".equals(System.getProperty("villageheart.l4.only"))) {
			ThreatTitleClientTests.titles(context);
			return;
		}
		if ("siege".equals(System.getProperty("villageheart.l4.only"))) {
			SiegeClientTests.wallCracks(context);
			return;
		}
		if ("guards".equals(System.getProperty("villageheart.l4.only"))) {
			GuardClientTests.guardOnDuty(context);
			GuardClientTests.villagersTabGuards(context);
			return;
		}
		if ("levels".equals(System.getProperty("villageheart.l4.only"))) {
			LevelClientTests.levelBadges(context);
			LevelClientTests.villagersTabLevels(context);
			return;
		}
		if ("smith".equals(System.getProperty("villageheart.l4.only"))) {
			SmithClientTests.smithyGolem(context);
			return;
		}
		if ("toasts".equals(System.getProperty("villageheart.l4.only"))) {
			toastsClearedBeforeScreenshot(context);
			return;
		}
		if ("workstation".equals(System.getProperty("villageheart.l4.only"))) {
			NoWorkstationClientTests.noWorkstation(context);
			return;
		}
		if ("art".equals(System.getProperty("villageheart.l4.only"))) {
			placedHeart(context);
			MarkerClientTests.markerWall(context);
			MarkerClientTests.markerItems(context);
			HeartScreenClientTests.heartTabs(context);
			RanksClientTests.ranksTab(context);
			BuildClientTests.builderFlow(context);
			MarketClientTests.stockpileReserves(context);
			return;
		}
		toastsClearedBeforeScreenshot(context);
		placedHeart(context);
		refusedPlacementKeepsClientItem(context);
		MarkerClientTests.markerWall(context);
		MarkerClientTests.markerItems(context);
		MarkerClientTests.validationReport(context);
		WorkerClientTests.workersAtJobBlocks(context);
		LumberClientTests.lumberjackFelling(context);
		FarmerClientTests.farmerField(context);
		CookClientTests.cookAtSmoker(context);
		CookClientTests.villagersTabNeedsFuel(context);
		HeartScreenClientTests.heartTabs(context);
		TreasuryClientTests.overviewStrike(context);
		BuildClientTests.builderFlow(context);
		DefenceClientTests.walledSection(context);
		LumberClientTests.villagersNeedsAxe(context);
		LevelClientTests.levelBadges(context);
		LevelClientTests.villagersTabLevels(context);
		MinerClientTests.villagersTabAwayMining(context);
		SelfCraftClientTests.villagersTabMakingTool(context);
		HunterClientTests.villagersTabAwayHunting(context);
		GuardClientTests.guardOnDuty(context);
		GuardClientTests.villagersTabGuards(context);
		SmithClientTests.smithyGolem(context);
		MarketClientTests.stockpileReserves(context);
		NoWorkstationClientTests.noWorkstation(context);
		RanksClientTests.ranksTab(context);
		MultiplayerClientTests.ownerAndResident(context);
		RaidClientTests.raidOverview(context);
		SiegeClientTests.wallCracks(context);
		ThreatTitleClientTests.titles(context);
	}

	/**
	 * A vanilla toast is up, then a screenshot is taken. The toast must be gone before the frame is captured, so it cannot
	 * cover the UI (the "New Recipe(s) Unlocked" toast on {@code treasury_overview}).
	 */
	static void toastsClearedBeforeScreenshot(ClientGameTestContext context) {
		var id = net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId.PERIODIC_NOTIFICATION;
		boolean showing = context.computeOnClient(mc -> {
			mc.getToastManager().addToast(net.minecraft.client.gui.components.toasts.SystemToast.multiline(mc, id,
					net.minecraft.network.chat.Component.literal("New Recipe(s) Unlocked!"),
					net.minecraft.network.chat.Component.literal("Check your recipe book")));
			return mc.getToastManager().getToast(net.minecraft.client.gui.components.toasts.SystemToast.class, id) != null;
		});
		if (!showing) throw new AssertionError("precondition: the vanilla toast was not showing");
		context.waitTicks(5); // let it draw, so a run without the clear still photographs it
		context.takeScreenshot("toast_cleared");
		boolean gone = context.computeOnClient(mc -> mc.getToastManager().getToast(net.minecraft.client.gui.components.toasts.SystemToast.class, id) == null);
		if (!gone) throw new AssertionError("a vanilla toast was still up when the screenshot was taken");
	}

	/** The Village Heart block in the world, with a bell beside it. */
	private static void placedHeart(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getClientWorld().waitForChunksRender();
			sp.getServer().runCommand("time set day");
			sp.getServer().runCommand("execute as @a at @s run setblock ^ ^1 ^3 villageheart:village_heart");
			sp.getServer().runCommand("execute as @a at @s run setblock ^2 ^1 ^3 minecraft:bell");
			context.waitTicks(40);
			sp.getClientWorld().waitForChunksRender();
			context.takeScreenshot("village_heart_placed");
		}
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
