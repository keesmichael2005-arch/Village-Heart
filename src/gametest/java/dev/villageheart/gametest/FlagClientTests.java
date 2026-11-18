package dev.villageheart.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.level.levelgen.Heightmap;

/** Both work plaques, seen in the world. */
final class FlagClientTests {
	private FlagClientTests() {}

	static void workFlags(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getClientWorld().waitForChunksRender();
			var srv = sp.getServer();
			int y = srv.computeOnServer(server -> server.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0));
			srv.runCommand("time set noon");
			srv.runCommand("weather clear");
			srv.runCommand("gamemode creative @a");
			srv.runCommand("effect give @a minecraft:night_vision infinite 0 true");
			srv.runCommand("setblock -2 " + y + " 0 minecraft:oak_fence");
			srv.runCommand("setblock -2 " + (y + 1) + " 1 villageheart:chopping_flag_marker[facing=south]");
			srv.runCommand("setblock 2 " + y + " 0 minecraft:oak_fence");
			srv.runCommand("setblock 2 " + (y + 1) + " 1 villageheart:hunting_ground_marker[facing=south]");
			srv.runCommand("tp @a 0.5 " + (y + 1) + " 6.5 180 0");
			boolean hideGui = context.computeOnClient(mc -> mc.options.hideGui);
			int fov = context.computeOnClient(mc -> mc.options.fov().get());
			context.runOnClient(mc -> { mc.options.hideGui = true; mc.options.fov().set(70); });
			context.waitTicks(40);
			sp.getClientWorld().waitForChunksRender();
			context.takeScreenshot("work_flags");
			context.runOnClient(mc -> { mc.options.hideGui = hideGui; mc.options.fov().set(fov); });
		}
	}
}
