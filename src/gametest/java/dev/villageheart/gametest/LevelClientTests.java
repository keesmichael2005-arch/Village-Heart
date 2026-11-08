package dev.villageheart.gametest;

import dev.villageheart.client.heart.HeartScreen;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.core.worker.WorkerData;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.worker.ModProfessions;
import dev.villageheart.worker.WorkerAttachments;
import dev.villageheart.worker.WorkerSystem;
import dev.villageheart.worker.Workers;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.levelgen.Heightmap;

/** Layer 4 tests for job levels (M15). Called from VillageHeartClientTests; both screenshots must be looked at. */
final class LevelClientTests {
	private LevelClientTests() {}

	private record Spawned(int entityId, int level) {}

	/**
	 * T-L4-1: three lumberjacks (frozen, facing the camera, noon) at levels 1, 2 and 3. Before the shot the client is asked what
	 * {@code VillagerData.level()} each entity carries (the synced badge); the shot shows the stone, iron and gold belt badges.
	 */
	static void levelBadges(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getClientWorld().waitForChunksRender();
			var srv = sp.getServer();
			int y = srv.computeOnServer(server -> server.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0));
			srv.runCommand("time set noon");
			srv.runCommand("weather clear");
			srv.runCommand("gamemode creative @a");
			srv.runCommand("effect give @a minecraft:night_vision infinite 0 true");
			srv.runCommand("gamerule advance_time false");
			srv.runCommand("fill -6 " + (y - 1) + " -3 6 " + (y - 1) + " 6 minecraft:stone_bricks");
			srv.runCommand("fill -6 " + y + " -3 6 " + (y + 3) + " 6 minecraft:air");
			final int fy = y;
			List<Spawned> spawned = srv.computeOnServer(server -> {
				var level = server.overworld();
				List<Spawned> out = new ArrayList<>();
				for (int i = 0; i < 3; i++) {
					int x = -2 + 2 * i;
					level.setBlock(new BlockPos(x, fy, -1), net.minecraft.core.registries.BuiltInRegistries.BLOCK
							.getValue(net.minecraft.resources.Identifier.parse(JobType.LUMBERJACK.jobBlockKey())).defaultBlockState(), 3);
					Villager v = EntityType.VILLAGER.create(level, EntitySpawnReason.COMMAND);
					v.snapTo(x + 0.5, fy, 0.5, 180.0F, 0.0F);
					v.setPersistenceRequired();
					level.addFreshEntity(v);
					HeartLocation block = VillageClaims.locate(level, new BlockPos(x, fy, -1));
					WorkerSystem.exemptFromOrphanSweepForTest(v.getUUID());
					Workers.install(level, v, WorkerData.hired(JobType.LUMBERJACK, block, block).withProgress(i + 1, i == 0 ? 40 : i == 1 ? 150 : 300));
					v.setNoAi(true);
					out.add(new Spawned(v.getId(), i + 1));
				}
				return out;
			});
			srv.runCommand("tp @a 0.5 " + y + " 4.2 180 6");
			boolean hideGui = context.computeOnClient(mc -> mc.options.hideGui);
			int fov = context.computeOnClient(mc -> mc.options.fov().get());
			context.runOnClient(mc -> { mc.options.hideGui = true; mc.options.fov().set(60); });
			context.waitTicks(40);
			sp.getClientWorld().waitForChunksRender();

			List<String> wrong = context.computeOnClient(mc -> {
				List<String> bad = new ArrayList<>();
				for (Spawned s : spawned) {
					var e = mc.level.getEntity(s.entityId());
					if (!(e instanceof Villager v)) {
						bad.add("entity " + s.entityId() + " is " + e);
						continue;
					}
					String got = v.getVillagerData().profession().unwrapKey().orElseThrow().identifier().toString();
					if (!got.equals(ModProfessions.key(JobType.LUMBERJACK).identifier().toString())) bad.add("entity " + s.entityId() + " profession " + got);
					if (v.getVillagerData().level() != s.level()) bad.add("entity " + s.entityId() + " expected level " + s.level() + " but the client sees " + v.getVillagerData().level());
				}
				return bad;
			});
			if (!wrong.isEmpty()) throw new AssertionError("client-side levels differ: " + wrong);

