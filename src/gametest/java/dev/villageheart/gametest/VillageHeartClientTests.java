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
}
