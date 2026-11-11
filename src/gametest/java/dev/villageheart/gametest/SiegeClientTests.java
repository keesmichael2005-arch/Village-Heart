package dev.villageheart.gametest;

import dev.villageheart.client.heart.HeartScreen;
import dev.villageheart.core.Tier;
import dev.villageheart.core.defence.DefenceCell;
import dev.villageheart.core.defence.DefenceIndex;
import dev.villageheart.core.defence.DefenceKind;
import dev.villageheart.core.heart.AlertCode;
import dev.villageheart.core.heart.Severity;
import dev.villageheart.core.siege.SiegeHits;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.siege.SiegeDamage;
import dev.villageheart.worker.Workers;
import dev.villageheart.world.VillageClaims;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

/**
 * Layer 4 (M25 T-L4-2). A recorded 10-long wall with four crack stages and one hole, the amber damage line, and the
 * same cracks after the player walks away and back.
 */
final class SiegeClientTests {
	private SiegeClientTests() {}

	private static final UUID BUILDER = new UUID(9, 1);

	static void wallCracks(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getClientWorld().waitForChunksRender();
			TestServerContext srv = sp.getServer();
			int y = srv.computeOnServer(server -> server.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0));
			int g = y - 1;
			srv.runCommand("time set noon");
			srv.runCommand("weather clear");
			srv.runCommand("difficulty normal");
			srv.runCommand("gamemode creative @a");
			srv.runCommand("gamerule advance_time false");
			srv.runCommand("gamerule spawn_mobs false");
			srv.runCommand("effect give @a minecraft:night_vision infinite 0 true");
			srv.runCommand("fill -24 " + g + " -4 24 " + g + " 84 minecraft:grass_block");
			srv.runCommand("fill -24 " + y + " -4 24 " + (y + 8) + " 84 minecraft:air");
			HeartScreen.recordLayout = true;
			try {
				int[] size = context.computeOnClient(mc -> new int[] {mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight()});
				if (size[0] != 427 || size[1] != 240) throw new AssertionError("the layout is sized for a 427 x 240 GUI, the window gives " + size[0] + " x " + size[1]);
				srv.runCommand("setblock 0 " + y + " 0 minecraft:bell");
				srv.runCommand("setblock -2 " + y + " 0 villageheart:village_heart");
				context.waitTicks(10);
				String claim = srv.computeOnServer(server -> VillageClaims.claim(server.overworld(), new BlockPos(-2, y, 0),
						server.getPlayerList().getPlayers().get(0)).name());
				if (!claim.equals("CLAIMED")) throw new AssertionError("the claim failed: " + claim);
				srv.computeOnServer(server -> {
					VillageClaims.data(server).update(v -> v.withTier(Tier.VILLAGE));
					return 0;
				});
				HeartScreenClientTests.room(srv, -9, y, 3, 3, "storage_marker");
				srv.runCommand("setblock -8 " + y + " 7 minecraft:chest");
				srv.runCommand("setblock -6 " + y + " 7 minecraft:chest");
				HeartScreenClientTests.room(srv, -3, y, 5, 4, "builders_hut_marker");
				srv.runCommand("setblock -2 " + y + " 7 villageheart:builders_table[facing=south]");
				srv.runCommand("setblock 2 " + y + " 10 minecraft:chest");
				srv.runCommand("setblock 0 " + y + " 10 minecraft:torch");
				srv.runCommand("item replace block -8 " + y + " 7 container.0 with minecraft:cobblestone 64");
				context.waitTicks(2);
				awaitMarker(context, srv, new BlockPos(-7, y + 2, 11), "Storage");
				awaitMarker(context, srv, new BlockPos(0, y + 2, 12), "Builder's Hut");

				srv.runCommand("villageheart build line wall_segment -4 " + g + " 32 5 " + g + " 32");
				String built = srv.computeOnServer(server -> {
					var jobs = new java.util.ArrayList<>(dev.villageheart.build.BuildJobs.list(server));
					if (jobs.size() != 1) return "builds " + jobs.size();
					var done = dev.villageheart.build.BuildJobs.advance(server, jobs.get(0).id(), Integer.MAX_VALUE);
					if (!done.finished()) return "not finished " + done;
					int sites = VillageClaims.current(server).orElseThrow().defences().size();
					return sites == 1 ? "ok" : "defences " + sites;
				});
				if (!built.equals("ok")) throw new AssertionError("the wall was not recorded: " + built);

				look(srv, 0.5, y, 40.5, 0.5, y + 1.5, 32.5);
				context.waitTicks(5);
				String damaged = srv.computeOnServer(server -> damage(server));
				if (!damaged.equals("ok")) throw new AssertionError(damaged);
				context.waitTicks(40);
				context.runOnClient(mc -> mc.gui.getChat().clearMessages(true));
				sp.getClientWorld().waitForChunksRender();
				context.takeScreenshot("wall_cracks");

				HeartScreenClientTests.summon(srv, BUILDER, "Mason", -2, y, 8);
				context.waitTicks(5);
				String hired = srv.computeOnServer(server -> {
					var villager = server.overworld().getEntity(BUILDER);
					var result = Workers.hire(server.overworld(), villager, JobType.BUILDER, Optional.empty());
					return result instanceof dev.villageheart.worker.HireResult.Hired ? "ok" : String.valueOf(result);
				});
				if (!hired.equals("ok")) throw new AssertionError("the builder was not hired: " + hired);

				double eyeY = y + 1.62;
				float pitch = (float) -Math.toDegrees(Math.atan2((y + 0.5) - eyeY, 3.0));
				srv.runCommand("tp @a -1.5 " + y + " 3.5 180 " + pitch);
				context.waitTicks(10);
				boolean onHeart = context.computeOnClient(mc -> mc.hitResult instanceof BlockHitResult hit
						&& hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(new BlockPos(-2, y, 0)));
				if (!onHeart) throw new AssertionError("the crosshair is not on the Heart: " + context.computeOnClient(mc -> String.valueOf(mc.hitResult)));
				context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
				context.waitForScreen(HeartScreen.class);
				var seen = HeartScreenClientTests.snapshot(context).overview();
				for (int i = 0; i < 40 && (seen.alerts().isEmpty() || seen.alerts().get(0).code() != AlertCode.DEFENCE_DAMAGED); i++) {
					context.waitTicks(1);
					seen = HeartScreenClientTests.snapshot(context).overview();
				}
				if (seen.alerts().isEmpty() || seen.alerts().get(0).code() != AlertCode.DEFENCE_DAMAGED
						|| seen.alerts().get(0).severity() != Severity.WARNING
						|| !seen.alerts().get(0).text().english().equals("Walls damaged: 1 broken, 4 cracked")) {
					throw new AssertionError("the damage line was not amber: " + seen.alerts());
				}
				HeartScreenClientTests.assertClean(context, "damaged walls");
				context.waitTicks(3);
				context.takeScreenshot("heart_overview_walls_damaged");
				context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
				context.waitTicks(5);
				srv.computeOnServer(server -> {
					var villager = server.overworld().getEntity(BUILDER);
					if (villager instanceof net.minecraft.world.entity.npc.villager.Villager v) Workers.fire(server.overworld(), v);
					return 0;
				});

				look(srv, 0.5, y, 80.5, 0.5, y + 1.5, 32.5);
				context.waitTicks(20);
				look(srv, 0.5, y, 40.5, 0.5, y + 1.5, 32.5);
				context.waitTicks(220);
				context.runOnClient(mc -> mc.gui.getChat().clearMessages(true));
				sp.getClientWorld().waitForChunksRender();
				context.takeScreenshot("wall_cracks_after_return");
			} finally {
				HeartScreen.recordLayout = false;
			}
		}
	}

	private static void look(TestServerContext srv, double px, double py, double pz, double tx, double ty, double tz) {
		double dx = tx - px, dz = tz - pz, horiz = Math.sqrt(dx * dx + dz * dz);
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) -Math.toDegrees(Math.atan2(ty - (py + 1.62), horiz));
		srv.runCommand("tp @a " + px + " " + py + " " + pz + " " + yaw + " " + pitch);
	}

	private static void awaitMarker(ClientGameTestContext context, TestServerContext srv, BlockPos pos, String name) {
		String state = "";
		for (int i = 0; i < 200; i++) {
			state = srv.computeOnServer(server -> {
				var block = server.overworld().getBlockState(pos);
				return block.getBlock() instanceof dev.villageheart.block.MarkerBlock
						? block.getValue(dev.villageheart.block.MarkerBlock.STATUS).name() : "none";
			});
			if (state.equals("VALID")) return;
			context.waitTicks(1);
		}
		throw new AssertionError(name + " marker stayed " + state);
	}

	/** Four spaced stones of the bottom course at 3, 6, 9 and 11 of 12, and the middle of the top course broken. */
	private static String damage(net.minecraft.server.MinecraftServer server) {
		ServerLevel level = server.overworld();
		var village = VillageClaims.current(server).orElse(null);
		if (village == null) return "no village";
		List<DefenceCell> stone = new ArrayList<>();
		for (DefenceCell cell : DefenceIndex.of(village.defences()).cells()) {
			if (cell.kind() == DefenceKind.STONE) stone.add(cell);
		}
		if (stone.isEmpty()) return "no stone cells";
		int minY = stone.stream().mapToInt(c -> c.pos().y()).min().orElse(0);
		int maxY = stone.stream().mapToInt(c -> c.pos().y()).max().orElse(0);
		List<DefenceCell> bottom = stone.stream().filter(c -> c.pos().y() == minY)
				.sorted(Comparator.comparingInt((DefenceCell c) -> c.pos().x()).thenComparingInt(c -> c.pos().z())).toList();
		List<DefenceCell> top = stone.stream().filter(c -> c.pos().y() == maxY)
				.sorted(Comparator.comparingInt((DefenceCell c) -> c.pos().x()).thenComparingInt(c -> c.pos().z())).toList();
		if (bottom.size() < 8 || top.isEmpty()) return "courses bottom " + bottom.size() + " top " + top.size();
		int step = Math.max(1, (bottom.size() - 2) / 3);
		DefenceCell[] cracks = new DefenceCell[4];
		for (int i = 0; i < 4; i++) cracks[i] = bottom.get(Math.min(bottom.size() - 1, 1 + i * step));
		DefenceCell hole = top.get(top.size() / 2);
		int[] hits = {3, 6, 9, 11};
		for (int i = 0; i < 4; i++) {
			BlockPos pos = new BlockPos(cracks[i].pos().x(), cracks[i].pos().y(), cracks[i].pos().z());
			for (int n = 0; n < hits[i]; n++) {
				SiegeDamage.Result result = SiegeDamage.hit(server, level, pos, SiegeHits.Attacker.ZOMBIE);
				if (result != SiegeDamage.Result.APPLIED) return "hit " + hits[i] + " on " + pos.toShortString() + " was " + result;
			}
		}
		BlockPos holePos = new BlockPos(hole.pos().x(), hole.pos().y(), hole.pos().z());
		SiegeDamage.Result last = SiegeDamage.Result.APPLIED;
		for (int n = 0; n < 12 && last != SiegeDamage.Result.BREACHED; n++) {
			last = SiegeDamage.hit(server, level, holePos, SiegeHits.Attacker.ZOMBIE);
		}
		if (last != SiegeDamage.Result.BREACHED) return "the top cell did not break: " + last + " at " + holePos.toShortString();
		var damage = SiegeDamage.damage(server);
		if (damage.breached().size() != 1 || damage.cracked().size() != 4) {
			return "damage is " + damage.breached().size() + " broken, " + damage.cracked().size() + " cracked";
		}
		return "ok";
	}
}
