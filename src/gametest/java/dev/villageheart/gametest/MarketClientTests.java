package dev.villageheart.gametest;

import dev.villageheart.client.heart.HeartScreen;
import dev.villageheart.core.Tier;
import dev.villageheart.core.heart.HeartSnapshot;
import dev.villageheart.core.heart.StockLine;
import dev.villageheart.core.market.Reserves;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.market.Markets;
import dev.villageheart.world.VillageClaims;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Layer 4 for the market (M22, T-L4-1): the Stockpile tab with a merchant that has really sold. A real claim at the VILLAGE tier, a real Market
 * with a stall and a Storage with cobblestone, cooked beef and wheat, a real hire; the shot is taken after the first sale. The reserve
 * buttons are used through the UI.
 */
final class MarketClientTests {
	private MarketClientTests() {}

	private static final UUID MERCHANT = new UUID(0, 22);

	private static long soldToday(TestServerContext srv) {
		return srv.computeOnServer(server -> (long) Markets.state(server).ledger().soldToday());
	}

	private static StockLine line(HeartSnapshot s, String id) {
		return s.stock().items().stream().filter(l -> l.itemId().equals(id)).findFirst().orElse(null);
	}

	static void stockpileReserves(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getClientWorld().waitForChunksRender();
			var srv = sp.getServer();
			int y = srv.computeOnServer(server -> server.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0));
			srv.runCommand("time set 3000");
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
				srv.computeOnServer(server -> {
					VillageClaims.data(server).update(v -> v.withTier(Tier.VILLAGE));
					return null;
				});

				// Storage (x -9..-5, two chests): 200 cobblestone, 40 cooked beef, 30 wheat
				HeartScreenClientTests.room(srv, -9, y, 3, 3, "storage_marker");
				srv.runCommand("setblock -8 " + y + " 7 minecraft:chest");
				srv.runCommand("setblock -6 " + y + " 7 minecraft:chest");
				// Market (x -3..3): one stall, a chest, a torch
				HeartScreenClientTests.room(srv, -3, y, 5, 4, "market_marker");
				srv.runCommand("setblock -2 " + y + " 7 villageheart:market_stall");
				srv.runCommand("setblock 2 " + y + " 10 minecraft:chest");
				srv.runCommand("setblock 0 " + y + " 10 minecraft:torch");
				context.waitTicks(2);
				srv.runCommand("item replace block -8 " + y + " 7 container.0 with minecraft:cobblestone 64");
				srv.runCommand("item replace block -8 " + y + " 7 container.1 with minecraft:cobblestone 64");
				srv.runCommand("item replace block -8 " + y + " 7 container.2 with minecraft:cobblestone 64");
				srv.runCommand("item replace block -8 " + y + " 7 container.3 with minecraft:cobblestone 8");
				srv.runCommand("item replace block -8 " + y + " 7 container.4 with minecraft:cooked_beef 40");
				srv.runCommand("item replace block -8 " + y + " 7 container.5 with minecraft:wheat 30");

				BlockPos[] markers = {new BlockPos(-7, y + 2, 11), new BlockPos(0, y + 2, 12)};
				String state = "";
				for (int i = 0; i < 250; i++) {
					state = srv.computeOnServer(server -> HeartScreenClientTests.marker(server.overworld(), markers[0]) + ","
							+ HeartScreenClientTests.marker(server.overworld(), markers[1]));
					if (state.equals("VALID,VALID")) break;
					context.waitTicks(1);
				}
				if (!state.equals("VALID,VALID")) throw new AssertionError("expected two green plaques, got " + state);

				srv.runCommand("summon minecraft:villager -1 " + y + " 8 {PersistenceRequired:1b,UUID:" + HeartScreenClientTests.uuid(MERCHANT) + ",CustomName:\"Mara\"}");
				String hired = srv.computeOnServer(server -> dev.villageheart.worker.Workers.hire(server.overworld(),
						server.overworld().getEntity(MERCHANT), JobType.MERCHANT, Optional.empty()).toString());
				if (!hired.startsWith("Hired")) throw new AssertionError("the hire failed: " + hired);
				srv.computeOnServer(server -> dev.villageheart.economy.Treasury.set(server, 5));

