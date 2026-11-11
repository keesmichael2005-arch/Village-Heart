package dev.villageheart.gametest;

import dev.villageheart.block.MarkerBlock;
import dev.villageheart.block.MarkerStatus;
import dev.villageheart.build.BlueprintSite;
import dev.villageheart.build.BuildJobs;
import dev.villageheart.build.BuildSystem;
import dev.villageheart.build.ModComponents;
import dev.villageheart.client.build.BlueprintPreview;
import dev.villageheart.client.build.BuildersTableScreen;
import dev.villageheart.client.heart.HeartScreen;
import dev.villageheart.core.Tier;
import dev.villageheart.core.blueprint.BuildStatus;
import dev.villageheart.core.blueprint.Blueprints;
import dev.villageheart.core.heart.BuildRow;
import dev.villageheart.core.heart.HeartSnapshot;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.worker.Workers;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import org.lwjgl.glfw.GLFW;

/**
 * Layer 4: the Builder's Table and the Small House build (M20). A claim with a Storage (stocked 12 planks short) and a Builder's Hut with
 * a table; the table opened by a real right-click and a blueprint taken through the screen; the footprint shown green on free ground and red
 * over the Storage; the site confirmed with two clicks; the build staged at 50 percent by the instant builder (a frozen builder villager at its
 * stand point), the Heart's Buildings tab showing the row with "Needs 12 more planks"; then the planks arrive and the finished House turns green.
 */
final class BuildClientTests {
	private BuildClientTests() {}

	private static final UUID BUILDER = new UUID(0, 20);

	private static BuildersTableScreen table(net.minecraft.client.Minecraft mc) {
		if (!(mc.screen instanceof BuildersTableScreen s)) throw new AssertionError("the Builder's Table screen is not open: " + mc.screen);
		return s;
	}

