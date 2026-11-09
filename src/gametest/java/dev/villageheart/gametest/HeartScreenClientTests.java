package dev.villageheart.gametest;

import dev.villageheart.client.heart.HeartScreen;
import dev.villageheart.core.heart.ActivityKind;
import dev.villageheart.core.heart.HeartSnapshot;
import dev.villageheart.core.heart.RowStatus;
import dev.villageheart.core.heart.VillagerRow;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.worker.HireResult;
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
import net.minecraft.world.level.levelgen.Heightmap;
import org.lwjgl.glfw.GLFW;

/**
 * Layer 4: the Heart screen (M8). A seeded village (3 villagers, a Storage and a Farmhouse, items in the chests) opened by a real
 * right-click, the four tabs by real clicks, a screenshot of each, and the Fire button used through the UI. The window is
 * 854 x 480, which at GUI scale 2 is the 427 x 240 the layout is sized for.
 */
final class HeartScreenClientTests {
	private HeartScreenClientTests() {}

	private static final UUID WORKER = new UUID(0, 1);
	private static final UUID ADA = new UUID(0, 2);
	private static final UUID BOB = new UUID(0, 3);

	static HeartScreen screen(net.minecraft.client.Minecraft mc) {
		if (!(mc.screen instanceof HeartScreen s)) throw new AssertionError("the Heart screen is not open: " + mc.screen);
		return s;
	}

	static String uuid(UUID id) {
		long m = id.getMostSignificantBits(), l = id.getLeastSignificantBits();
		return "[I;" + (int) (m >> 32) + "," + (int) m + "," + (int) (l >> 32) + "," + (int) l + "]";
	}

	static void summon(TestServerContext srv, UUID id, String name, int x, int y, int z) {
		srv.runCommand("summon minecraft:villager " + x + " " + y + " " + z + " {NoAI:1b,PersistenceRequired:1b,UUID:" + uuid(id)
				+ (name == null ? "" : ",CustomName:\"" + name + "\"") + "}");
	}

	/** A hollow oak room: shell x0..x0+w+1, z 6..6+d+1, floor on the ground layer, a south door and its marker. */
	static void room(TestServerContext srv, int x0, int y, int w, int d, String marker) {
		int x1 = x0 + w + 1, z1 = 6 + d + 1, doorX = x0 + (w + 1) / 2;
		srv.runCommand("fill " + x0 + " " + (y - 1) + " 6 " + x1 + " " + (y + 3) + " " + z1 + " minecraft:oak_planks hollow");
		srv.runCommand("setblock " + doorX + " " + y + " " + z1 + " minecraft:oak_door[facing=south,half=lower]");
		srv.runCommand("setblock " + doorX + " " + (y + 1) + " " + z1 + " minecraft:oak_door[facing=south,half=upper]");
		srv.runCommand("setblock " + doorX + " " + (y + 2) + " " + (z1 + 1) + " villageheart:" + marker + "[facing=south]");
	}

	static void clickAt(ClientGameTestContext context, int[] gui) {
		int[] px = context.computeOnClient(mc -> {
			var w = mc.getWindow();
			return new int[] {gui[0] * w.getScreenWidth() / w.getGuiScaledWidth(), gui[1] * w.getScreenHeight() / w.getGuiScaledHeight()};
		});
		context.getInput().setCursorPos(px[0], px[1]);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTicks(3);
	}

	static void openTab(ClientGameTestContext context, int tab) {
		int[] c = context.computeOnClient(mc -> screen(mc).tabButtonCentre(tab));
		clickAt(context, c);
		int now = context.computeOnClient(mc -> screen(mc).currentTab());
		if (now != tab) throw new AssertionError("clicking tab " + tab + " selected " + now);
		context.waitTicks(2);
	}

	static void assertClean(ClientGameTestContext context, String what) {
		List<String> problems = context.computeOnClient(mc -> screen(mc).layoutProblems());
		int clipped = context.computeOnClient(mc -> screen(mc).clippedThisFrame());
		if (!problems.isEmpty()) throw new AssertionError(what + ": layout problems " + problems);
		if (clipped != 0) throw new AssertionError(what + ": " + clipped + " texts were cut off");
	}

	static HeartSnapshot snapshot(ClientGameTestContext context) {
		return context.computeOnClient(mc -> screen(mc).getMenu().snapshot());
	}

	static void heartTabs(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getClientWorld().waitForChunksRender();
			var srv = sp.getServer();
			int y = srv.computeOnServer(server -> server.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0));
			srv.runCommand("time set noon");
			srv.runCommand("weather clear");
			srv.runCommand("difficulty peaceful");
			srv.runCommand("gamemode creative @a");
			srv.runCommand("effect give @a minecraft:night_vision infinite 0 true");
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
				String claim = srv.computeOnServer(server -> dev.villageheart.world.VillageClaims.claim(server.overworld(),
						new BlockPos(-2, fy, 0), server.getPlayerList().getPlayers().get(0)).name());
				if (!claim.equals("CLAIMED")) throw new AssertionError("the claim failed: " + claim);

