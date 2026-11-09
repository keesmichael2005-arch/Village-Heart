package dev.villageheart.gametest;

import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.Tier;
import dev.villageheart.core.heart.ActivityKind;
import dev.villageheart.core.heart.HeartTexts;
import dev.villageheart.core.heart.VillagerRow;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.worker.job.AwayTrips;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;

/** Layer 4 test for the hunter (M17). Called from VillageHeartClientTests. */
final class HunterClientTests {
	private HunterClientTests() {}

	/**
	 * T-L4-1: the Villagers tab of the Heart screen while a hunter is out in its Hunting Ground: its row reads "Away (hunting)" with the
	 * hunter's name. A real claim, a real Hunting Lodge (fletching table, chest with a stone sword) with a real Hunting Ground linked to it,
	 * a real hire; the hunter is sent out by a test call to {@code AwayTrips.hide} (the walk is covered by Layer 2). The row is checked in
	 * the snapshot the client holds, then the shot is taken.
	 */
	static void villagersTabAwayHunting(ClientGameTestContext context) {
		UUID worker = new UUID(0, 17);
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getClientWorld().waitForChunksRender();
			var srv = sp.getServer();
			int y = srv.computeOnServer(server -> server.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0));
			srv.runCommand("time set noon");
			srv.runCommand("gamerule advance_time false");
			srv.runCommand("weather clear");
			srv.runCommand("difficulty peaceful");
			srv.runCommand("gamemode creative @a");
			srv.runCommand("effect give @a minecraft:night_vision infinite 0 true");
			srv.runCommand("setblock 0 " + y + " 0 minecraft:bell");
			srv.runCommand("setblock -2 " + y + " 0 villageheart:village_heart");
			context.waitTicks(10);
			final int fy = y;
			String claim = srv.computeOnServer(server -> dev.villageheart.world.VillageClaims.claim(server.overworld(),
					new BlockPos(-2, fy, 0), server.getPlayerList().getPlayers().get(0)).name());
			if (!claim.equals("CLAIMED")) throw new AssertionError("the claim failed: " + claim);
			srv.runCommand("fill -14 " + (y - 1) + " -6 24 " + (y - 1) + " 20 minecraft:grass_block");
			srv.computeOnServer(server -> {
				dev.villageheart.world.VillageClaims.data(server).update(v -> v.withTier(Tier.VILLAGE));
				return null;
			});
			// the Hunting Lodge: a room with a fletching table and a chest
			HeartScreenClientTests.room(srv, -3, y, 5, 4, "hunting_lodge_marker");
			srv.runCommand("setblock -2 " + y + " 7 minecraft:fletching_table");
			srv.runCommand("setblock 2 " + y + " 10 minecraft:chest");
			srv.runCommand("setblock 0 " + y + " 10 minecraft:torch");
			// the Hunting Ground: its marker on a fence post in the middle of the grass (plenty of natural blocks around it)
			int gx = 12, gz = 9;
			srv.runCommand("setblock " + gx + " " + y + " " + (gz - 1) + " minecraft:oak_fence");
			srv.runCommand("setblock " + gx + " " + y + " " + gz + " villageheart:hunting_ground_marker[facing=south]");
			BlockPos lodgeMarker = new BlockPos(0, y + 2, 12), groundMarker = new BlockPos(gx, y, gz);
			String lodge = "", ground = "";
			for (int i = 0; i < 400 && !(lodge.equals("VALID") && ground.equals("VALID")); i++) {
				lodge = srv.computeOnServer(server -> HeartScreenClientTests.marker(server.overworld(), lodgeMarker));
				ground = srv.computeOnServer(server -> HeartScreenClientTests.marker(server.overworld(), groundMarker));
				context.waitTicks(1);
			}
			if (!lodge.equals("VALID") || !ground.equals("VALID")) throw new AssertionError("the Lodge plaque is " + lodge + ", the Hunting Ground plaque is " + ground);
			// a stone sword in the Lodge's chest
			srv.computeOnServer(server -> {
				var c = dev.villageheart.stock.StorageContainers.at(server.overworld(), new BlockPos(2, fy, 10));
				c.setItem(0, new ItemStack(Items.STONE_SWORD));
				c.setChanged();
				return null;
			});
			// the hunter: a villager with AI
			srv.runCommand("summon minecraft:villager 0 " + y + " 8 {PersistenceRequired:1b,UUID:" + HeartScreenClientTests.uuid(worker) + ",CustomName:\"Hana\"}");
			String hired = srv.computeOnServer(server -> dev.villageheart.worker.Workers.hire(server.overworld(),
					server.overworld().getEntity(worker), JobType.HUNTER, Optional.empty()).toString());
			if (!hired.startsWith("Hired")) throw new AssertionError("the hire failed: " + hired);
			// the hunter goes out (a test call: the walk to the ground is covered by Layer 2)
			boolean out = srv.computeOnServer(server -> {
				var level = server.overworld();
				HeartLocation loc = dev.villageheart.world.VillageClaims.locate(level, groundMarker);
				if (!(level.getEntity(worker) instanceof Villager v)) return false;
				AwayTrips.hide(level, v, "hunter", loc, groundMarker.west(2));
				return true;
			});
			if (!out) throw new AssertionError("the hunter was not found");
			double eyeY = y + 1.62, targetY = y + 0.5, pz = 3.5;
			float pitch = (float) -Math.toDegrees(Math.atan2(targetY - eyeY, pz - 0.5));
			srv.runCommand("tp @a -1.5 " + y + " " + pz + " 180 " + pitch);
			context.waitTicks(10);
			sp.getClientWorld().waitForChunksRender();
			context.getInput().pressMouse(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT);
			context.waitForScreen(dev.villageheart.client.heart.HeartScreen.class);
			HeartScreenClientTests.openTab(context, 1);
			VillagerRow row = null;
			for (int i = 0; i < 200 && (row == null || row.activity() != ActivityKind.AWAY_WORKING); i++) {
				context.waitTicks(1);
				row = HeartScreenClientTests.snapshot(context).villagers().stream().filter(r -> r.id().equals(worker)).findFirst().orElse(null);
			}
			if (row == null || row.activity() != ActivityKind.AWAY_WORKING) throw new AssertionError("the row is " + row);
			if (!row.loaded() || !row.name().equals("Hana")) throw new AssertionError("the row has lost its name or is not loaded: " + row);
			String text = HeartTexts.EN.get(HeartTexts.activityKey(row.activity(), row.jobId()));
			if (!"Away (hunting)".equals(text)) throw new AssertionError("the row reads " + text);
			context.takeScreenshot("villagers_tab_away_hunting");
			HeartScreenClientTests.assertClean(context, "villagers tab with a hunter away");
			srv.runCommand("gamerule advance_time true");
			srv.runCommand("kill @e[type=minecraft:villager]");
		}
		dev.villageheart.worker.WorkerSystem.clearOrphanExemptionsForTest();
	}
}
