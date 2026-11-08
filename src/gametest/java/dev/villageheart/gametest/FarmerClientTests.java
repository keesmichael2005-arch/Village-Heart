package dev.villageheart.gametest;

import dev.villageheart.core.worker.JobType;
import dev.villageheart.worker.ModProfessions;
import dev.villageheart.worker.job.WorkStats;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.levelgen.Heightmap;

/** Layer 4 test for the farmer (M10). Called from VillageHeartClientTests. */
final class FarmerClientTests {
	private FarmerClientTests() {}

	/**
	 * T-L4-1: a real run. A claimed village with a real Farmhouse (composter, chest), a hired farmer and a 5x5 field of ripe wheat on moist
	 * farmland in front of it. The server waits until the farmer has really harvested 10 cells (they stand there as age-0 sprouts between
	 * the ripe wheat), freezes it where it stands and sets the composter to level 4. Before the shot the client is asked for the farmer's
	 * profession and for the ages of the field, so a villager that rendered as a plain one or a field that was not worked cannot pass.
	 */
	static void farmerField(ClientGameTestContext context) {
		UUID worker = new UUID(0, 21);
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getClientWorld().waitForChunksRender();
			var srv = sp.getServer();
			int y = srv.computeOnServer(server -> server.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0));
			srv.runCommand("time set noon");
			srv.runCommand("gamerule advance_time false");
			srv.runCommand("gamerule random_tick_speed 0");
			srv.runCommand("weather clear");
			srv.runCommand("difficulty peaceful");
			srv.runCommand("gamemode creative @a");
			srv.runCommand("effect give @a minecraft:night_vision infinite 0 true");
			srv.runCommand("fill -14 " + (y - 1) + " -6 14 " + (y - 1) + " 30 minecraft:grass_block");
			srv.runCommand("fill -14 " + y + " -6 14 " + (y + 8) + " 30 minecraft:air");
			srv.runCommand("setblock 0 " + y + " 0 minecraft:bell");
			srv.runCommand("setblock -2 " + y + " 0 villageheart:village_heart");
			context.waitTicks(10);
			final int fy = y;
			String claim = srv.computeOnServer(server -> dev.villageheart.world.VillageClaims.claim(server.overworld(),
					new BlockPos(-2, fy, 0), server.getPlayerList().getPlayers().get(0)).name());
			if (!claim.equals("CLAIMED")) throw new AssertionError("the claim failed: " + claim);
			HeartScreenClientTests.room(srv, -3, y, 5, 4, "farmhouse_marker");
			srv.runCommand("setblock -2 " + y + " 7 minecraft:composter");
			srv.runCommand("setblock 2 " + y + " 10 minecraft:chest");
			srv.runCommand("setblock 0 " + y + " 10 minecraft:torch");
			// 5x5 ripe wheat on moist farmland, five blocks south of the door
			srv.runCommand("fill -2 " + (y - 1) + " 16 2 " + (y - 1) + " 20 minecraft:farmland[moisture=7]");
			srv.runCommand("fill -2 " + y + " 16 2 " + y + " 20 minecraft:wheat[age=7]");
			srv.runCommand("summon minecraft:villager 0 " + y + " 8 {PersistenceRequired:1b,UUID:" + HeartScreenClientTests.uuid(worker) + ",CustomName:\"Fern\"}");
			BlockPos marker = new BlockPos(0, y + 2, 12);
			String state = "";
			for (int i = 0; i < 200 && !state.equals("VALID"); i++) {
				state = srv.computeOnServer(server -> HeartScreenClientTests.marker(server.overworld(), marker));
				context.waitTicks(1);
			}
			if (!state.equals("VALID")) throw new AssertionError("the Farmhouse plaque is " + state);
			String hired = srv.computeOnServer(server -> dev.villageheart.worker.Workers.hire(server.overworld(),
					server.overworld().getEntity(worker), JobType.FARMER, java.util.Optional.empty()).toString());
			if (!hired.startsWith("Hired")) throw new AssertionError("the hire failed: " + hired);
			long before = srv.computeOnServer(server -> WorkStats.get("farmer", "harvested"));
			long done = 0;
			for (int i = 0; i < 300 && done < 10; i++) {
				context.waitTicks(20);
				done = srv.computeOnServer(server -> WorkStats.get("farmer", "harvested")) - before;
			}
			if (done < 10) throw new AssertionError("the farmer harvested only " + done + " cells");
			srv.runCommand("setblock -2 " + y + " 7 minecraft:composter[level=4]");
			// freeze it where it stands, then look at the field from behind it
			srv.computeOnServer(server -> {
				if (server.overworld().getEntity(worker) instanceof Villager v) {
					v.setNoAi(true);
					v.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
				}
				return null;
			});
			srv.runCommand("tp @a 0.5 " + (y + 4) + " 25.5 facing 0.5 " + (y + 0.3) + " 17.5");
			boolean hideGui = context.computeOnClient(mc -> mc.options.hideGui);
			int fov = context.computeOnClient(mc -> mc.options.fov().get());
			context.runOnClient(mc -> { mc.options.hideGui = true; mc.options.fov().set(70); });
			context.waitTicks(30);
			sp.getClientWorld().waitForChunksRender();

			String problem = context.computeOnClient(mc -> {
				Villager found = null;
				for (var e : mc.level.entitiesForRendering()) if (e instanceof Villager v) found = v;
				if (found == null) return "no villager on the client";
				String got = found.getVillagerData().profession().unwrapKey().orElseThrow().identifier().toString();
				if (!got.equals(ModProfessions.key(JobType.FARMER).identifier().toString())) return "profession on the client is " + got;
				int sprouts = 0, ripe = 0;
				for (int x = -2; x <= 2; x++) for (int z = 16; z <= 20; z++) {
					var st = mc.level.getBlockState(new BlockPos(x, fy, z));
					if (!st.is(Blocks.WHEAT)) return "no wheat at " + x + "," + z + ": " + st;
					int age = st.getValue(CropBlock.AGE);
					if (age == 0) sprouts++;
					if (age == 7) ripe++;
				}
				if (sprouts < 10) return "only " + sprouts + " sprouts on the client";
				if (ripe < 5) return "only " + ripe + " ripe cells on the client";
				return null;
			});
			if (problem != null) throw new AssertionError("farmer scene: " + problem);

			context.takeScreenshot("farmer_field");
			context.runOnClient(mc -> { mc.options.hideGui = hideGui; mc.options.fov().set(fov); });
			srv.runCommand("gamerule advance_time true");
			srv.runCommand("gamerule random_tick_speed 3");
			srv.runCommand("kill @e[type=minecraft:villager]");
		}
	}
}