				// Storage: shell x -9..-5 (interior 3x3), two chests. Farmhouse: shell x -3..3 (interior 5x4), composter, torch, chest.
				room(srv, -9, y, 3, 3, "storage_marker");
				srv.runCommand("setblock -8 " + y + " 7 minecraft:chest");
				srv.runCommand("setblock -6 " + y + " 7 minecraft:chest");
				room(srv, -3, y, 5, 4, "farmhouse_marker");
				srv.runCommand("setblock -2 " + y + " 7 minecraft:composter");
				srv.runCommand("setblock 0 " + y + " 10 minecraft:torch");
				srv.runCommand("setblock 2 " + y + " 10 minecraft:chest");
				context.waitTicks(2);
				srv.runCommand("item replace block -8 " + y + " 7 container.0 with minecraft:oak_log 64");
				srv.runCommand("item replace block -8 " + y + " 7 container.1 with minecraft:cobblestone 64");
				srv.runCommand("item replace block -8 " + y + " 7 container.2 with minecraft:cobblestone 64");
				srv.runCommand("item replace block -6 " + y + " 7 container.0 with minecraft:bread 20");
				srv.runCommand("item replace block -6 " + y + " 7 container.1 with minecraft:wheat_seeds 12");
				srv.runCommand("item replace block -6 " + y + " 7 container.2 with minecraft:iron_ingot 5");

				summon(srv, WORKER, null, 4, y, 3);
				summon(srv, ADA, "Ada", 5, y, 4);
				summon(srv, BOB, null, 6, y, 3);

				BlockPos storageMarker = new BlockPos(-7, y + 2, 11), farmMarker = new BlockPos(0, y + 2, 12);
				String state = "";
				for (int i = 0; i < 200; i++) {
					state = srv.computeOnServer(server -> marker(server.overworld(), storageMarker) + "," + marker(server.overworld(), farmMarker));
					if (state.equals("VALID,VALID")) break;
					context.waitTicks(1);
				}
				if (!state.equals("VALID,VALID")) throw new AssertionError("expected two green plaques, got " + state);

				String hired = srv.computeOnServer(server -> {
					var e = server.overworld().getEntity(WORKER);
					HireResult r = Workers.hire(server.overworld(), e, JobType.FARMER, Optional.empty());
					return r.toString();
				});
				if (!hired.startsWith("Hired")) throw new AssertionError("the hire failed: " + hired);

				// stand south of the Heart, looking at it
				double eyeY = y + 1.62, targetY = y + 0.5, pz = 3.5;
				float pitch = (float) -Math.toDegrees(Math.atan2(targetY - eyeY, pz - 0.5));
				srv.runCommand("tp @a -1.5 " + y + " " + pz + " 180 " + pitch);
				context.waitTicks(10);
				sp.getClientWorld().waitForChunksRender();
				BlockPos heart = new BlockPos(-2, y, 0);
				boolean onHeart = context.computeOnClient(mc -> mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit
						&& hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK && hit.getBlockPos().equals(heart));
				if (!onHeart) throw new AssertionError("the crosshair is not on the Heart: " + context.computeOnClient(mc -> String.valueOf(mc.hitResult)));
				context.runOnClient(mc -> mc.gui.getChat().clearMessages(true));
				context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
				context.waitForScreen(HeartScreen.class);
				context.waitTicks(5);

				// ---- Overview
				HeartSnapshot s = snapshot(context);
				if (s.overview().population() != 3 || s.overview().workers() != 1) throw new AssertionError("overview numbers: " + s.overview());
				assertClean(context, "overview");
				context.takeScreenshot("heart_overview");

				// ---- Villagers
				openTab(context, 1);
				s = snapshot(context);
				if (s.villagers().size() != 3) throw new AssertionError("villager rows: " + s.villagers());
				VillagerRow first = s.villagers().get(0);
				if (!first.id().equals(WORKER) || !first.jobId().equals("farmer") || !first.loaded()
						|| first.activity() == ActivityKind.AWAY) {
					throw new AssertionError("first row should be the farmer: " + first);
				}
				if (s.villagers().stream().noneMatch(v -> v.name().equals("Ada"))) throw new AssertionError("Ada has her name tag: " + s.villagers());
				assertClean(context, "villagers");
				context.takeScreenshot("heart_villagers");

