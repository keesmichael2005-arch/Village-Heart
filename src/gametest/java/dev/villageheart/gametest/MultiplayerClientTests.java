package dev.villageheart.gametest;

import static dev.villageheart.gametest.HeartScreenClientTests.assertClean;
import static dev.villageheart.gametest.HeartScreenClientTests.clickAt;
import static dev.villageheart.gametest.HeartScreenClientTests.openTab;
import static dev.villageheart.gametest.HeartScreenClientTests.screen;

import dev.villageheart.VillageHeart;
import dev.villageheart.client.heart.HeartScreen;
import dev.villageheart.core.Rank;
import dev.villageheart.core.heart.HeartAction;
import dev.villageheart.core.heart.HeartSnapshot;
import dev.villageheart.core.heart.MemberRow;
import dev.villageheart.heart.HeartActions;
import dev.villageheart.heart.HeartMenu;
import dev.villageheart.world.VillageClaims;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

/**
 * Layer 4, the release gate's "2 players on a dedicated server, no desync" (M24 section 3.8). Fabric's client-gametest API starts an
 * in-process dedicated server and connects the one real test client to it. The second player is a server-side {@link FakePlayer}
 * (the village Owner, "Olga") who acts through the same server code a real client reaches. So this is 1 real networked client + 1
 * server-side player: everything the real client sees crosses the real codecs, the fake Owner's side does not.
 *
 * <p>The client starts as a Visitor, is made a Resident (opens a chest), then the Owner (sees the Ranks tab unlock). After every step the
 * test checks that the client's inventory equals the server's and that the client's Heart snapshot is the last one the server
 * sent. {@code MP_SESSION begin/end} bracket the session in the log; {@code scripts/check-mp-log.sh} fails the run when the
 * server logged an error or anyone was disconnected in between.
 */
final class MultiplayerClientTests {
	private MultiplayerClientTests() {}

	private static final UUID OLGA_ID = new UUID(0xA1, 1);
	private static final String OLGA = "Olga";

	private static int freePort() {
		try (ServerSocket s = new ServerSocket(0)) {
			return s.getLocalPort();
		} catch (java.io.IOException e) {
			throw new AssertionError(e);
		}
	}

	// ---- world layout (surface height y) ----------------------------------------------------------
	// Bell (0,y,0), Heart (-2,y,0). Storage room: shell x 6..12, z 6..11, interior x 7..11, z 7..10, door and marker on the south side.
	// Chests at z = 10: double chest (8,y,10) left + (9,y,10) right, single chest (11,y,10); jukebox (7,y,8).

	private static void buildRoom(TestDedicatedServerContext srv, int y) {
		srv.runCommand("fill 6 " + (y - 1) + " 6 12 " + (y + 3) + " 11 minecraft:oak_planks hollow");
		srv.runCommand("setblock 9 " + y + " 11 minecraft:oak_door[facing=south,half=lower]");
		srv.runCommand("setblock 9 " + (y + 1) + " 11 minecraft:oak_door[facing=south,half=upper]");
		srv.runCommand("setblock 9 " + (y + 2) + " 12 villageheart:storage_marker[facing=south]");
		srv.runCommand("setblock 8 " + y + " 10 minecraft:chest[facing=north,type=left]");
		srv.runCommand("setblock 9 " + y + " 10 minecraft:chest[facing=north,type=right]");
		srv.runCommand("setblock 11 " + y + " 10 minecraft:chest[facing=north,type=single]");
		srv.runCommand("setblock 7 " + y + " 8 minecraft:jukebox");
	}

	// ---- helpers -----------------------------------------------------------------------------------

