package dev.villageheart.gametest;

import dev.villageheart.block.MarkerBlocks;
import dev.villageheart.build.BlueprintItem;
import dev.villageheart.build.BlueprintQuill;
import dev.villageheart.build.BlueprintUse;
import dev.villageheart.build.BuildJobs;
import dev.villageheart.build.BuildSystem;
import dev.villageheart.build.SiteGround;
import dev.villageheart.core.Tier;
import dev.villageheart.core.blueprint.Blueprint;
import dev.villageheart.core.blueprint.BlueprintParams;
import dev.villageheart.core.blueprint.Blueprints;
import dev.villageheart.core.blueprint.Material;
import dev.villageheart.core.blueprint.PlacementMode;
import dev.villageheart.core.blueprint.Rotation4;
import dev.villageheart.core.room.Pos;
import dev.villageheart.stock.Stockpiles;
import dev.villageheart.world.VillageClaims;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;

/** Screenshots of every authored building, vanilla houses, wall pieces, a quill save, the ghost, scaffold and a slope. */
final class P4ClientTests {
	private P4ClientTests() {}

	private static final List<String> FAILURES = new ArrayList<>();

	static void shots(ClientGameTestContext context) {
		FAILURES.clear();
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getClientWorld().waitForChunksRender();
			var srv = sp.getServer();
			int y = srv.computeOnServer(server -> server.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0));
			srv.runCommand("time set noon");
			srv.runCommand("weather clear");
			srv.runCommand("gamemode creative @a");
			// a fill command refuses more than 32768 blocks, so the pad is laid in strips
			for (int x = -80; x < 240; x += 8) {
				int x2 = Math.min(239, x + 7);
				srv.runCommand("fill " + x + " " + (y - 2) + " -20 " + x2 + " " + (y - 2) + " 260 minecraft:dirt");
				srv.runCommand("fill " + x + " " + (y - 1) + " -20 " + x2 + " " + (y - 1) + " 260 minecraft:grass_block");
				srv.runCommand("fill " + x + " " + y + " -20 " + x2 + " " + (y + 12) + " 260 minecraft:air");
			}
			srv.runCommand("setblock 0 " + y + " 0 minecraft:bell");
			srv.runCommand("setblock -2 " + y + " 0 villageheart:village_heart");
			context.waitTicks(10);
			final int fy = y;
			String claim = srv.computeOnServer(server -> VillageClaims.claim(server.overworld(), new BlockPos(-2, fy, 0), server.getPlayerList().getPlayers().get(0)).name());
			if (!claim.equals("CLAIMED")) throw new AssertionError("claim: " + claim);
			srv.computeOnServer(server -> { VillageClaims.data(server).update(v -> v.withTier(Tier.TOWN)); return 0; });
			HeartScreenClientTests.room(srv, -20, y, 14, 6, "storage_marker");
			for (int row = 0; row < 3; row++) for (int i = 0; i < 10; i++) {
				srv.runCommand("setblock " + (-18 + i) + " " + y + " " + (8 + row) + " minecraft:chest");
			}
			context.waitTicks(40);
			BlockPos chest = new BlockPos(-18, y, 8);
			srv.computeOnServer(server -> { stockAll(server.overworld(), chest); return 0; });
			List<String> ids = new ArrayList<>();
			for (Blueprint bp : Blueprints.all()) ids.add(bp.id());
			int n = 0;
			for (String id : ids) {
				int x = -60 + (n % 6) * 40;
				int z = 30 + (n / 6) * 40;
				if (id.equals("watchtower")) shootWatchtower(context, sp, srv, x, y - 1, z);
				else shoot(context, srv, id, x, y - 1, z, "built_" + id);
				if (id.equals("kitchen")) slopeShot(context, srv, 80, y, 200);
				n++;
			}
			if (!FAILURES.isEmpty()) throw new AssertionError(String.join("; ", FAILURES));
			quill(context, srv, y);
			ghost(context, sp, srv, y);
			if (!FAILURES.isEmpty()) throw new AssertionError(String.join("; ", FAILURES));
		}
	}

	private static void slopeShot(ClientGameTestContext context, TestServerContext srv, int x, int y, int z) {
		// the south half of the footprint is three blocks lower, so the dirt fill is visible under the cobble
		srv.runCommand("fill " + (x - 2) + " " + (y - 4) + " " + z + " " + (x + 5) + " " + (y - 1) + " " + (z + 4) + " minecraft:air");
		srv.runCommand("fill " + (x - 2) + " " + (y - 5) + " " + z + " " + (x + 5) + " " + (y - 5) + " " + (z + 4) + " minecraft:stone");
		shoot(context, srv, "kitchen", x, y - 1, z, "slope_foundation");
		look(context, srv, x + 2, y - 2, z + 8, x + 1, y - 1, z + 2);
		context.waitTicks(5);
		context.takeScreenshot("slope_foundation_side");
	}

	private static void shootWatchtower(ClientGameTestContext context, TestSingleplayerContext sp, TestServerContext srv, int x, int y, int z) {
		srv.runCommand("tp @a " + x + " " + (y + 8) + " " + z);
		context.waitTicks(15);
		String id = srv.computeOnServer(server -> {
			var bp = resolve("watchtower");
			var job = BuildJobs.start(server.overworld(), bp, new BlockPos(x, y, z), Rotation4.NONE, server.getPlayerList().getPlayers().get(0).getUUID());
			SiteGround.prepare(server.overworld(), bp, job);
			BuildJobs.advance(server, job.id(), 40);
			BuildSystem.showGhost(server);
			return job.id().toString();
		});
		context.waitTicks(15);
		look(context, srv, x + 10, y + 8, z + 12, x + 3, y + 3, z + 3);
		sp.getClientWorld().waitForChunksRender();
		context.takeScreenshot("scaffold_mid");
		String result = srv.computeOnServer(server -> BuildJobs.advance(server, java.util.UUID.fromString(id), Integer.MAX_VALUE).toString());
		if (!result.contains("finished=true")) {
			FAILURES.add("watchtower " + result);
			return;
		}
		context.waitTicks(10);
		look(context, srv, x + 16, y + 8, z + 18, x + 4, y + 2, z + 4);
		context.takeScreenshot("built_watchtower");
	}

	private static void ghost(ClientGameTestContext context, TestSingleplayerContext sp, TestServerContext srv, int y) {
		srv.runCommand("tp @a 160 " + (y + 6) + " 20");
		context.waitTicks(15);
		srv.computeOnServer(server -> {
			var player = server.getPlayerList().getPlayers().get(0);
			var bp = Blueprints.byId("kitchen").orElseThrow();
			var job = BuildJobs.start(server.overworld(), bp, new BlockPos(160, y - 1, 20), Rotation4.NONE, player.getUUID());
			BuildJobs.advance(server, job.id(), 40);
			BuildSystem.showGhost(server);
			ItemStack stack = BlueprintItem.of(bp);
			player.setItemInHand(InteractionHand.MAIN_HAND, stack);
			BlueprintUse.click(player, server.overworld(), player.getMainHandItem(), new BlockPos(160, y - 1, 40), false);
			return 0;
		});
		context.waitTicks(20);
		look(context, srv, 168, y + 6, 28, 160, y + 2, 23);
		sp.getClientWorld().waitForChunksRender();
		context.takeScreenshot("ghost_half_kitchen");
		look(context, srv, 168, y + 4, 48, 160, y + 1, 40);
		context.waitTicks(10);
		context.takeScreenshot("ghost_preview");
	}

	private static void quill(ClientGameTestContext context, TestServerContext srv, int y) {
		srv.runCommand("tp @a 160 " + (y + 6) + " 80");
		context.waitTicks(15);
		String saved = srv.computeOnServer(server -> {
			var player = server.getPlayerList().getPlayers().get(0);
			var bp = Blueprints.SMALL_HOUSE;
			var job = BuildJobs.start(server.overworld(), bp, new BlockPos(160, y - 1, 80), Rotation4.NONE, player.getUUID());
			var adv = BuildJobs.advance(server, job.id(), Integer.MAX_VALUE);
			if (!adv.finished()) return "unfinished " + adv;
			var pl = job.placement();
			Pos min = pl.min(bp), max = pl.max(bp);
			String reason = BlueprintQuill.save(server.overworld(), new BlockPos(min.x(), min.y(), min.z()), new BlockPos(max.x(), max.y(), max.z()), player);
			if (!reason.startsWith("Saved blueprint ")) return reason;
			return reason.substring("Saved blueprint ".length()).trim();
		});
		if (saved.startsWith("unfinished") || saved.contains(" ") || !saved.startsWith("saved_")) {
			throw new AssertionError("quill did not save: " + saved);
		}
		shoot(context, srv, saved, 180, y - 1, 80, "quill_saved");
	}

	private static void shoot(ClientGameTestContext context, TestServerContext srv, String blueprint, int x, int y, int z, String shot) {
		srv.runCommand("tp @a " + x + " " + (y + 6) + " " + z);
		context.waitTicks(15);
		String result = srv.computeOnServer(server -> {
			var bp = resolve(blueprint);
			if (bp == null) return "missing";
			var job = BuildJobs.start(server.overworld(), bp, new BlockPos(x, y, z), Rotation4.NONE, params(blueprint),
					server.getPlayerList().getPlayers().get(0).getUUID());
			var adv = BuildJobs.advance(server, job.id(), Integer.MAX_VALUE);
			return adv.toString();
		});
		if (blueprint.startsWith("vanilla") && !result.contains("finished=true")) {
			FAILURES.add(blueprint + " " + result);
			return;
		}
		if (result.equals("missing") || !result.contains("finished=true")) {
			FAILURES.add(blueprint + " " + result);
			return;
		}
		context.waitTicks(10);
		look(context, srv, x + 16, y + 8, z + 18, x + 4, y + 2, z + 4);
		context.waitTicks(8);
		context.takeScreenshot(shot);
	}

	private static Blueprint resolve(String id) {
		return Blueprints.resolve(id, params(id)).orElse(null);
	}

	private static BlueprintParams params(String id) {
		return id.equals("wall_segment") || id.equals("gate") ? new BlueprintParams(6) : BlueprintParams.NONE;
	}

	private static void stockAll(net.minecraft.server.level.ServerLevel level, BlockPos chest) {
		java.util.Map<Item, Integer> need = new java.util.LinkedHashMap<>();
		List<Blueprint> plans = new ArrayList<>(Blueprints.all());
		plans.add(Blueprints.SMALL_HOUSE);
		for (Blueprint bp : plans) {
			int mult = bp.id().equals("kitchen") ? 2 : 1;
			for (var line : bp.materials().lines()) {
				Item item = itemFor(line.material());
				if (item != null) need.merge(item, line.count() * mult + 8, Integer::sum);
			}
		}
		for (Item item : MarkerBlocks.ITEMS.values()) if (item != null) need.merge(item, 4, Integer::sum);
		need.merge(Items.DIRT, 400, Integer::sum);
		// dirt first: the slope and any hole under a floor must not lose out to later stacks
		for (int i = 0; i < 8; i++) Stockpiles.deposit(level, chest, new ItemStack(Items.DIRT, 64));
		for (var e : need.entrySet()) {
			int left = e.getValue();
			while (left > 0) {
				int n = Math.min(64, left);
				int stored = Stockpiles.deposit(level, chest, new ItemStack(e.getKey(), n));
				if (stored <= 0) return;
				left -= stored;
			}
		}
	}

	private static Item itemFor(Material m) {
		if (!m.isTag()) return BuiltInRegistries.ITEM.getOptional(Identifier.parse(m.id())).orElse(null);
		return switch (m.bareId()) {
			case "minecraft:planks" -> Items.OAK_PLANKS;
			case "minecraft:wooden_doors", "minecraft:doors" -> Items.OAK_DOOR;
			case "minecraft:beds" -> Items.WHITE_BED;
			case "minecraft:logs", "minecraft:oak_logs" -> Items.OAK_LOG;
			case "minecraft:wooden_stairs", "minecraft:stairs" -> Items.OAK_STAIRS;
			case "minecraft:wooden_slabs", "minecraft:slabs" -> Items.OAK_SLAB;
			case "minecraft:wooden_fences", "minecraft:fences" -> Items.OAK_FENCE;
			case "minecraft:fence_gates" -> Items.OAK_FENCE_GATE;
			case "minecraft:wool" -> Items.WHITE_WOOL;
			case "minecraft:glass_panes" -> Items.GLASS_PANE;
			case "minecraft:stone_bricks" -> Items.STONE_BRICKS;
			default -> Items.OAK_PLANKS;
		};
	}

	private static void look(ClientGameTestContext context, TestServerContext srv, double px, double py, double pz, double tx, double ty, double tz) {
		double dx = tx - px, dz = tz - pz, horiz = Math.sqrt(dx * dx + dz * dz);
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) -Math.toDegrees(Math.atan2(ty - (py + 1.62), horiz));
		srv.runCommand("tp @a " + px + " " + py + " " + pz + " " + yaw + " " + pitch);
		context.waitTicks(12);
	}
}