	/** Teleports the player and aims at a block's top face (the crosshair is asserted to be on it). */
	private static void aimAt(ClientGameTestContext context, TestServerContext srv, double px, double py, double pz, int tx, int ty, int tz, String what) {
		double eyeY = py + 1.62;
		double dx = tx + 0.5 - px, dz = tz + 0.5 - pz, horiz = Math.sqrt(dx * dx + dz * dz);
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) -Math.toDegrees(Math.atan2(ty + 1.0 - eyeY, horiz));
		srv.runCommand("tp @a " + px + " " + py + " " + pz + " " + yaw + " " + pitch);
		context.waitTicks(10);
		BlockPos want = new BlockPos(tx, ty, tz);
		boolean on = context.computeOnClient(mc -> mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit
				&& hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK && hit.getBlockPos().equals(want));
		if (!on) throw new AssertionError("the crosshair is not on " + what + " " + want + ": " + context.computeOnClient(mc -> String.valueOf(mc.hitResult)));
	}

	/** Teleports the player to a spot looking at a point (no crosshair check): for the pictures. */
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

	static void builderFlow(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getClientWorld().waitForChunksRender();
			var srv = sp.getServer();
			int y = srv.computeOnServer(server -> server.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0));
			srv.runCommand("time set noon");
			srv.runCommand("weather clear");
			srv.runCommand("difficulty peaceful");
			srv.runCommand("gamemode creative @a");
			srv.runCommand("effect give @a minecraft:night_vision infinite 0 true");
			srv.runCommand("gamerule advance_time false");
			srv.runCommand("fill -16 " + (y - 1) + " -4 16 " + (y - 1) + " 40 minecraft:grass_block");
			srv.runCommand("fill -16 " + y + " -4 16 " + (y + 14) + " 40 minecraft:air");
			HeartScreen.recordLayout = true;
			try {
				int[] size = context.computeOnClient(mc -> new int[] {mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight()});
				if (size[0] != 427 || size[1] != 240) throw new AssertionError("the layout is sized for a 427 x 240 GUI, the window gives " + size[0] + " x " + size[1]);
				srv.runCommand("setblock 0 " + y + " 0 minecraft:bell");
				srv.runCommand("setblock -2 " + y + " 0 villageheart:village_heart");
				context.waitTicks(10);
				final int fy = y;
				String claim = srv.computeOnServer(server -> VillageClaims.claim(server.overworld(), new BlockPos(-2, fy, 0), server.getPlayerList().getPlayers().get(0)).name());
				if (!claim.equals("CLAIMED")) throw new AssertionError("the claim failed: " + claim);
				srv.computeOnServer(server -> {
					VillageClaims.data(server).update(v -> v.withTier(Tier.VILLAGE));
					return 0;
				});
				// Storage (3x3, two chests) and a Builder's Hut (5x4, the table, a chest, a torch)
				HeartScreenClientTests.room(srv, -9, y, 3, 3, "storage_marker");
				srv.runCommand("setblock -8 " + y + " 7 minecraft:chest");
				srv.runCommand("setblock -6 " + y + " 7 minecraft:chest");
				HeartScreenClientTests.room(srv, -3, y, 5, 4, "builders_hut_marker");
				srv.runCommand("setblock -2 " + y + " 7 villageheart:builders_table[facing=south]");
				srv.runCommand("setblock 2 " + y + " 10 minecraft:chest");
				srv.runCommand("setblock 0 " + y + " 10 minecraft:torch");
				context.waitTicks(2);
				// the materials of a Small House, 12 planks short
				String[][] items = {{"-8", "0", "minecraft:cobblestone 49"}, {"-8", "1", "minecraft:oak_planks 64"}, {"-8", "2", "minecraft:oak_planks 43"},
						{"-8", "3", "minecraft:oak_door 1"}, {"-8", "4", "minecraft:white_bed 1"}, {"-6", "0", "minecraft:torch 1"}, {"-6", "1", "villageheart:house_marker 1"},
						{"-6", "2", "minecraft:dirt 3"}};
				for (String[] it : items) srv.runCommand("item replace block " + it[0] + " " + y + " 7 container." + it[1] + " with " + it[2]);
				BlockPos storageMarker = new BlockPos(-7, y + 2, 11), hutMarker = new BlockPos(0, y + 2, 12);
				String state = "";
				for (int i = 0; i < 200; i++) {
					state = srv.computeOnServer(server -> marker(server, storageMarker) + "," + marker(server, hutMarker));
					if (state.equals("VALID,VALID")) break;
					context.waitTicks(1);
				}
				if (!state.equals("VALID,VALID")) throw new AssertionError("expected two green plaques, got " + state);

				// ---- 1: the table, opened by a real right-click from inside the Hut
				aimAt(context, srv, -0.5, y, 8.5, -2, y, 7, "the Builder's Table");
				context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
				context.waitForScreen(BuildersTableScreen.class);
				context.waitTicks(5);
				var snap = context.computeOnClient(mc -> table(mc).getMenu().snapshot());
				if (snap.blueprints().size() != Blueprints.all().size() || snap.blueprints().isEmpty() || snap.blueprints().get(0).locked()) {
					throw new AssertionError("the table's blueprints: " + snap.blueprints().size());
				}
				int gateRow = -1, towerRow = -1;
				for (int i = 0; i < snap.blueprints().size(); i++) {
					if (snap.blueprints().get(i).id().equals("gate")) gateRow = i;
					if (snap.blueprints().get(i).id().equals("watchtower")) towerRow = i;
				}
				if (gateRow < 0 || towerRow < 0) throw new AssertionError("gate or watchtower missing from the table");
				int[] row = context.computeOnClient(mc -> table(mc).blueprintRowCentre(0));
				HeartScreenClientTests.clickAt(context, row);
				if (context.computeOnClient(mc -> table(mc).selectedIndex()) != 0) throw new AssertionError("the first blueprint row was not selected");
				int clipped = context.computeOnClient(mc -> table(mc).clippedThisFrame());
				if (clipped != 0) throw new AssertionError("the table screen cut off " + clipped + " texts");
				context.takeScreenshot("builders_table");
				for (int[] pick : new int[][] {{gateRow, 0}, {towerRow, 1}}) {
					context.computeOnClient(mc -> { table(mc).scrollBlueprintIntoView(pick[0]); return 0; });
					HeartScreenClientTests.clickAt(context, context.computeOnClient(mc -> table(mc).blueprintRowCentre(pick[0])));
					if (context.computeOnClient(mc -> table(mc).selectedIndex()) != pick[0]) throw new AssertionError("row " + pick[0] + " was not selected");
					context.takeScreenshot(pick[1] == 0 ? "builders_table_gate" : "builders_table_watchtower");
				}
				context.computeOnClient(mc -> { table(mc).scrollBlueprintIntoView(0); return 0; });
				HeartScreenClientTests.clickAt(context, context.computeOnClient(mc -> table(mc).blueprintRowCentre(0)));
				// take the blueprint through the screen, then put the tested Small House in hand (it is no longer in the catalogue)
				boolean takeOn = context.computeOnClient(mc -> table(mc).takeButton().active);
				if (!takeOn) throw new AssertionError("the Take button is off for the Owner");
				HeartScreenClientTests.clickAt(context, context.computeOnClient(mc -> table(mc).takeButtonCentre()));
				boolean got = false;
				for (int i = 0; i < 60 && !got; i++) {
					context.waitTicks(1);
					got = context.computeOnClient(mc -> mc.player.getInventory().countItem(dev.villageheart.item.ModItems.BLUEPRINT) == 1);
				}
				if (!got) throw new AssertionError("the blueprint item did not arrive: screen says '" + context.computeOnClient(mc -> mc.screen instanceof BuildersTableScreen s ? s.statusText() : "(no table screen: " + mc.screen + ")")
						+ "', server inventory has " + srv.computeOnServer(server -> server.getPlayerList().getPlayers().get(0).getInventory().countItem(dev.villageheart.item.ModItems.BLUEPRINT)));
				context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
				context.waitTicks(5);
				if (!context.computeOnClient(mc -> mc.player.getMainHandItem().is(dev.villageheart.item.ModItems.BLUEPRINT))) {
					throw new AssertionError("the blueprint is not in the hand (the first hotbar slot)");
				}

				srv.computeOnServer(server -> {
					var p = server.getPlayerList().getPlayers().get(0);
					p.getInventory().setItem(0, dev.villageheart.build.BlueprintItem.of(Blueprints.SMALL_HOUSE));
					return 0;
				});
				context.waitTicks(5);
				// ---- 2: the footprint, green on free ground
				final int gz = 22;
				aimAt(context, srv, 0.5, y, gz + 5.05, 0, y - 1, gz, "the site ground");
				context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
				context.waitTicks(10);
				BlueprintSite green = heldSite(context);
				if (green == null || !green.ok() || green.ground().getZ() != gz) throw new AssertionError("a green site was expected: " + green);
				if (green.rotationEnum() != dev.villageheart.core.blueprint.Rotation4.CLOCKWISE_180) throw new AssertionError("the door should face the player: " + green);
				if (BlueprintPreview.lastCount <= 0 || BlueprintPreview.lastColor != BlueprintPreview.OK_COLOR) throw new AssertionError("no green particles were drawn");
				context.takeScreenshot("blueprint_footprint");
				// over the Storage room: red
				aimAt(context, srv, -6.5, y, 17.05, -7, y - 1, 12, "the ground next to the Storage");
				context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
				context.waitTicks(10);
				BlueprintSite red = heldSite(context);
				if (red == null || red.ok() || !red.reason().english().startsWith("Overlaps the Storage")) throw new AssertionError("a red site was expected: " + red);
				if (BlueprintPreview.lastColor != BlueprintPreview.BAD_COLOR) throw new AssertionError("no red particles were drawn");
				context.takeScreenshot("blueprint_footprint_red");

				// ---- 3: confirm the green site with two clicks
				aimAt(context, srv, 0.5, y, gz + 5.05, 0, y - 1, gz, "the site ground");
				context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
				context.waitTicks(8);
				context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
				context.waitTicks(10);
				UUID id = srv.computeOnServer(server -> {
					var builds = BuildJobs.list(server);
					return builds.size() == 1 ? builds.get(0).id() : null;
				});
				if (id == null) throw new AssertionError("the second click did not confirm the site");

				// ---- 4: half built (86 of 172 placements), a builder at its stand point, the Heart's Buildings tab
				srv.runCommand("summon minecraft:villager 5.5 " + y + " 22.5 {NoAI:1b,PersistenceRequired:1b,UUID:" + HeartScreenClientTests.uuid(BUILDER) + "}");
				context.waitTicks(2);
				String advanced = srv.computeOnServer(server -> {
					var villager = server.overworld().getEntity(BUILDER);
					var hired = Workers.hire(server.overworld(), villager, JobType.BUILDER, Optional.empty());
					if (!hired.toString().startsWith("Hired")) return "hire: " + hired;
					var a = BuildJobs.advance(server, id, 246 + 86);
					BuildSystem.pinClaimForTest(server, id, BUILDER);
					BuildSystem.refresh(server);
					return a.toString();
				});
				if (!advanced.contains("steps=332")) throw new AssertionError("the instant builder: " + advanced);
				context.waitTicks(10);
				sp.getClientWorld().waitForChunksRender();
				lookAt(context, srv, 7.5, y + 3, gz + 10.0, 0.5, y + 1.5, gz);
				sp.getClientWorld().waitForChunksRender();
				context.takeScreenshot("build_mid");
				aimAt(context, srv, -1.5, y, 3.5, -2, y, 0, "the Village Heart");
				context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
				context.waitForScreen(HeartScreen.class);
				context.waitTicks(5);
				HeartScreenClientTests.openTab(context, 2);
				HeartSnapshot hs = HeartScreenClientTests.snapshot(context);
				BuildRow br = null;
				for (int i = 0; i < 100 && br == null; i++) {
					hs = HeartScreenClientTests.snapshot(context);
					if (!hs.builds().isEmpty() && hs.builds().get(0).state() == BuildStatus.BUILDING) br = hs.builds().get(0);
					else context.waitTicks(1);
				}
				if (br == null) throw new AssertionError("the Heart snapshot never showed the build as building: " + hs.builds());
				if (!br.status().english().equals("Building: 50%") || br.missing().isEmpty() || !br.missing().get(0).english().equals("Needs 12 more planks")
						|| br.placed() != 86 || br.total() != 172) {
					throw new AssertionError("the build row: " + br);
				}
				HeartScreenClientTests.assertClean(context, "buildings with a build");
				context.takeScreenshot("heart_buildings_build");
				context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
				context.waitTicks(5);

				// ---- 5: the planks arrive, the House is finished and turns green
				srv.runCommand("item replace block -6 " + y + " 7 container.2 with minecraft:oak_planks 12");
				String done = srv.computeOnServer(server -> {
					var a = BuildJobs.advance(server, id, Integer.MAX_VALUE);
					return a.toString();
				});
				if (!done.contains("finished=true")) throw new AssertionError("the House was not finished: " + done);
				BlockPos houseMarker = new BlockPos(0, y + 3, gz + 4); // layer 3 of the front row, outside the front wall (rotation 180: the door faces +z)
				boolean green2 = false;
				for (int i = 0; i < 200 && !green2; i++) {
					context.waitTicks(1);
					green2 = context.computeOnClient(mc -> mc.level.getBlockState(houseMarker).getBlock() instanceof MarkerBlock
							&& mc.level.getBlockState(houseMarker).getValue(MarkerBlock.STATUS) == MarkerStatus.VALID);
				}
				if (!green2) throw new AssertionError("the client never saw the House marker turn green at " + houseMarker + ": "
						+ context.computeOnClient(mc -> String.valueOf(mc.level.getBlockState(houseMarker))));
				srv.runCommand("kill @e[type=minecraft:villager]");
				lookAt(context, srv, 3.5, y, gz + 11.0, 0.5, y + 2.0, gz + 4);
				sp.getClientWorld().waitForChunksRender();
				context.waitTicks(5);
				context.takeScreenshot("build_finished");
			} finally {
				HeartScreen.recordLayout = false;
			}
		}
	}

	private static String marker(net.minecraft.server.MinecraftServer server, BlockPos pos) {
		var s = server.overworld().getBlockState(pos);
		return s.getBlock() instanceof MarkerBlock ? s.getValue(MarkerBlock.STATUS).name() : "none";
	}
}
