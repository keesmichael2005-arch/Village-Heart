package dev.villageheart.gametest;

import dev.villageheart.client.heart.HeartScreen;
import dev.villageheart.core.heart.AlertCode;
import dev.villageheart.core.heart.HeartSnapshot;
import dev.villageheart.core.heart.VillagerRow;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.needs.NeedsOverride;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.worker.Workers;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import org.lwjgl.glfw.GLFW;

/**
 * The Heart with a paid farmer whose Farmhouse is gone: the Overview names the missing workstation, and the Villagers row
 * still holds the job and says "No workstation".
 */
final class NoWorkstationClientTests {
	private NoWorkstationClientTests() {}

	private static final UUID ADA = new UUID(0, 41);

	static void noWorkstation(ClientGameTestContext context) {
		NeedsOverride.wagesEnabled = true;
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

				HeartScreenClientTests.room(srv, -3, y, 5, 4, "farmhouse_marker");
				srv.runCommand("setblock 0 " + y + " 7 minecraft:composter");
				srv.runCommand("setblock 0 " + y + " 10 minecraft:torch");
				srv.runCommand("setblock 2 " + y + " 10 minecraft:chest");
				HeartScreenClientTests.summon(srv, ADA, "Ada", 1, y, 4);
				BlockPos marker = new BlockPos(0, y + 2, 12);
				String state = "";
				for (int i = 0; i < 250 && !"VALID".equals(state); i++) {
					state = srv.computeOnServer(server -> HeartScreenClientTests.marker(server.overworld(), marker));
					if (!"VALID".equals(state)) context.waitTicks(1);
				}
				if (!"VALID".equals(state)) throw new AssertionError("the Farmhouse never went green: " + state);

				srv.computeOnServer(server -> {
					VillageClaims.depositTreasury(server, 5);
					return 0;
				});
				String hired = srv.computeOnServer(server -> Workers.hire(server.overworld(), server.overworld().getEntity(ADA), JobType.FARMER,
						java.util.Optional.empty()).toString());
				if (!hired.startsWith("Hired")) throw new AssertionError("the hire failed: " + hired);
				srv.runCommand("setblock 0 " + (y + 2) + " 12 minecraft:air");
				boolean waiting = false;
				for (int i = 0; i < 80 && !waiting; i++) {
					waiting = srv.computeOnServer(server -> {
						var rec = VillageClaims.current(server).flatMap(v -> v.worker(ADA));
						return rec.isPresent() && Workers.status(server, rec.get()) == dev.villageheart.core.worker.WorkerStatus.WORKPLACE_INVALID
								&& !dev.villageheart.economy.Wages.onStrike(server, ADA);
					});
					if (!waiting) context.waitTicks(1);
				}
				if (!waiting) throw new AssertionError("Ada should still be a paid farmer with no workstation");

				double eyeY = y + 1.62, targetY = y + 0.5, pz = 3.5;
				float pitch = (float) -Math.toDegrees(Math.atan2(targetY - eyeY, pz - 0.5));
				srv.runCommand("tp @a -1.5 " + y + " " + pz + " 180 " + pitch);
				context.waitTicks(10);
				sp.getClientWorld().waitForChunksRender();
				context.runOnClient(mc -> mc.gui.getChat().clearMessages(true));
				context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
				context.waitForScreen(HeartScreen.class);
				context.waitTicks(5);

				HeartSnapshot s = HeartScreenClientTests.snapshot(context);
				String line = s.overview().alerts().stream().filter(a -> a.code() == AlertCode.WORKPLACE_INVALID).findFirst()
						.map(a -> a.text().english()).orElse("");
				if (!"1 worker has no workstation".equals(line)) throw new AssertionError("overview line: " + s.overview().alerts());
				HeartScreenClientTests.assertClean(context, "overview");
				context.takeScreenshot("no_workstation_overview");

				HeartScreenClientTests.openTab(context, 1);
				s = HeartScreenClientTests.snapshot(context);
				VillagerRow row = s.villagers().stream().filter(v -> v.id().equals(ADA)).findFirst().orElseThrow();
				if (!"farmer".equals(row.jobId()) || row.level() < 1 || !row.workplaceInvalid() || !row.needs().paid()
						|| row.activity() == dev.villageheart.core.heart.ActivityKind.ON_STRIKE) {
					throw new AssertionError("Ada should still be a paid farmer with no workstation: " + row);
				}
				HeartScreenClientTests.assertClean(context, "villagers");
				context.takeScreenshot("no_workstation_villager");
			} finally {
				HeartScreen.recordLayout = false;
			}
		} finally {
			NeedsOverride.wagesEnabled = false;
		}
	}
}
