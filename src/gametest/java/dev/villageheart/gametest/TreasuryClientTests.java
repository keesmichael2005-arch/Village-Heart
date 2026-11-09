package dev.villageheart.gametest;

import dev.villageheart.client.heart.HeartScreen;
import dev.villageheart.core.heart.ActivityKind;
import dev.villageheart.core.heart.AlertCode;
import dev.villageheart.core.heart.AlertLine;
import dev.villageheart.core.heart.HeartSnapshot;
import dev.villageheart.core.heart.VillagerRow;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.needs.NeedsOverride;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.worker.Workers;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.StreamSupport;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import org.lwjgl.glfw.GLFW;

/**
 * Layer 4: the treasury on the Overview (M12, T-L4-1). No test hooks: wages are on, so the three hires are charged at once and strike with an
 * empty treasury. A real right-click with 2 emeralds pays two of them in hiring order; the Overview then shows the treasury, tomorrow's wage
 * bill and the strike and short-treasury alerts, and the Villagers tab shows the third worker on strike. The Deposit all and Withdraw buttons
 * are used through the UI.
 */
final class TreasuryClientTests {
	private TreasuryClientTests() {}

	private static final UUID A = new UUID(0, 21);
	private static final UUID B = new UUID(0, 22);
	private static final UUID C = new UUID(0, 23);

	private static int treasury(TestServerContext srv) {
		return srv.computeOnServer(server -> VillageClaims.current(server).orElseThrow().treasury());
	}

	private static List<UUID> unpaid(TestServerContext srv) {
		return srv.computeOnServer(server -> VillageClaims.data(server).payroll().map(p -> p.unpaid().stream().map(x -> x.villager()).toList()).orElse(List.of()));
	}

	private static int emeralds(ClientGameTestContext context) {
		return context.computeOnClient(mc -> mc.player.getInventory().countItem(Items.EMERALD));
	}

