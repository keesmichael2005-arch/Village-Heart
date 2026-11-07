package dev.villageheart.gametest;

import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.core.worker.WorkerData;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.worker.ModProfessions;
import dev.villageheart.worker.WorkerSystem;
import dev.villageheart.worker.Workers;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.levelgen.Heightmap;

/** Layer 4 test for the worker framework (M7). Called from VillageHeartClientTests. */
final class WorkerClientTests {
	private WorkerClientTests() {}

	private record Spawned(int entityId, String expectedProfession) {}

	/** Vanilla workstations need a facing so the front shows at the camera; the others take the default state. */
	private static String blockState(JobType job) {
		String key = job.jobBlockKey();
		boolean facing = key.startsWith("villageheart:") || key.equals("minecraft:smoker") || key.equals("minecraft:blast_furnace")
				|| key.equals("minecraft:grindstone") || key.equals("minecraft:stonecutter");
		return key + (facing ? "[facing=south]" : "");
	}

	/**
	 * T-L4-1: all nine workers, each standing at its own job block, plus one unemployed villager for contrast. Before the shot the
	 * client is asked what profession each villager entity carries (the synced VillagerData), so a worker that rendered as a plain
	 * villager cannot pass; the shot is then looked at for the apron colours and glyphs and for any missing texture.
	 */
	static void workersAtJobBlocks(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getClientWorld().waitForChunksRender();
			var srv = sp.getServer();
			int y = srv.computeOnServer(server -> server.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0));
			srv.runCommand("time set noon");
			srv.runCommand("weather clear");
			srv.runCommand("gamemode creative @a");
			srv.runCommand("effect give @a minecraft:night_vision infinite 0 true");
			srv.runCommand("gamerule advance_time false");
			srv.runCommand("fill -12 " + (y - 1) + " -3 12 " + (y - 1) + " 6 minecraft:stone_bricks");
			srv.runCommand("fill -12 " + y + " -3 12 " + (y + 3) + " 6 minecraft:air");
			JobType[] jobs = JobType.values();
			final int fy = y;
			// one row, x = -9 .. 9: the nine jobs in JobType order, then the unemployed villager
			List<Spawned> spawned = srv.computeOnServer(server -> {
				var level = server.overworld();
				List<Spawned> out = new ArrayList<>();
				for (int i = 0; i < 10; i++) {
					int x = -9 + 2 * i;
					int z = 0;
					if (i < jobs.length) {
						level.setBlock(new BlockPos(x, fy, z - 1), net.minecraft.core.registries.BuiltInRegistries.BLOCK
								.getValue(net.minecraft.resources.Identifier.parse(jobs[i].jobBlockKey())).defaultBlockState(), 3);
					}
					Villager v = EntityType.VILLAGER.create(level, EntitySpawnReason.COMMAND);
					v.snapTo(x + 0.5, fy, z + 0.5, 180.0F, 0.0F);
					v.setPersistenceRequired();
					level.addFreshEntity(v);
					if (i < jobs.length) {
						HeartLocation block = VillageClaims.locate(level, new BlockPos(x, fy, z - 1));
						WorkerSystem.exemptFromOrphanSweepForTest(v.getUUID());
						Workers.install(level, v, WorkerData.hired(jobs[i], block, block));
						out.add(new Spawned(v.getId(), ModProfessions.key(jobs[i]).identifier().toString()));
					} else {
						out.add(new Spawned(v.getId(), "minecraft:*")); // a vanilla villager: it may well take a free workstation
					}
				}
				return out;
			});
			// the vanilla workstation blocks need their facing set with commands (setblock default states face north)
			for (int i = 0; i < jobs.length; i++) {
				int x = -9 + 2 * i;
				int z = 0;
				srv.runCommand("setblock " + x + " " + y + " " + (z - 1) + " " + blockState(jobs[i]));
			}
			srv.runCommand("tp @a 0.5 " + y + " 8.0 180 8");
			boolean hideGui = context.computeOnClient(mc -> mc.options.hideGui);
			int fov = context.computeOnClient(mc -> mc.options.fov().get());
			context.runOnClient(mc -> { mc.options.hideGui = true; mc.options.fov().set(95); });
			context.waitTicks(40);
			sp.getClientWorld().waitForChunksRender();

			// all ten villagers still exist on the server
			List<String> far = srv.computeOnServer(server -> {
				List<String> bad = new ArrayList<>();
				for (Spawned s : spawned) {
					if (server.overworld().getEntity(s.entityId()) == null) bad.add("entity " + s.entityId() + " is gone");
				}
				return bad;
			});
			if (!far.isEmpty()) throw new AssertionError("villagers missing on the server: " + far);

			// client side: the synced profession of each villager entity
			List<String> wrong = context.computeOnClient(mc -> {
				List<String> bad = new ArrayList<>();
				for (Spawned s : spawned) {
					var e = mc.level.getEntity(s.entityId());
					if (!(e instanceof Villager v)) {
						bad.add("entity " + s.entityId() + " is " + e);
						continue;
					}
					String got = v.getVillagerData().profession().unwrapKey().orElseThrow().identifier().toString();
					boolean ok = s.expectedProfession().equals("minecraft:*") ? got.startsWith("minecraft:") : got.equals(s.expectedProfession());
					if (!ok) bad.add("entity " + s.entityId() + " expected " + s.expectedProfession() + " but is " + got);
				}
				return bad;
			});
			if (!wrong.isEmpty()) throw new AssertionError("client-side professions differ: " + wrong);

			context.takeScreenshot("workers_at_job_blocks");
			context.runOnClient(mc -> { mc.options.hideGui = hideGui; mc.options.fov().set(fov); });
			srv.runCommand("gamerule advance_time true");
			srv.runCommand("kill @e[type=minecraft:villager]");
		}
		WorkerSystem.clearOrphanExemptionsForTest();
	}
}
