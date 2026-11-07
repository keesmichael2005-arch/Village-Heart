package dev.villageheart.gametest;

import static dev.villageheart.gametest.HeartScreenClientTests.assertClean;
import static dev.villageheart.gametest.HeartScreenClientTests.clickAt;
import static dev.villageheart.gametest.HeartScreenClientTests.openTab;
import static dev.villageheart.gametest.HeartScreenClientTests.screen;
import static dev.villageheart.gametest.HeartScreenClientTests.snapshot;

import dev.villageheart.client.heart.HeartScreen;
import dev.villageheart.core.Rank;
import dev.villageheart.core.heart.HeartSnapshot;
import dev.villageheart.core.heart.MemberRow;
import dev.villageheart.world.VillageClaims;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import org.lwjgl.glfw.GLFW;

/**
 * Layer 4: the Ranks tab (M24). A claimed village with two offline members, the tab opened by a real click, rows picked and rank
 * buttons pressed with the real mouse, then the two-click ownership transfer. Screenshots: {@code heart_ranks},
 * {@code heart_ranks_confirm}. The window is 854 x 480, i.e. the 427 x 240 GUI the layout is sized for.
 */
final class RanksClientTests {
	private RanksClientTests() {}

	private static final UUID BEA = new UUID(0, 11);
	private static final UUID CAL = new UUID(0, 12);

	private static MemberRow member(HeartSnapshot s, UUID id) {
		return s.members().stream().filter(m -> m.id().equals(id)).findFirst().orElse(null);
	}