	private static String describe(Inventory inv) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack st = inv.getItem(i);
			if (!st.isEmpty()) sb.append(i).append(':').append(st.getItem()).append('x').append(st.getCount()).append(st.getComponentsPatch()).append(';');
		}
		return sb.toString();
	}

	private static int count(Container c) {
		int n = 0;
		for (int i = 0; i < c.getContainerSize(); i++) n += c.getItem(i).getCount();
		return n;
	}

	private static BlockHitResult hit(net.minecraft.client.Minecraft mc) {
		return mc.hitResult instanceof BlockHitResult b && b.getType() == HitResult.Type.BLOCK ? b : null;
	}

	/** Stands the client at (px, py, pz) looking at the middle of {@code target}, and checks the crosshair is on it. */
	private static void aimAt(ClientGameTestContext context, TestDedicatedServerContext srv, String me, double px, double py, double pz,
			BlockPos target) {
		double dx = target.getX() + 0.5 - px, dy = target.getY() + 0.5 - (py + 1.62), dz = target.getZ() + 0.5 - pz;
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
		srv.runCommand(String.format(Locale.ROOT, "tp %s %.3f %.3f %.3f %.2f %.2f", me, px, py, pz, yaw, pitch));
		context.waitTicks(8);
		BlockPos got = context.computeOnClient(mc -> hit(mc) == null ? null : hit(mc).getBlockPos());
		if (!target.equals(got)) throw new AssertionError("the crosshair is on " + got + ", not on " + target);
	}

	private static MemberRow member(HeartSnapshot s, UUID id) {
		return s.members().stream().filter(m -> m.id().equals(id)).findFirst().orElse(null);
	}

	/**
	 * The desync checks (plan 3.8 item 5): connected, the fake player still there, the client's inventory equal to the server's, and,
	 * with the Heart screen open, the client's snapshot equal to the last one the server sent AND {@code expect} true for it (so two
	 * equal but stale snapshots cannot pass). Polled for up to 40 ticks.
	 */
	private static void syncCheck(ClientGameTestContext context, TestDedicatedServerContext srv, String me, String step,
			Predicate<HeartSnapshot> expect) {
		List<String> problems = new ArrayList<>();
		for (int attempt = 0; attempt < 40; attempt++) {
			problems.clear();
			boolean connected = context.computeOnClient(mc -> mc.getConnection() != null && mc.player != null && mc.level != null);
			if (!connected) throw new AssertionError(step + ": the client lost its connection");
			String client = context.computeOnClient(mc -> describe(mc.player.getInventory()));
			String server = srv.computeOnServer(s -> {
				if (s.getPlayerList().getPlayerByName(OLGA) == null) return "OLGA_GONE";
				ServerPlayer p = s.getPlayerList().getPlayerByName(me);
				return p == null ? "CLIENT_GONE" : describe(p.getInventory());
			});
			if (server.equals("OLGA_GONE") || server.equals("CLIENT_GONE")) throw new AssertionError(step + ": " + server);
			if (!client.equals(server)) problems.add("inventory differs: client [" + client + "] server [" + server + "]");
			boolean heartOpen = context.computeOnClient(mc -> mc.screen instanceof HeartScreen);
			if (heartOpen) {
				HeartSnapshot mine = context.computeOnClient(mc -> screen(mc).getMenu().snapshot());
				HeartSnapshot sent = srv.computeOnServer(s -> {
					ServerPlayer p = s.getPlayerList().getPlayerByName(me);
					return p != null && p.containerMenu instanceof HeartMenu m ? m.lastSent() : null;
				});
				if (!mine.equals(sent)) problems.add("the client's snapshot is not the last one the server sent");
				else if (!expect.test(mine)) problems.add("the snapshot does not show the expected change: rank " + mine.viewerRank()
						+ ", members " + mine.members().size());
			}
			if (problems.isEmpty()) return;
			context.waitTicks(1);
		}
		throw new AssertionError(step + ": desync " + problems);
	}

	private static Rank rankOf(TestDedicatedServerContext srv, UUID who) {
		return srv.computeOnServer(s -> VillageClaims.current(s).orElseThrow().rankOf(who));
	}

	private static void awaitRank(ClientGameTestContext context, TestDedicatedServerContext srv, UUID who, Rank rank, String what) {
		for (int i = 0; i < 80; i++) {
			if (rankOf(srv, who) == rank) return;
			context.waitTicks(1);
		}
		throw new AssertionError(what + ": the server never made " + who + " a " + rank + " (it is " + rankOf(srv, who) + ")");
	}

	// ---- the session -------------------------------------------------------------------------------

	static void ownerAndResident(ClientGameTestContext context) {
		Properties props = new Properties();
		props.setProperty("server-port", Integer.toString(freePort()));
		VillageHeart.LOG.info("MP_SESSION begin");
		boolean ended = false;
		try (TestDedicatedServerContext srv = context.worldBuilder().createServer(props)) {
			try (TestServerConnection connection = srv.connect()) {
				connection.getClientWorld().waitForChunksRender();
				String me = context.computeOnClient(mc -> mc.getUser().getName());
				FakePlayer[] olgaFake = new FakePlayer[1];
				int y = srv.computeOnServer(s -> s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0));
				srv.runCommand("time set noon");
				srv.runCommand("weather clear");
				srv.runCommand("difficulty peaceful");
				srv.runCommand("gamemode survival " + me);
				srv.runCommand("clear " + me);
				srv.runCommand("tp " + me + " 3 " + y + " 3");
				HeartScreen.recordLayout = true;
				try {
					int[] size = context.computeOnClient(mc -> new int[] {mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight()});
					if (size[0] != 427 || size[1] != 240) throw new AssertionError("expected a 427 x 240 GUI, got " + size[0] + " x " + size[1]);

					// ---- 1. the Owner (a fake player) founds the village
					srv.computeOnServer(s -> {
						olgaFake[0] = FakePlayer.join(s, s.overworld(), OLGA_ID, OLGA, GameType.SURVIVAL, new BlockPos(-1, y, 3));
						return null;
					});
					UUID meId = srv.computeOnServer(s -> s.getPlayerList().getPlayerByName(me).getUUID());
					srv.runCommand("setblock 0 " + y + " 0 minecraft:bell");
					srv.runCommand("setblock -2 " + y + " 0 villageheart:village_heart");
					context.waitTicks(10);
					String claim = srv.computeOnServer(s -> VillageClaims.claim(s.overworld(), new BlockPos(-2, y, 0),
							s.getPlayerList().getPlayerByName(OLGA)).name());
					if (!claim.equals("CLAIMED")) throw new AssertionError("the claim failed: " + claim);
					buildRoom(srv, y);
					context.waitTicks(2);
					srv.runCommand("item replace block 11 " + y + " 10 container.0 with minecraft:oak_log 64");
					srv.runCommand("item replace block 11 " + y + " 10 container.1 with minecraft:bread 20");
					srv.runCommand("item replace block 8 " + y + " 10 container.0 with minecraft:cobblestone 64");
					srv.runCommand("item replace block 9 " + y + " 10 container.0 with minecraft:iron_ingot 5");
					for (int i = 0; i < 200; i++) {
						boolean scanned = srv.computeOnServer(s -> {
							var areas = dev.villageheart.building.Buildings.guardAreas(s);
							return !areas.isEmpty() && areas.stream().allMatch(a -> a.enclosed().isPresent());
						});
						if (scanned) break;
						context.waitTicks(1);
						if (i == 199) throw new AssertionError("the Storage room never got an enclosed scan");
					}
					srv.runCommand("give " + me + " minecraft:music_disc_13");
					context.waitTicks(5);
					syncCheck(context, srv, me, "setup", s -> true);
					int chestTotal = srv.computeOnServer(s -> count((Container) s.overworld().getBlockEntity(new BlockPos(11, y, 10))));
					BlockPos single = new BlockPos(11, y, 10), jukebox = new BlockPos(7, y, 8), left = new BlockPos(8, y, 10), right = new BlockPos(9, y, 10);

					// ---- 2. the client is a Visitor: nothing opens, nothing breaks, and nothing desyncs
					aimAt(context, srv, me, 9.5, y, 8.5, single);
					context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
					context.waitTicks(6);
					boolean noScreen = context.computeOnClient(mc -> mc.screen == null && mc.player.containerMenu == mc.player.inventoryMenu);
					boolean serverClosed = srv.computeOnServer(s -> {
						ServerPlayer p = s.getPlayerList().getPlayerByName(me);
						return p.containerMenu == p.inventoryMenu && count((Container) s.overworld().getBlockEntity(single)) == chestTotal;
					});
					if (!noScreen || !serverClosed) throw new AssertionError("the Visitor opened the chest (client screen closed " + noScreen + ", server " + serverClosed + ")");
					syncCheck(context, srv, me, "visitor chest", s -> true);

					aimAt(context, srv, me, 8.5, y, 8.5, jukebox);
					context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
					context.waitTicks(8);
					String jukeStates = srv.computeOnServer(s -> s.overworld().getBlockState(jukebox).toString());
					String jukeClient = context.computeOnClient(mc -> mc.level.getBlockState(jukebox).toString());
					boolean holding = context.computeOnClient(mc -> mc.player.getMainHandItem().is(net.minecraft.world.item.Items.MUSIC_DISC_13));
					if (!holding) throw new AssertionError("the refused disc vanished from the client's hand");
					if (!jukeStates.equals(jukeClient) || jukeStates.contains("has_record=true")) {
						throw new AssertionError("jukebox states: server " + jukeStates + " client " + jukeClient);
					}
					syncCheck(context, srv, me, "visitor jukebox", s -> true);

					aimAt(context, srv, me, 9.0, y, 8.5, left);
					context.getInput().holdMouseFor(GLFW.GLFW_MOUSE_BUTTON_LEFT, 120);
					context.waitTicks(15);
					String[] before = srv.computeOnServer(s -> new String[] {s.overworld().getBlockState(left).toString(), s.overworld().getBlockState(right).toString()});
					String[] after = context.computeOnClient(mc -> new String[] {mc.level.getBlockState(left).toString(), mc.level.getBlockState(right).toString()});
					if (!before[0].contains("type=left") || !before[1].contains("type=right")) throw new AssertionError("the server's chest changed: " + List.of(before));
					if (!before[0].equals(after[0]) || !before[1].equals(after[1])) {
						throw new AssertionError("the double chest desynced: server " + List.of(before) + " client " + List.of(after));
					}
					syncCheck(context, srv, me, "visitor break", s -> true);

					// ---- 3. the Owner makes the client a Resident; the chest opens and a stack moves
					int olgaMenu = srv.computeOnServer(s -> {
						ServerPlayer o = s.getPlayerList().getPlayerByName(OLGA);
						o.teleportTo(1.5, y, 2.5); // off the client's line of sight to the Heart
						BuildingTestSupport.rightClickAbs(s.overworld(), o, new BlockPos(-2, y, 0), Direction.UP, ItemStack.EMPTY);
						if (!(o.containerMenu instanceof HeartMenu)) throw new AssertionError("Olga's Heart menu is not open");
						HeartActions.handle(o, o.containerMenu.containerId, new HeartAction.SetRank(meId, Rank.RESIDENT.ordinal()));
						return o.containerMenu.containerId;
					});
					awaitRank(context, srv, meId, Rank.RESIDENT, "step 3");
					aimAt(context, srv, me, 9.5, y, 8.5, single);
					context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
					context.waitForScreen(net.minecraft.client.gui.screens.inventory.ContainerScreen.class);
					context.waitTicks(3);
					context.runOnClient(mc -> {
						var menu = mc.player.containerMenu;
						int slot = -1;
						for (int i = 0; i < menu.slots.size() && slot < 0; i++) if (!menu.getSlot(i).getItem().isEmpty() && menu.getSlot(i).container != mc.player.getInventory()) slot = i;
						if (slot < 0) throw new AssertionError("the chest is empty on the client");
						mc.gameMode.handleInventoryMouseClick(menu.containerId, slot, 0, ClickType.QUICK_MOVE, mc.player);
					});
					context.waitTicks(5);
					context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
					context.waitTicks(5);
					int left2 = srv.computeOnServer(s -> count((Container) s.overworld().getBlockEntity(single)));
					int mine = srv.computeOnServer(s -> {
						ServerPlayer p = s.getPlayerList().getPlayerByName(me);
						int n = 0;
						for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
							ItemStack st = p.getInventory().getItem(i);
							if (!st.is(net.minecraft.world.item.Items.MUSIC_DISC_13)) n += st.getCount();
						}
						return n;
					});
					if (mine <= 0 || left2 + mine != chestTotal) throw new AssertionError("the stack did not move: chest " + left2 + ", client " + mine + ", was " + chestTotal);
					syncCheck(context, srv, me, "resident chest", s -> true);

					// ---- 4. the Heart: the Ranks tab is locked, then the Owner gives the village away and it unlocks
					aimAt(context, srv, me, -1.5, y, 3.5, new BlockPos(-2, y, 0));
					context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
					context.waitForScreen(HeartScreen.class);
					context.waitTicks(5);
					syncCheck(context, srv, me, "resident Heart", s -> s.viewerRank() == Rank.RESIDENT && s.members().isEmpty());
					srv.computeOnServer(s -> {
						ServerPlayer o = s.getPlayerList().getPlayerByName(OLGA);
						HeartActions.handle(o, olgaMenu, new HeartAction.SetRank(meId, Rank.STEWARD.ordinal()));
						return null;
					});
					awaitRank(context, srv, meId, Rank.STEWARD, "step 4 steward");
					syncCheck(context, srv, me, "steward Heart", s -> s.viewerRank() == Rank.STEWARD && s.members().isEmpty());
					srv.computeOnServer(s -> {
						ServerPlayer o = s.getPlayerList().getPlayerByName(OLGA);
						HeartActions.handle(o, olgaMenu, new HeartAction.Transfer(meId, Rank.STEWARD.ordinal()));
						return null;
					});
					syncCheck(context, srv, me, "owner Heart", s -> s.viewerRank() == Rank.OWNER && s.members().size() == 2
							&& member(s, OLGA_ID) != null && member(s, OLGA_ID).rank() == Rank.STEWARD && member(s, OLGA_ID).online());
					openTab(context, HeartScreen.TAB_RANKS);
					assertClean(context, "mp ranks tab");
					clickAt(context, context.computeOnClient(mc -> screen(mc).memberRowCentre(OLGA_ID)));
					context.clickScreenButton("gui.villageheart.heart.ranks.make_resident");
					awaitRank(context, srv, OLGA_ID, Rank.RESIDENT, "step 4 UI");
					syncCheck(context, srv, me, "owner demotes Olga", s -> s.viewerRank() == Rank.OWNER && member(s, OLGA_ID) != null
							&& member(s, OLGA_ID).rank() == Rank.RESIDENT);
					context.waitTicks(3);
					context.runOnClient(mc -> mc.getToastManager().clear()); // a recipe toast would cover the tab bar
					context.takeScreenshot("mp_ranks");
					context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
					context.waitTicks(5);
					syncCheck(context, srv, me, "end", s -> true);
				} finally {
					HeartScreen.recordLayout = false;
				}
				// ---- 6. the fake player leaves, then the client; the server stops (and saves)
				VillageHeart.LOG.info("MP_SESSION end");
				ended = true;
				srv.computeOnServer(s -> {
					olgaFake[0].leave(s);
					return null;
				});
			}
		} finally {
			if (!ended) VillageHeart.LOG.info("MP_SESSION end");
		}
	}
}
