package dev.villageheart.gametest;

import dev.villageheart.client.pocket.PocketScreen;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.core.worker.WorkerData;
import dev.villageheart.worker.Workers;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

/** Layer 4: the read-only pocket card. */
final class WorkerPocketClientTests {
	private WorkerPocketClientTests() {}

	static void pocket(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			var srv = sp.getServer();
			sp.getClientWorld().waitForChunksRender();
			int y = srv.computeOnServer(server -> server.overworld().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, 0, 0));
			UUID id = new UUID(0, 77);
			srv.runCommand("summon minecraft:villager 0.5 " + y + " 2.5 {NoAI:1b,PersistenceRequired:1b,CustomName:\"Ada\",UUID:" + HeartScreenClientTests.uuid(id) + "}");
			context.waitTicks(10);
			srv.computeOnServer(server -> {
				var v = (net.minecraft.world.entity.npc.villager.Villager) server.overworld().getEntity(id);
				HeartLocation here = new HeartLocation("minecraft:overworld", 0, y, 2);
				Workers.install(server.overworld(), v, WorkerData.hired(JobType.LUMBERJACK, here, here).withProgress(2, 40));
				v.getInventory().setItem(0, new ItemStack(Items.IRON_AXE));
				v.getInventory().setItem(1, new ItemStack(Items.OAK_LOG, 12));
				v.getInventory().setItem(2, new ItemStack(Items.OAK_SAPLING, 4));
				return null;
			});
			srv.runCommand("tp @a 0.5 " + y + " 4.5 180 10");
			context.waitTicks(15);
			sp.getClientWorld().waitForChunksRender();
			context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
			context.waitForScreen(PocketScreen.class);
			context.waitTicks(5);
			context.takeScreenshot("worker_pocket");
			srv.runCommand("kill @e[type=minecraft:villager]");
			context.waitTicks(10);
		}
	}
}