	static void overviewStrike(ClientGameTestContext context) {
		NeedsOverride.wagesEnabled = true; // the older L4 tests switched them off; this one needs the real thing
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
			HeartScreen.recordLayout = true;
			try {
				int[] size = context.computeOnClient(mc -> new int[] {mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight()});
				if (size[0] != 427 || size[1] != 240) throw new AssertionError("the layout is sized for 427 x 240, the window gives " + size[0] + " x " + size[1]);

				srv.runCommand("setblock 0 " + y + " 0 minecraft:bell");
				srv.runCommand("setblock -2 " + y + " 0 villageheart:village_heart");
				context.waitTicks(10);
				final int fy = y;
				String claim = srv.computeOnServer(server -> VillageClaims.claim(server.overworld(), new BlockPos(-2, fy, 0),
						server.getPlayerList().getPlayers().get(0)).name());
				if (!claim.equals("CLAIMED")) throw new AssertionError("the claim failed: " + claim);

				// Storage (x -9..-5, two chests, 20 bread), Farmhouse (x -3..3, three composters), House (x 5..11, three beds, a light)
				HeartScreenClientTests.room(srv, -9, y, 3, 3, "storage_marker");
				srv.runCommand("setblock -8 " + y + " 7 minecraft:chest");
				srv.runCommand("setblock -6 " + y + " 7 minecraft:chest");
				HeartScreenClientTests.room(srv, -3, y, 5, 4, "farmhouse_marker");
				for (int x : new int[] {-2, 0, 2}) srv.runCommand("setblock " + x + " " + y + " 7 minecraft:composter");
				srv.runCommand("setblock 0 " + y + " 10 minecraft:torch");
				srv.runCommand("setblock 2 " + y + " 10 minecraft:chest");
				HeartScreenClientTests.room(srv, 5, y, 5, 4, "house_marker");
				for (int x : new int[] {6, 8, 10}) {
					srv.runCommand("setblock " + x + " " + y + " 7 minecraft:red_bed[facing=south,part=foot]");
					srv.runCommand("setblock " + x + " " + y + " 8 minecraft:red_bed[facing=south,part=head]");
				}
				srv.runCommand("setblock 8 " + y + " 10 minecraft:torch");
				context.waitTicks(2);
				srv.runCommand("item replace block -6 " + y + " 7 container.0 with minecraft:bread 20");

				HeartScreenClientTests.summon(srv, A, "Ada", 4, y, 3);
				HeartScreenClientTests.summon(srv, B, "Bob", 5, y, 4);
				HeartScreenClientTests.summon(srv, C, "Cy", 6, y, 3);

				BlockPos[] markers = {new BlockPos(-7, y + 2, 11), new BlockPos(0, y + 2, 12), new BlockPos(8, y + 2, 12)};
				String state = "";
				for (int i = 0; i < 250; i++) {
					state = srv.computeOnServer(server -> HeartScreenClientTests.marker(server.overworld(), markers[0]) + ","
							+ HeartScreenClientTests.marker(server.overworld(), markers[1]) + "," + HeartScreenClientTests.marker(server.overworld(), markers[2]));
					if (state.equals("VALID,VALID,VALID")) break;
					context.waitTicks(1);
				}
				if (!state.equals("VALID,VALID,VALID")) throw new AssertionError("expected three green plaques, got " + state);

				// three farmers, hired in a known order with an empty treasury: each hire is charged at once and strikes
				for (UUID id : new UUID[] {A, B, C}) {
					String hired = srv.computeOnServer(server -> Workers.hire(server.overworld(), server.overworld().getEntity(id), JobType.FARMER, Optional.empty()).toString());
					if (!hired.startsWith("Hired")) throw new AssertionError("the hire failed: " + hired);
				}
				if (unpaid(srv).size() != 3) throw new AssertionError("all three should be on strike at once: " + unpaid(srv));

				// stand south of the Heart, looking at it, with 2 emeralds in the main hand
				double eyeY = y + 1.62, targetY = y + 0.5, pz = 3.5;
				float pitch = (float) -Math.toDegrees(Math.atan2(targetY - eyeY, pz - 0.5));
				srv.runCommand("tp @a -1.5 " + y + " " + pz + " 180 " + pitch);
				srv.runCommand("item replace entity @a weapon.mainhand with minecraft:emerald 2");
				context.waitTicks(10);
				sp.getClientWorld().waitForChunksRender();
				BlockPos heart = new BlockPos(-2, y, 0);
				boolean onHeart = context.computeOnClient(mc -> mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit
						&& hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK && hit.getBlockPos().equals(heart));
				if (!onHeart) throw new AssertionError("the crosshair is not on the Heart: " + context.computeOnClient(mc -> String.valueOf(mc.hitResult)));
				context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
				boolean settled = false;
				for (int i = 0; i < 40 && !settled; i++) {
					context.waitTicks(1);
					settled = treasury(srv) == 0 && unpaid(srv).equals(List.of(C));
				}
				if (!settled) throw new AssertionError("expected 2 emeralds to pay Ada and Bob: treasury " + treasury(srv) + ", unpaid " + unpaid(srv));
				boolean handEmpty = srv.computeOnServer(server -> server.getPlayerList().getPlayers().get(0).getMainHandItem().isEmpty());
				if (!handEmpty) throw new AssertionError("the emeralds should have left the server-side hand");
				context.waitTicks(10); // creative clients sync their slots in their own way

				// the Overview with an empty main hand
				// 5 emeralds in the last hotbar slot, put there while no screen is open (an open Heart menu hides inventory updates from a creative
				// client); the main hand stays empty, so the click opens the screen instead of depositing
				srv.runCommand("item replace entity @a weapon.mainhand with minecraft:air");
				srv.runCommand("item replace entity @a hotbar.8 with minecraft:emerald 5");
				boolean carried = false;
				for (int i = 0; i < 40 && !carried; i++) {
					context.waitTicks(1);
					carried = emeralds(context) == 5;
				}
				if (!carried) throw new AssertionError("the client should carry 5 emeralds, has " + emeralds(context) + " (the server says "
						+ srv.computeOnServer(server -> server.getPlayerList().getPlayers().get(0).getInventory().countItem(Items.EMERALD)) + ")");
				context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
				context.waitForScreen(HeartScreen.class);
				HeartSnapshot s = null;
				for (int i = 0; i < 60; i++) {
					context.waitTicks(1);
					s = HeartScreenClientTests.snapshot(context);
					List<AlertCode> codes = s.overview().alerts().stream().map(AlertLine::code).toList();
					if (s.overview().treasury() == 0 && s.overview().wageBill() == 3 && codes.equals(List.of(AlertCode.STRIKE, AlertCode.TREASURY_SHORT))) break;
				}
				List<AlertCode> codes = s.overview().alerts().stream().map(AlertLine::code).toList();
				if (s.overview().treasury() != 0 || s.overview().wageBill() != 3 || !codes.equals(List.of(AlertCode.STRIKE, AlertCode.TREASURY_SHORT))) {
					throw new AssertionError("the overview: " + s.overview());
				}
				if (!"1 worker is on strike: deposit emeralds at the Heart".equals(s.overview().alerts().get(0).text().english())
						|| !"Treasury short: 0 of 3 emeralds for tomorrow's wages".equals(s.overview().alerts().get(1).text().english())) {
					throw new AssertionError("alert texts: " + s.overview().alerts());
				}
				context.waitTicks(3);
				HeartScreenClientTests.assertClean(context, "overview with a strike");
				List<String> bar = context.computeOnClient(mc -> HeartScreenClientTests.screen(mc).actionLabels());
				if (bar.size() != 3) throw new AssertionError("the Owner's treasury bar should have 3 buttons: " + bar);
				context.takeScreenshot("treasury_overview");

				// the Villagers tab: the third worker is on strike, the first two are not
				HeartScreenClientTests.openTab(context, 1);
				s = HeartScreenClientTests.snapshot(context);
				VillagerRow ada = row(s, A), bob = row(s, B), cy = row(s, C);
				if (cy.activity() != ActivityKind.ON_STRIKE || cy.needs().paid()) throw new AssertionError("Cy should be on strike: " + cy);
				if (ada.activity() == ActivityKind.ON_STRIKE || !ada.needs().paid() || bob.activity() == ActivityKind.ON_STRIKE || !bob.needs().paid()) {
					throw new AssertionError("Ada and Bob are paid: " + ada + " / " + bob);
				}
				HeartScreenClientTests.assertClean(context, "villagers with a strike");
				context.takeScreenshot("treasury_villagers");

				// the UI path: 5 emeralds in the inventory, "Deposit all"
				HeartScreenClientTests.openTab(context, 0);
				context.waitTicks(3);
				context.clickScreenButton("gui.villageheart.heart.deposit_all");
				boolean deposited = false;
				for (int i = 0; i < 60 && !deposited; i++) {
					context.waitTicks(1);
					deposited = treasury(srv) == 4 && unpaid(srv).isEmpty() && emeralds(context) == 0;
				}
				if (!deposited) throw new AssertionError("expected treasury 4 (5 in, 1 to Cy), nobody on strike, no emerald left: treasury " + treasury(srv)
						+ ", unpaid " + unpaid(srv) + ", carried " + emeralds(context));
				boolean calm = false;
				for (int i = 0; i < 60 && !calm; i++) {
					context.waitTicks(1);
					s = HeartScreenClientTests.snapshot(context);
					calm = s.overview().treasury() == 4 && s.overview().alerts().stream().noneMatch(a -> a.code() == AlertCode.STRIKE);
				}
				if (!calm) throw new AssertionError("the Overview still shows the strike: " + s.overview());
				context.clickScreenButton("gui.villageheart.heart.withdraw_one");
				boolean withdrawn = false;
				for (int i = 0; i < 60 && !withdrawn; i++) {
					context.waitTicks(1);
					withdrawn = treasury(srv) == 3 && emeralds(context) == 1;
				}
				if (!withdrawn) throw new AssertionError("expected treasury 3 and 1 emerald carried: treasury " + treasury(srv) + ", carried " + emeralds(context));

				context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
				context.waitTicks(5);
				srv.runCommand("kill @e[type=minecraft:villager]");
				boolean removed = false;
				for (int i = 0; i < 100 && !removed; i++) {
					context.waitTicks(1);
					boolean serverNone = srv.computeOnServer(server -> server.overworld().getEntities(EntityType.VILLAGER, e -> true).isEmpty());
					boolean clientNone = context.computeOnClient(mc -> StreamSupport.stream(mc.level.entitiesForRendering().spliterator(), false)
							.noneMatch(e -> e.getType() == EntityType.VILLAGER));
					removed = serverNone && clientNone;
				}
				if (!removed) throw new AssertionError("villagers were still present 100 ticks after the kill");
				context.waitTicks(2);
			} finally {
				HeartScreen.recordLayout = false;
				NeedsOverride.wagesEnabled = false;
			}
		}
	}

	private static VillagerRow row(HeartSnapshot s, UUID id) {
		return s.villagers().stream().filter(v -> v.id().equals(id)).findFirst().orElseThrow(() -> new AssertionError("no row for " + id + ": " + s.villagers()));
	}
}
