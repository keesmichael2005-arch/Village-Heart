package dev.villageheart.gametest;

import dev.villageheart.core.siege.ThreatWarnings;
import dev.villageheart.gametest.raid.RaidStart;
import dev.villageheart.raid.ThreatMessages;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Layer 4 for the owner's raid and siege titles (2026-11-11): a real client gets "A raid is starting!" the moment a raid on the claim
 * starts, and "The dead are coming!" when a siege does. Screenshots {@code raid_title} and {@code siege_title}. The siege title is
 * sent through the same {@link ThreatMessages} call the siege start makes: a siege needs ground 44 blocks out to spawn on, and what
 * the screenshot proves is how the title looks on a real client.
 */
final class ThreatTitleClientTests {
	private ThreatTitleClientTests() {}

	static void titles(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getClientWorld().waitForChunksRender();
			TestServerContext srv = sp.getServer();
			int y = srv.computeOnServer(server -> server.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0));
			srv.runCommand("time set day");
			srv.runCommand("weather clear");
			srv.runCommand("difficulty normal");
			srv.runCommand("gamerule advance_time false");
			srv.runCommand("gamerule spawn_mobs false");
			srv.runCommand("gamerule advance_weather false");
			srv.runCommand("gamemode creative @a");
			BlockPos bell = new BlockPos(0, y, 0);
			BlockPos heart = new BlockPos(-2, y, 0);
			srv.runCommand("setblock 0 " + y + " 0 minecraft:bell");
			srv.runCommand("setblock -2 " + y + " 0 villageheart:village_heart");
			context.waitTicks(10);
			String claim = srv.computeOnServer(server -> dev.villageheart.world.VillageClaims.claim(server.overworld(), heart,
					server.getPlayerList().getPlayers().get(0)).name());
			if (!claim.equals("CLAIMED")) throw new AssertionError("the claim failed: " + claim);
			// Vanilla starts a raid only where a POI of the village is occupied. Nobody is hired here, so the bell is taken.
			srv.computeOnServer(server -> {
				server.overworld().getPoiManager().take(type -> type.is(net.minecraft.tags.PoiTypeTags.VILLAGE), (type, pos) -> pos.equals(bell), bell, 1);
				return 0;
			});
			boolean village = false;
			for (int i = 0; i < 200 && !village; i++) {
				context.waitTicks(1);
				village = srv.computeOnServer(server -> server.overworld().isVillage(bell));
			}
			if (!village) throw new AssertionError("the bell never became a village");
			srv.runCommand("tp @a 0.5 " + y + " 3.5 180 0");
			context.waitTicks(10);
			context.runOnClient(mc -> mc.gui.getChat().clearMessages(true));
			srv.computeOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				Raid raid = RaidStart.start(player, bell, 1);
				RaidStart.holdWaves(raid);
				return 0;
			});
			// Ten ticks to fade in, then the title stays for 70: look at it in the middle of the stay.
			for (int i = 0; i < 25; i++) {
				context.waitTicks(1);
				srv.computeOnServer(server -> {
					Raid raid = server.overworld().getRaidAt(bell);
					if (raid != null) RaidStart.holdWaves(raid);
					return 0;
				});
			}
			context.takeScreenshot("raid_title");
			srv.computeOnServer(server -> {
				Raid raid = server.overworld().getRaidAt(bell);
				if (raid != null) raid.stop();
				return 0;
			});
			context.waitTicks(120);
			srv.computeOnServer(server -> {
				ThreatMessages.title(server, ThreatWarnings.SIEGE_TITLE);
				return 0;
			});
			context.waitTicks(25);
			context.takeScreenshot("siege_title");
			context.waitTicks(100);
		}
	}
}