				// ---- Stockpile
				openTab(context, 3);
				s = snapshot(context);
				if (s.stock().items().stream().noneMatch(i -> i.itemId().equals("minecraft:oak_log") && i.count() == 64)
						|| s.stock().items().stream().noneMatch(i -> i.itemId().equals("minecraft:bread") && i.count() == 20)
						|| s.stock().nutrition() != 100) {
					throw new AssertionError("stock: " + s.stock());
				}
				assertClean(context, "stockpile");
				context.takeScreenshot("heart_stockpile");

				// ---- Buildings: break the Farmhouse roof, wait for the client to see it
				srv.runCommand("setblock 0 " + (y + 3) + " 8 minecraft:air");
				boolean red = false;
				for (int i = 0; i < 100 && !red; i++) {
					context.waitTicks(1);
					red = snapshot(context).buildings().stream().anyMatch(b -> b.typeId().equals("farmhouse") && b.status() == RowStatus.INVALID
							&& !b.reasons().isEmpty());
				}
				if (!red) throw new AssertionError("the client never saw the Farmhouse turn invalid: " + snapshot(context).buildings());
				openTab(context, 2);
				s = snapshot(context);
				if (s.buildings().size() != 2) throw new AssertionError("building rows: " + s.buildings());
				assertClean(context, "buildings");
				context.takeScreenshot("heart_buildings");

				// ---- Fire through the UI
				openTab(context, 1);
				clickAt(context, context.computeOnClient(mc -> screen(mc).villagerRowCentre(WORKER)));
				context.clickScreenButton("gui.villageheart.heart.fire");
				boolean gone = false;
				for (int i = 0; i < 60 && !gone; i++) {
					context.waitTicks(1);
					boolean serverEmpty = srv.computeOnServer(server -> dev.villageheart.world.VillageClaims.current(server).orElseThrow().workers().isEmpty());
					boolean clientRow = snapshot(context).villagers().stream().anyMatch(v -> v.id().equals(WORKER) && !v.isWorker());
					gone = serverEmpty && clientRow;
				}
				if (!gone) throw new AssertionError("the worker was not fired through the UI: " + snapshot(context).villagers());

				// ---- T-L4-2: a long list scrolls, a long item name is cut with an ellipsis
				for (int i = 0; i < 17; i++) summon(srv, new UUID(1, i), null, 4 + i % 6, y, 6 + i / 6);
				boolean listed = false;
				for (int i = 0; i < 100 && !listed; i++) {
					context.waitTicks(1);
					listed = snapshot(context).villagers().size() == 20;
				}
				if (!listed) throw new AssertionError("20 villagers expected, got " + snapshot(context).villagers().size());
				context.waitTicks(3);
				int max = context.computeOnClient(mc -> screen(mc).maxScrollAmount());
				if (max <= 0) throw new AssertionError("20 rows should scroll, max scroll " + max);
				assertClean(context, "20 villagers");
				context.getInput().scroll(-3);
				context.waitTicks(2);
				int firstRow = context.computeOnClient(mc -> screen(mc).firstVisibleVillagerRow());
				if (firstRow <= 0) throw new AssertionError("scrolling did not move the list: first row " + firstRow);
				assertClean(context, "scrolled villagers");

				srv.runCommand("item replace block -6 " + y + " 7 container.5 with minecraft:waxed_weathered_cut_copper_stairs 1");
				openTab(context, 3);
				boolean longName = false;
				for (int i = 0; i < 100 && !longName; i++) {
					context.waitTicks(1);
					longName = snapshot(context).stock().items().stream().anyMatch(it -> it.itemId().contains("copper_stairs"));
				}
				if (!longName) throw new AssertionError("the long-named item never showed up");
				context.waitTicks(3);
				int clipped = context.computeOnClient(mc -> screen(mc).clippedThisFrame());
				if (clipped != 1) throw new AssertionError("exactly the long item name should be cut, " + clipped + " texts were");
				List<String> problems = context.computeOnClient(mc -> screen(mc).layoutProblems());
				if (!problems.isEmpty()) throw new AssertionError("layout problems: " + problems);

				// ---- Escape closes the screen and the server menu
				context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
				context.waitTicks(5);
				boolean closed = srv.computeOnServer(server -> {
					var p = server.getPlayerList().getPlayers().get(0);
					return p.containerMenu == p.inventoryMenu;
				});
				if (!closed) throw new AssertionError("the server still has the Heart menu open after Escape");
				srv.runCommand("kill @e[type=minecraft:villager]");
				// Quiesce before the world closes (docs/TESTING.md, NetworkSynchronizer): wait until the 20 deaths are over on both sides,
				// so the server is not broadcasting a burst of entity packets while the connection closes.
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
			}
		}
	}

	static String marker(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
		var st = level.getBlockState(pos);
		return st.hasProperty(dev.villageheart.block.MarkerBlock.STATUS) ? st.getValue(dev.villageheart.block.MarkerBlock.STATUS).name() : "MISSING";
	}
}