				// the merchant walks to its stall and sells its first lot (600 ticks of work after the walk)
				boolean sold = false;
				for (int i = 0; i < 150 && !sold; i++) {
					context.waitTicks(20);
					sold = soldToday(srv) >= 1;
				}
				if (!sold) throw new AssertionError("the merchant made no sale in 3000 ticks");

				double eyeY = y + 1.62, targetY = y + 0.5, pz = 3.5;
				float pitch = (float) -Math.toDegrees(Math.atan2(targetY - eyeY, pz - 0.5));
				srv.runCommand("tp @a -1.5 " + y + " " + pz + " 180 " + pitch);
				context.waitTicks(10);
				sp.getClientWorld().waitForChunksRender();
				context.getInput().pressMouse(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT);
				context.waitForScreen(HeartScreen.class);
				HeartScreenClientTests.openTab(context, 3);
				HeartSnapshot s = null;
				for (int i = 0; i < 100; i++) {
					context.waitTicks(1);
					s = HeartScreenClientTests.snapshot(context);
					StockLine cobble = line(s, "minecraft:cobblestone");
					if (s.stock().soldToday() >= 1 && cobble != null && cobble.forSale() > 0) break;
				}
				StockLine cobble = line(s, "minecraft:cobblestone"), wheat = line(s, "minecraft:wheat"), beef = line(s, "minecraft:cooked_beef");
				if (s.stock().soldToday() < 1 || cobble == null || cobble.forSale() <= 0) throw new AssertionError("the tab shows no sale: " + s.stock());
				if (wheat == null || wheat.forSale() != 0) throw new AssertionError("wheat is below the item reserve: " + wheat);
				if (beef == null || beef.forSale() <= 0) throw new AssertionError("cooked beef is above the food reserve: " + beef);
				if (s.stock().merchants() != 1) throw new AssertionError("one merchant: " + s.stock().merchants());
				context.waitTicks(3);
				HeartScreenClientTests.assertClean(context, "stockpile tab with a market");
				List<String> bar = context.computeOnClient(mc -> HeartScreenClientTests.screen(mc).actionLabels());
				if (!bar.equals(List.of("-", "+", "-16", "+16"))) throw new AssertionError("the reserve bar should have four buttons: " + bar);
				context.takeScreenshot("market_stockpile");

				// the buttons: items +16 -> 80, food -1 -> 2 days
				context.clickScreenButton("gui.villageheart.heart.reserve.items_more");
				boolean set = false;
				for (int i = 0; i < 200 && !set; i++) {
					context.waitTicks(1);
					set = srv.computeOnServer(server -> Markets.state(server).reserves().itemReserve() == 80);
				}
				if (!set) throw new AssertionError("the item reserve did not become 80: " + srv.computeOnServer(server -> Markets.state(server).reserves()));
				for (int i = 0; i < 60; i++) {
					context.waitTicks(1);
					s = HeartScreenClientTests.snapshot(context);
					if (s.stock().reserves().itemReserve() == 80) break;
				}
				if (s.stock().reserves().itemReserve() != 80) throw new AssertionError("the screen did not get the new reserve: " + s.stock().reserves());
				cobble = line(s, "minecraft:cobblestone");
				long expect = Math.max(0, cobble.count() - 80);
				if (cobble.forSale() != expect) throw new AssertionError("cobblestone for sale " + cobble.forSale() + ", expected " + expect + " of " + cobble.count() + " in the same snapshot");
				context.clickScreenButton("gui.villageheart.heart.reserve.food_less");
				set = false;
				for (int i = 0; i < 200 && !set; i++) {
					context.waitTicks(1);
					set = srv.computeOnServer(server -> Markets.state(server).reserves().equals(new Reserves(2, 80)));
				}
				if (!set) throw new AssertionError("the food reserve did not become 2 days");
				context.waitTicks(5);
				HeartScreenClientTests.assertClean(context, "stockpile tab after the buttons");
				context.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE);
				context.waitTicks(3);
				srv.runCommand("gamerule advance_time true");
				srv.runCommand("kill @e[type=minecraft:villager]");
			} finally {
				HeartScreen.recordLayout = false;
			}
		}
		dev.villageheart.worker.WorkerSystem.clearOrphanExemptionsForTest();
	}
}
