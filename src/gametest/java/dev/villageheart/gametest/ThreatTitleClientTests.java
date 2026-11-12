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
 * Layer 4 for the owner's raid and siege start messages (2026-11-11, corrected 2026-11-12): a real client gets the big title "A raid is
 * starting!" AND a bold red chat line the moment a raid on the claim starts (screenshots {@code raid_title}, and {@code raid_title_gui1} at
 * GUI scale 1, the smallest), and only the bold red chat line "The dead are coming!" when a siege does, no title (screenshot
 * {@code siege_start}). The siege lines are sent through the same {@link ThreatMessages#started} call the siege start makes: a siege needs
 * ground 44 blocks out to spawn on, and what the screenshot proves is how the message looks on a real client.
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
			assertTitleFitsAtEveryGuiScale(context, ThreatWarnings.RAID_TITLE.key());
			context.takeScreenshot("raid_title");
			// The smallest GUI scale: the same title and chat line, smaller on the screen.
			setGuiScale(context, 1);
			context.waitTicks(1);
			assertTitleFits(context, ThreatWarnings.RAID_TITLE.key());
			context.takeScreenshot("raid_title_gui1");
			setGuiScale(context, 0);
			srv.computeOnServer(server -> {
				Raid raid = server.overworld().getRaidAt(bell);
				if (raid != null) raid.stop();
				return 0;
			});
			// The raid title and its chat line are gone from the screen before the siege message is sent.
			context.waitTicks(120);
			context.runOnClient(mc -> mc.gui.getChat().clearMessages(true));
			srv.computeOnServer(server -> {
				ThreatMessages.started(server, ThreatWarnings.Kind.SIEGE);
				return 0;
			});
			context.waitTicks(25);
			boolean title = context.computeOnClient(mc -> clientShowsTitle(mc));
			if (title) throw new AssertionError("a siege start showed a title on the client");
			context.takeScreenshot("siege_start");
			context.waitTicks(100);
		}
	}

	/** Is a title on the client's screen now? {@code Gui.title} is private; it is null when no title is showing. */
	private static boolean clientShowsTitle(net.minecraft.client.Minecraft mc) {
		try {
			var f = net.minecraft.client.gui.Gui.class.getDeclaredField("title");
			f.setAccessible(true);
			return f.get(mc.gui) != null;
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("cannot read Gui.title: " + e);
		}
	}

	private static void setGuiScale(ClientGameTestContext context, int scale) {
		context.runOnClient(mc -> mc.options.guiScale().set(scale));
	}

	/** Scale 1 is the smallest, 0 is automatic, and the largest the window allows is where the title is widest next to the screen. */
	private static void assertTitleFitsAtEveryGuiScale(ClientGameTestContext context, String key) {
		int max = context.computeOnClient(mc -> mc.getWindow().calculateScale(0, mc.isEnforceUnicode()));
		for (int scale : new int[] {1, 0, max}) {
			setGuiScale(context, scale);
			assertTitleFits(context, key);
		}
		setGuiScale(context, 0);
	}

	/**
	 * The title is drawn at 4 times the font size. Its rendered width must not exceed the GUI-scaled screen width
	 * (the first "The dead are coming!" lost its first and last letters at the client-test size, 854 by 480).
	 */
	private static void assertTitleFits(ClientGameTestContext context, String key) {
		int[] widths = context.computeOnClient(mc -> new int[] {
				mc.font.width(net.minecraft.network.chat.Component.translatable(key)) * 4, mc.getWindow().getGuiScaledWidth()});
		if (widths[0] > widths[1]) {
			throw new AssertionError("the title " + key + " is " + widths[0] + " px wide on a " + widths[1] + " px screen: it does not fit");
		}
	}
}