	static void ranksTab(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getClientWorld().waitForChunksRender();
			var srv = sp.getServer();
			int y = srv.computeOnServer(server -> server.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0));
			srv.runCommand("time set noon");
			srv.runCommand("weather clear");
			srv.runCommand("difficulty peaceful");
			srv.runCommand("gamemode creative @a");
			HeartScreen.recordLayout = true;
			try {
				int[] size = context.computeOnClient(mc -> new int[] {mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight()});
				if (size[0] != 427 || size[1] != 240) {
					throw new AssertionError("the layout is sized for a 427 x 240 GUI, the window gives " + size[0] + " x " + size[1]);
				}
				srv.runCommand("setblock 0 " + y + " 0 minecraft:bell");
				srv.runCommand("setblock -2 " + y + " 0 villageheart:village_heart");
				context.waitTicks(10);
				final int fy = y;
				String claim = srv.computeOnServer(server -> VillageClaims.claim(server.overworld(), new BlockPos(-2, fy, 0),
						server.getPlayerList().getPlayers().get(0)).name());
				if (!claim.equals("CLAIMED")) throw new AssertionError("the claim failed: " + claim);
				UUID me = srv.computeOnServer(server -> server.getPlayerList().getPlayers().get(0).getUUID());
				srv.runCommand("tp @a -1.5 " + y + " 3.5 180 " + (float) -Math.toDegrees(Math.atan2(y + 0.5 - (y + 1.62), 3.0)));
				context.waitTicks(10);

				// two offline members, written straight into the saved data: nothing extra on the client connection
				srv.computeOnServer(server -> {
					long t = server.overworld().getGameTime();
					VillageClaims.data(server).update(v -> v.withRank(BEA, "Bea", Rank.STEWARD, t).withRank(CAL, "Cal", Rank.RESIDENT, t));
					return null;
				});
				sp.getClientWorld().waitForChunksRender();
				context.runOnClient(mc -> mc.gui.getChat().clearMessages(true));
				context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
				context.waitForScreen(HeartScreen.class);
				context.waitTicks(5);

				openTab(context, HeartScreen.TAB_RANKS);
				HeartSnapshot s = snapshot(context);
				List<MemberRow> rows = s.members();
				if (rows.size() != 3 || rows.get(0).rank() != Rank.OWNER || !rows.get(0).online() || !rows.get(0).id().equals(me)
						|| !rows.get(1).id().equals(BEA) || rows.get(1).rank() != Rank.STEWARD || rows.get(1).online() || !rows.get(1).name().equals("Bea")
						|| !rows.get(2).id().equals(CAL) || rows.get(2).rank() != Rank.RESIDENT || rows.get(2).online() || !rows.get(2).name().equals("Cal")) {
					throw new AssertionError("expected Owner (online), Bea (Steward, offline), Cal (Resident, offline), got " + rows);
				}
				assertClean(context, "ranks tab");
				context.takeScreenshot("heart_ranks");

				// Cal becomes a Steward through the UI
				clickAt(context, context.computeOnClient(mc -> screen(mc).memberRowCentre(CAL)));
				context.clickScreenButton("gui.villageheart.heart.ranks.make_steward");
				awaitRank(context, srv, CAL, Rank.STEWARD);
				boolean shown = false;
				for (int i = 0; i < 60 && !shown; i++) {
					context.waitTicks(1);
					MemberRow cal = member(snapshot(context), CAL);
					shown = cal != null && cal.rank() == Rank.STEWARD;
				}
				if (!shown) throw new AssertionError("the client row never said Steward: " + snapshot(context).members());
				assertClean(context, "after the rank change");

				// Transfer to Bea needs two clicks
				clickAt(context, context.computeOnClient(mc -> screen(mc).memberRowCentre(BEA)));
				context.clickScreenButton("gui.villageheart.heart.ranks.transfer");
				context.waitTicks(3);
				if (!context.computeOnClient(mc -> screen(mc).transferPending())) throw new AssertionError("the first click should ask for confirmation");
				context.waitTicks(10);
				UUID ownerNow = srv.computeOnServer(server -> VillageClaims.current(server).orElseThrow().owner());
				if (!ownerNow.equals(me)) throw new AssertionError("one click already gave the village away");
				assertClean(context, "transfer confirm");
				context.takeScreenshot("heart_ranks_confirm");
				// the label now carries the player name, which clickScreenButton (it compares the untranslated text) cannot match
				clickAt(context, context.computeOnClient(mc -> screen(mc).lastActionCentre()));
				boolean gone = false;
				for (int i = 0; i < 80 && !gone; i++) {
					context.waitTicks(1);
					boolean serverSays = srv.computeOnServer(server -> VillageClaims.current(server).orElseThrow().owner().equals(BEA));
					gone = serverSays && snapshot(context).viewerRank() == Rank.STEWARD;
				}
				if (!gone) throw new AssertionError("the transfer did not reach the server and the client: " + snapshot(context).viewerRank());
				Rank mine = srv.computeOnServer(server -> VillageClaims.current(server).orElseThrow().rankOf(me));
				if (mine != Rank.STEWARD) throw new AssertionError("the old owner should be a Steward, is " + mine);
				context.waitTicks(3);
				if (context.computeOnClient(mc -> screen(mc).currentTab()) != 0) throw new AssertionError("the Ranks tab stayed open after losing ownership");
				int[] c = context.computeOnClient(mc -> screen(mc).tabButtonCentre(HeartScreen.TAB_RANKS));
				clickAt(context, c);
				if (context.computeOnClient(mc -> screen(mc).currentTab()) != 0) throw new AssertionError("a Steward could open the Ranks tab");
				if (!snapshot(context).members().isEmpty()) throw new AssertionError("a Steward is sent the member list");

				context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
				context.waitTicks(5);
			} finally {
				HeartScreen.recordLayout = false;
			}
		}
	}

	private static void awaitRank(ClientGameTestContext context, net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext srv,
			UUID who, Rank rank) {
		for (int i = 0; i < 60; i++) {
			context.waitTicks(1);
			Rank now = srv.computeOnServer(server -> VillageClaims.current(server).orElseThrow().rankOf(who));
			if (now == rank) return;
		}
		throw new AssertionError("the server never gave " + who + " the rank " + rank);
	}
}
