package dev.villageheart.gametest;

import dev.villageheart.client.heart.HeartScreen;
import dev.villageheart.core.Balance;
import dev.villageheart.core.Tier;
import dev.villageheart.core.guard.Shift;
import dev.villageheart.core.heart.GuardShiftView;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.worker.Workers;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;

/** Layer 4 tests for guards (M18). Called from VillageHeartClientTests; both screenshots must be looked at. */
final class GuardClientTests {
	private GuardClientTests() {}

	/** A Barracks as commands build it: a room with {@code n} grindstones, beds and a chest. Its marker is at the south door. */
	private static void barracks(net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext srv, int y, int n) {
		HeartScreenClientTests.room(srv, -3, y, 5, 4, "barracks_marker");
		for (int i = 0; i < n; i++) {
			srv.runCommand("setblock " + (-2 + i) + " " + y + " 7 minecraft:grindstone[face=floor,facing=north]");
			srv.runCommand("setblock " + (-2 + i) + " " + y + " 9 minecraft:red_bed[facing=south,part=head]");
			srv.runCommand("setblock " + (-2 + i) + " " + y + " 8 minecraft:red_bed[facing=south,part=foot]");
		}
		srv.runCommand("setblock 1 " + y + " 7 minecraft:chest");
		srv.runCommand("setblock 1 " + y + " 10 minecraft:torch");
	}

