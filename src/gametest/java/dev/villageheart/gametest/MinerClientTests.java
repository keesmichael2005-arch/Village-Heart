package dev.villageheart.gametest;

import dev.villageheart.building.Buildings;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.Tier;
import dev.villageheart.core.heart.ActivityKind;
import dev.villageheart.core.heart.HeartTexts;
import dev.villageheart.core.heart.VillagerRow;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.worker.job.AwayTrips;
import dev.villageheart.worker.mine.MineSites;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;

/** Layer 4 test for the miner (M16). Called from VillageHeartClientTests. */
final class MinerClientTests {
	private MinerClientTests() {}

	/**
	 * T-L4-1: the Villagers tab of the Heart screen while a miner is inside the Mineshaft: its row reads "Away (mining)" with the miner's
	 * name. A real claim, a real Mine (stonecutter, chest with a stone pickaxe) with a real 12-deep Mineshaft linked to it, a real hire; the
	 * miner is sent down by a test call to {@code AwayTrips.hide} (the walk is covered by Layer 2). The row is checked in the snapshot the
	 * client holds, then the shot is taken.
	 */
	static void villagersTabAwayMining(ClientGameTestContext context) {
		UUID worker = new UUID(0, 16);
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
			// the Mine: a room with a stonecutter and a chest
			HeartScreenClientTests.room(srv, -3, y, 5, 4, "mine_marker");
			srv.runCommand("setblock -2 " + y + " 7 minecraft:stonecutter");
			srv.runCommand("setblock 2 " + y + " 10 minecraft:chest");
			srv.runCommand("setblock 0 " + y + " 10 minecraft:torch");
			// the Mineshaft: a 13-high stone tower with a chamber, a column, 8 rails, a torch, a fence post and its marker (12 deep)
			int sx = 12, sz = 9, top = y + 12;
			srv.runCommand("fill " + (sx - 2) + " " + y + " " + (sz - 2) + " " + (sx + 2) + " " + top + " " + (sz + 2) + " minecraft:stone");
			srv.runCommand("fill " + (sx - 1) + " " + (y + 1) + " " + (sz - 1) + " " + (sx + 1) + " " + (y + 2) + " " + (sz + 1) + " minecraft:air");
			srv.runCommand("fill " + sx + " " + (y + 3) + " " + sz + " " + sx + " " + top + " " + sz + " minecraft:air");
			int n = 0;
			for (int x = sx - 1; x <= sx + 1; x++) for (int z = sz - 1; z <= sz + 1; z++) {
				srv.runCommand("setblock " + x + " " + (y + 1) + " " + z + (n < 8 ? " minecraft:rail" : " minecraft:torch"));
				n++;
			}
			srv.runCommand("setblock " + sx + " " + (top + 1) + " " + (sz - 1) + " minecraft:oak_fence");
			srv.runCommand("setblock " + sx + " " + (top + 1) + " " + sz + " villageheart:mineshaft_marker[facing=south]");
			BlockPos mineMarker = new BlockPos(0, y + 2, 12), shaftMarker = new BlockPos(sx, top + 1, sz);
			String mine = "", shaft = "";
			for (int i = 0; i < 400 && !(mine.equals("VALID") && shaft.equals("VALID")); i++) {
				mine = srv.computeOnServer(server -> HeartScreenClientTests.marker(server.overworld(), mineMarker));
				shaft = srv.computeOnServer(server -> HeartScreenClientTests.marker(server.overworld(), shaftMarker));
				context.waitTicks(1);
			}
			if (!mine.equals("VALID") || !shaft.equals("VALID")) throw new AssertionError("the Mine plaque is " + mine + ", the Mineshaft plaque is " + shaft);
			// a stone pickaxe in the Mine's chest
			srv.computeOnServer(server -> {
				var c = dev.villageheart.stock.StorageContainers.at(server.overworld(), new BlockPos(2, fy, 10));
				c.setItem(0, new ItemStack(Items.STONE_PICKAXE));
				c.setChanged();
				return null;
			});
			// the miner: a villager with AI
			srv.runCommand("summon minecraft:villager 0 " + y + " 8 {PersistenceRequired:1b,UUID:" + HeartScreenClientTests.uuid(worker) + ",CustomName:\"Mina\"}");
			String hired = srv.computeOnServer(server -> dev.villageheart.worker.Workers.hire(server.overworld(),
					server.overworld().getEntity(worker), JobType.MINER, Optional.empty()).toString());
			if (!hired.startsWith("Hired")) throw new AssertionError("the hire failed: " + hired);
			// the miner goes down (a test call: the walk to the shaft is covered by Layer 2)
			boolean down = false;
			for (int i = 0; i < 100 && !down; i++) {
				down = srv.computeOnServer(server -> {
					var level = server.overworld();
					HeartLocation loc = dev.villageheart.world.VillageClaims.locate(level, shaftMarker);
					var depth = Buildings.shaftDepth(server, loc);
					if (depth.isEmpty() || !(level.getEntity(worker) instanceof Villager v)) return false;
					AwayTrips.hide(level, v, "miner", loc, MineSites.holdSpot(level, shaftMarker, depth.get()));
					return true;
				});
				context.waitTicks(1);
			}
			if (!down) throw new AssertionError("the shaft depth never became known");
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
			if (!row.loaded() || !row.name().equals("Mina")) throw new AssertionError("the row has lost its name or is not loaded: " + row);
			String text = HeartTexts.EN.get(HeartTexts.activityKey(row.activity(), row.jobId()));
			if (!"Away (mining)".equals(text)) throw new AssertionError("the row reads " + text);
			context.takeScreenshot("villagers_tab_away_mining");
			HeartScreenClientTests.assertClean(context, "villagers tab with a miner away");
			srv.runCommand("gamerule advance_time true");
			srv.runCommand("kill @e[type=minecraft:villager]");
		}
		dev.villageheart.worker.WorkerSystem.clearOrphanExemptionsForTest();
	}
}
