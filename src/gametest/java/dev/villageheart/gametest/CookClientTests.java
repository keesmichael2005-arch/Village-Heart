package dev.villageheart.gametest;

import dev.villageheart.core.worker.JobType;
import dev.villageheart.worker.ModProfessions;
import dev.villageheart.worker.job.WorkStats;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

/** Layer 4 tests for the cook (M13). Called from VillageHeartClientTests. */
final class CookClientTests {
	private CookClientTests() {}

	/** A claimed village with a real Kitchen (smoker, crafting table, chest), a Storage holding beef and coal, and a hired cook. */
	private static void scene(ClientGameTestContext context, TestSingleplayerContext sp, UUID cook, boolean fuel, int y) {
		var srv = sp.getServer();
		srv.runCommand("time set noon");
		srv.runCommand("gamerule advance_time false");
		srv.runCommand("gamerule random_tick_speed 0");
		srv.runCommand("weather clear");
		srv.runCommand("difficulty peaceful");
		srv.runCommand("gamemode creative @a");
		srv.runCommand("effect give @a minecraft:night_vision infinite 0 true");
		srv.runCommand("fill -14 " + (y - 1) + " -6 20 " + (y - 1) + " 20 minecraft:grass_block");
		srv.runCommand("fill -14 " + y + " -6 20 " + (y + 8) + " 20 minecraft:air");
		srv.runCommand("setblock 0 " + y + " 0 minecraft:bell");
		srv.runCommand("setblock -2 " + y + " 0 villageheart:village_heart");
		context.waitTicks(10);
		final int fy = y;
		String claim = srv.computeOnServer(server -> dev.villageheart.world.VillageClaims.claim(server.overworld(),
				new BlockPos(-2, fy, 0), server.getPlayerList().getPlayers().get(0)).name());
		if (!claim.equals("CLAIMED")) throw new AssertionError("the claim failed: " + claim);
		srv.runCommand("villageheart treasury add 8");
		// the Kitchen: smoker in the north-west corner, crafting table beside it, chest and a torch
		HeartScreenClientTests.room(srv, -3, y, 5, 4, "kitchen_marker");
		srv.runCommand("setblock -2 " + y + " 7 minecraft:smoker[facing=south]");
		srv.runCommand("setblock 2 " + y + " 7 minecraft:crafting_table");
		srv.runCommand("setblock -2 " + y + " 10 minecraft:chest");
		srv.runCommand("setblock 0 " + y + " 10 minecraft:torch");
		// the Storage east of it
		HeartScreenClientTests.room(srv, 6, y, 5, 4, "storage_marker");
		srv.runCommand("setblock 11 " + y + " 10 minecraft:chest");
		srv.runCommand("setblock 7 " + y + " 10 minecraft:chest[facing=north]{Items:[{Slot:0b,id:\"minecraft:beef\",count:16}"
				+ (fuel ? ",{Slot:1b,id:\"minecraft:coal\",count:2}" : "") + "]}");
		srv.runCommand("setblock 9 " + y + " 10 minecraft:torch");
		srv.runCommand("summon minecraft:villager 0 " + y + " 8 {PersistenceRequired:1b,UUID:" + HeartScreenClientTests.uuid(cook) + ",CustomName:\"Basil\"}");
		BlockPos kitchen = new BlockPos(0, y + 2, 12), storage = new BlockPos(9, y + 2, 12);
		String ks = "", ss = "";
		for (int i = 0; i < 300 && !(ks.equals("VALID") && ss.equals("VALID")); i++) {
			ks = srv.computeOnServer(server -> HeartScreenClientTests.marker(server.overworld(), kitchen));
			ss = srv.computeOnServer(server -> HeartScreenClientTests.marker(server.overworld(), storage));
			context.waitTicks(1);
		}
		if (!ks.equals("VALID") || !ss.equals("VALID")) throw new AssertionError("the plaques are " + ks + " / " + ss);
		String hired = srv.computeOnServer(server -> dev.villageheart.worker.Workers.hire(server.overworld(),
				server.overworld().getEntity(cook), JobType.COOK, java.util.Optional.empty()).toString());
		if (!hired.startsWith("Hired")) throw new AssertionError("the hire failed: " + hired);
	}

