package dev.villageheart.gametest;

import static dev.villageheart.gametest.BuildTestSupport.YARD;

import dev.villageheart.build.BlueprintQuill;
import dev.villageheart.build.DesignWorld;
import dev.villageheart.build.BuildJobs;
import dev.villageheart.build.PaletteStates;
import dev.villageheart.stock.Stockpiles;
import dev.villageheart.core.blueprint.Blueprint;
import dev.villageheart.core.blueprint.BlueprintParser;
import dev.villageheart.core.blueprint.Blueprints;
import dev.villageheart.core.blueprint.BuildJob;
import dev.villageheart.core.blueprint.PaletteEntry;
import dev.villageheart.core.blueprint.Placement;
import dev.villageheart.core.blueprint.Rotation4;
import dev.villageheart.gametest.BuildTestSupport.With;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.gametest.framework.GameTestHelper;

/** Foundation dirt, priority, and mirror of a facing. */
public class P4GameTests {
	@GameTest(maxTicks = 100)
	public void designPlotDoesNotReturnItems(GameTestHelper h) {
		var server = h.getLevel().getServer();
		for (var layer : net.minecraft.server.RegistryLayer.values()) {
			var found = server.registries().getLayer(layer).lookup(net.minecraft.core.registries.Registries.LEVEL_STEM);
			var ids = found.map(r -> r.listElementIds().map(k -> k.identifier().toString()).toList()).orElse(java.util.List.of());
			dev.villageheart.VillageHeart.LOG.info("DESIGN_LAYER {} present={} {}", layer, found.isPresent(), ids);
		}
		dev.villageheart.VillageHeart.LOG.info("DESIGN_LEVELS {}", server.levelKeys());
		if (!DesignWorld.available(server)) {
			h.assertFalse(server.levelKeys().toString().contains("villageheart:design"),
					"the datapack file did not create a level: " + server.levelKeys());
			h.succeed();
			return;
		}
		var profile = new com.mojang.authlib.GameProfile(UUID.randomUUID(), "designer");
		var player = new net.minecraft.server.level.ServerPlayer(server, h.getLevel(), profile, net.minecraft.server.level.ClientInformation.createDefault());
		player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 3));
		h.assertTrue(DesignWorld.enter(player), "the player can be sent to the design level");
		player.getInventory().setItem(1, new ItemStack(Items.EMERALD, 5));
		h.assertTrue(DesignWorld.leave(player), "the player can leave");
		h.assertValueEqual(3, player.getInventory().countItem(Items.DIAMOND), "the diamonds that went in come back");
		h.assertValueEqual(0, player.getInventory().countItem(Items.EMERALD), "an item picked up in the plot does not come back");
		h.succeed();
	}

	@GameTest(maxTicks = 100)
	public void mirrorFlipsAnEastFacingStair(GameTestHelper h) {
		Blueprint bp = BlueprintParser.parse("""
				id stair_mirror
				name Stair
				tier VILLAGE
				kind BUILDING
				builds none
				base 0
				anchor 0 0
				front NORTH
				size 1 1 1
				palette
				. any
				_ air
				S minecraft:oak_stairs[facing=east] <- minecraft:oak_stairs "stairs"
				layer 0
				S
				end
				""");
		PaletteEntry e = bp.entry(0, 0, 0);
		var state = PaletteStates.resolve(e, Rotation4.NONE, true, null);
		h.assertTrue(state.getValue(BlockStateProperties.HORIZONTAL_FACING) == net.minecraft.core.Direction.WEST, "mirrored east stair faces west: " + state);
		h.assertTrue(PaletteStates.resolve(e, Rotation4.NONE, false, null).getValue(BlockStateProperties.HORIZONTAL_FACING) == net.minecraft.core.Direction.EAST,
				"unmirrored stair still faces east");
		h.succeed();
	}

	@GameTest(environment = "villageheart-gametest:build_p4_cobble", structure = YARD, skyAccess = true, maxTicks = 800)
	public void kitchenPlacesCobbleThatIsInStorage(GameTestHelper h) {
		BuildTestSupport.layout(h, With.STORAGE_NO_HUT).thenExecute(() -> {
			Blueprint bp = Blueprints.byId("kitchen").orElseThrow();
			java.util.List<ItemStack> stacks = new java.util.ArrayList<>();
			for (var line : bp.materials().lines()) {
				Item item = PaletteStates.displayItem(line.material());
				h.assertTrue(item != null, "no item for " + line.material().id());
				stacks.add(new ItemStack(item, line.count()));
			}
			BuildTestSupport.stock(h, stacks.toArray(ItemStack[]::new));
			long cobble = Stockpiles.count(h.getLevel().getServer(), Items.COBBLESTONE);
			h.assertTrue(cobble >= 49, "Storage is holding cobblestone before the build: " + cobble);
			BlockPos ground = h.absolutePos(BuildTestSupport.GROUND);
			BuildJob job = BuildJobs.start(h.getLevel(), bp, ground, Rotation4.NONE, UUID.randomUUID());
			var adv = BuildJobs.advance(h.getLevel().getServer(), job.id(), Integer.MAX_VALUE);
			h.assertFalse(adv.stoppedBecause().contains("cobble"), "cobble is in Storage but the build stopped: " + adv);
			h.assertTrue(adv.finished(), adv.toString());
			VillageTestSupport.done(h);
		});
	}

	@GameTest(environment = "villageheart-gametest:build_p4_foundation", structure = YARD, skyAccess = true, maxTicks = 800)
	public void foundationPausesWithoutDirt(GameTestHelper h) {
		BuildTestSupport.layout(h, With.STORAGE_NO_HUT).thenExecute(() -> {
			BlockPos ground = h.absolutePos(BuildTestSupport.GROUND);
			BuildJob job = BuildJobs.start(h.getLevel(), Blueprints.SMALL_HOUSE, ground, Rotation4.NONE, UUID.randomUUID());
			Blueprint bp = BuildJobs.blueprint(job);
			Placement pl = job.placement();
			int dug = 0;
			for (int x = 0; x < bp.width(); x++) for (int z = 0; z < bp.depth(); z++) {
				for (int y = 0; y < bp.height(); y++) {
					if (!bp.entry(x, y, z).primary()) continue;
					BlockPos at = BuildTestSupport.rel(h, pl.world(bp, x, y, z));
					h.setBlock(at.below(), Blocks.AIR.defaultBlockState());
					dug++;
					break;
				}
			}
			h.assertTrue(dug > 0, "dug under the floor");
			var stopped = BuildJobs.advance(h.getLevel().getServer(), job.id(), 20);
			h.assertTrue(stopped.stoppedBecause().contains("dirt"), stopped.toString());
			h.assertTrue(stopped.steps() == 0, "nothing is placed while the foundation wants dirt");
			var view = dev.villageheart.build.BuildSystem.view(h.getLevel().getServer(), BuildJobs.find(h.getLevel().getServer(), job.id()).orElseThrow());
			h.assertTrue(view.line().english().contains("dirt"), view.line().english());
			BuildTestSupport.stock(h, new ItemStack(Items.DIRT, 64), new ItemStack(Items.DIRT, 64));
			var next = BuildJobs.advance(h.getLevel().getServer(), job.id(), 30);
			h.assertFalse(next.stoppedBecause().contains("dirt"), next.toString());
			h.assertTrue(BuildJobs.prioritise(h.getLevel().getServer(), job.id()), "the house is still a build");
			var bumped = BuildJobs.find(h.getLevel().getServer(), job.id()).orElseThrow();
			h.assertTrue(bumped.priority() > 0, "Build this next raises priority: " + bumped.priority());
			VillageTestSupport.done(h);
		});
	}

	@GameTest(environment = "villageheart-gametest:build_p4_quill", structure = YARD, skyAccess = true, maxTicks = 800)
	public void quillSavesAFinishedHouse(GameTestHelper h) {
		BuildTestSupport.layout(h, With.STORAGE_NO_HUT).thenExecute(() -> {
			BuildTestSupport.stock(h, new ItemStack(Items.DIRT, 64));
			BuildTestSupport.stock(h, BuildTestSupport.houseMaterials(2));
			var player = BuildTestSupport.playerWithRank(h, "steward", dev.villageheart.core.Rank.STEWARD);
			BlockPos ground = h.absolutePos(BuildTestSupport.GROUND);
			BuildJob job = BuildJobs.start(h.getLevel(), Blueprints.SMALL_HOUSE, ground, Rotation4.NONE, player.getUUID());
			var adv = BuildJobs.advance(h.getLevel().getServer(), job.id(), Integer.MAX_VALUE);
			h.assertTrue(adv.finished(), adv.toString());
			var pl = job.placement();
			var min = pl.min(Blueprints.SMALL_HOUSE);
			var max = pl.max(Blueprints.SMALL_HOUSE);
			String reason = BlueprintQuill.save(h.getLevel(), new BlockPos(min.x(), min.y(), min.z()), new BlockPos(max.x(), max.y(), max.z()), player);
			h.assertTrue(reason.startsWith("Saved blueprint saved_"), reason);
			String id = reason.substring("Saved blueprint ".length()).trim();
			var saved = Blueprints.byId(id);
			h.assertTrue(saved.isPresent(), id);
			h.assertTrue(saved.get().markerEntry() != null, "the save kept the house marker");
			for (var e : saved.get().palette().values()) {
				if (!e.isBlock()) continue;
				var real = dev.villageheart.room.RoomCells.of(PaletteStates.resolve(e, Rotation4.NONE, null));
				var expected = e.scannerCell();
				h.assertTrue(new java.util.TreeSet<>(real.contents()).equals(new java.util.TreeSet<>(expected.contents())),
						e.symbol() + " " + e.state() + " contents " + real.contents() + " vs " + expected.contents());
			}
			Blueprints.removeDynamic(id);
			dev.villageheart.build.SavedBlueprints.of(h.getLevel().getServer()).removeId(id);
			VillageTestSupport.done(h);
		});
	}
}
