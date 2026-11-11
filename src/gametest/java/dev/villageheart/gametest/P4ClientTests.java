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
	private static BlockPos storageChest;

	static void shots(ClientGameTestContext context) {
		FAILURES.clear();
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getClientWorld().waitForChunksRender();
			var srv = sp.getServer();
			int y = srv.computeOnServer(server -> server.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0));
			srv.runCommand("time set noon");
			srv.runCommand("weather clear");
			srv.runCommand("gamemode creative @a");
			srv.runCommand("recipe give @a *");
			srv.runCommand("effect give @a minecraft:night_vision infinite 0 true");
			context.waitTicks(80);
			srv.runCommand("setblock 0 " + y + " 0 minecraft:bell");
			srv.runCommand("setblock -2 " + y + " 0 villageheart:village_heart");
			context.waitTicks(10);
			final int fy = y;
			String claim = srv.computeOnServer(server -> VillageClaims.claim(server.overworld(), new BlockPos(-2, fy, 0), server.getPlayerList().getPlayers().get(0)).name());
			if (!claim.equals("CLAIMED")) throw new AssertionError("claim: " + claim);
			srv.computeOnServer(server -> { VillageClaims.data(server).update(v -> v.withTier(Tier.TOWN)); return 0; });
			HeartScreenClientTests.room(srv, -20, y, 7, 5, "storage_marker");
			for (int row = 0; row < 3; row++) for (int i = 0; i < 6; i++) {
				srv.runCommand("setblock " + (-18 + i) + " " + y + " " + (8 + row) + " minecraft:chest");
			}
			BlockPos storageMarker = new BlockPos(-16, y + 2, 13);
			String storage = "";
			for (int i = 0; i < 200 && !"VALID".equals(storage); i++) {
				storage = srv.computeOnServer(server -> {
					var s = server.overworld().getBlockState(storageMarker);
					return s.getBlock() instanceof dev.villageheart.block.MarkerBlock
							? s.getValue(dev.villageheart.block.MarkerBlock.STATUS).name() : "none";
				});
				context.waitTicks(1);
			}
			if (!"VALID".equals(storage)) throw new AssertionError("storage did not go green: " + storage + " at " + storageMarker);
			storageChest = new BlockPos(-18, y, 8);
			String stocked = srv.computeOnServer(server -> {
				stockFor(server.overworld(), storageChest, Blueprints.byId("kitchen").orElseThrow(), 64);
				return "cobble=" + Stockpiles.count(server, Items.COBBLESTONE)
						+ " planks=" + Stockpiles.count(server, Items.OAK_PLANKS)
						+ " fence=" + Stockpiles.count(server, Items.OAK_FENCE)
						+ " dirt=" + Stockpiles.count(server, Items.DIRT);
			});
			if (!stocked.startsWith("cobble=") || stocked.startsWith("cobble=0")) {
				throw new AssertionError("Storage did not take cobblestone: " + stocked);
			}
			List<String> ids = new ArrayList<>();
			for (Blueprint bp : Blueprints.all()) ids.add(bp.id());
			int n = 0;
			for (String id : ids) {
				int x = -60 + (n % 6) * 40;
				int z = 30 + (n / 6) * 40;
				if (id.equals("watchtower")) shootWatchtower(context, sp, srv, x, y, z);
				else shoot(context, srv, id, x, y, z, "built_" + id, true);
				if (id.equals("kitchen")) slopeShot(context, srv, 80, y, 200);
				n++;
			}
			quill(context, srv, y);
			ghost(context, sp, srv, y);
			if (!FAILURES.isEmpty()) throw new AssertionError(String.join("; ", FAILURES));
		}
	}

	private static void slopeShot(ClientGameTestContext context, TestServerContext srv, int x, int y, int z) {
		pad(context, srv, x, y, z);
		// south of the anchor is three blocks lower, so the dirt courses show under the cobble in daylight
		srv.runCommand("fill " + (x - 1) + " " + (y - 4) + " " + z + " " + (x + 4) + " " + (y - 1) + " " + (z + 4) + " minecraft:air");
		srv.runCommand("fill " + (x - 1) + " " + (y - 5) + " " + z + " " + (x + 4) + " " + (y - 5) + " " + (z + 4) + " minecraft:stone");
		shoot(context, srv, "kitchen", x, y, z, "slope_foundation", false);
		look(context, srv, x + 9, y + 3, z + 9, x + 2, y - 2, z + 2);
		context.waitTicks(5);
		picture(context, "slope_foundation_side");
	}

	/** A solid pad. Teleport first so the chunk loads, then stand clear of the footprint. */
	private static void pad(ClientGameTestContext context, TestServerContext srv, int x, int y, int z) {
		srv.runCommand("tp @a " + x + " " + (y + 4) + " " + z);
		context.waitTicks(30);
		srv.computeOnServer(server -> {
			var level = server.overworld();
			level.getChunk(x >> 4, z >> 4);
			var stone = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
			var dirt = net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState();
			var grass = net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState();
			var air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
			for (int dx = -16; dx <= 16; dx++) for (int dz = -16; dz <= 16; dz++) {
				level.getChunk((x + dx) >> 4, (z + dz) >> 4);
				BlockPos column = new BlockPos(x + dx, y, z + dz);
				level.setBlock(column.below(3), stone, 3);
				level.setBlock(column.below(2), dirt, 3);
				level.setBlock(column.below(1), grass, 3);
				for (int dy = 0; dy <= 12; dy++) level.setBlock(column.above(dy), air, 3);
			}
			return 0;
		});
		srv.runCommand("tp @a " + (x + 24) + " " + (y + 4) + " " + (z + 24));
		context.waitTicks(5);
	}

	private static void shootWatchtower(ClientGameTestContext context, TestSingleplayerContext sp, TestServerContext srv, int x, int y, int z) {
		pad(context, srv, x, y, z);
		String id = srv.computeOnServer(server -> {
			var bp = resolve("watchtower");
			stockFor(server.overworld(), storageChest, bp, 32);
			var job = BuildJobs.start(server.overworld(), bp, new BlockPos(x, y - 1, z), Rotation4.NONE, server.getPlayerList().getPlayers().get(0).getUUID());
			SiteGround.prepare(server.overworld(), bp, job);
			BuildJobs.advance(server, job.id(), 40);
			BuildSystem.showGhost(server);
			return job.id().toString();
		});
		context.waitTicks(15);
		look(context, srv, x + 18, y + 8, z + 18, x + 2, y + 3, z + 2);
		sp.getClientWorld().waitForChunksRender();
		picture(context, "scaffold_mid");
		String result = srv.computeOnServer(server -> BuildJobs.advance(server, java.util.UUID.fromString(id), Integer.MAX_VALUE).toString());
		if (!result.contains("finished=true")) {
			FAILURES.add("watchtower " + result);
			return;
		}
		context.waitTicks(10);
		look(context, srv, x + 16, y + 8, z + 18, x + 4, y + 2, z + 4);
		picture(context, "built_watchtower");
	}

	private static void ghost(ClientGameTestContext context, TestSingleplayerContext sp, TestServerContext srv, int y) {
		pad(context, srv, 160, y, 20);
		srv.computeOnServer(server -> {
			var player = server.getPlayerList().getPlayers().get(0);
			var bp = Blueprints.byId("kitchen").orElseThrow();
			stockFor(server.overworld(), storageChest, bp, 16);
			var job = BuildJobs.start(server.overworld(), bp, new BlockPos(160, y - 1, 20), Rotation4.NONE, player.getUUID());
			BuildJobs.advance(server, job.id(), 40);
			BuildSystem.showGhost(server);
			ItemStack stack = BlueprintItem.of(bp);
			player.setItemInHand(InteractionHand.MAIN_HAND, stack);
			BlueprintUse.click(player, server.overworld(), player.getMainHandItem(), new BlockPos(168, y - 1, 28), false);
			return 0;
		});
		context.waitTicks(20);
		look(context, srv, 168, y + 6, 28, 160, y + 2, 23);
		sp.getClientWorld().waitForChunksRender();
		picture(context, "ghost_half_kitchen");
		look(context, srv, 176, y + 4, 36, 168, y + 1, 28);
		context.waitTicks(10);
		picture(context, "ghost_preview");
	}

	private static void quill(ClientGameTestContext context, TestServerContext srv, int y) {
		pad(context, srv, 160, y, 80);
		String saved = srv.computeOnServer(server -> {
			var player = server.getPlayerList().getPlayers().get(0);
			var bp = Blueprints.SMALL_HOUSE;
			stockFor(server.overworld(), storageChest, bp, 32);
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
		shoot(context, srv, saved, 180, y, 80, "quill_saved", true);
	}

	private static void shoot(ClientGameTestContext context, TestServerContext srv, String blueprint, int x, int y, int z, String shot, boolean prepareGround) {
		if (prepareGround) pad(context, srv, x, y, z);
		else {
			srv.runCommand("tp @a " + (x + 30) + " " + (y + 4) + " " + (z + 30));
			context.waitTicks(5);
		}
		String result = srv.computeOnServer(server -> {
			var bp = resolve(blueprint);
			if (bp == null) return "missing";
			stockFor(server.overworld(), storageChest, bp, shot.contains("slope") ? 200 : 32);
			var job = BuildJobs.start(server.overworld(), bp, new BlockPos(x, y - 1, z), Rotation4.NONE, params(blueprint),
					server.getPlayerList().getPlayers().get(0).getUUID());
			var adv = BuildJobs.advance(server, job.id(), Integer.MAX_VALUE);
			return adv + " cobble=" + Stockpiles.count(server, Items.COBBLESTONE) + " dirt=" + Stockpiles.count(server, Items.DIRT);
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
		boolean small = blueprint.equals("training_yard") || blueprint.equals("lamp_post") || blueprint.equals("farm_plot");
		if (small) look(context, srv, x + 7, y + 3, z + 9, x + 1, y + 1, z + 2);
		else look(context, srv, x + 12, y + 6, z + 14, x + 3, y + 2, z + 3);
		context.waitTicks(8);
		picture(context, shot);
	}

	private static Blueprint resolve(String id) {
		return Blueprints.resolve(id, params(id)).orElse(null);
	}

	private static BlueprintParams params(String id) {
		return id.equals("wall_segment") || id.equals("gate") ? new BlueprintParams(6) : BlueprintParams.NONE;
	}

	/** Tops the Storage chests up to this blueprint's cost. Writes the slots directly, the same way the GameTests do. */
	private static void stockFor(net.minecraft.server.level.ServerLevel level, BlockPos chest, Blueprint bp, int extraDirt) {
		var server = level.getServer();
		for (var line : bp.materials().lines()) {
			Item item = itemFor(line.material());
			if (item == null) continue;
			int left = line.count() + 4 - (int) Math.min(Integer.MAX_VALUE, Stockpiles.count(server, item));
			put(level, chest, item, left);
		}
		int dirt = extraDirt - (int) Math.min(Integer.MAX_VALUE, Stockpiles.count(server, Items.DIRT));
		put(level, chest, Items.DIRT, dirt);
	}

	private static void put(net.minecraft.server.level.ServerLevel level, BlockPos origin, Item item, int count) {
		if (count <= 0) return;
		java.util.List<net.minecraft.world.Container> chests = new java.util.ArrayList<>();
		for (var view : dev.villageheart.building.Buildings.valid(level.getServer(), dev.villageheart.core.building.BuildingType.STORAGE)) {
			var scan = view.latestScan().orElse(null);
			if (scan == null) continue;
			for (var p : scan.positions(dev.villageheart.core.building.BuildingRequirements.CHEST)) {
				var c = dev.villageheart.stock.StorageContainers.at(level, new BlockPos(p.x(), p.y(), p.z()));
				if (c != null) chests.add(c);
			}
		}
		if (chests.isEmpty() && level.getBlockEntity(origin) instanceof net.minecraft.world.Container c) chests.add(c);
		int left = count;
		for (var c : chests) {
			for (int i = 0; i < c.getContainerSize() && left > 0; i++) {
				ItemStack slot = c.getItem(i);
				if (slot.isEmpty()) {
					int n = Math.min(item.getDefaultMaxStackSize(), left);
					c.setItem(i, new ItemStack(item, n));
					left -= n;
				} else if (slot.is(item) && slot.getCount() < slot.getMaxStackSize()) {
					int n = Math.min(slot.getMaxStackSize() - slot.getCount(), left);
					slot.grow(n);
					left -= n;
				}
			}
			c.setChanged();
		}
		if (left > 0 && !chests.isEmpty()) {
			var c = chests.get(0);
			for (int i = c.getContainerSize() - 1; i >= 0 && left > 0; i--) {
				int n = Math.min(item.getDefaultMaxStackSize(), left);
				c.setItem(i, new ItemStack(item, n));
				left -= n;
			}
			c.setChanged();
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

	/** Clears the recipe toast on the client thread, then takes the shot. Local to this test, not a mixin. */
	private static void picture(ClientGameTestContext context, String name) {
		context.computeOnClient(mc -> {
			mc.getToastManager().clear();
			return 0;
		});
		context.waitTicks(2);
		context.takeScreenshot(name);
	}

	private static void look(ClientGameTestContext context, TestServerContext srv, double px, double py, double pz, double tx, double ty, double tz) {
		double dx = tx - px, dz = tz - pz, horiz = Math.sqrt(dx * dx + dz * dz);
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) -Math.toDegrees(Math.atan2(ty - (py + 1.62), horiz));
		srv.runCommand("tp @a " + px + " " + py + " " + pz + " " + yaw + " " + pitch);
		context.waitTicks(12);
	}
}
