package dev.villageheart.gametest;

import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.core.worker.WorkerData;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.worker.ModProfessions;
import dev.villageheart.worker.WorkerSystem;
import dev.villageheart.worker.Workers;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

/** Layer 4 test for the lumberjack (M9). Called from VillageHeartClientTests. */
final class LumberClientTests {
	private LumberClientTests() {}

	private record Scene(int villagerId, BlockPos aboveBase, BlockPos stump, int ground) {}

	/**
	 * T-L4-1: a lumberjack at a half-felled oak. The scene is staged on the server (an oak grown from the real configured feature, its top
	 * two logs gone, the crack overlay on the next log, a sapling on a stump, the drops lying around) and the villager is frozen so it stays
	 * at the trunk; the real felling is covered by the GameTests. Before the shot the client is asked for the villager's profession and the
	 * block above the base, so a worker that rendered as a plain villager or a tree that was felled whole cannot pass.
	 */
	static void lumberjackFelling(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getClientWorld().waitForChunksRender();
			var srv = sp.getServer();
			int y = srv.computeOnServer(server -> server.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0));
			srv.runCommand("time set noon");
			srv.runCommand("weather clear");
			srv.runCommand("gamemode creative @a");
			srv.runCommand("effect give @a minecraft:night_vision infinite 0 true");
			srv.runCommand("gamerule advance_time false");
			srv.runCommand("fill -14 " + (y - 1) + " -6 14 " + (y - 1) + " 16 minecraft:grass_block");
			srv.runCommand("fill -14 " + y + " -6 14 " + (y + 14) + " 16 minecraft:air");
			final int fy = y;
			Scene scene = srv.computeOnServer(server -> {
				var level = server.overworld();
				BlockPos base = new BlockPos(0, fy, 7);
				var feature = level.registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE).getOrThrow(TreeFeatures.OAK).value();
				if (!feature.place(level, level.getChunkSource().getGenerator(), RandomSource.create(7L), base)) throw new AssertionError("the oak was not placed");
				// the trunk: highest logs first, two felled
				int top = base.getY();
				while (level.getBlockState(new BlockPos(0, top + 1, 7)).is(Blocks.OAK_LOG)) top++;
				int felled = 3;
				for (int i = 0; i < felled; i++) level.setBlock(new BlockPos(0, top - i, 7), Blocks.AIR.defaultBlockState(), 3);
				// the leaves around the felled part are cleared too, so the cut is visible in the picture (staged, not what the worker does)
				for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = 0; dy <= 3; dy++) {
					BlockPos lp = new BlockPos(dx, top - felled + 1 + dy, 7 + dz);
					if (level.getBlockState(lp).is(net.minecraft.tags.BlockTags.LEAVES)) level.setBlock(lp, Blocks.AIR.defaultBlockState(), 3);
				}
				BlockPos next = new BlockPos(0, top - felled, 7);
				if (!level.getBlockState(next).is(Blocks.OAK_LOG)) throw new AssertionError("the trunk is too short for the scene");
				// a stump with a sapling, and the drops
				BlockPos stump = new BlockPos(1, fy, 4); // left of the trunk in the picture, between the camera and the worker
				level.setBlock(stump, Blocks.OAK_SAPLING.defaultBlockState(), 3);
				level.addFreshEntity(new ItemEntity(level, -2.5, fy + 0.3, 4.5, new ItemStack(Items.OAK_LOG, 2)));
				level.addFreshEntity(new ItemEntity(level, -3.2, fy + 0.3, 5.8, new ItemStack(Items.STICK, 3)));
				level.addFreshEntity(new ItemEntity(level, 3.5, fy + 0.3, 4.2, new ItemStack(Items.OAK_SAPLING, 1)));
				// the worker, frozen next to the trunk, facing it
				HeartLocation block = VillageClaims.locate(level, base);
				Villager v = EntityType.VILLAGER.create(level, EntitySpawnReason.COMMAND);
				v.snapTo(-1.1, fy, 6.6, -90.0F, 0.0F);
				v.setYHeadRot(-90.0F);
				v.setYBodyRot(-90.0F);
				v.setPersistenceRequired();
				level.addFreshEntity(v);
				WorkerSystem.exemptFromOrphanSweepForTest(v.getUUID());
				Workers.install(level, v, WorkerData.hired(JobType.LUMBERJACK, block, block));
				v.setNoAi(true);
				level.destroyBlockProgress(v.getId(), next, 5);
				return new Scene(v.getId(), next.above(), stump, fy);
			});
			srv.runCommand("tp @a 2.2 " + (y + 1) + " 2.4 facing -0.2 " + (y + 1.8) + " 7.0");
			boolean hideGui = context.computeOnClient(mc -> mc.options.hideGui);
			int fov = context.computeOnClient(mc -> mc.options.fov().get());
			context.runOnClient(mc -> { mc.options.hideGui = true; mc.options.fov().set(70); });
			context.waitTicks(30);
			sp.getClientWorld().waitForChunksRender();

			String problem = context.computeOnClient(mc -> {
				var e = mc.level.getEntity(scene.villagerId());
				if (!(e instanceof Villager v)) return "the lumberjack is " + e + " on the client";
				String got = v.getVillagerData().profession().unwrapKey().orElseThrow().identifier().toString();
				if (!got.equals(ModProfessions.key(JobType.LUMBERJACK).identifier().toString())) return "profession on the client is " + got;
				if (!mc.level.getBlockState(new BlockPos(0, scene.aboveBase().getY() - 1, 7)).is(Blocks.OAK_LOG)) return "the log below the crack is not an oak log";
				if (!mc.level.getBlockState(scene.aboveBase()).isAir()) return "the log above the cracked one is still there";
				if (!mc.level.getBlockState(scene.stump()).is(Blocks.OAK_SAPLING)) return "no sapling on the stump";
				return null;
			});
			if (problem != null) throw new AssertionError("lumberjack scene: " + problem);

			context.takeScreenshot("lumberjack_felling");
			context.runOnClient(mc -> { mc.options.hideGui = hideGui; mc.options.fov().set(fov); });
			srv.runCommand("gamerule advance_time true");
			srv.runCommand("kill @e[type=minecraft:villager]");
		}
		WorkerSystem.clearOrphanExemptionsForTest();
	}

	/**
	 * T-L4-2: the Villagers tab of the Heart screen with a lumberjack whose camp chest holds no axe: its row reads "Needs axe". A real claim,
	 * a real Lumber Camp (Chopping Block and chest, no axe), a real hire, the screen opened by a real right-click; the row is checked in the
	 * snapshot the client holds, then the shot is taken.
	 */
	static void villagersNeedsAxe(ClientGameTestContext context) {
		java.util.UUID worker = new java.util.UUID(0, 11);
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
			srv.runCommand("setblock 0 " + y + " 0 minecraft:bell");
			srv.runCommand("setblock -2 " + y + " 0 villageheart:village_heart");
			context.waitTicks(10);
			final int fy = y;
			String claim = srv.computeOnServer(server -> dev.villageheart.world.VillageClaims.claim(server.overworld(),
					new BlockPos(-2, fy, 0), server.getPlayerList().getPlayers().get(0)).name());
			if (!claim.equals("CLAIMED")) throw new AssertionError("the claim failed: " + claim);
			HeartScreenClientTests.room(srv, -3, y, 5, 4, "lumber_camp_marker");
			srv.runCommand("setblock -2 " + y + " 7 villageheart:chopping_block[facing=south]");
			srv.runCommand("setblock 2 " + y + " 10 minecraft:chest");
			srv.runCommand("setblock 0 " + y + " 10 minecraft:torch");
			// a villager with AI: the status comes from the job running in its brain
			srv.runCommand("summon minecraft:villager 0 " + y + " 8 {PersistenceRequired:1b,UUID:" + HeartScreenClientTests.uuid(worker) + ",CustomName:\"Tom\"}");
			BlockPos marker = new BlockPos(0, y + 2, 12);
			String state = "";
			for (int i = 0; i < 200 && !state.equals("VALID"); i++) {
				state = srv.computeOnServer(server -> HeartScreenClientTests.marker(server.overworld(), marker));
				context.waitTicks(1);
			}
			if (!state.equals("VALID")) throw new AssertionError("the Lumber Camp plaque is " + state);
			String hired = srv.computeOnServer(server -> dev.villageheart.worker.Workers.hire(server.overworld(),
					server.overworld().getEntity(worker), JobType.LUMBERJACK, java.util.Optional.empty()).toString());
			if (!hired.startsWith("Hired")) throw new AssertionError("the hire failed: " + hired);
			// M19a: this shot is about the old "Needs axe" words; with nothing to make an axe from the row now reads differently (SelfCraftClientTests shows that)
			dev.villageheart.worker.gear.SelfCraftSwitch.off();
			double eyeY = y + 1.62, targetY = y + 0.5, pz = 3.5;
			float pitch = (float) -Math.toDegrees(Math.atan2(targetY - eyeY, pz - 0.5));
			srv.runCommand("tp @a -1.5 " + y + " " + pz + " 180 " + pitch);
			context.waitTicks(10);
			sp.getClientWorld().waitForChunksRender();
			context.getInput().pressMouse(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT);
			context.waitForScreen(dev.villageheart.client.heart.HeartScreen.class);
			HeartScreenClientTests.openTab(context, 1);
			// the worker's first plan sets the status within a second; the client gets the snapshot on its next refresh
			dev.villageheart.core.heart.VillagerRow row = null;
			for (int i = 0; i < 200 && (row == null || row.activity() != dev.villageheart.core.heart.ActivityKind.NEEDS_TOOL); i++) {
				context.waitTicks(1);
				row = HeartScreenClientTests.snapshot(context).villagers().stream().filter(r -> r.id().equals(worker)).findFirst().orElse(null);
			}
			if (row == null || row.activity() != dev.villageheart.core.heart.ActivityKind.NEEDS_TOOL) throw new AssertionError("the row is " + row);
			String text = dev.villageheart.core.heart.HeartTexts.EN.get(dev.villageheart.core.heart.HeartTexts.activityKey(row.activity(), row.jobId()));
			if (!"Needs axe".equals(text)) throw new AssertionError("the row reads " + text);
			context.takeScreenshot("villagers_tab_needs_axe");
			HeartScreenClientTests.assertClean(context, "villagers tab with a status");
			dev.villageheart.worker.gear.SelfCraftSwitch.reset();
			srv.runCommand("gamerule advance_time true");
			srv.runCommand("kill @e[type=minecraft:villager]");
		}
	}
}
