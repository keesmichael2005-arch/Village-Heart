package dev.villageheart.gametest;

import dev.villageheart.core.heart.ActivityKind;
import dev.villageheart.core.heart.HeartTexts;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.worker.ModProfessions;
import dev.villageheart.worker.WorkerBrain;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.levelgen.Heightmap;

/** Layer 4: a farmer inside the Farmhouse while rain is falling, not out in the field. */
final class RainClientTests {
	private RainClientTests() {}

	static void rainShelter(ClientGameTestContext context) {
		UUID worker = new UUID(0, 61);
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getClientWorld().waitForChunksRender();
			var srv = sp.getServer();
			int y = srv.computeOnServer(server -> server.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0));
			srv.runCommand("time set noon");
			srv.runCommand("gamerule advance_time false");
			srv.runCommand("gamerule random_tick_speed 0");
			srv.runCommand("gamerule advance_weather false");
			srv.runCommand("weather rain");
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
			srv.runCommand("fill -2 " + (y - 1) + " 16 2 " + (y - 1) + " 20 minecraft:farmland[moisture=7]");
			srv.runCommand("fill -2 " + y + " 16 2 " + y + " 20 minecraft:wheat[age=7]");
			srv.runCommand("summon minecraft:villager 0 " + y + " 14 {PersistenceRequired:1b,UUID:" + HeartScreenClientTests.uuid(worker) + ",CustomName:\"Fern\"}");
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
			boolean inside = false;
			for (int i = 0; i < 80 && !inside; i++) {
				context.waitTicks(10);
				inside = srv.computeOnServer(server -> {
					if (!(server.overworld().getEntity(worker) instanceof Villager v)) return false;
					BlockPos p = v.blockPosition();
					boolean inRoom = p.getX() >= -2 && p.getX() <= 2 && p.getZ() >= 7 && p.getZ() <= 10 && p.getY() >= fy && p.getY() <= fy + 2;
					return inRoom && v.getBrain().isActive(WorkerBrain.RAIN);
				});
			}
			if (!inside) throw new AssertionError("the farmer did not wait inside the Farmhouse");
			String tab = srv.computeOnServer(server -> {
				Villager v = (Villager) server.overworld().getEntity(worker);
				ActivityKind activity = dev.villageheart.heart.HeartFacts.activityOf(v);
				return HeartTexts.EN.get(HeartTexts.activityKey(activity, "farmer"));
			});
			if (!"Out of the rain".equals(tab)) throw new AssertionError("the Villagers tab reads " + tab);
			srv.runCommand("setblock 0 " + y + " 11 minecraft:oak_door[facing=south,half=lower,open=true]");
			srv.runCommand("setblock 0 " + (y + 1) + " 11 minecraft:oak_door[facing=south,half=upper,open=true]");
			srv.runCommand("tp @a 0.5 " + y + " 14.5 facing -1.5 " + (y + 1) + " 7.5");
			boolean hideGui = context.computeOnClient(mc -> mc.options.hideGui);
			int fov = context.computeOnClient(mc -> mc.options.fov().get());
			context.runOnClient(mc -> { mc.options.hideGui = true; mc.options.fov().set(70); });
			context.waitTicks(40);
			sp.getClientWorld().waitForChunksRender();
			String problem = context.computeOnClient(mc -> {
				if (!mc.level.isRaining()) return "the client is not raining";
				Villager found = null;
				for (var e : mc.level.entitiesForRendering()) if (e instanceof Villager villager && villager.getUUID().equals(worker)) found = villager;
				if (found == null) return "no farmer on the client";
				String got = found.getVillagerData().profession().unwrapKey().orElseThrow().identifier().toString();
				if (!got.equals(ModProfessions.key(JobType.FARMER).identifier().toString())) return "profession on the client is " + got;
				BlockPos p = found.blockPosition();
				if (p.getZ() >= 16) return "the farmer is still in the field at " + p.toShortString();
				if (p.getZ() < 7 || p.getZ() > 10 || p.getX() < -2 || p.getX() > 2) return "the farmer is not inside the Farmhouse at " + p.toShortString();
				for (int x = -2; x <= 2; x++) for (int z = 16; z <= 20; z++) {
					var st = mc.level.getBlockState(new BlockPos(x, fy, z));
					if (!st.is(Blocks.WHEAT) || st.getValue(CropBlock.AGE) != 7) return "the field was worked at " + x + "," + z + ": " + st;
				}
				return null;
			});
			if (problem != null) throw new AssertionError("rain shelter: " + problem);
			context.takeScreenshot("rain_shelter");
			context.runOnClient(mc -> { mc.options.hideGui = hideGui; mc.options.fov().set(fov); });
			srv.runCommand("weather clear");
			srv.runCommand("gamerule advance_time true");
			srv.runCommand("gamerule random_tick_speed 3");
			srv.runCommand("kill @e[type=minecraft:villager]");
		}
	}
}
