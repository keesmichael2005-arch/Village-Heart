package dev.villageheart.gametest;

import dev.villageheart.core.Tier;
import dev.villageheart.core.gear.GearCraft;
import dev.villageheart.core.gear.GearNeed;
import dev.villageheart.core.gear.ItemCount;
import dev.villageheart.core.gear.OrderKey;
import dev.villageheart.core.gear.SelfCraft;
import dev.villageheart.core.heart.ActivityKind;
import dev.villageheart.core.heart.HeartTexts;
import dev.villageheart.core.heart.VillagerRow;
import dev.villageheart.core.worker.JobType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;

/** Layer 4 test for the self-made tools (M19a). Called from VillageHeartClientTests. */
final class SelfCraftClientTests {
	private SelfCraftClientTests() {}

	private static VillagerRow row(ClientGameTestContext context, UUID id, ActivityKind want) {
		VillagerRow row = null;
		for (int i = 0; i < 300 && (row == null || row.activity() != want); i++) {
			context.waitTicks(1);
			row = HeartScreenClientTests.snapshot(context).villagers().stream().filter(r -> r.id().equals(id)).findFirst().orElse(null);
		}
		if (row == null || row.activity() != want) throw new AssertionError("the row of " + id + " is " + row + ", wanted " + want);
		return row;
	}

	/**
	 * T-L4-1: the Villagers tab with a miner in the middle of making a stone pickaxe ("Making a stone pickaxe") and a lumberjack that has no
	 * axe and nothing to make one from ("Needs axe (no wood)"). A real claim, a real Mine and Lumber Camp, real hires; the
	 * miner's craft is a saved record at progress 1000 (the fetch and the 4800-tick craft are covered by Layer 2).
	 */
	static void villagersTabMakingTool(ClientGameTestContext context) {
		UUID miner = new UUID(0, 19), jack = new UUID(0, 20);
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
			srv.runCommand("fill -24 " + (y - 1) + " -6 14 " + (y - 1) + " 20 minecraft:grass_block");
			srv.computeOnServer(server -> {
				dev.villageheart.world.VillageClaims.data(server).update(v -> v.withTier(Tier.VILLAGE));
				return null;
			});
			// the Mine (x -3..3) with a stonecutter and a chest, and the Lumber Camp (x -13..-7) with a chopping block and a chest
			HeartScreenClientTests.room(srv, -3, y, 5, 4, "mine_marker");
			srv.runCommand("setblock -2 " + y + " 7 minecraft:stonecutter");
			srv.runCommand("setblock 2 " + y + " 10 minecraft:chest");
			srv.runCommand("setblock 0 " + y + " 10 minecraft:torch");
			HeartScreenClientTests.room(srv, -13, y, 5, 4, "lumber_camp_marker");
			srv.runCommand("setblock -12 " + y + " 7 villageheart:chopping_block[facing=south]");
			srv.runCommand("setblock -8 " + y + " 10 minecraft:chest");
			srv.runCommand("setblock -10 " + y + " 10 minecraft:torch");
			BlockPos mineMarker = new BlockPos(0, y + 2, 12), campMarker = new BlockPos(-10, y + 2, 12);
			String mine = "", camp = "";
			for (int i = 0; i < 400 && !(mine.equals("VALID") && camp.equals("VALID")); i++) {
				mine = srv.computeOnServer(server -> HeartScreenClientTests.marker(server.overworld(), mineMarker));
				camp = srv.computeOnServer(server -> HeartScreenClientTests.marker(server.overworld(), campMarker));
				context.waitTicks(1);
			}
			if (!mine.equals("VALID") || !camp.equals("VALID")) throw new AssertionError("the Mine plaque is " + mine + ", the Lumber Camp plaque is " + camp);
			srv.runCommand("summon minecraft:villager 0 " + y + " 8 {PersistenceRequired:1b,UUID:" + HeartScreenClientTests.uuid(miner) + ",CustomName:\"Mina\"}");
			srv.runCommand("summon minecraft:villager -10 " + y + " 8 {PersistenceRequired:1b,UUID:" + HeartScreenClientTests.uuid(jack) + ",CustomName:\"Tom\"}");
			String hired = srv.computeOnServer(server -> dev.villageheart.worker.Workers.hire(server.overworld(),
					server.overworld().getEntity(miner), JobType.MINER, Optional.empty()).toString());
			if (!hired.startsWith("Hired")) throw new AssertionError("the miner hire failed: " + hired);
			hired = srv.computeOnServer(server -> dev.villageheart.worker.Workers.hire(server.overworld(),
					server.overworld().getEntity(jack), JobType.LUMBERJACK, Optional.empty()).toString());
			if (!hired.startsWith("Hired")) throw new AssertionError("the lumberjack hire failed: " + hired);
			// the miner is in the middle of a stone pickaxe: a saved craft at progress 1000
			srv.computeOnServer(server -> {
				var level = server.overworld();
				OrderKey key = new OrderKey(dev.villageheart.world.VillageClaims.locate(level, mineMarker), GearNeed.PICKAXE, 0);
				SelfCraft craft = new SelfCraft(miner, new GearCraft("minecraft:stone_pickaxe", key, 1000,
						List.of(new ItemCount("minecraft:cobblestone", 3), new ItemCount("minecraft:stick", 2))), false);
				dev.villageheart.world.VillageClaims.data(server).update(v -> v.withSelfCraft(craft));
				dev.villageheart.worker.gear.GearOrders.rebuild(server);
				return null;
			});
			double eyeY = y + 1.62, targetY = y + 0.5, pz = 3.5;
			float pitch = (float) -Math.toDegrees(Math.atan2(targetY - eyeY, pz - 0.5));
			srv.runCommand("tp @a -1.5 " + y + " " + pz + " 180 " + pitch);
			context.waitTicks(10);
			sp.getClientWorld().waitForChunksRender();
			context.getInput().pressMouse(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT);
			context.waitForScreen(dev.villageheart.client.heart.HeartScreen.class);
			HeartScreenClientTests.openTab(context, 1);
			VillagerRow minerRow = row(context, miner, ActivityKind.MAKING_STONE_TOOL);
			String text = HeartTexts.EN.get(HeartTexts.activityKey(minerRow.activity(), minerRow.jobId()));
			if (!"Making a stone pickaxe".equals(text)) throw new AssertionError("the miner's row reads " + text);
			VillagerRow jackRow = row(context, jack, ActivityKind.TOOL_NO_MATERIALS);
			text = HeartTexts.EN.get(HeartTexts.activityKey(jackRow.activity(), jackRow.jobId()));
			if (!"Needs axe (no wood)".equals(text)) throw new AssertionError("the lumberjack's row reads " + text);
			context.takeScreenshot("villagers_tab_making_tool");
			HeartScreenClientTests.assertClean(context, "villagers tab with a worker making a tool");
			srv.runCommand("gamerule advance_time true");
			srv.runCommand("kill @e[type=minecraft:villager]");
		}
		dev.villageheart.worker.WorkerSystem.clearOrphanExemptionsForTest();
	}
}