	/**
	 * T-L4-1: the cook at its smoker mid-batch. The server waits until two items are really cooked; the client is then asked for the smoker's
	 * {@code LIT} state and the villager's profession, so an unlit smoker or a plain villager cannot pass. The shot is taken from inside the
	 * Kitchen (hidden GUI), the smoker lit and the cook beside it.
	 */
	static void cookAtSmoker(ClientGameTestContext context) {
		UUID cook = new UUID(0, 31);
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getClientWorld().waitForChunksRender();
			var srv = sp.getServer();
			int y = srv.computeOnServer(server -> server.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0));
			scene(context, sp, cook, true, y);
			long before = srv.computeOnServer(server -> WorkStats.get("cook", "items"));
			long done = 0;
			for (int i = 0; i < 400 && done < 2; i++) {
				context.waitTicks(10);
				done = srv.computeOnServer(server -> WorkStats.get("cook", "items")) - before;
			}
			if (done < 2) throw new AssertionError("the cook made only " + done + " items");
			final int fy = y;
			// stand in the Kitchen's south-east corner and look at the smoker corner, the cook between
			srv.runCommand("tp @a 1.5 " + (y + 0.0) + " 10.5 facing -1.5 " + (y + 1.0) + " 7.5");
			boolean hideGui = context.computeOnClient(mc -> mc.options.hideGui);
			int fov = context.computeOnClient(mc -> mc.options.fov().get());
			context.runOnClient(mc -> { mc.options.hideGui = true; mc.options.fov().set(70); });
			context.waitTicks(4);
			sp.getClientWorld().waitForChunksRender();
			String problem = context.computeOnClient(mc -> {
				var st = mc.level.getBlockState(new BlockPos(-2, fy, 7));
				if (!st.is(Blocks.SMOKER)) return "no smoker: " + st;
				if (!st.getValue(AbstractFurnaceBlock.LIT)) return "the smoker is not lit on the client";
				net.minecraft.world.entity.npc.villager.Villager found = null;
				for (var e : mc.level.entitiesForRendering()) if (e instanceof net.minecraft.world.entity.npc.villager.Villager v) found = v;
				if (found == null) return "no villager on the client";
				String got = found.getVillagerData().profession().unwrapKey().orElseThrow().identifier().toString();
				if (!got.equals(ModProfessions.key(JobType.COOK).identifier().toString())) return "profession on the client is " + got;
				return null;
			});
			if (problem != null) throw new AssertionError("cook scene: " + problem);
			context.takeScreenshot("cook_at_smoker");
			context.runOnClient(mc -> { mc.options.hideGui = hideGui; mc.options.fov().set(fov); });
			srv.runCommand("gamerule advance_time true");
			srv.runCommand("gamerule random_tick_speed 3");
			srv.runCommand("kill @e[type=minecraft:villager]");
		}
	}

	/** T-L4-2: a cook with raw food and no fuel: the Villagers tab says "Needs fuel". */
	static void villagersTabNeedsFuel(ClientGameTestContext context) {
		UUID cook = new UUID(0, 32);
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getClientWorld().waitForChunksRender();
			var srv = sp.getServer();
			int y = srv.computeOnServer(server -> server.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0));
			scene(context, sp, cook, false, y);
			double eyeY = y + 1.62, targetY = y + 0.5, pz = 3.5;
			float pitch = (float) -Math.toDegrees(Math.atan2(targetY - eyeY, pz - 0.5));
			srv.runCommand("tp @a -1.5 " + y + " " + pz + " 180 " + pitch);
			context.waitTicks(10);
			sp.getClientWorld().waitForChunksRender();
			context.getInput().pressMouse(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT);
			context.waitForScreen(dev.villageheart.client.heart.HeartScreen.class);
			HeartScreenClientTests.openTab(context, 1);
			dev.villageheart.core.heart.VillagerRow row = null;
			for (int i = 0; i < 300 && (row == null || row.activity() != dev.villageheart.core.heart.ActivityKind.NEEDS_FUEL); i++) {
				context.waitTicks(1);
				row = HeartScreenClientTests.snapshot(context).villagers().stream().filter(r -> r.id().equals(cook)).findFirst().orElse(null);
			}
			if (row == null || row.activity() != dev.villageheart.core.heart.ActivityKind.NEEDS_FUEL) throw new AssertionError("the row is " + row);
			String text = dev.villageheart.core.heart.HeartTexts.EN.get(dev.villageheart.core.heart.HeartTexts.activityKey(row.activity(), row.jobId()));
			if (!"Needs fuel".equals(text)) throw new AssertionError("the row reads " + text);
			context.takeScreenshot("villagers_tab_needs_fuel");
			HeartScreenClientTests.assertClean(context, "villagers tab with a fuel-less cook");
			srv.runCommand("gamerule advance_time true");
			srv.runCommand("kill @e[type=minecraft:villager]");
		}
	}
}
