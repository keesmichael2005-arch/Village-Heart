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
			var rules = h.getLevel().getGameRules();
			h.assertTrue(rules.get(SiegeGamerules.SIEGE_DAMAGE) && rules.get(SiegeGamerules.RANDOM_RAIDS) && rules.get(SiegeGamerules.SIEGES),
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
	@GameTest(environment = "villageheart-gametest:siege_watchtower_breach", structure = YARD, skyAccess = true, maxTicks = 900)
	public void watchtowerBreachKeepsSite(GameTestHelper h) {
		BuildTestSupport.layout(h, With.GRID).thenExecute(() -> {
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
		int[] sawSix = new int[1];
		boolean[] running = new boolean[1];
		var seq = BuildTestSupport.layout(h, With.GRID).thenExecute(() -> {
			closedRing(h, false);
			var villager = h.spawn(EntityType.VILLAGER, rel(2, 0, 2));
			villager.setNoAi(true);
			idle[0] = spawnRavager(h, rel(2, 0, -5));
		});
		seq = seq.thenIdle(600).thenExecute(() -> {
			var server = h.getLevel().getServer();
			h.assertTrue(SiegeDamage.damage(server).damageAt(packed(h, rel(2, 0, -4))) == 0, "a ravager in no raid leaves the wall");
			h.assertTrue(h.getBlockState(rel(2, 0, -4)).is(net.minecraft.world.level.block.Blocks.COBBLESTONE), "the cell is still cobble");
			idle[0].discard();
			var raid = dev.villageheart.siege.TrackedRaids.track(server, BuildingTestSupport.owner(), h.absolutePos(rel(1, 1, 1)));
			ravager[0] = (net.minecraft.world.entity.monster.Ravager) joinRaider(h, raid, rel(2, 0, -5), EntityType.RAVAGER, false);
			sawSix[0] = 0;
			running[0] = false;
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 200, "BreachGoal runs", () -> noteBreach(h, ravager[0], sawSix, running, 6));
		seq = BuildingTestSupport.awaitWithin(h, seq, 900, "the ravager is inside the 2x3 hole", () -> inside(h, ravager[0], sawSix, 6));
		seq = seq.thenExecute(() -> {
			ravager[0].discard();
			for (int x = 2; x <= 3; x++) for (int y = 0; y <= 2; y++) h.setBlock(rel(x, y, -4), net.minecraft.world.level.block.Blocks.COBBLESTONE.defaultBlockState());
			var server = h.getLevel().getServer();
			SiegeDamage.scan(server, VillageClaims.current(server).orElseThrow(), -1);
			var raid = h.getLevel().getRaids().get(VillageClaims.current(server).orElseThrow().threats().raid().orElseThrow().vanillaId());
			ravager[0] = (net.minecraft.world.entity.monster.Ravager) joinRaider(h, raid, rel(2, 0, -5), EntityType.RAVAGER, true);
			h.assertTrue(!ravager[0].getPassengers().isEmpty(), "a pillager is riding");
			sawSix[0] = 0;
			running[0] = false;
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 200, "BreachGoal runs while ridden", () -> noteBreach(h, ravager[0], sawSix, running, 6));
		seq = BuildingTestSupport.awaitWithin(h, seq, 900, "the ridden ravager is inside", () -> inside(h, ravager[0], sawSix, 6));
		seq.thenExecute(() -> VillageTestSupport.done(h));
	}

	/** T-L2-18. A vindicator in the raid chops the gate in two hits of 4 and walks through. Stone it hits takes 2 a hit. */
	@GameTest(environment = "villageheart-gametest:siege_vindicator", structure = YARD, skyAccess = true, maxTicks = 2000)
	public void vindicatorChopsGate(GameTestHelper h) {
		net.minecraft.world.entity.monster.illager.Vindicator[] vindicator = new net.minecraft.world.entity.monster.illager.Vindicator[1];
		int[] saw = new int[1];
		boolean[] running = new boolean[1];
		var seq = BuildTestSupport.layout(h, With.GRID).thenExecute(() -> {
			closedRing(h, true);
			h.spawn(EntityType.VILLAGER, rel(2, 0, 2)).setNoAi(true);
			var server = h.getLevel().getServer();
			var raid = dev.villageheart.siege.TrackedRaids.track(server, BuildingTestSupport.owner(), h.absolutePos(rel(1, 1, 1)));
			vindicator[0] = (net.minecraft.world.entity.monster.illager.Vindicator) joinRaider(h, raid, rel(2, 0, -5), EntityType.VINDICATOR, false);
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 200, "BreachGoal runs at the gate", () -> {
			noteBreach(h, vindicator[0], saw, running, 4);
			h.assertTrue(breachRunning(vindicator[0]) || h.getBlockState(rel(2, 0, -4)).isAir(), "the breach goal is the one running");
			h.assertTrue(!doorRunning(vindicator[0]), "not the wooden-door goal");
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 900, "the vindicator walks through the gate", () -> {
			int d = SiegeDamage.damage(h.getLevel().getServer()).damageAt(packed(h, rel(2, 0, -4)));
			if (d == 4) saw[0] = 4;
			h.assertTrue(saw[0] == 4, "the gate was hit for 4");
			h.assertTrue(h.getBlockState(rel(2, 0, -4)).isAir(), "the gate cell is open");
			h.assertTrue(VillageTestSupport.rel(h, vindicator[0].blockPosition()).getZ() > -4, "through the gate");
			var damage = SiegeDamage.damage(h.getLevel().getServer());
			for (DefenceCell cell : DefenceIndex.of(VillageClaims.current(h.getLevel().getServer()).orElseThrow().defences()).cells()) {
				if (cell.kind() != DefenceKind.STONE) continue;
				int n = damage.damageAt(cell.packed());
				h.assertTrue(n % 2 == 0, "stone takes 2 a hit, got " + n);
			}
		});
		seq.thenExecute(() -> VillageTestSupport.done(h));
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

	private static void noteBreach(GameTestHelper h, net.minecraft.world.entity.Mob mob, int[] saw, boolean[] running, int hit) {
		if (breachRunning(mob)) running[0] = true;
		int d = SiegeDamage.damage(h.getLevel().getServer()).damageAt(packed(h, rel(2, 0, -4)));
		if (d == hit) saw[0] = hit;
		h.assertTrue(running[0] || saw[0] == hit || h.getBlockState(rel(2, 0, -4)).isAir(), "BreachGoal has run");
	}

	private static void inside(GameTestHelper h, net.minecraft.world.entity.Mob mob, int[] saw, int hit) {
		int d = SiegeDamage.damage(h.getLevel().getServer()).damageAt(packed(h, rel(2, 0, -4)));
		if (d == hit) saw[0] = hit;
		h.assertTrue(saw[0] == hit, "the first hit was " + hit + ", saw " + saw[0]);
		for (int x = 2; x <= 3; x++) for (int y = 0; y <= 2; y++) {
			h.assertTrue(h.getBlockState(rel(x, y, -4)).isAir(), "hole " + x + "," + y);
		}
		h.assertTrue(VillageTestSupport.rel(h, mob.blockPosition()).getZ() > -4, "inside " + VillageTestSupport.rel(h, mob.blockPosition()));
	}

	private static void closedRing(GameTestHelper h, boolean gate) {
		for (int x = -6; x <= 10; x++) for (int z = -6; z <= 10; z++) h.setBlock(rel(x, -1, z), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
		BuildTestSupport.stock(h, new ItemStack(Items.COBBLESTONE, 256), new ItemStack(Items.OAK_FENCE_GATE, 3));
		DefenceTestSupport.startLine(h, "wall_segment", rel(-4, -1, 8), rel(8, -1, 8));
		DefenceTestSupport.startLine(h, "wall_segment", rel(-4, -1, -4), rel(-4, -1, 8));
		DefenceTestSupport.startLine(h, "wall_segment", rel(8, -1, -4), rel(8, -1, 8));
		if (gate) {
			DefenceTestSupport.startLine(h, "wall_segment", rel(-4, -1, -4), rel(-1, -1, -4));
			DefenceTestSupport.startLine(h, "gate", rel(0, -1, -4), rel(4, -1, -4));
			DefenceTestSupport.startLine(h, "wall_segment", rel(5, -1, -4), rel(8, -1, -4));
		} else {
			DefenceTestSupport.startLine(h, "wall_segment", rel(-4, -1, -4), rel(8, -1, -4));
		}
		DefenceTestSupport.advanceAll(h);
	}

	private static net.minecraft.world.entity.monster.Ravager spawnRavager(GameTestHelper h, BlockPos at) {
		var level = h.getLevel();
		var ravager = EntityType.RAVAGER.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
		BlockPos abs = h.absolutePos(at);
		ravager.setPos(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
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
		raider.setPos(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
		raid.joinRaid(level, 1, raider, abs, false);
		raider.setPos(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
		raider.setPersistenceRequired();
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
