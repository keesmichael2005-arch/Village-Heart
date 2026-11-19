package dev.villageheart.gametest;

import dev.villageheart.client.guide.GuideHud;
import dev.villageheart.client.heart.HeartScreen;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import org.lwjgl.glfw.GLFW;

/** The task list on the left, and a clicked guide step with its recipe. */
final class GuideClientTests {
	private GuideClientTests() {}

	static void tasksAndRecipe(ClientGameTestContext context) {
		context.runOnClient(mc -> GuideHud.setTasks(List.of()));
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getClientWorld().waitForChunksRender();
			var srv = sp.getServer();
			int y = srv.computeOnServer(server -> server.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0));
			srv.runCommand("time set noon");
			srv.runCommand("weather clear");
			srv.runCommand("gamemode survival @a");
			HeartScreen.recordLayout = true;
			try {
				srv.runCommand("setblock 0 " + y + " 0 minecraft:bell");
				srv.runCommand("setblock -2 " + y + " 0 villageheart:village_heart");
				context.waitTicks(10);
				final int fy = y;
				String claim = srv.computeOnServer(server -> dev.villageheart.world.VillageClaims.claim(server.overworld(),
						new BlockPos(-2, fy, 0), server.getPlayerList().getPlayers().get(0)).name());
				if (!claim.equals("CLAIMED")) throw new AssertionError("the claim failed: " + claim);

				List<String> want = List.of("open_heart", "blank_marker", "house_marker");
				List<String> hud = List.of();
				for (int i = 0; i < 80 && !hud.equals(want); i++) {
					context.waitTicks(1);
					hud = context.computeOnClient(mc -> GuideHud.tasks());
				}
				if (!hud.equals(want)) throw new AssertionError("the first three tasks: " + hud);
				context.takeScreenshot("guide_tasks");

				double eyeY = y + 1.62, targetY = y + 0.5, pz = 3.5;
				float pitch = (float) -Math.toDegrees(Math.atan2(targetY - eyeY, pz - 0.5));
				srv.runCommand("tp @a -1.5 " + y + " " + pz + " 180 " + pitch);
				context.waitTicks(10);
				sp.getClientWorld().waitForChunksRender();
				context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
				context.waitForScreen(HeartScreen.class);
				context.waitTicks(5);
				HeartScreenClientTests.openTab(context, HeartScreen.TAB_GUIDE);
				int[] row = context.computeOnClient(mc -> HeartScreenClientTests.screen(mc).guideRowCentre("blank_marker"));
				if (row == null) throw new AssertionError("no blank-marker row");
				HeartScreenClientTests.clickAt(context, row);
				String selected = context.computeOnClient(mc -> HeartScreenClientTests.screen(mc).selectedGuide());
				if (!selected.equals("blank_marker")) throw new AssertionError("the click selected " + selected);
				HeartScreenClientTests.assertClean(context, "guide detail");
				context.takeScreenshot("guide_detail");

				srv.runCommand("give @p villageheart:blank_marker");
				boolean ticked = false;
				for (int i = 0; i < 40 && !ticked; i++) {
					context.waitTicks(1);
					ticked = context.computeOnClient(mc -> HeartScreenClientTests.screen(mc).getMenu().snapshot().guideDone()
							.contains("blank_marker"));
				}
				if (!ticked) throw new AssertionError("a blank marker in the inventory did not check the step: "
						+ context.computeOnClient(mc -> HeartScreenClientTests.screen(mc).getMenu().snapshot().guideDone()));
			} finally {
				HeartScreen.recordLayout = false;
			}
		}
	}
}
