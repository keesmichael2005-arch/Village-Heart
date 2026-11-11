package dev.villageheart.gametest;

import dev.villageheart.core.Tier;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.worker.ModProfessions;
import dev.villageheart.worker.Workers;
import dev.villageheart.worker.cook.KitchenVisuals;
import dev.villageheart.worker.smith.GolemBuilds;
import dev.villageheart.world.VillageClaims;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

/** Layer 4: the Smithy with a lit furnace, a finished golem and a cracked one (M19). Called from VillageHeartClientTests. */
final class SmithClientTests {
	private SmithClientTests() {}

	/**
	 * A Town Smithy. The blacksmith finishes a golem build that was saved at 7990 ticks, so the new golem is the one the build spawns at
	 * the wait spot. A second golem at 20 health stands beside it (crackiness HIGH; 30 of 100 is MEDIUM in this version). Time and weather stay
	 * frozen and both golems have no AI.
	 */
	static void smithyGolem(ClientGameTestContext context) {
		dev.villageheart.needs.NeedsOverride.wagesEnabled = false;
		UUID smithId = new UUID(0, 61);
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
			srv.runCommand("fill -14 " + (y - 1) + " -6 20 " + (y - 1) + " 24 minecraft:grass_block");
			srv.runCommand("fill -14 " + y + " -6 20 " + (y + 8) + " 24 minecraft:air");
			srv.runCommand("setblock 0 " + y + " 0 minecraft:bell");
			srv.runCommand("setblock -2 " + y + " 0 villageheart:village_heart");
			context.waitTicks(10);
			final int fy = y;
			String claim = srv.computeOnServer(server -> {
				String named = VillageClaims.claim(server.overworld(), new BlockPos(-2, fy, 0), server.getPlayerList().getPlayers().get(0)).name();
				if (named.equals("CLAIMED")) VillageClaims.data(server).update(v -> v.withTier(Tier.TOWN));
				return named;
			});
			if (!claim.equals("CLAIMED")) throw new AssertionError("the claim failed: " + claim);
			HeartScreenClientTests.room(srv, -3, y, 4, 4, "smithy_marker");
			srv.runCommand("setblock -2 " + y + " 7 minecraft:blast_furnace[facing=south]");
			srv.runCommand("setblock 0 " + y + " 7 minecraft:anvil");
			srv.runCommand("setblock 1 " + y + " 9 minecraft:chest");
			srv.runCommand("setblock -2 " + y + " 9 minecraft:torch");
			BlockPos marker = new BlockPos(-1, y + 2, 12);
			String state = "";
			for (int i = 0; i < 300 && !state.equals("VALID"); i++) {
				state = srv.computeOnServer(server -> HeartScreenClientTests.marker(server.overworld(), marker));
				context.waitTicks(1);
			}
			if (!state.equals("VALID")) throw new AssertionError("the Smithy plaque is " + state);
			srv.runCommand("summon minecraft:villager -1 " + y + " 8 {PersistenceRequired:1b,UUID:" + HeartScreenClientTests.uuid(smithId) + "}");
			String hired = srv.computeOnServer(server -> {
				String result = Workers.hire(server.overworld(), server.overworld().getEntity(smithId), JobType.BLACKSMITH, Optional.empty()).toString();
				var smithy = VillageClaims.locate(server.overworld(), marker);
				GolemBuilds.start(server, smithy);
				GolemBuilds.progress(server, smithy, 7990);
				return result;
			});
			if (!hired.startsWith("Hired")) throw new AssertionError("the smith was not hired: " + hired);
			boolean appeared = false;
			for (int i = 0; i < 400 && !appeared; i++) {
				context.waitTicks(1);
				appeared = srv.computeOnServer(server -> {
					for (var e : server.overworld().getAllEntities()) if (e instanceof IronGolem g && g.isAlive()) return true;
					return false;
				});
			}
			if (!appeared) throw new AssertionError("the build did not finish a golem");
			String posed = srv.computeOnServer(server -> {
				IronGolem built = null;
				for (var e : server.overworld().getAllEntities()) if (e instanceof IronGolem g && g.isAlive()) built = g;
				if (built == null) return "the finished golem is gone";
				built.setNoAi(true);
				built.setYRot(0.0F);
				built.setYBodyRot(0.0F);
				built.setYHeadRot(0.0F);
				IronGolem cracked = EntityType.IRON_GOLEM.create(server.overworld(), EntitySpawnReason.MOB_SUMMONED);
				if (cracked == null) return "the cracked golem was not created";
				cracked.setPos(built.getX() + 2.2, built.getY(), built.getZ());
				cracked.setPlayerCreated(true);
				cracked.setYRot(0.0F);
				cracked.setYBodyRot(0.0F);
				cracked.setYHeadRot(0.0F);
				server.overworld().addFreshEntity(cracked);
				cracked.setHealth(20.0F);
				cracked.setNoAi(true);
				if (!"HIGH".equals(cracked.getCrackiness().name()))
					return "crackiness " + cracked.getCrackiness() + " at health " + cracked.getHealth();
				Villager smith = (Villager) server.overworld().getEntity(smithId);
				if (smith == null) return "the smith is gone";
				smith.teleportTo(-1.5, fy, 8.5);
				smith.setYRot(0.0F);
				smith.setYBodyRot(0.0F);
				smith.setYHeadRot(0.0F);
				smith.setNoAi(true);
				// The build puts the furnace out when the golem appears. The shot wants the glow the smith was working by.
				KitchenVisuals.light(server.overworld(), new BlockPos(-2, fy, 7), true);
				return "ok";
			});
			if (!posed.equals("ok")) throw new AssertionError(posed);
			int smithEid = srv.computeOnServer(server -> {
				var e = server.overworld().getEntity(smithId);
				return e == null ? -1 : e.getId();
			});
			if (smithEid < 0) throw new AssertionError("the smith is gone");
			// The south wall comes out after the build, the way the guard shot opens its wall: a one-block door hides the furnace.
			srv.runCommand("fill -3 " + y + " 11 2 " + (y + 2) + " 11 minecraft:air");
			boolean hideGui = context.computeOnClient(mc -> mc.options.hideGui);
			int fov = context.computeOnClient(mc -> mc.options.fov().get());
			context.runOnClient(mc -> { mc.options.hideGui = true; mc.options.fov().set(70); });
			srv.runCommand("tp @a -0.5 " + (y + 1) + " 20 facing -1.5 " + (y + 1.0) + " 7.5");
			context.waitTicks(20);
			sp.getClientWorld().waitForChunksRender();
			String problem = "not checked";
			for (int i = 0; i < 40 && problem != null; i++) {
				context.waitTicks(2);
				problem = context.computeOnClient(mc -> {
				var st = mc.level.getBlockState(new BlockPos(-2, fy, 7));
				if (!st.is(Blocks.BLAST_FURNACE)) return "no blast furnace: " + st;
				if (!st.getValue(AbstractFurnaceBlock.LIT)) return "the blast furnace is not lit on the client";
				List<IronGolem> golems = new ArrayList<>();
				for (var e : mc.level.entitiesForRendering()) if (e instanceof IronGolem g) golems.add(g);
				if (!(mc.level.getEntity(smithEid) instanceof Villager found)) return "no smith on the client";
				String got = found.getVillagerData().profession().unwrapKey().orElseThrow().identifier().toString();
				String want = ModProfessions.key(JobType.BLACKSMITH).identifier().toString();
				if (!got.equals(want)) return "profession on the client is " + got;
				if (golems.size() < 2) return "golems on the client: " + golems.size();
				boolean cracked = false;
				for (IronGolem g : golems) if ("HIGH".equals(g.getCrackiness().name())) cracked = true;
				if (!cracked) return "no cracked golem on the client";
				return null;
			});
			}
			if (problem != null) {
				String serverJob = srv.computeOnServer(server -> {
					var e = server.overworld().getEntity(smithId);
					if (!(e instanceof Villager v)) return "no smith";
					return v.getVillagerData().profession().unwrapKey().map(k -> k.identifier().toString()).orElse("none");
				});
				throw new AssertionError("smithy scene: " + problem + "; server profession " + serverJob);
			}
			context.takeScreenshot("smithy_golem");
			context.runOnClient(mc -> { mc.options.hideGui = hideGui; mc.options.fov().set(fov); });
			srv.runCommand("gamerule advance_time true");
			srv.runCommand("kill @e[type=minecraft:villager]");
			srv.runCommand("kill @e[type=minecraft:iron_golem]");
		}
	}
}