			context.takeScreenshot("worker_level_badges");
			context.runOnClient(mc -> { mc.options.hideGui = hideGui; mc.options.fov().set(fov); });
			srv.runCommand("gamerule advance_time true");
			srv.runCommand("kill @e[type=minecraft:villager]");
		}
		WorkerSystem.clearOrphanExemptionsForTest();
	}

	/**
	 * T-L4-2: the Villagers tab with a Hamlet worker that has the XP for level 2 (1, 150: its bar is full and gold) and one at (1, 40)
	 * (green, 40 %). A real claim, camp and hires, the screen opened by a real right-click.
	 */
	static void villagersTabLevels(ClientGameTestContext context) {
		UUID capped = new UUID(0, 21), fresh = new UUID(0, 22);
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
				srv.runCommand("setblock 0 " + y + " 0 minecraft:bell");
				srv.runCommand("setblock -2 " + y + " 0 villageheart:village_heart");
				context.waitTicks(10);
				final int fy = y;
				String claim = srv.computeOnServer(server -> VillageClaims.claim(server.overworld(),
						new BlockPos(-2, fy, 0), server.getPlayerList().getPlayers().get(0)).name());
				if (!claim.equals("CLAIMED")) throw new AssertionError("the claim failed: " + claim);
				HeartScreenClientTests.room(srv, -3, y, 5, 4, "lumber_camp_marker");
				srv.runCommand("setblock -2 " + y + " 7 villageheart:chopping_block[facing=south]");
				srv.runCommand("setblock 2 " + y + " 7 villageheart:chopping_block[facing=south]");
				srv.runCommand("setblock 2 " + y + " 10 minecraft:chest");
				srv.runCommand("setblock 0 " + y + " 10 minecraft:torch");
				srv.runCommand("summon minecraft:villager -1 " + y + " 8 {PersistenceRequired:1b,UUID:" + HeartScreenClientTests.uuid(capped) + ",CustomName:\"Ada\"}");
				srv.runCommand("summon minecraft:villager 1 " + y + " 8 {PersistenceRequired:1b,UUID:" + HeartScreenClientTests.uuid(fresh) + ",CustomName:\"Bram\"}");
				BlockPos marker = new BlockPos(0, y + 2, 12);
				String state = "";
				for (int i = 0; i < 200 && !state.equals("VALID"); i++) {
					state = srv.computeOnServer(server -> HeartScreenClientTests.marker(server.overworld(), marker));
					context.waitTicks(1);
				}
				if (!state.equals("VALID")) throw new AssertionError("the Lumber Camp plaque is " + state);
				for (UUID id : List.of(capped, fresh)) {
					String hired = srv.computeOnServer(server -> Workers.hire(server.overworld(), server.overworld().getEntity(id), JobType.LUMBERJACK,
							java.util.Optional.empty()).toString());
					if (!hired.startsWith("Hired")) throw new AssertionError("the hire failed: " + hired);
				}
				// Ada has banked 150 XP at a Hamlet, Bram 40
				srv.computeOnServer(server -> {
					for (var e : List.of(java.util.Map.entry(capped, 150), java.util.Map.entry(fresh, 40))) {
						Villager v = (Villager) server.overworld().getEntity(e.getKey());
						v.setAttached(WorkerAttachments.WORKER, v.getAttached(WorkerAttachments.WORKER).withProgress(1, e.getValue()));
					}
					return null;
				});
				double eyeY = y + 1.62, targetY = y + 0.5, pz = 3.5;
				float pitch = (float) -Math.toDegrees(Math.atan2(targetY - eyeY, pz - 0.5));
				srv.runCommand("tp @a -1.5 " + y + " " + pz + " 180 " + pitch);
				context.waitTicks(10);
				sp.getClientWorld().waitForChunksRender();
				context.getInput().pressMouse(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT);
				context.waitForScreen(HeartScreen.class);
				HeartScreenClientTests.openTab(context, 1);
				dev.villageheart.core.heart.VillagerRow a = null, b = null;
				for (int i = 0; i < 200 && (a == null || b == null || a.xp() != 150 || b.xp() != 40); i++) {
					context.waitTicks(1);
					var rows = HeartScreenClientTests.snapshot(context).villagers();
					a = rows.stream().filter(r -> r.id().equals(capped)).findFirst().orElse(null);
					b = rows.stream().filter(r -> r.id().equals(fresh)).findFirst().orElse(null);
				}
				if (a == null || b == null) throw new AssertionError("rows missing: " + a + " / " + b);
				if (!a.levelCapped() || a.level() != 1 || a.xp() != 150) throw new AssertionError("Ada's row is " + a);
				if (b.levelCapped() || b.level() != 1 || b.xp() != 40 || b.xpForNext() != 100) throw new AssertionError("Bram's row is " + b);
				context.waitTicks(5);
				context.takeScreenshot("heart_villagers_levels");
				HeartScreenClientTests.assertClean(context, "villagers tab with levels");
				srv.runCommand("gamerule advance_time true");
				srv.runCommand("kill @e[type=minecraft:villager]");
				context.waitTicks(20);
			} finally {
				HeartScreen.recordLayout = false;
			}
		}
	}
}
