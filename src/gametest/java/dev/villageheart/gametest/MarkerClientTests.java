package dev.villageheart.gametest;

import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.building.JobBlockType;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;

/** Layer 4 tests for markers and job blocks (M3). Called from VillageHeartClientTests. */
final class MarkerClientTests {
	private MarkerClientTests() {}

	private static List<Identifier> modBlockIds() {
		List<Identifier> ids = new ArrayList<>();
		for (BuildingType t : BuildingType.values()) ids.add(Identifier.fromNamespaceAndPath("villageheart", t.markerId()));
		for (JobBlockType t : JobBlockType.values()) ids.add(Identifier.fromNamespaceAndPath("villageheart", t.id()));
		return ids;
	}

	/** T-L4-1: a stone-brick wall with every marker, the three job blocks and a fence-post site marker. */
	static void markerWall(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getClientWorld().waitForChunksRender();
			int y = sp.getServer().computeOnServer(server -> server.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0));
			var srv = sp.getServer();
			srv.runCommand("time set noon");
			srv.runCommand("weather clear");
			srv.runCommand("gamemode creative @a");
			srv.runCommand("effect give @a minecraft:night_vision infinite 0 true");
			srv.runCommand("fill -4 " + y + " -1 4 " + (y + 3) + " -1 minecraft:stone_bricks");
			BuildingType[] types = BuildingType.values();
			for (int i = 0; i < types.length; i++) {
				int x = (i % 7) - 3;
				int my = i < 7 ? y + 2 : y + 1;
				srv.runCommand("setblock " + x + " " + my + " 0 villageheart:" + types[i].markerId() + "[facing=south]");
			}
			JobBlockType[] jobs = JobBlockType.values();
			for (int i = 0; i < jobs.length; i++) {
				srv.runCommand("setblock " + (i * 3 - 3) + " " + y + " 0 villageheart:" + jobs[i].id() + "[facing=south]");
			}
			srv.runCommand("setblock 5 " + y + " 0 minecraft:oak_fence");
			srv.runCommand("setblock 5 " + (y + 1) + " 0 minecraft:oak_fence");
			srv.runCommand("setblock 5 " + (y + 1) + " 1 villageheart:mineshaft_marker[facing=south]");
			srv.runCommand("tp @a 0.5 " + y + " 5.5 180 0");
			boolean hideGui = context.computeOnClient(mc -> mc.options.hideGui);
			int fov = context.computeOnClient(mc -> mc.options.fov().get());
			context.runOnClient(mc -> { mc.options.hideGui = true; mc.options.fov().set(70); });
			context.waitTicks(40);
			sp.getClientWorld().waitForChunksRender();

			// Server side: everything stands and faces south.
			final int fy = y;
			List<String> problems = srv.computeOnServer(server -> {
				List<String> bad = new ArrayList<>();
				var level = server.overworld();
				List<BlockPos> spots = new ArrayList<>();
				List<Identifier> expected = new ArrayList<>();
				for (int i = 0; i < types.length; i++) {
					spots.add(new BlockPos((i % 7) - 3, i < 7 ? fy + 2 : fy + 1, 0));
					expected.add(Identifier.fromNamespaceAndPath("villageheart", types[i].markerId()));
				}
				for (int i = 0; i < jobs.length; i++) {
					spots.add(new BlockPos(i * 3 - 3, fy, 0));
					expected.add(Identifier.fromNamespaceAndPath("villageheart", jobs[i].id()));
				}
				spots.add(new BlockPos(5, fy + 1, 1));
				expected.add(Identifier.fromNamespaceAndPath("villageheart", "mineshaft_marker"));
				for (int i = 0; i < spots.size(); i++) {
					BlockState st = level.getBlockState(spots.get(i));
					Identifier got = BuiltInRegistries.BLOCK.getKey(st.getBlock());
					if (!got.equals(expected.get(i))) bad.add(spots.get(i) + " expected " + expected.get(i) + " but found " + got);
					else if (st.getValue(HorizontalDirectionalBlock.FACING) != Direction.SOUTH) bad.add(spots.get(i) + " facing " + st.getValue(HorizontalDirectionalBlock.FACING));
				}
				if (spots.size() != 18) bad.add("expected 18 spots, have " + spots.size());
				return bad;
			});
			if (!problems.isEmpty()) throw new AssertionError("marker wall not as expected: " + problems);