	/**
	 * T-L4-1: a day guard with an iron sword at noon in front of the camera and a night guard asleep in its bed (the room's wall is open on the
	 * camera side). Before the shot the client is asked for the sword (main hand) and the sleeper's pose. Look at it: the sword is drawn.
	 */
	static void guardOnDuty(ClientGameTestContext context) {
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
				String claim = srv.computeOnServer(server -> VillageClaims.claim(server.overworld(), new BlockPos(-2, fy, 0), server.getPlayerList().getPlayers().get(0)).name());
				if (!claim.equals("CLAIMED")) throw new AssertionError("the claim failed: " + claim);
				srv.computeOnServer(server -> {
					VillageClaims.data(server).update(v -> v.withTier(Tier.VILLAGE));
					return null;
				});
				barracks(srv, y, 2);
				UUID day = new UUID(0, 41), night = new UUID(0, 42);
				srv.runCommand("summon minecraft:villager -1 " + y + " 12 {PersistenceRequired:1b,UUID:" + HeartScreenClientTests.uuid(day) + "}");
				srv.runCommand("summon minecraft:villager 1.5 " + y + " 8.5 {PersistenceRequired:1b,UUID:" + HeartScreenClientTests.uuid(night) + "}");
				BlockPos marker = new BlockPos(0, y + 2, 12);
				String state = "";
				for (int i = 0; i < 200 && !state.equals("VALID"); i++) {
					state = srv.computeOnServer(server -> HeartScreenClientTests.marker(server.overworld(), marker));
					context.waitTicks(1);
				}
				if (!state.equals("VALID")) throw new AssertionError("the Barracks plaque is " + state);
				String hired = srv.computeOnServer(server -> {
					String a = Workers.hire(server.overworld(), server.overworld().getEntity(day), JobType.GUARD, Optional.empty()).toString();
					String b = Workers.hire(server.overworld(), server.overworld().getEntity(night), JobType.GUARD, Optional.empty()).toString();
					VillageClaims.data(server).update(v -> v.withGuardPin(day, Optional.of(Shift.DAY)).withGuardPin(night, Optional.of(Shift.NIGHT)));
					Villager d = (Villager) server.overworld().getEntity(day), n = (Villager) server.overworld().getEntity(night);
					d.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
					d.teleportTo(0.5, fy, 4.6);
					d.setYRot(180.0F);
					d.setYBodyRot(180.0F);
					d.setYHeadRot(180.0F);
					d.setNoAi(true);
					return a + " " + b;
				});
				if (hired.contains("Refused")) throw new AssertionError("a hire failed: " + hired);
				// the night guard goes to bed by itself (noon, night shift) and is frozen once it lies there: a teleported sleeper renders in the wrong place
				boolean asleep = false;
				for (int i = 0; i < 400 && !asleep; i++) {
					context.waitTicks(1);
					asleep = srv.computeOnServer(sv -> ((Villager) sv.overworld().getEntity(night)).isSleeping());
				}
				if (!asleep) throw new AssertionError("the night guard did not go to sleep");
				final BlockPos bed = srv.computeOnServer(sv -> dev.villageheart.worker.guard.GuardSystem.bedOf(night).orElseThrow());
				srv.computeOnServer(sv -> {
					((Villager) sv.overworld().getEntity(night)).setNoAi(true);
					return null;
				});
				srv.runCommand("fill -2 " + y + " 6 2 " + (y + 1) + " 6 minecraft:air"); // after the hires: an opening in the north wall, the camera looks in from there
				srv.runCommand("fill -2 " + y + " 7 -1 " + y + " 7 minecraft:air"); // the grindstones would hide the sleeper from the camera
				boolean hideGui = context.computeOnClient(mc -> mc.options.hideGui);
				context.runOnClient(mc -> mc.options.hideGui = true);
				srv.runCommand("tp @a -0.5 " + y + " 1.2 0 6");
				context.waitTicks(30);
				sp.getClientWorld().waitForChunksRender();
				int dayEid = srv.computeOnServer(sv -> sv.overworld().getEntity(day).getId());
				int nightEid = srv.computeOnServer(sv -> sv.overworld().getEntity(night).getId());
				String where = srv.computeOnServer(sv -> {
					Villager n = (Villager) sv.overworld().getEntity(night);
					double d = n.position().distanceTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(bed));
					return (d <= 1.5 && n.isSleeping() ? "ok" : "bad") + " " + n.position() + " sleeping=" + n.isSleeping() + " pos=" + n.getSleepingPos() + " d=" + d;
				});
				if (!where.startsWith("ok")) throw new AssertionError("the night guard is not in its bed: " + where);
				List<String> wrong = context.computeOnClient(mc -> {
					List<String> bad = new java.util.ArrayList<>();
					if (!(mc.level.getEntity(dayEid) instanceof Villager d)
							|| !d.getMainHandItem().is(Items.IRON_SWORD)) bad.add("the day guard does not hold the iron sword on the client");
					if (!(mc.level.getEntity(nightEid) instanceof Villager n)
							|| n.getPose() != Pose.SLEEPING) bad.add("the night guard is not sleeping on the client");
					else if (n.position().distanceTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(bed)) > 1.5) bad.add("the night guard is not at its bed on the client: " + n.position());
					return bad;
				});
				if (!wrong.isEmpty()) throw new AssertionError("client side differs: " + wrong);
				context.takeScreenshot("guard_on_duty");
				context.runOnClient(mc -> mc.options.hideGui = hideGui);
				srv.runCommand("gamerule advance_time true");
				srv.runCommand("kill @e[type=minecraft:villager]");
				context.waitTicks(10);
			} finally {
				HeartScreen.recordLayout = false;
			}
		}
	}

	/**
	 * T-L4-2: the Villagers tab with a day guard (auto), a night guard (pinned) and an unemployed villager; the Shift button of the first guard
	 * is clicked once through the UI (auto -> Day: the row reads "Day shift"). The layout self-check must be clean.
	 */
	static void villagersTabGuards(ClientGameTestContext context) {
		UUID dayId = new UUID(0, 51), nightId = new UUID(0, 52), plain = new UUID(0, 53);
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
				String claim = srv.computeOnServer(server -> VillageClaims.claim(server.overworld(), new BlockPos(-2, fy, 0), server.getPlayerList().getPlayers().get(0)).name());
				if (!claim.equals("CLAIMED")) throw new AssertionError("the claim failed: " + claim);
				srv.computeOnServer(server -> {
					VillageClaims.data(server).update(v -> v.withTier(Tier.VILLAGE));
					return null;
				});
				barracks(srv, y, 2);
				srv.runCommand("summon minecraft:villager -1 " + y + " 12 {PersistenceRequired:1b,UUID:" + HeartScreenClientTests.uuid(dayId) + ",CustomName:\"Ada\"}");
				srv.runCommand("summon minecraft:villager 1 " + y + " 12 {PersistenceRequired:1b,UUID:" + HeartScreenClientTests.uuid(nightId) + ",CustomName:\"Bram\"}");
				srv.runCommand("summon minecraft:villager 3 " + y + " 12 {PersistenceRequired:1b,UUID:" + HeartScreenClientTests.uuid(plain) + ",CustomName:\"Cy\"}");
				BlockPos marker = new BlockPos(0, y + 2, 12);
				String state = "";
				for (int i = 0; i < 200 && !state.equals("VALID"); i++) {
					state = srv.computeOnServer(server -> HeartScreenClientTests.marker(server.overworld(), marker));
					context.waitTicks(1);
				}
				if (!state.equals("VALID")) throw new AssertionError("the Barracks plaque is " + state);
				// Ada is hired first (night, auto), Bram second (day, auto); pinning Bram to the night moves Ada, the newest unpinned guard of the bigger side, to the day
				String hired = srv.computeOnServer(server -> {
					String a = Workers.hire(server.overworld(), server.overworld().getEntity(dayId), JobType.GUARD, Optional.empty()).toString();
					String b = Workers.hire(server.overworld(), server.overworld().getEntity(nightId), JobType.GUARD, Optional.empty()).toString();
					VillageClaims.data(server).update(v -> v.withGuardPin(nightId, Optional.of(Shift.NIGHT)));
					return a + " " + b;
				});
				if (hired.contains("Refused")) throw new AssertionError("a hire failed: " + hired);
				double eyeY = y + 1.62, targetY = y + 0.5, pz = 3.5;
				float pitch = (float) -Math.toDegrees(Math.atan2(targetY - eyeY, pz - 0.5));
				srv.runCommand("tp @a -1.5 " + y + " " + pz + " 180 " + pitch);
				context.waitTicks(10);
				sp.getClientWorld().waitForChunksRender();
				// the Heart is at (-2, y, 0): open it with a real right-click
				context.getInput().pressMouse(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT);
				context.waitForScreen(HeartScreen.class);
				HeartScreenClientTests.openTab(context, 1);
				dev.villageheart.core.heart.VillagerRow a = null;
				for (int i = 0; i < 200 && (a == null || !a.shift().isGuard()); i++) {
					context.waitTicks(1);
					a = HeartScreenClientTests.snapshot(context).villagers().stream().filter(r -> r.id().equals(dayId)).findFirst().orElse(null);
				}
				if (a == null || a.shift() != GuardShiftView.DAY_AUTO) throw new AssertionError("Ada's row is " + a);
				int[] row = context.computeOnClient(mc -> ((HeartScreen) mc.screen).villagerRowCentre(dayId));
				HeartScreenClientTests.clickAt(context, row);
				context.waitTicks(5);
				int[] button = context.computeOnClient(mc -> ((HeartScreen) mc.screen).firstActionCentre());
				if (button == null) throw new AssertionError("no Shift button for the selected guard");
				HeartScreenClientTests.clickAt(context, button);
				for (int i = 0; i < 100 && (a == null || a.shift() != GuardShiftView.DAY_PINNED); i++) {
					context.waitTicks(1);
					a = HeartScreenClientTests.snapshot(context).villagers().stream().filter(r -> r.id().equals(dayId)).findFirst().orElse(null);
				}
				if (a == null || a.shift() != GuardShiftView.DAY_PINNED) throw new AssertionError("after one click Ada's row is " + a);
				context.waitTicks(5);
				context.takeScreenshot("heart_villagers_guards");
				HeartScreenClientTests.assertClean(context, "villagers tab with guards");
				srv.runCommand("gamerule advance_time true");
				srv.runCommand("kill @e[type=minecraft:villager]");
				context.waitTicks(20);
			} finally {
				HeartScreen.recordLayout = false;
			}
		}
	}
}
