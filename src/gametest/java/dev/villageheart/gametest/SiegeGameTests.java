package dev.villageheart.gametest;

import static dev.villageheart.gametest.BuildTestSupport.YARD;

import dev.villageheart.core.defence.DefenceCell;
import dev.villageheart.core.defence.DefenceCells;
import dev.villageheart.core.defence.DefenceIndex;
import dev.villageheart.core.defence.DefenceKind;
import dev.villageheart.core.defence.DefenceSite;
import dev.villageheart.core.defence.Defences;
import dev.villageheart.core.siege.SiegeHits;
import dev.villageheart.gametest.BuildTestSupport.With;
import dev.villageheart.siege.SiegeDamage;
import dev.villageheart.siege.SiegeGamerules;
import dev.villageheart.world.VillageClaims;
import io.netty.channel.ChannelFutureListener;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/**
 * Layer 2 siege tests (docs/plans/M25.md 5.2). One environment per test, the yard, a layout first, {@code VillageTestSupport.done} last.
 * Walls are built with the real pipeline.
 */
public class SiegeGameTests {
	private static BlockPos rel(int x, int y, int z) { return new BlockPos(x, y, z); }

	/** T-L2-13. A wall of 10 and a gate of 5 are recorded, and the index counts their defence cells. */
	@GameTest(environment = "villageheart-gametest:siege_wall_is_recorded", structure = YARD, skyAccess = true, maxTicks = 600)
	public void wallIsRecorded(GameTestHelper h) {
		BuildTestSupport.layout(h, With.GRID).thenExecute(() -> {
			BuildTestSupport.stock(h, new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.OAK_FENCE_GATE, 3));
			DefenceTestSupport.startLine(h, "wall_segment", rel(2, -1, 18), rel(11, -1, 18));
			DefenceTestSupport.startLine(h, "gate", rel(14, -1, 18), rel(18, -1, 18));
			h.assertTrue(BuildTestSupport.builds(h).size() == 2, "wall and gate started");
			DefenceTestSupport.advanceAll(h);
			var village = VillageClaims.current(h.getLevel().getServer()).orElseThrow();
			DefenceSite wall = null, gate = null;
			for (DefenceSite s : village.defences()) {
				if (s.blueprintId().equals("wall_segment")) wall = s;
				if (s.blueprintId().equals("gate")) gate = s;
			}
			h.assertTrue(wall != null && wall.length() == 10, "wall length 10: " + village.defences());
			h.assertTrue(gate != null && gate.length() == 5, "gate length 5: " + village.defences());
			h.assertTrue(Defences.patrolPoints(village.defences(), p -> true).isEmpty(), "a wall and a gate have no patrol point");
			DefenceIndex index = DefenceIndex.of(village.defences());
			int stone = DefenceCells.count(index.cells().stream().map(c -> new DefenceCells.Placed(c.pos(), c.kind(), c.expectedState())).toList(), DefenceKind.STONE);
			int gates = DefenceCells.count(index.cells().stream().map(c -> new DefenceCells.Placed(c.pos(), c.kind(), c.expectedState())).toList(), DefenceKind.GATE);
			h.assertTrue(index.size() == 42 && stone == 39 && gates == 3, "index " + index.size() + " stone " + stone + " gate " + gates);
			// The live values are one world, shared with every other GameTest. The registered default is what "default on" means.
			h.assertTrue(Boolean.TRUE.equals(SiegeGamerules.SIEGE_DAMAGE.defaultValue())
					&& Boolean.TRUE.equals(SiegeGamerules.RANDOM_RAIDS.defaultValue())
					&& Boolean.TRUE.equals(SiegeGamerules.SIEGES.defaultValue()),
					"the three siege gamerules default on");
			VillageTestSupport.done(h);
		});
	}

	/** T-L2-14. Eleven zombie hits crack a recorded cell to stage 9. The twelfth opens it, with no drop, and the breach is saved at once. */
	@GameTest(environment = "villageheart-gametest:siege_damage_hits", structure = YARD, skyAccess = true, maxTicks = 600)
	public void siegeDamageHitsCracksAndBreaks(GameTestHelper h) {
		BuildTestSupport.layout(h, With.GRID).thenExecute(() -> {
			BuildTestSupport.stock(h, new ItemStack(Items.COBBLESTONE, 64));
			DefenceTestSupport.startLine(h, "wall_segment", rel(2, -1, 18), rel(11, -1, 18));
			DefenceTestSupport.advanceAll(h);
			var server = h.getLevel().getServer();
			var village = VillageClaims.current(server).orElseThrow();
			DefenceCell cell = DefenceIndex.of(village.defences()).cells().stream().filter(c -> c.kind() == DefenceKind.STONE).findFirst().orElseThrow();
			BlockPos pos = new BlockPos(cell.pos().x(), cell.pos().y(), cell.pos().z());
			BlockPos relPos = VillageTestSupport.rel(h, pos);
			Capture capture = new Capture();
			ServerPlayer player = VillageTestSupport.player(h, "siege", GameType.SURVIVAL, (s, level, profile) ->
					new ServerPlayer(s, level, profile, net.minecraft.server.level.ClientInformation.createDefault()), capture);
			player.setPos(pos.getX() + 5.5, pos.getY(), pos.getZ() + 0.5);
			for (int i = 0; i < 11; i++) SiegeDamage.hit(server, h.getLevel(), pos, SiegeHits.Attacker.ZOMBIE);
			h.assertTrue(h.getBlockState(relPos).is(net.minecraft.world.level.block.Blocks.COBBLESTONE), "eleven hits leave the block: " + h.getBlockState(relPos));
			h.assertTrue(SiegeDamage.damage(server).damageAt(cell.packed()) == 11, "damage 11, got " + SiegeDamage.damage(server).damageAt(cell.packed()));
			h.assertTrue(capture.packets.stream().anyMatch(p -> p.getPos().equals(pos) && p.getProgress() == 9), "stage 9 was sent: " + capture.packets.size());
			h.assertTrue(capture.packets.stream().anyMatch(p -> p.getId() < 0), "crack ids are negative");
			SiegeDamage.flush(server);
			try {
				var saved = VillageTestSupport.reload(server);
				h.assertTrue(saved.village().orElseThrow().threats().damage().damageAt(cell.packed()) == 11, "flushed damage reloads");
				h.assertTrue(!saved.village().orElseThrow().threats().damage().isBreached(cell.packed()), "a crack is not a breach");
				SiegeDamage.hit(server, h.getLevel(), pos, SiegeHits.Attacker.ZOMBIE);
				h.assertTrue(h.getBlockState(relPos).isAir(), "the twelfth hit breaks it");
				for (ItemEntity drop : h.getEntities(EntityType.ITEM, relPos, 4)) h.assertTrue(!drop.getItem().is(Items.COBBLESTONE), "no cobblestone drop");
				h.assertTrue(SiegeDamage.damage(server).isBreached(cell.packed()), "breached");
				h.assertTrue(capture.packets.stream().anyMatch(p -> p.getPos().equals(pos) && p.getProgress() == -1), "stage -1 was sent");
				var breached = VillageTestSupport.reload(server);
				h.assertTrue(breached.village().orElseThrow().threats().damage().isBreached(cell.packed()), "a breach is in the save without waiting for the flush");
			} catch (java.io.IOException e) {
				throw new AssertionError(e);
			}
			var before = SiegeDamage.damage(server);
			SiegeDamage.hit(server, h.getLevel(), pos, SiegeHits.Attacker.ZOMBIE);
			h.assertTrue(SiegeDamage.damage(server).equals(before), "a hit on a breached cell does nothing");
			VillageTestSupport.done(h);
		});
	}

	/** T-L2-15. A hit or a blast changes only a recorded defence cell that still holds its material. A torch, gravel or ladder stops the break. */
	@GameTest(environment = "villageheart-gametest:siege_non_defence", structure = YARD, skyAccess = true, maxTicks = 600)
	public void nonDefenceBlocksNeverDamaged(GameTestHelper h) {
		BuildTestSupport.layout(h, With.GRID).thenExecute(() -> {
			DefenceCell wall = stoneWall(h);
			BlockPos wallPos = block(wall);
			BlockPos wallRel = VillageTestSupport.rel(h, wallPos);
			var server = h.getLevel().getServer();
			int before = SiegeDamage.rejected(server);
			BlockPos fill = rel(2, 0, 4);
			h.setBlock(fill, net.minecraft.world.level.block.Blocks.COBBLESTONE.defaultBlockState());
			SiegeDamage.hit(server, h.getLevel(), h.absolutePos(fill), SiegeHits.Attacker.ZOMBIE);
			SiegeDamage.blast(server, h.getLevel(), h.absolutePos(fill), true, false);
			h.assertTrue(h.getBlockState(fill).is(net.minecraft.world.level.block.Blocks.COBBLESTONE), "a filled wall is not a defence");
			BlockPos planks = rel(4, 0, 4);
			h.setBlock(planks, net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState());
			SiegeDamage.hit(server, h.getLevel(), h.absolutePos(planks), SiegeHits.Attacker.ZOMBIE);
			SiegeDamage.blast(server, h.getLevel(), h.absolutePos(planks), false, true);
			h.assertTrue(h.getBlockState(planks).is(net.minecraft.world.level.block.Blocks.OAK_PLANKS), "a house plank is not a defence");
			h.setBlock(wallRel, net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState());
			SiegeDamage.hit(server, h.getLevel(), wallPos, SiegeHits.Attacker.ZOMBIE);
			h.assertTrue(h.getBlockState(wallRel).is(net.minecraft.world.level.block.Blocks.STONE_BRICKS), "a replaced cell is the player's");
			h.assertTrue(SiegeDamage.rejected(server) > before, "rejected hits " + SiegeDamage.rejected(server));
			h.setBlock(wallRel, net.minecraft.world.level.block.Blocks.COBBLESTONE.defaultBlockState());
			DefenceCell other = otherStone(h, wall);
			BlockPos support = block(other);
			BlockPos supportRel = VillageTestSupport.rel(h, support);
			h.setBlock(supportRel.above(), net.minecraft.world.level.block.Blocks.GRAVEL.defaultBlockState());
			h.setBlock(wallRel.south(), net.minecraft.world.level.block.Blocks.WALL_TORCH.defaultBlockState()
					.setValue(net.minecraft.world.level.block.WallTorchBlock.FACING, net.minecraft.core.Direction.SOUTH));
			for (int i = 0; i < 20; i++) {
				SiegeDamage.hit(server, h.getLevel(), wallPos, SiegeHits.Attacker.ZOMBIE);
				SiegeDamage.hit(server, h.getLevel(), support, SiegeHits.Attacker.ZOMBIE);
			}
			h.assertTrue(h.getBlockState(wallRel).is(net.minecraft.world.level.block.Blocks.COBBLESTONE), "the torch's block stays");
			h.assertTrue(h.getBlockState(wallRel.south()).is(net.minecraft.world.level.block.Blocks.WALL_TORCH), "the torch stays");
			h.assertTrue(h.getBlockState(supportRel).is(net.minecraft.world.level.block.Blocks.COBBLESTONE), "the gravel's block stays");
			h.assertTrue(h.getBlockState(supportRel.above()).is(net.minecraft.world.level.block.Blocks.GRAVEL), "the gravel stays");
			h.assertTrue(SiegeDamage.damage(server).damageAt(wall.packed()) == 11, "torch support stops at hp-1");
			h.assertTrue(SiegeDamage.damage(server).damageAt(other.packed()) == 11, "gravel support stops at hp-1");
			VillageTestSupport.done(h);
		});
	}

	/** T-L2-16. A cell the player breaks is opened: not a breach, not a repair, and it counts again once the material is back. */
	@GameTest(environment = "villageheart-gametest:siege_player_opened", structure = YARD, skyAccess = true, maxTicks = 800)
	public void playerOpenedCellIsLeftAlone(GameTestHelper h) {
		BlockPos[] relPos = new BlockPos[1];
		long[] packed = new long[1];
		BuildTestSupport.layout(h, With.GRID).thenExecute(() -> {
			DefenceCell cell = stoneWall(h);
			packed[0] = cell.packed();
			BlockPos pos = block(cell);
			relPos[0] = VillageTestSupport.rel(h, pos);
			var player = BuildingTestSupport.owner();
			player.setGameMode(GameType.CREATIVE);
			player.teleportTo(pos.getX() + 1.5, pos.getY(), pos.getZ() + 0.5);
			h.assertTrue(player.gameMode.destroyBlock(pos), "the player breaks the cell");
			var server = h.getLevel().getServer();
			var site = VillageClaims.current(server).orElseThrow().defences().get(0);
			long relative = DefenceCells.relative(site.ground(), cell.pos());
			h.assertTrue(site.opened().contains(relative), "opened");
			h.assertTrue(!SiegeDamage.isBreached(server, cell.packed()), "not breached");
			var plan = dev.villageheart.core.siege.RepairPlanner.next(false, dev.villageheart.core.worker.DayPhase.WORK,
					java.util.List.of(new dev.villageheart.core.siege.RepairPlanner.Cell(cell.packed(), DefenceKind.STONE, 0, false, true)),
					VillageClaims.current(server).orElseThrow().heart(), java.util.Map.of(dev.villageheart.core.siege.RepairPlanner.COBBLESTONE, 64));
			h.assertTrue(plan.isEmpty(), "an opened cell is not planned");
			h.setBlock(relPos[0], net.minecraft.world.level.block.Blocks.COBBLESTONE.defaultBlockState());
		}).thenIdle(200).thenExecute(() -> {
			var site = VillageClaims.current(h.getLevel().getServer()).orElseThrow().defences().get(0);
			h.assertTrue(site.opened().isEmpty(), "the cobblestone counts again: " + site.opened());
			h.assertTrue(!SiegeDamage.isBreached(h.getLevel().getServer(), packed[0]), "still not a breach");
			VillageTestSupport.done(h);
		});
	}

	/** T-L2-36, the zombie half. Siege damage ignores mobGriefing, and it stops when the gamerule is turned off. The creeper half is step 14. */
	@GameTest(environment = "villageheart-gametest:siege_damage_rule", structure = YARD, skyAccess = true, maxTicks = 400)
	public void siegeDamageGamerule(GameTestHelper h) {
		BuildTestSupport.layout(h, With.GRID).thenExecute(() -> {
			DefenceCell cell = stoneWall(h);
			BlockPos pos = block(cell);
			var server = h.getLevel().getServer();
			var rules = h.getLevel().getGameRules();
			try {
				rules.set(net.minecraft.world.level.gamerules.GameRules.MOB_GRIEFING, false, server);
				h.assertTrue(SiegeDamage.hit(server, h.getLevel(), pos, SiegeHits.Attacker.ZOMBIE) == SiegeDamage.Result.APPLIED, "mobGriefing off still damages");
				int cracked = SiegeDamage.damage(server).damageAt(cell.packed());
				rules.set(SiegeGamerules.SIEGE_DAMAGE, false, server);
				h.assertTrue(SiegeDamage.hit(server, h.getLevel(), pos, SiegeHits.Attacker.ZOMBIE) == SiegeDamage.Result.IGNORED, "the gamerule stops the hit");
				h.assertTrue(SiegeDamage.damage(server).damageAt(cell.packed()) == cracked, "damage stays " + cracked);
			} finally {
				rules.set(SiegeGamerules.SIEGE_DAMAGE, true, server);
				rules.set(net.minecraft.world.level.gamerules.GameRules.MOB_GRIEFING, true, server);
			}
			VillageTestSupport.done(h);
		});
	}

	/**
	 * T-L2-40. A missing watchtower key is dropped, and the same key recorded as a breach keeps the site and its patrol point.
	 * The builder walking over to replace the shaft is step 15.
	 */
	@GameTest(environment = "villageheart-gametest:siege_watchtower_breach", structure = YARD, skyAccess = true, maxTicks = 6000)
	public void watchtowerBreachKeepsSite(GameTestHelper h) {
		var seq = BuildTestSupport.layout(h, With.GRID).thenExecute(() -> {
			BuildingTestSupport.setTier(h, dev.villageheart.core.Tier.TOWN);
			BuildTestSupport.stock(h, DefenceTestSupport.towerMaterials(2));
			BuildTestSupport.startBuild(h, dev.villageheart.core.blueprint.Blueprints.WATCHTOWER, BuildTestSupport.GROUND, dev.villageheart.core.blueprint.Rotation4.NONE);
			BuildTestSupport.startBuild(h, dev.villageheart.core.blueprint.Blueprints.WATCHTOWER, rel(24, -1, 14), dev.villageheart.core.blueprint.Rotation4.NONE);
			DefenceTestSupport.advanceAll(h);
			var server = h.getLevel().getServer();
			var sites = VillageClaims.current(server).orElseThrow().defences();
			h.assertTrue(sites.size() == 2, "two towers: " + sites);
			clearKeys(h, sites.get(0));
			dev.villageheart.defence.DefenceSystem.refresh(server, VillageClaims.current(server).orElseThrow());
			h.assertTrue(VillageClaims.current(server).orElseThrow().defences().size() == 1, "a missing key drops the site");
			breachKeys(h, VillageClaims.current(server).orElseThrow().defences().get(0));
		}).thenIdle(400).thenExecute(() -> {
			var server = h.getLevel().getServer();
			dev.villageheart.defence.DefenceSystem.refresh(server, VillageClaims.current(server).orElseThrow());
			var left = VillageClaims.current(server).orElseThrow().defences();
			h.assertTrue(left.size() == 1 && left.get(0).blueprintId().equals("watchtower"), "the breached tower stays: " + left);
			h.assertTrue(dev.villageheart.defence.Defences.patrolPoints(server).size() == 1, "and it is still a patrol point");
			h.getLevel().setDayTime(2000);
			for (int x = 16; x <= 30; x++) for (int z = 2; z <= 20; z++) h.setBlock(rel(x, -1, z), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
			BuildingTestSupport.room(h, BuildTestSupport.HUT, 5, 4);
			h.setBlock(BuildTestSupport.table(), dev.villageheart.block.JobBlocks.BLOCKS.get(dev.villageheart.core.building.JobBlockType.BUILDERS_TABLE));
			h.setBlock(BuildTestSupport.hutChest(), net.minecraft.world.level.block.Blocks.CHEST);
			h.setBlock(BuildingTestSupport.inside(BuildTestSupport.HUT, 2, 0, 3), net.minecraft.world.level.block.Blocks.TORCH);
			BuildingTestSupport.placeMarker(h, dev.villageheart.core.building.BuildingType.BUILDERS_HUT, BuildTestSupport.hutMarker(), net.minecraft.core.Direction.NORTH);
			BuildTestSupport.stock(h, new ItemStack(Items.LADDER, 4));
		});
		seq = seq.thenWaitUntil(() -> BuildingTestSupport.assertStatus(h, BuildTestSupport.hutMarker(), dev.villageheart.block.MarkerStatus.VALID));
		seq = seq.thenExecute(() -> BuildTestSupport.hireBuilder(h));
		seq = BuildingTestSupport.awaitWithin(h, seq, 3000, "the builder replaces the shaft", () -> {
			var server = h.getLevel().getServer();
			var site = VillageClaims.current(server).orElseThrow().defences().get(0);
			for (var key : Defences.keys(site)) {
				BlockPos pos = new BlockPos(key.at().x(), key.at().y(), key.at().z());
				h.assertTrue(h.getBlockState(VillageTestSupport.rel(h, pos)).is(net.minecraft.world.level.block.Blocks.LADDER), "shaft " + key.at());
				h.assertTrue(!SiegeDamage.damage(server).isBreached(key.at().pack()), "the breach is still recorded at " + key.at());
				h.assertTrue(!VillageClaims.current(server).orElseThrow().threats().damage().isBreached(key.at().pack()), "the saved breach is still at " + key.at());
			}
		});
		seq.thenExecute(() -> {
			var server = h.getLevel().getServer();
			var left = VillageClaims.current(server).orElseThrow().defences();
			h.assertTrue(left.size() == 1 && left.get(0).blueprintId().equals("watchtower"), "the repaired tower stays: " + left);
			h.assertTrue(dev.villageheart.defence.Defences.patrolPoints(server).size() == 1, "and it is still a patrol point");
			VillageTestSupport.done(h);
		});
	}

	private static void clearKeys(GameTestHelper h, DefenceSite site) {
		for (var key : Defences.keys(site)) {
			var at = key.at();
			h.setBlock(VillageTestSupport.rel(h, new BlockPos(at.x(), at.y(), at.z())), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
		}
	}

	private static void breachKeys(GameTestHelper h, DefenceSite site) {
		var server = h.getLevel().getServer();
		for (var key : Defences.keys(site)) {
			var at = key.at();
			BlockPos pos = new BlockPos(at.x(), at.y(), at.z());
			h.setBlock(VillageTestSupport.rel(h, pos), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
			SiegeDamage.recordBreach(server, pos);
		}
	}

	/**
	 * T-L2-17. A ravager in the tracked raid opens a 2×3 hole and walks through. One that is not in the raid does not touch the wall.
	 * The same ravager ridden by a pillager still does.
	 */
	@GameTest(environment = "villageheart-gametest:siege_ravager", structure = YARD, skyAccess = true, maxTicks = 4000)
	public void ravagerBreaksWallInTwoHits(GameTestHelper h) {
		net.minecraft.world.entity.monster.Ravager[] idle = new net.minecraft.world.entity.monster.Ravager[1];
		net.minecraft.world.entity.monster.Ravager[] ravager = new net.minecraft.world.entity.monster.Ravager[1];
		net.minecraft.world.entity.npc.villager.Villager[] villager = new net.minecraft.world.entity.npc.villager.Villager[1];
		int[] sawSix = new int[1];
		boolean[] running = new boolean[1];
		var seq = BuildTestSupport.layout(h, With.GRID).thenExecute(() -> {
			closedRing(h, false);
			villager[0] = h.spawn(EntityType.VILLAGER, rel(4, 0, 4));
			villager[0].setNoAi(true);
			idle[0] = spawnRavager(h, mobSpot());
		});
		seq = seq.thenIdle(600).thenExecute(() -> {
			var server = h.getLevel().getServer();
			h.assertTrue(SiegeDamage.damage(server).damageAt(packed(h, attackCell())) == 0, "a ravager in no raid leaves the wall");
			h.assertTrue(h.getBlockState(attackCell()).is(net.minecraft.world.level.block.Blocks.COBBLESTONE), "the cell is still cobble");
			idle[0].discard();
			claimBell(h);
			var raid = dev.villageheart.siege.TrackedRaids.track(server, BuildingTestSupport.owner(), h.absolutePos(BuildingTestSupport.BELL));
			ravager[0] = (net.minecraft.world.entity.monster.Ravager) joinRaider(h, raid, mobSpot(), EntityType.RAVAGER, false);
			ravager[0].setTarget(villager[0]);
			sawSix[0] = 0;
			running[0] = false;
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 200, "BreachGoal runs", () -> noteBreach(h, ravager[0], sawSix, running, 6));
		seq = BuildingTestSupport.awaitWithin(h, seq, 900, "the ravager is inside the 2x3 hole", () -> inside(h, ravager[0], sawSix, 6));
		seq = seq.thenExecute(() -> {
			ravager[0].discard();
			for (int x = ATTACK_X; x <= ATTACK_X + 1; x++) for (int y = 0; y <= 2; y++) h.setBlock(rel(x, y, ATTACK_Z), net.minecraft.world.level.block.Blocks.COBBLESTONE.defaultBlockState());
			var server = h.getLevel().getServer();
			SiegeDamage.scan(server, VillageClaims.current(server).orElseThrow(), -1);
			var raid = h.getLevel().getRaids().get(VillageClaims.current(server).orElseThrow().threats().raid().orElseThrow().vanillaId());
			ravager[0] = (net.minecraft.world.entity.monster.Ravager) joinRaider(h, raid, mobSpot(), EntityType.RAVAGER, true);
			ravager[0].setTarget(villager[0]);
			h.assertTrue(!ravager[0].getPassengers().isEmpty(), "a pillager is riding");
			sawSix[0] = 0;
			running[0] = false;
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 200, "BreachGoal runs while ridden", () -> noteBreach(h, ravager[0], sawSix, running, 6));
		seq = BuildingTestSupport.awaitWithin(h, seq, 900, "the ridden ravager is inside", () -> inside(h, ravager[0], sawSix, 6));
		seq.thenExecute(() -> VillageTestSupport.done(h));
	}

	/** T-L2-18. A vindicator in the raid chops the gate in two hits of 4 (the second hit breaks it) and walks through. Stone it hits takes 2 a hit. */
	@GameTest(environment = "villageheart-gametest:siege_vindicator", structure = YARD, skyAccess = true, maxTicks = 2000)
	public void vindicatorChopsGate(GameTestHelper h) {
		net.minecraft.world.entity.monster.illager.Vindicator[] vindicator = new net.minecraft.world.entity.monster.illager.Vindicator[1];
		net.minecraft.world.entity.npc.villager.Villager[] villager = new net.minecraft.world.entity.npc.villager.Villager[1];
		int[] saw = new int[1];
		boolean[] running = new boolean[1];
		String[] violation = new String[1];
		var seq = BuildTestSupport.layout(h, With.GRID).thenExecute(() -> {
			closedRing(h, true);
			villager[0] = h.spawn(EntityType.VILLAGER, rel(4, 0, 4));
			villager[0].setNoAi(true);
			var server = h.getLevel().getServer();
			claimBell(h);
			var raid = dev.villageheart.siege.TrackedRaids.track(server, BuildingTestSupport.owner(), h.absolutePos(BuildingTestSupport.BELL));
			vindicator[0] = (net.minecraft.world.entity.monster.illager.Vindicator) joinRaider(h, raid, mobSpot(), EntityType.VINDICATOR, false);
			vindicator[0].setTarget(villager[0]);
			h.assertTrue(h.getBlockState(attackCell()).is(net.minecraft.world.level.block.Blocks.OAK_FENCE_GATE), "the attack cell is the gate: " + h.getBlockState(attackCell()));
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 200, "BreachGoal runs at the gate", () -> {
			gateBreaksOnTheSecondHit(h, violation);
			noteBreach(h, vindicator[0], saw, running, 4);
			h.assertTrue(breachRunning(vindicator[0]) || h.getBlockState(attackCell()).isAir(), "the breach goal is the one running");
			h.assertTrue(!doorRunning(vindicator[0]), "not the wooden-door goal");
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 900, "the vindicator walks through the gate", () -> {
			gateBreaksOnTheSecondHit(h, violation);
			if (!h.getBlockState(attackCell()).isAir()) holdAtWall(h, vindicator[0]);
			int d = SiegeDamage.damage(h.getLevel().getServer()).damageAt(packed(h, attackCell()));
			if (d == 4) saw[0] = 4;
			h.assertTrue(saw[0] == 4, "the gate was hit for 4");
			h.assertTrue(h.getBlockState(attackCell()).isAir(), "the gate cell is open");
			h.assertTrue(VillageTestSupport.rel(h, vindicator[0].blockPosition()).getZ() > ATTACK_Z, "through the gate");
			var damage = SiegeDamage.damage(h.getLevel().getServer());
			for (DefenceCell cell : DefenceIndex.of(VillageClaims.current(h.getLevel().getServer()).orElseThrow().defences()).cells()) {
				if (cell.kind() != DefenceKind.STONE) continue;
				int n = damage.damageAt(cell.packed());
				h.assertTrue(n % 2 == 0, "stone takes 2 a hit, got " + n);
			}
		});
		seq.thenExecute(() -> {
			h.assertTrue(violation[0] == null, String.valueOf(violation[0]));
			VillageTestSupport.done(h);
		});
	}

	/** T-L2-19. A reload, twice, leaves one of each siege goal. Zombies carry the lure and the breach. The other hostiles carry none. */
	@GameTest(environment = "villageheart-gametest:siege_goals", structure = YARD, skyAccess = true, maxTicks = 400)
	public void goalsInstalledOnce(GameTestHelper h) {
		BuildTestSupport.layout(h, With.GRID).thenExecute(() -> {
			var level = h.getLevel();
			Mob[] mobs = new Mob[] {
					h.spawn(EntityType.ZOMBIE, rel(2, 0, 4)),
					h.spawn(EntityType.HUSK, rel(4, 0, 4)),
					h.spawn(EntityType.ZOMBIE_VILLAGER, rel(6, 0, 4)),
					h.spawn(EntityType.CREEPER, rel(8, 0, 4)),
					h.spawn(EntityType.RAVAGER, rel(10, 0, 4)),
					h.spawn(EntityType.VINDICATOR, rel(12, 0, 4)),
					h.spawn(EntityType.SKELETON, rel(14, 0, 4)),
					h.spawn(EntityType.SPIDER, rel(16, 0, 4)),
					h.spawn(EntityType.DROWNED, rel(18, 0, 4)),
					h.spawn(EntityType.ZOMBIFIED_PIGLIN, rel(20, 0, 4)),
					h.spawn(EntityType.WITCH, rel(22, 0, 4)),
					h.spawn(EntityType.PILLAGER, rel(24, 0, 4))
			};
			int[] expect = {2, 2, 2, 1, 1, 1, 0, 0, 0, 0, 0, 0};
			for (int i = 0; i < mobs.length; i++) {
				mobs[i].setNoAi(true);
				dev.villageheart.siege.SiegeGoals.install(mobs[i]);
				h.assertValueEqual(expect[i], siegeGoals(mobs[i]), "installed once " + mobs[i].getType());
				mobs[i] = reloadMob(level, mobs[i]);
				mobs[i] = reloadMob(level, mobs[i]);
				h.assertValueEqual(expect[i], siegeGoals(mobs[i]), "still once after two reloads " + mobs[i].getType());
			}
			VillageTestSupport.done(h);
		});
	}

	/**
	 * A gate has 8 hit points and a vindicator hits it for 4: after the first hit it stands with 4 damage, and the second hit opens it.
	 * So a gate that is still in the world never carries more than 4. With three-hit hit points (12) it would stand with 8 damage for
	 * the whole cooldown between the second and third hit. Called inside a wait, where a failed assertion only means "not yet", so
	 * the first violation is kept in {@code violation} and asserted after the wait.
	 */
	private static void gateBreaksOnTheSecondHit(GameTestHelper h, String[] violation) {
		if (h.getBlockState(attackCell()).isAir() || violation[0] != null) return;
		int d = SiegeDamage.damage(h.getLevel().getServer()).damageAt(packed(h, attackCell()));
		if (d != 0 && d != 4) violation[0] = "the gate is standing with " + d + " damage at tick " + h.getTick() + ": it must break on the second hit of 4 (8 hit points)";
	}

	private static void noteBreach(GameTestHelper h, net.minecraft.world.entity.Mob mob, int[] saw, boolean[] running, int hit) {
		if (!h.getBlockState(attackCell()).isAir()) holdAtWall(h, mob);
		if (breachRunning(mob)) running[0] = true;
		int d = SiegeDamage.damage(h.getLevel().getServer()).damageAt(packed(h, attackCell()));
		if (d == hit) saw[0] = hit;
		h.assertTrue(running[0] || saw[0] == hit || h.getBlockState(attackCell()).isAir(), "BreachGoal has run" + where(h, mob));
	}

	private static void inside(GameTestHelper h, net.minecraft.world.entity.Mob mob, int[] saw, int hit) {
		if (!holeOpen(h)) holdAtWall(h, mob);
		int d = SiegeDamage.damage(h.getLevel().getServer()).damageAt(packed(h, attackCell()));
		if (d == hit) saw[0] = hit;
		h.assertTrue(saw[0] == hit, "the first hit was " + hit + ", saw " + saw[0] + where(h, mob));
		for (int x = ATTACK_X; x <= ATTACK_X + 1; x++) for (int y = 0; y <= 2; y++) {
			h.assertTrue(h.getBlockState(rel(x, y, ATTACK_Z)).isAir(), "hole " + x + "," + y);
		}
		h.assertTrue(VillageTestSupport.rel(h, mob.blockPosition()).getZ() > ATTACK_Z, "inside " + VillageTestSupport.rel(h, mob.blockPosition()));
	}

	/**
	 * A raid stops on its first tick unless the centre is a vanilla village: an occupied POI in the village tag. The villager in
	 * these tests has no AI, so it never takes the bell. Take it here, the same claim a villager's brain would make.
	 */
	private static void claimBell(GameTestHelper h) {
		BlockPos bell = h.absolutePos(BuildingTestSupport.BELL);
		var taken = h.getLevel().getPoiManager().take(type -> type.is(net.minecraft.tags.PoiTypeTags.VILLAGE), (type, pos) -> pos.equals(bell), bell, 1);
		h.assertTrue(taken.isPresent(), "the bell's meeting point was free to claim");
		h.assertTrue(h.getLevel().isVillage(bell), "the claimed bell is a vanilla village");
	}

	/**
	 * South wall of the ring. The bell at (1, 1) sits inside, so a raid centred on it has to come through this face.
	 * The other three faces are far enough that this face is the closest a mob can stand to the bell. A tight ring ties
	 * the east face, and the path then ends there instead of on the wall in front of the mob.
	 */
	private static final int RING_X0 = -12, RING_X1 = 14, RING_Z0 = -6, RING_Z1 = 14;
	/** Floor around the ring. Stops short of the grid Storage at x=27. Extra room to the south, where a stroll walks. */
	private static final int FLOOR_X0 = RING_X0 - 8, FLOOR_X1 = RING_X1 + 8, FLOOR_Z0 = RING_Z0 - 24, FLOOR_Z1 = RING_Z1 + 4;
	private static final int ATTACK_X = 1, ATTACK_Z = RING_Z0;

	private static BlockPos attackCell() { return rel(ATTACK_X, 0, ATTACK_Z); }

	private static boolean holeOpen(GameTestHelper h) {
		for (int x = ATTACK_X; x <= ATTACK_X + 1; x++) for (int y = 0; y <= 2; y++) {
			if (!h.getBlockState(rel(x, y, ATTACK_Z)).isAir()) return false;
		}
		return true;
	}

	private static BlockPos mobSpot() { return rel(ATTACK_X, 0, ATTACK_Z - 1); }

	/**
	 * A ravager is 1.95 wide. Centred in the block south of the wall, its body is inside the cobble and the push moves it off
	 * the cell {@code BreachChoice} aims at (exactly one block toward the goal). Stand on the south edge of that block.
	 */
	private static final double RAVAGER_Z_IN_BLOCK = 0.01;

	/**
	 * Keep the raider on the block in front of the attack cell until that cell is open. A stroll or a melee path moves it
	 * one block along the wall, and {@code BreachChoice} then opens the next column instead of this one.
	 */
	private static void holdAtWall(GameTestHelper h, net.minecraft.world.entity.Mob mob) {
		BlockPos abs = h.absolutePos(mobSpot());
		double zIn = mob.getType() == EntityType.RAVAGER ? RAVAGER_Z_IN_BLOCK : 0.5;
		mob.setPos(abs.getX() + 0.5, abs.getY(), abs.getZ() + zIn);
		mob.setDeltaMovement(0, 0, 0);
		mob.fallDistance = 0;
	}

	private static String where(GameTestHelper h, net.minecraft.world.entity.Mob mob) {
		String raid = "";
		if (mob instanceof net.minecraft.world.entity.raid.Raider raider && raider.getCurrentRaid() != null) {
			raid = " raid " + VillageTestSupport.rel(h, raider.getCurrentRaid().getCenter())
					+ " stopped " + raider.getCurrentRaid().isStopped();
		}
		return " mob " + VillageTestSupport.rel(h, mob.blockPosition()) + raid + " running " + breachRunning(mob);
	}

	private static void closedRing(GameTestHelper h, boolean gate) {
		// The mob stands just outside the structure. Force those chunks so its goals tick, and give it a floor so a stroll cannot drop it off the pad.
		BlockPos cornerA = h.absolutePos(rel(FLOOR_X0, 0, FLOOR_Z0));
		BlockPos cornerB = h.absolutePos(rel(FLOOR_X1, 0, FLOOR_Z1));
		int cx0 = Math.min(cornerA.getX(), cornerB.getX()) >> 4, cx1 = Math.max(cornerA.getX(), cornerB.getX()) >> 4;
		int cz0 = Math.min(cornerA.getZ(), cornerB.getZ()) >> 4, cz1 = Math.max(cornerA.getZ(), cornerB.getZ()) >> 4;
		for (int cx = cx0; cx <= cx1; cx++) for (int cz = cz0; cz <= cz1; cz++) h.getLevel().setChunkForced(cx, cz, true);
		for (int x = FLOOR_X0; x <= FLOOR_X1; x++) for (int z = FLOOR_Z0; z <= FLOOR_Z1; z++) {
			h.setBlock(rel(x, -1, z), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
			for (int y = 0; y <= 4; y++) {
				if ((x == 1 || x == 3) && z == 1) continue; // bell and Heart
				h.setBlock(rel(x, y, z), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
			}
		}
		BuildTestSupport.stock(h, new ItemStack(Items.COBBLESTONE, 512), new ItemStack(Items.OAK_FENCE_GATE, 3));
		// Corners belong to one segment. A second segment on the same cell is rejected, and the ring would have a gap.
		DefenceTestSupport.startLine(h, "wall_segment", rel(RING_X0, -1, RING_Z1), rel(RING_X1, -1, RING_Z1));
		DefenceTestSupport.startLine(h, "wall_segment", rel(RING_X0, -1, RING_Z0), rel(RING_X0, -1, RING_Z1 - 1));
		DefenceTestSupport.startLine(h, "wall_segment", rel(RING_X1, -1, RING_Z0), rel(RING_X1, -1, RING_Z1 - 1));
		if (gate) {
			DefenceTestSupport.startLine(h, "wall_segment", rel(RING_X0 + 1, -1, RING_Z0), rel(-2, -1, RING_Z0));
			DefenceTestSupport.startLine(h, "gate", rel(-1, -1, RING_Z0), rel(3, -1, RING_Z0));
			DefenceTestSupport.startLine(h, "wall_segment", rel(4, -1, RING_Z0), rel(RING_X1 - 1, -1, RING_Z0));
		} else {
			DefenceTestSupport.startLine(h, "wall_segment", rel(RING_X0 + 1, -1, RING_Z0), rel(RING_X1 - 1, -1, RING_Z0));
		}
		DefenceTestSupport.advanceAll(h);
		h.assertTrue(VillageClaims.current(h.getLevel().getServer()).orElseThrow().defences().size() >= 4, "the ring is recorded");
	}

	private static net.minecraft.world.entity.monster.Ravager spawnRavager(GameTestHelper h, BlockPos at) {
		var level = h.getLevel();
		var ravager = EntityType.RAVAGER.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
		BlockPos abs = h.absolutePos(at);
		ravager.setPos(abs.getX() + 0.5, abs.getY(), abs.getZ() + RAVAGER_Z_IN_BLOCK);
		ravager.setPersistenceRequired();
		level.addFreshEntity(ravager);
		return ravager;
	}

	private static net.minecraft.world.entity.Mob joinRaider(GameTestHelper h, net.minecraft.world.entity.raid.Raid raid, BlockPos at,
			EntityType<? extends net.minecraft.world.entity.raid.Raider> type, boolean ridden) {
		var level = h.getLevel();
		net.minecraft.world.entity.raid.Raider raider = type.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
		if (raider == null) throw new AssertionError("did not create the raider");
		BlockPos abs = h.absolutePos(at);
		double zIn = type == EntityType.RAVAGER ? RAVAGER_Z_IN_BLOCK : 0.5;
		raider.setPos(abs.getX() + 0.5, abs.getY(), abs.getZ() + zIn);
		raid.joinRaid(level, 1, raider, abs, false);
		raider.setPos(abs.getX() + 0.5, abs.getY(), abs.getZ() + zIn);
		raider.setPersistenceRequired();
		h.assertTrue(raider.getCurrentRaid() != null && dev.villageheart.siege.TrackedRaids.member(level, raider), "joined the tracked raid");
		if (ridden) {
			var pillager = EntityType.PILLAGER.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			pillager.setPos(raider.getX(), raider.getY(), raider.getZ());
			level.addFreshEntity(pillager);
			pillager.startRiding(raider);
		}
		return raider;
	}

	private static boolean breachRunning(net.minecraft.world.entity.Mob mob) {
		for (net.minecraft.world.entity.ai.goal.WrappedGoal wrapped : ((dev.villageheart.mixin.MobGoalsAccessor) mob).villageheart$goalSelector().getAvailableGoals()) {
			if (wrapped.getGoal() instanceof dev.villageheart.siege.BreachGoal && wrapped.isRunning()) return true;
		}
		return false;
	}

	private static boolean doorRunning(net.minecraft.world.entity.Mob mob) {
		for (net.minecraft.world.entity.ai.goal.WrappedGoal wrapped : ((dev.villageheart.mixin.MobGoalsAccessor) mob).villageheart$goalSelector().getAvailableGoals()) {
			if (wrapped.isRunning() && wrapped.getGoal().getClass().getSimpleName().contains("BreakDoor")) return true;
		}
		return false;
	}

	private static int siegeGoals(net.minecraft.world.entity.Mob mob) {
		int n = 0;
		for (net.minecraft.world.entity.ai.goal.WrappedGoal wrapped : ((dev.villageheart.mixin.MobGoalsAccessor) mob).villageheart$goalSelector().getAvailableGoals()) {
			var goal = wrapped.getGoal();
			if (goal instanceof dev.villageheart.siege.BreachGoal || goal instanceof dev.villageheart.siege.VillageLureGoal
					|| goal instanceof dev.villageheart.siege.CreeperBreachGoal) n++;
		}
		return n;
	}

	private static net.minecraft.world.entity.Mob reloadMob(net.minecraft.server.level.ServerLevel level, net.minecraft.world.entity.Mob mob) {
		var out = net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess());
		mob.saveWithoutId(out);
		var tag = out.buildResult();
		var type = mob.getType();
		mob.discard();
		net.minecraft.world.entity.Entity loaded = EntityType.create(type, net.minecraft.world.level.storage.TagValueInput.create(
				net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), tag), level, net.minecraft.world.entity.EntitySpawnReason.LOAD).orElseThrow();
		level.addFreshEntity(loaded);
		return (net.minecraft.world.entity.Mob) loaded;
	}

	private static long packed(GameTestHelper h, BlockPos relative) {
		BlockPos abs = h.absolutePos(relative);
		return dev.villageheart.core.room.Pos.pack(abs.getX(), abs.getY(), abs.getZ());
	}

	private static DefenceCell stoneWall(GameTestHelper h) {
		BuildTestSupport.stock(h, new ItemStack(Items.COBBLESTONE, 64));
		DefenceTestSupport.startLine(h, "wall_segment", rel(2, -1, 18), rel(11, -1, 18));
		DefenceTestSupport.advanceAll(h);
		return DefenceIndex.of(VillageClaims.current(h.getLevel().getServer()).orElseThrow().defences()).cells().stream()
				.filter(c -> c.kind() == DefenceKind.STONE).findFirst().orElseThrow();
	}

	private static DefenceCell otherStone(GameTestHelper h, DefenceCell first) {
		return DefenceIndex.of(VillageClaims.current(h.getLevel().getServer()).orElseThrow().defences()).cells().stream()
				.filter(c -> c.kind() == DefenceKind.STONE && c.packed() != first.packed()).findFirst().orElseThrow();
	}

	private static BlockPos block(DefenceCell cell) {
		return new BlockPos(cell.pos().x(), cell.pos().y(), cell.pos().z());
	}

	private static final class Capture extends Connection {
		final List<ClientboundBlockDestructionPacket> packets = new ArrayList<>();

		Capture() { super(PacketFlow.SERVERBOUND); }

		@Override
		public void send(Packet<?> packet, ChannelFutureListener listener, boolean flush) {
			if (packet instanceof ClientboundBlockDestructionPacket destruction) packets.add(destruction);
			super.send(packet, listener, flush);
		}
	}

}
