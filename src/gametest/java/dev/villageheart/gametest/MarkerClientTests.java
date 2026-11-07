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
}