			// Client side: every state of every mod block resolves to a real model.
			List<String> missing = context.computeOnClient(mc -> {
				List<String> bad = new ArrayList<>();
				var missingModel = mc.getModelManager().getMissingBlockStateModel();
				for (Identifier id : modBlockIds()) {
					Block b = BuiltInRegistries.BLOCK.getValue(id);
					for (BlockState st : b.getStateDefinition().getPossibleStates()) {
						if (mc.getBlockRenderer().getBlockModel(st) == missingModel) bad.add(st.toString());
					}
				}
				return bad;
			});
			if (!missing.isEmpty()) throw new AssertionError("block states with the missing model: " + missing);

			context.takeScreenshot("marker_wall");
			context.runOnClient(mc -> { mc.options.hideGui = hideGui; mc.options.fov().set(fov); });
		}
	}

	/** T-L4-2: the Blank Marker, 14 markers and 3 job blocks as inventory icons. */
	static void markerItems(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getClientWorld().waitForChunksRender();
			var srv = sp.getServer();
			srv.runCommand("time set noon");
			srv.runCommand("gamemode survival @a");
			srv.runCommand("clear @a");
			srv.runCommand("give @a villageheart:blank_marker 1");
			for (BuildingType t : BuildingType.values()) srv.runCommand("give @a villageheart:" + t.markerId() + " 1");
			for (JobBlockType t : JobBlockType.values()) srv.runCommand("give @a villageheart:" + t.id() + " 1");
			context.waitTicks(10);
			List<String> missing = context.computeOnClient(mc -> {
				List<String> bad = new ArrayList<>();
				var missingItem = mc.getModelManager().getItemModel(Identifier.fromNamespaceAndPath("villageheart", "no_such_item"));
				List<String> ids = new ArrayList<>(List.of("blank_marker", "village_heart"));
				for (BuildingType t : BuildingType.values()) ids.add(t.markerId());
				for (JobBlockType t : JobBlockType.values()) ids.add(t.id());
				for (String id : ids) {
					if (mc.getModelManager().getItemModel(Identifier.fromNamespaceAndPath("villageheart", id)) == missingItem) bad.add(id);
				}
				return bad;
			});
			if (!missing.isEmpty()) throw new AssertionError("items with the missing item model: " + missing);
			context.setScreen(() -> new InventoryScreen(net.minecraft.client.Minecraft.getInstance().player));
			context.waitForScreen(InventoryScreen.class);
			context.getInput().setCursorPos(2, 2);
			context.runOnClient(mc -> mc.getToastManager().clear());
			context.waitTicks(5);
			context.takeScreenshot("marker_items");
			context.setScreen(() -> null);
		}
	}

	/**
	 * T-L4-1 (M5): a red and a green Storage marker side by side, and the chat report after a real right-click on the red one.
	 * The rooms have 3x3x3 interiors (floor 9, the Storage minimum) one block apart; the player stands 4 blocks south of the red
	 * plaque (inside the block reach) with a wide FOV so both plaques are in the frame.
	 */
	static void validationReport(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getClientWorld().waitForChunksRender();
			var srv = sp.getServer();
			int y = srv.computeOnServer(server -> server.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0));
			srv.runCommand("time set noon");
			srv.runCommand("weather clear");
			srv.runCommand("gamemode creative @a");
			srv.runCommand("effect give @a minecraft:night_vision infinite 0 true");
			srv.runCommand("setblock 0 " + y + " 0 minecraft:bell");
			srv.runCommand("setblock -2 " + y + " 0 villageheart:village_heart");
			context.waitTicks(10);
			final int fy = y;
			String claim = srv.computeOnServer(server -> dev.villageheart.world.VillageClaims.claim(server.overworld(),
					new BlockPos(-2, fy, 0), server.getPlayerList().getPlayers().get(0)).name());
			if (!claim.equals("CLAIMED")) throw new AssertionError("the claim failed: " + claim);

			// two Storage rooms, shells x -9..-5 and -3..1 (a gap of one block), z 6..10, floor on the ground layer
			room(srv, -9, y, -7, 2);
			room(srv, -3, y, -1, 1);
			context.waitTicks(5);

			// wait for the verdicts: left green, right red
			BlockPos green = new BlockPos(-7, y + 2, 11), red = new BlockPos(-1, y + 2, 11);
			String state = "";
			for (int i = 0; i < 100; i++) {
				state = srv.computeOnServer(server -> status(server.overworld(), green) + "," + status(server.overworld(), red));
				if (state.equals("VALID,INVALID")) break;
				context.waitTicks(1);
			}
			if (!state.equals("VALID,INVALID")) throw new AssertionError("expected green and red plaques, got " + state);
			String why = srv.computeOnServer(server -> dev.villageheart.building.Buildings.report(server,
					dev.villageheart.world.VillageClaims.locate(server.overworld(), red)).map(v -> v.reasons().get(0).message()).orElse("none"));
			if (!why.equals("Needs 1 more chest")) throw new AssertionError("the red marker says: " + why);

			// stand 4 blocks south of the red plaque, looking at it
			double targetY = y + 2.5, eyeY = y + 1.62, targetZ = 11.0625, px = -0.5, pz = 15.2;
			float pitch = (float) -Math.toDegrees(Math.atan2(targetY - eyeY, pz - targetZ));
			srv.runCommand("tp @a " + px + " " + y + " " + pz + " 180 " + pitch);
			boolean hideGui = context.computeOnClient(mc -> mc.options.hideGui);
			int fov = context.computeOnClient(mc -> mc.options.fov().get());
			context.runOnClient(mc -> { mc.options.hideGui = false; mc.options.fov().set(100); });
			context.waitTicks(10);
			sp.getClientWorld().waitForChunksRender();
			boolean onRed = context.computeOnClient(mc -> mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit
					&& hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK && hit.getBlockPos().equals(red));
			if (!onRed) throw new AssertionError("the crosshair is not on the red marker: " + context.computeOnClient(mc -> String.valueOf(mc.hitResult)));
			context.getInput().pressMouse(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT);
			context.waitTicks(5);
			context.takeScreenshot("marker_validation");
			context.runOnClient(mc -> { mc.options.hideGui = hideGui; mc.options.fov().set(fov); });
		}
	}

	private static String status(net.minecraft.server.level.ServerLevel level, BlockPos marker) {
		BlockState st = level.getBlockState(marker);
		return st.hasProperty(dev.villageheart.block.MarkerBlock.STATUS) ? st.getValue(dev.villageheart.block.MarkerBlock.STATUS).name() : "MISSING";
	}

	/** A 3x3x3 room on the ground layer with a south door at {@code doorX}, {@code chests} chests and a Storage marker above the door. */
	private static void room(net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext srv, int x0, int y, int doorX, int chests) {
		srv.runCommand("fill " + x0 + " " + (y - 1) + " 6 " + (x0 + 4) + " " + (y + 3) + " 10 minecraft:oak_planks hollow");
		srv.runCommand("setblock " + doorX + " " + y + " 10 minecraft:oak_door[facing=south,half=lower]");
		srv.runCommand("setblock " + doorX + " " + (y + 1) + " 10 minecraft:oak_door[facing=south,half=upper]");
		srv.runCommand("setblock " + (x0 + 1) + " " + y + " 7 minecraft:chest");
		if (chests >= 2) srv.runCommand("setblock " + (x0 + 3) + " " + y + " 7 minecraft:chest");
		srv.runCommand("setblock " + doorX + " " + (y + 2) + " 11 villageheart:storage_marker[facing=south]");
	}
}
