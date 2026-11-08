package dev.villageheart.gametest;

import dev.villageheart.build.BlueprintItem;
import dev.villageheart.build.BlueprintSite;
import dev.villageheart.build.BuildJobs;
import dev.villageheart.build.ModComponents;
import dev.villageheart.client.build.BlueprintPreview;
import dev.villageheart.client.heart.HeartScreen;
import dev.villageheart.core.Tier;
import dev.villageheart.core.blueprint.Blueprints;
import dev.villageheart.world.VillageClaims;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.levelgen.Heightmap;
import org.lwjgl.glfw.GLFW;

/**
 * Layer 4: a walled section (M21). The Wall Segment blueprint held in hand shows its line after two real clicks (start, end 16 blocks
 * east); then a wall, a gate, a wall in one line, a lamp post at each end and a watchtower behind the gate are built by command at dusk
 * and photographed from the south-east; then the middle gate is opened with a real right-click.
 */
final class DefenceClientTests {
	private DefenceClientTests() {}

	/** Teleports the player and aims at a point of a block (the crosshair is asserted to be on that block). */
	private static void aim(ClientGameTestContext context, TestServerContext srv, double px, double py, double pz, int tx, int ty, int tz, double height, String what) {
		double eyeY = py + 1.62;
		double dx = tx + 0.5 - px, dz = tz + 0.5 - pz, horiz = Math.sqrt(dx * dx + dz * dz);
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) -Math.toDegrees(Math.atan2(ty + height - eyeY, horiz));
		srv.runCommand("tp @a " + px + " " + py + " " + pz + " " + yaw + " " + pitch);
		context.waitTicks(10);
		BlockPos want = new BlockPos(tx, ty, tz);
		boolean on = context.computeOnClient(mc -> mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit
				&& hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK && hit.getBlockPos().equals(want));
		if (!on) throw new AssertionError("the crosshair is not on " + what + " " + want + ": " + context.computeOnClient(mc -> String.valueOf(mc.hitResult)));
	}

	private static void lookAt(ClientGameTestContext context, TestServerContext srv, double px, double py, double pz, double tx, double ty, double tz) {
		double dx = tx - px, dz = tz - pz, horiz = Math.sqrt(dx * dx + dz * dz);
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) -Math.toDegrees(Math.atan2(ty - (py + 1.62), horiz));
		srv.runCommand("tp @a " + px + " " + py + " " + pz + " " + yaw + " " + pitch);
		context.waitTicks(10);
	}

	private static BlueprintSite heldSite(ClientGameTestContext context) {
		return context.computeOnClient(mc -> mc.player.getMainHandItem().get(ModComponents.BLUEPRINT_SITE));
	}

	private static String marker(net.minecraft.server.MinecraftServer server, BlockPos pos) {
		var s = server.overworld().getBlockState(pos);
		return s.getBlock() instanceof dev.villageheart.block.MarkerBlock ? s.getValue(dev.villageheart.block.MarkerBlock.STATUS).name() : "none";
	}

	static void walledSection(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getClientWorld().waitForChunksRender();
			var srv = sp.getServer();
			int y = srv.computeOnServer(server -> server.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0));
			final int g = y - 1; // the ground layer
			srv.runCommand("time set noon");
			srv.runCommand("weather clear");
			srv.runCommand("difficulty peaceful");
			srv.runCommand("gamemode creative @a");
			srv.runCommand("gamerule advance_time false");
			srv.runCommand("fill -24 " + g + " -4 24 " + g + " 48 minecraft:grass_block");
			srv.runCommand("fill -24 " + y + " -4 24 " + (y + 14) + " 48 minecraft:air");
			HeartScreen.recordLayout = true;
			try {
				int[] size = context.computeOnClient(mc -> new int[] {mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight()});
				if (size[0] != 427 || size[1] != 240) throw new AssertionError("the layout is sized for a 427 x 240 GUI, the window gives " + size[0] + " x " + size[1]);
				srv.runCommand("setblock 0 " + y + " 0 minecraft:bell");
				srv.runCommand("setblock -2 " + y + " 0 villageheart:village_heart");
				context.waitTicks(10);
				String claim = srv.computeOnServer(server -> VillageClaims.claim(server.overworld(), new BlockPos(-2, y, 0), server.getPlayerList().getPlayers().get(0)).name());
				if (!claim.equals("CLAIMED")) throw new AssertionError("the claim failed: " + claim);
				srv.computeOnServer(server -> {
					VillageClaims.data(server).update(v -> v.withTier(Tier.TOWN));
					return 0;
				});
				// a Storage with everything the section costs
				HeartScreenClientTests.room(srv, -9, y, 3, 3, "storage_marker");
				srv.runCommand("setblock -8 " + y + " 7 minecraft:chest");
				srv.runCommand("setblock -6 " + y + " 7 minecraft:chest");
				context.waitTicks(2);
				String[][] items = {{"-8", "0", "minecraft:cobblestone 64"}, {"-8", "1", "minecraft:cobblestone 64"}, {"-8", "2", "minecraft:oak_planks 24"},
						{"-8", "3", "minecraft:oak_fence 20"}, {"-8", "4", "minecraft:oak_fence_gate 3"}, {"-6", "0", "minecraft:ladder 6"}, {"-6", "1", "minecraft:lantern 2"}};
				for (String[] it : items) srv.runCommand("item replace block " + it[0] + " " + y + " 7 container." + it[1] + " with " + it[2]);
				BlockPos storageMarker = new BlockPos(-7, y + 2, 11);
				String state = "";
				for (int i = 0; i < 200; i++) {
					state = srv.computeOnServer(server -> marker(server, storageMarker));
					if (state.equals("VALID")) break;
					context.waitTicks(1);
				}
				if (!state.equals("VALID")) throw new AssertionError("expected a green Storage plaque, got " + state);

				// ---- 1: the line preview, from two real clicks
				srv.computeOnServer(server -> {
					server.getPlayerList().getPlayers().get(0).setItemInHand(InteractionHand.MAIN_HAND, BlueprintItem.of(Blueprints.WALL_SEGMENT));
					return 0;
				});
				context.waitTicks(2);
				aim(context, srv, 0.5, y, 38.5, 0, g, 34, 1.0, "the start of the line");
				context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
				context.waitTicks(10);
				BlueprintSite start = heldSite(context);
				if (start == null || !start.ok() || start.length() != 0 || start.ground().getZ() != 34) throw new AssertionError("only the start was expected: " + start);
				if (BlueprintPreview.lastCount <= 0) throw new AssertionError("no column was drawn on the start");
				aim(context, srv, 15.5, y, 38.5, 15, g, 34, 1.0, "the end of the line");
				context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
				context.waitTicks(10);
				BlueprintSite line = heldSite(context);
				if (line == null || !line.ok() || line.length() != 16) throw new AssertionError("a green 16-long line was expected: " + line);
				if (BlueprintPreview.lastCount <= 0 || BlueprintPreview.lastColor != BlueprintPreview.OK_COLOR) throw new AssertionError("no green particles were drawn");
				lookAt(context, srv, 8.5, y, 43.5, 8.0, y + 1.0, 34);
				sp.getClientWorld().waitForChunksRender();
				context.takeScreenshot("wall_line_preview");
				srv.runCommand("clear @a");

				// ---- 2: the section: wall 10, gate 7, wall 10 in one line, a lamp post at each end, a watchtower behind the gate
				srv.runCommand("villageheart build line wall_segment -15 " + g + " 24 -6 " + g + " 24");
				srv.runCommand("villageheart build line gate -5 " + g + " 24 1 " + g + " 24");
				srv.runCommand("villageheart build line wall_segment 2 " + g + " 24 11 " + g + " 24");
				srv.runCommand("villageheart build start lamp_post -14 " + g + " 21");
				srv.runCommand("villageheart build start lamp_post 10 " + g + " 21");
				srv.runCommand("villageheart build start watchtower -2 " + g + " 18");
				String built = srv.computeOnServer(server -> {
					var jobs = new java.util.ArrayList<>(BuildJobs.list(server));
					if (jobs.size() != 6) return "builds started: " + jobs.size();
					for (var j : jobs) {
						var a = BuildJobs.advance(server, j.id(), Integer.MAX_VALUE);
						if (!a.finished()) return j.blueprintId() + " not finished: " + a;
					}
					var village = VillageClaims.current(server).orElseThrow();
					return village.defences().size() == 3 && BuildJobs.list(server).isEmpty() ? "ok" : "defences " + village.defences().size();
				});
				if (!built.equals("ok")) throw new AssertionError("the section was not built: " + built);
				srv.runCommand("time set 12800");
				srv.runCommand("fill 5 " + (y + 9) + " 45 7 " + (y + 9) + " 47 minecraft:stone"); // a stand for the camera: high over the wall
				context.waitTicks(10);
				sp.getClientWorld().waitForChunksRender();
				lookAt(context, srv, 6.5, y + 10, 46.5, -2.0, y + 1.0, 24);
				sp.getClientWorld().waitForChunksRender();
				context.waitTicks(10);
				context.takeScreenshot("walled_section");
				lookAt(context, srv, 5.5, y, 31.5, -2.0, y + 1.0, 24);
				sp.getClientWorld().waitForChunksRender();
				context.takeScreenshot("walled_section_gate_closed");

				// ---- 3: the middle gate opens
				aim(context, srv, -1.5, y, 27.5, -2, y, 24, 0.5, "the middle gate");
				context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
				context.waitTicks(10);
				boolean open = context.computeOnClient(mc -> mc.level.getBlockState(new BlockPos(-2, y, 24)).getValue(net.minecraft.world.level.block.FenceGateBlock.OPEN));
				if (!open) throw new AssertionError("the gate did not open");
				lookAt(context, srv, 5.5, y, 31.5, -2.0, y + 1.0, 24);
				sp.getClientWorld().waitForChunksRender();
				context.waitTicks(5);
				context.takeScreenshot("walled_section_gate_open");
				// the tower from inside the wall: its entrance, the ladder behind it and the railed platform
				lookAt(context, srv, -2.5, y, 10.5, -2.0, y + 3.0, 18);
				sp.getClientWorld().waitForChunksRender();
				context.waitTicks(5);
				context.takeScreenshot("watchtower");
			} finally {
				HeartScreen.recordLayout = false;
			}
		}
	}
}
