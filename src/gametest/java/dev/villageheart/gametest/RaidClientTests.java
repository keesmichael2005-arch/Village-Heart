package dev.villageheart.gametest;

import dev.villageheart.client.heart.HeartScreen;
import dev.villageheart.core.heart.AlertCode;
import dev.villageheart.core.heart.DeathRow;
import dev.villageheart.core.heart.Overview;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.gametest.raid.RaidStart;
import dev.villageheart.worker.HireResult;
import dev.villageheart.worker.Workers;
import java.util.UUID;
import java.util.stream.StreamSupport;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

/**
 * Layer 4 (M25 T-L4-1): the Overview during a held raid, with one death row. Screenshot
 * {@code heart_overview_raid}.
 */
final class RaidClientTests {
	private RaidClientTests() {}

	private static final UUID FARMER = new UUID(3, 1);
	private static final UUID GUARD = new UUID(3, 2);

	static void raidOverview(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getClientWorld().waitForChunksRender();
			TestServerContext srv = sp.getServer();
			int y = srv.computeOnServer(server -> server.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0));
			srv.runCommand("time set day");
			srv.runCommand("weather clear");
			srv.runCommand("difficulty normal");
			srv.runCommand("gamerule advance_time false");
			srv.runCommand("gamerule spawn_mobs false");
			srv.runCommand("gamerule advance_weather false");
			srv.runCommand("gamemode creative @a");
			srv.runCommand("effect give @a minecraft:night_vision infinite 0 true");
			HeartScreen.recordLayout = true;
			try {
				int[] size = context.computeOnClient(mc -> new int[] {mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight()});
				if (size[0] != 427 || size[1] != 240) {
					throw new AssertionError("the layout is sized for a 427 x 240 GUI, the window gives " + size[0] + " x " + size[1]);
				}
				BlockPos bell = new BlockPos(0, y, 0);
				BlockPos heart = new BlockPos(-2, y, 0);
				srv.runCommand("setblock 0 " + y + " 0 minecraft:bell");
				srv.runCommand("setblock -2 " + y + " 0 villageheart:village_heart");
				context.waitTicks(10);
				String claim = srv.computeOnServer(server -> dev.villageheart.world.VillageClaims.claim(server.overworld(), heart,
						server.getPlayerList().getPlayers().get(0)).name());
				if (!claim.equals("CLAIMED")) throw new AssertionError("the claim failed: " + claim);

				HeartScreenClientTests.room(srv, -9, y, 3, 3, "storage_marker");
				srv.runCommand("setblock -8 " + y + " 7 minecraft:chest");
				srv.runCommand("setblock -6 " + y + " 7 minecraft:chest");
				HeartScreenClientTests.room(srv, -3, y, 5, 4, "farmhouse_marker");
				srv.runCommand("setblock -2 " + y + " 7 minecraft:composter");
				srv.runCommand("setblock 0 " + y + " 10 minecraft:torch");
				srv.runCommand("setblock 2 " + y + " 10 minecraft:chest");
				HeartScreenClientTests.room(srv, 10, y, 5, 4, "barracks_marker");
				srv.runCommand("setblock 11 " + y + " 7 minecraft:grindstone");
				srv.runCommand("setblock 12 " + y + " 8 minecraft:red_bed[facing=south,part=foot]");
				srv.runCommand("setblock 12 " + y + " 9 minecraft:red_bed[facing=south,part=head]");
				srv.runCommand("setblock 14 " + y + " 7 minecraft:chest");
				srv.runCommand("setblock 15 " + y + " 10 minecraft:torch");
				context.waitTicks(2);

				BlockPos storageMarker = new BlockPos(-7, y + 2, 11);
				BlockPos farmMarker = new BlockPos(0, y + 2, 12);
				BlockPos barracksMarker = new BlockPos(13, y + 2, 12);
				String state = "";
				for (int i = 0; i < 200; i++) {
					state = srv.computeOnServer(server -> HeartScreenClientTests.marker(server.overworld(), storageMarker) + ","
							+ HeartScreenClientTests.marker(server.overworld(), farmMarker) + ","
							+ HeartScreenClientTests.marker(server.overworld(), barracksMarker));
					if (state.equals("VALID,VALID,VALID")) break;
					context.waitTicks(1);
				}
				if (!state.equals("VALID,VALID,VALID")) throw new AssertionError("expected three green plaques, got " + state);

				HeartScreenClientTests.summon(srv, FARMER, "Ada", 4, y, 3);
				HeartScreenClientTests.summon(srv, GUARD, null, 6, y, 3);
				context.waitTicks(10);
				String hired = srv.computeOnServer(server -> {
					ServerLevel level = server.overworld();
					HireResult farmer = Workers.hire(level, level.getEntity(FARMER), JobType.FARMER, java.util.Optional.empty());
					HireResult guard = Workers.hire(level, level.getEntity(GUARD), JobType.GUARD, java.util.Optional.empty());
					return farmer + " || " + guard;
				});
				String[] hires = hired.split(" \\|\\| ", 2);
				if (hires.length != 2 || !hires[0].startsWith("Hired") || !hires[1].startsWith("Hired")) {
					throw new AssertionError("the hires failed: " + hired);
				}

				boolean village = false;
				for (int i = 0; i < 200 && !village; i++) {
					context.waitTicks(1);
					village = srv.computeOnServer(server -> server.overworld().isVillage(bell));
				}
				if (!village) throw new AssertionError("the bell never became a village");

				srv.runCommand("tp @a 0.5 " + y + " 0.5");
				context.waitTicks(5);
				String started = srv.computeOnServer(server -> {
					ServerPlayer player = server.getPlayerList().getPlayers().get(0);
					Raid raid = RaidStart.start(player, bell, 1);
					RaidStart.holdWaves(raid);
					return "ok";
				});
				if (!started.equals("ok")) throw new AssertionError(started);

				boolean tracked = false;
				for (int i = 0; i < 80 && !tracked; i++) {
					context.waitTicks(1);
					tracked = srv.computeOnServer(server -> {
						Raid raid = server.overworld().getRaidAt(bell);
						if (raid != null) RaidStart.holdWaves(raid);
						return dev.villageheart.world.VillageClaims.current(server).orElseThrow().threats().raid().isPresent();
					});
				}
				if (!tracked) throw new AssertionError("the raid was not tracked");

				srv.runCommand("summon minecraft:vindicator 5 " + y + " 3 {NoAI:1b,PersistenceRequired:1b}");
				context.waitTicks(5);
				String killed = srv.computeOnServer(server -> {
					ServerLevel level = server.overworld();
					Raid raid = level.getRaidAt(bell);
					if (raid == null) return "no raid";
					RaidStart.holdWaves(raid);
					LivingEntity farmer = (LivingEntity) level.getEntity(FARMER);
					if (farmer == null) return "no farmer";
					Vindicator vindicator = null;
					for (Vindicator found : level.getEntities(EntityType.VINDICATOR, e -> true)) vindicator = found;
					if (vindicator == null) return "no vindicator";
					raid.joinRaid(level, 1, vindicator, vindicator.blockPosition(), false);
					farmer.hurtServer(level, level.damageSources().mobAttack(vindicator), 1000.0F);
					return farmer.isDeadOrDying() ? "dead" : "alive";
				});
				if (!killed.equals("dead")) throw new AssertionError("the farmer did not die: " + killed);

				boolean logged = false;
				DeathRow serverRow = null;
				for (int i = 0; i < 40 && !logged; i++) {
					context.waitTicks(1);
					serverRow = srv.computeOnServer(server -> {
						Raid raid = server.overworld().getRaidAt(bell);
						if (raid != null) RaidStart.holdWaves(raid);
						var recent = dev.villageheart.world.VillageClaims.current(server).orElseThrow().deaths().recent(1);
						if (recent.isEmpty()) return null;
						var e = recent.get(0);
						return new DeathRow(e.name(), e.jobId(), e.cause(), e.day(), e.duringRaid());
					});
					logged = serverRow != null && serverRow.duringRaid();
				}
				if (!logged) throw new AssertionError("the death log never recorded a raid death: " + serverRow);

				double eyeY = y + 1.62, targetY = y + 0.5, pz = 3.5;
				float pitch = (float) -Math.toDegrees(Math.atan2(targetY - eyeY, pz - 0.5));
				srv.runCommand("tp @a -1.5 " + y + " " + pz + " 180 " + pitch);
				context.waitTicks(10);
				sp.getClientWorld().waitForChunksRender();
				boolean onHeart = context.computeOnClient(mc -> mc.hitResult instanceof BlockHitResult hit
						&& hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(heart));
				if (!onHeart) throw new AssertionError("the crosshair is not on the Heart: " + context.computeOnClient(mc -> String.valueOf(mc.hitResult)));
				context.runOnClient(mc -> mc.gui.getChat().clearMessages(true));
				context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
				context.waitForScreen(HeartScreen.class);

				Overview seen = null;
				for (int i = 0; i < 100; i++) {
					context.waitTicks(1);
					srv.computeOnServer(server -> {
						Raid raid = server.overworld().getRaidAt(bell);
						if (raid != null) RaidStart.holdWaves(raid);
						return 0;
					});
					seen = HeartScreenClientTests.snapshot(context).overview();
					if (!seen.alerts().isEmpty() && seen.alerts().get(0).code() == AlertCode.RAID && seen.recentDeaths().size() == 1) break;
				}
				if (seen == null || seen.alerts().isEmpty() || seen.alerts().get(0).code() != AlertCode.RAID) {
					throw new AssertionError("the client never showed the raid first: " + seen);
				}
				if (seen.recentDeaths().size() != 1) throw new AssertionError("the client never showed the death: " + seen.recentDeaths());
				DeathRow row = seen.recentDeaths().get(0);
				if (!row.name().equals("Ada") || !row.jobId().equals("farmer") || !row.cause().contains("vindicator") || row.day() != 1
						|| !row.duringRaid() || seen.deathsLogged() < 1) {
					throw new AssertionError("death row " + row + " logged " + seen.deathsLogged());
				}
				HeartScreenClientTests.assertClean(context, "raid overview");
				context.waitTicks(3);
				context.takeScreenshot("heart_overview_raid");

				srv.computeOnServer(server -> {
					Raid raid = server.overworld().getRaidAt(bell);
					if (raid != null) raid.stop();
					for (Vindicator v : server.overworld().getEntities(EntityType.VINDICATOR, e -> true)) v.discard();
					return 0;
				});
				context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
				context.waitTicks(5);
				boolean quiet = false;
				for (int i = 0; i < 100 && !quiet; i++) {
					context.waitTicks(1);
					boolean serverNone = srv.computeOnServer(server -> server.overworld().getEntities(EntityType.VINDICATOR, e -> true).isEmpty());
					boolean clientNone = context.computeOnClient(mc -> StreamSupport.stream(mc.level.entitiesForRendering().spliterator(), false)
							.noneMatch(e -> e.getType() == EntityType.VINDICATOR));
					boolean screenClosed = context.computeOnClient(mc -> mc.screen == null);
					quiet = serverNone && clientNone && screenClosed;
				}
				if (!quiet) throw new AssertionError("the raid screen was not quiet before close");
				context.waitTicks(2);
			} finally {
				HeartScreen.recordLayout = false;
			}
		}
	}
}
