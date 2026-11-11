package dev.villageheart.gametest;

import static dev.villageheart.gametest.BuildTestSupport.YARD;

import dev.villageheart.build.PaletteStates;
import dev.villageheart.core.Rank;
import dev.villageheart.core.blueprint.Blueprint;
import dev.villageheart.core.blueprint.BlueprintParams;
import dev.villageheart.core.blueprint.Blueprints;
import dev.villageheart.core.blueprint.PaletteEntry;
import dev.villageheart.core.blueprint.Placement;
import dev.villageheart.core.defence.DefenceCell;
import dev.villageheart.core.defence.DefenceIndex;
import dev.villageheart.core.defence.DefenceKind;
import dev.villageheart.core.defence.DefenceSite;
import dev.villageheart.core.heart.AlertCode;
import dev.villageheart.core.heart.HeartSnapshotBuilder;
import dev.villageheart.core.room.Pos;
import dev.villageheart.core.siege.SiegeHits;
import dev.villageheart.heart.HeartFacts;
import dev.villageheart.siege.BreachNotices;
import dev.villageheart.siege.SiegeDamage;
import dev.villageheart.siege.TrackedRaids;
import dev.villageheart.stock.Stockpiles;
import dev.villageheart.worker.WorkerAttachments;
import dev.villageheart.worker.build.WallRepair;
import dev.villageheart.worker.WorkerBrain;
import dev.villageheart.world.VillageClaims;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Layer 2 repair tests (docs/plans/M25.md 5.2, T-L2-28, 29, 30, 44). Each test has its own environment. A missing write
 * (the block, the stock, the saved damage, the work clock) fails the test.
 */
public class SiegeRepairGameTests {
	private static BlockPos rel(int x, int y, int z) { return new BlockPos(x, y, z); }

	/** T-L2-28. Cobblestone and one fence gate close four holes and both cracks, on the clock. */
	@GameTest(environment = "villageheart-gametest:siege_repair_order", structure = YARD, skyAccess = true, maxTicks = 4000)
	public void builderRepairsWithStorageCobblestone(GameTestHelper h) {
		Villager[] builder = new Villager[1];
		Damage[] damage = new Damage[1];
		long[] start = new long[1];
		List<String> chat = new ArrayList<>();
		GameTestSequence seq = BuildTestSupport.layout(h, BuildTestSupport.With.STANDARD).thenExecute(() -> {
			BreachNotices.setSinkForTest(chat::add);
			damage[0] = prepareGate(h, true);
			builder[0] = BuildTestSupport.hireBuilder(h);
			start[0] = h.getTick();
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 2400, "the breaches and cracks are repaired", () -> assertRepaired(h, damage[0]));
		seq.thenExecute(() -> {
			var server = h.getLevel().getServer();
			h.assertTrue(stored(h, Items.COBBLESTONE) == 6, "cobblestone " + stored(h, Items.COBBLESTONE));
			h.assertTrue(storedGates(h) == 0, "fence gates " + storedGates(h));
			h.assertTrue(builder[0].getInventory().countItem(Items.COBBLESTONE) == 0, "the builder still holds cobble");
			h.assertTrue(builder[0].getInventory().countItem(Items.OAK_FENCE_GATE) == 0, "the builder still holds a gate");
			int xp = WorkerAttachments.get(builder[0]).orElseThrow().xp();
			h.assertTrue(xp >= 1, "repairs grant block-placed xp, got " + xp);
			int work = WallRepair.workTicks(builder[0].getUUID());
			int walk = WallRepair.walkTicks(builder[0].getUUID());
			h.assertTrue(work >= 6 * 20, "work ticks " + work + " (six actions)");
			h.assertTrue(h.getTick() - start[0] >= work + walk, "elapsed " + (h.getTick() - start[0]) + " work " + work + " walk " + walk);
			h.assertTrue(WallRepair.saw(builder[0].getUUID(), "Repairing"), "status never said Repairing: " + WallRepair.line(builder[0].getUUID()));
			String name = VillageClaims.current(server).orElseThrow().ownerName();
			long breaches = chat.stream().filter(s -> s.equals("The wall of " + name + " was breached")).count();
			long repaired = chat.stream().filter(s -> s.equals("The walls of " + name + " are repaired")).count();
			h.assertTrue(breaches == 1, "breach lines " + chat);
			h.assertTrue(repaired == 1, "repaired lines " + chat);
			HeartFacts.invalidate();
			var snap = HeartSnapshotBuilder.build(HeartFacts.collect(server).orElseThrow(), Rank.OWNER);
			h.assertTrue(snap.overview().alerts().stream().noneMatch(a -> a.code() == AlertCode.DEFENCE_DAMAGED), "alert still up: " + snap.overview().alerts());
			var saved = VillageClaims.current(server).orElseThrow().threats().damage();
			h.assertTrue(saved.isEmpty(), "saved damage " + saved);
			VillageTestSupport.done(h);
		});
	}

	/** T-L2-29. With an empty stock the holes stay open and the status names the fence gate. Depositing one closes that hole. */
	@GameTest(environment = "villageheart-gametest:siege_repair_short", structure = YARD, skyAccess = true, maxTicks = 4000)
	public void breachStaysOpenWithoutMaterial(GameTestHelper h) {
		Villager[] builder = new Villager[1];
		Damage[] damage = new Damage[1];
		GameTestSequence seq = BuildTestSupport.layout(h, BuildTestSupport.With.STANDARD).thenExecute(() -> {
			damage[0] = prepareGate(h, false);
			builder[0] = BuildTestSupport.hireBuilder(h);
		});
		seq = seq.thenIdle(1200).thenExecute(() -> {
			var server = h.getLevel().getServer();
			SiegeDamage.flush(server);
			var saved = VillageClaims.current(server).orElseThrow().threats().damage();
			h.assertTrue(saved.breached().size() == 4, "still breached " + saved.breached().size());
			h.assertTrue(saved.cracked().size() == 2, "still cracked " + saved.cracked().size());
			h.assertTrue(h.getBlockState(relPos(h, damage[0].gate)).isAir(), "the gate filled with no material");
			HeartFacts.invalidate();
			var snap = HeartSnapshotBuilder.build(HeartFacts.collect(server).orElseThrow(), Rank.OWNER);
			boolean line = snap.overview().alerts().stream().anyMatch(a -> a.text().english().equals("Walls damaged: 4 broken, 2 cracked"));
			h.assertTrue(line, "snapshot " + snap.overview().alerts());
			h.assertTrue(WallRepair.line(builder[0].getUUID()).equals("Repairs need 1 more fence gate"), "status " + WallRepair.line(builder[0].getUUID()));
			BuildTestSupport.stock(h, new ItemStack(Items.OAK_FENCE_GATE, 1));
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 1200, "the deposited gate is placed", () -> {
			BlockState got = h.getBlockState(relPos(h, damage[0].gate));
			h.assertTrue(got.equals(expected(h, damage[0].gate)), "gate " + got + " vs " + expected(h, damage[0].gate));
		});
		seq.thenExecute(() -> {
			h.assertTrue(h.getBlockState(relPos(h, damage[0].stoneHoles.get(0))).isAir(), "stone was repaired with no cobblestone");
			h.assertTrue(storedGates(h) == 0, "the gate was not taken from Storage");
			VillageTestSupport.done(h);
		});
	}

	/** T-L2-30. A tracked raid with its waves held: the builder shelters and touches nothing, then starts once the raid stops. */
	@GameTest(environment = "villageheart-gametest:siege_repair_raid", structure = YARD, skyAccess = true, maxTicks = 4000)
	public void noRepairDuringThreat(GameTestHelper h) {
		Villager[] builder = new Villager[1];
		Damage[] damage = new Damage[1];
		GameTestSequence seq = BuildTestSupport.layout(h, BuildTestSupport.With.STANDARD).thenExecute(() -> {
			damage[0] = prepareGate(h, true);
			builder[0] = BuildTestSupport.hireBuilder(h);
			claimBell(h);
			var raid = TrackedRaids.track(h.getLevel().getServer(), BuildingTestSupport.owner(), h.absolutePos(BuildingTestSupport.BELL));
			TrackedRaids.holdWaves(raid);
		});
		seq = seq.thenIdle(600).thenExecute(() -> {
			h.assertTrue(builder[0].getBrain().isActive(WorkerBrain.SHELTER), "activity " + builder[0].getBrain().getActiveNonCoreActivity().orElse(null));
			h.assertTrue(stored(h, Items.COBBLESTONE) == 10, "cobble moved during the raid: " + stored(h, Items.COBBLESTONE));
			h.assertTrue(storedGates(h) == 1, "a gate moved during the raid");
			h.assertTrue(h.getBlockState(relPos(h, damage[0].gate)).isAir(), "the gate was repaired during the raid");
			h.assertTrue(WallRepair.workTicks(builder[0].getUUID()) == 0, "work ticks during the raid " + WallRepair.workTicks(builder[0].getUUID()));
			TrackedRaids.stop(h.getLevel().getServer());
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 600, "repairs start after the raid stops", () -> {
			h.assertTrue(WallRepair.saw(builder[0].getUUID(), "Repairing"), "status " + WallRepair.line(builder[0].getUUID()));
			h.assertTrue(storedGates(h) == 0, "the fence gate is still in Storage");
		});
		seq.thenExecute(() -> VillageTestSupport.done(h));
	}

	/**
	 * T-L2-44. A hurt guard does not eat or refill while a zombie stands 8 blocks away. The guard's brain is held
	 * ({@code setNoAi}) so a patrol cannot walk them out of that bubble; feed does not use the brain. Once that zombie is 30
	 * blocks off and the guard is off duty, the ration grows and then the health does.
	 */
	@GameTest(environment = "villageheart-gametest:guards_ration_pin", structure = YARD, skyAccess = true, maxTicks = 2500)
	public void rationDoesNotRefillBesideAnEnemy(GameTestHelper h) {
		WorkerTestSupport.begin(h);
		Villager[] guard = new Villager[1];
		Zombie[] zombie = new Zombie[1];
		float[] health = new float[1];
		GameTestSequence seq = GuardTestSupport.scene(h, 1).thenExecute(() -> {
			BuildingTestSupport.room(h, GuardTestSupport.STORE, 5, 4);
			h.setBlock(BuildingTestSupport.inside(GuardTestSupport.STORE, 0, 0, 3), Blocks.CHEST);
			h.setBlock(BuildingTestSupport.inside(GuardTestSupport.STORE, 4, 0, 3), Blocks.CHEST);
			BuildingTestSupport.placeMarker(h, dev.villageheart.core.building.BuildingType.STORAGE, GuardTestSupport.storeMarker(), net.minecraft.core.Direction.NORTH);
		}).thenWaitUntil(() -> BuildingTestSupport.assertStatus(h, GuardTestSupport.storeMarker(), dev.villageheart.block.MarkerStatus.VALID));
		seq = seq.thenExecute(() -> {
			LumberTestSupport.putInChest(h, BuildingTestSupport.inside(GuardTestSupport.STORE, 0, 0, 3), 0, new ItemStack(Items.BREAD, 8));
			GuardTestSupport.setTime(h, 2000);
			guard[0] = GuardTestSupport.hireGuard(h, GuardTestSupport.spawnSpot(), Optional.of(dev.villageheart.core.guard.Shift.DAY));
			guard[0].setHealth(15.0F);
			dev.villageheart.worker.guard.GuardAttachments.set(guard[0], dev.villageheart.worker.guard.GuardAttachments.get(guard[0]).withRation(2));
			h.assertTrue(guard[0].getMaxHealth() == 20.0F, "max health " + guard[0].getMaxHealth());
			// A duty patrol walks out of the 10-block bubble within 100 ticks, and then a refill is the rule working.
			// The pin is "does not eat beside an enemy", so the guard stays put. Feed does not use the brain.
			guard[0].setNoAi(true);
			guard[0].getNavigation().stop();
			h.getLevel().getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
			zombie[0] = h.spawn(EntityType.ZOMBIE, GuardTestSupport.spawnSpot().offset(8, 0, 0));
			zombie[0].setNoAi(true);
			zombie[0].setPersistenceRequired();
			zombie[0].setTarget(null);
			guard[0].setTarget(null);
			zombie[0].setHealth(100.0F);
			var knockback = zombie[0].getAttribute(Attributes.KNOCKBACK_RESISTANCE);
			if (knockback != null) knockback.setBaseValue(1.0);
		});
		seq = seq.thenIdle(100).thenExecute(() -> {
			int ration = dev.villageheart.worker.guard.GuardAttachments.get(guard[0]).ration();
			double apart = guard[0].distanceTo(zombie[0]);
			h.assertTrue(zombie[0].isAlive(), "the zombie is still there");
			h.assertTrue(ration == 2, "ration beside the zombie " + ration + " at " + apart);
			h.assertTrue(guard[0].getHealth() == 15.0F, "health beside the zombie " + guard[0].getHealth());
			// (30, y, 30) is 30 blocks from the spawn and still inside the yard. +30 x walks out of the structure.
			BlockPos home = h.absolutePos(GuardTestSupport.spawnSpot());
			guard[0].teleportTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5);
			BlockPos away = h.absolutePos(new BlockPos(30, GuardTestSupport.spawnSpot().getY(), 30));
			zombie[0].teleportTo(away.getX() + 0.5, away.getY(), away.getZ() + 0.5);
			zombie[0].setTarget(null);
			guard[0].setTarget(null);
			GuardTestSupport.setTime(h, 13200);
			health[0] = guard[0].getHealth();
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 40, "the ration grows once the zombie is 30 blocks away", () -> {
			int ration = dev.villageheart.worker.guard.GuardAttachments.get(guard[0]).ration();
			h.assertTrue(ration > 2, "ration " + ration);
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 200, "health rises after the ration grows", () ->
				h.assertTrue(guard[0].getHealth() > health[0], "health " + guard[0].getHealth() + " from " + health[0]));
		seq.thenExecute(() -> {
			VillageTestSupport.done(h);
			WorkerTestSupport.end(h);
		});
	}

	private static void assertRepaired(GameTestHelper h, Damage damage) {
		var server = h.getLevel().getServer();
		for (DefenceCell hole : damage.stoneHoles) {
			BlockState got = h.getBlockState(relPos(h, hole));
			h.assertTrue(got.equals(expected(h, hole)), "stone " + hole.pos() + " is " + got);
		}
		BlockState gate = h.getBlockState(relPos(h, damage.gate));
		h.assertTrue(gate.equals(expected(h, damage.gate)), "gate " + gate + " vs " + expected(h, damage.gate));
		var live = SiegeDamage.damage(server);
		h.assertTrue(live.damageAt(damage.heavy.packed()) == 0, "heavy crack " + live.damageAt(damage.heavy.packed()));
		h.assertTrue(live.damageAt(damage.light.packed()) == 0, "light crack " + live.damageAt(damage.light.packed()));
		h.assertTrue(!live.isBreached(damage.gate.packed()), "the gate is still breached");
	}

	private static Damage prepareGate(GameTestHelper h, boolean repairStock) {
		h.getLevel().setDayTime(2000);
		for (int x = 8; x <= 28; x++) for (int z = 0; z <= 24; z++) h.setBlock(rel(x, -1, z), Blocks.STONE.defaultBlockState());
		BuildTestSupport.stock(h, new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.OAK_FENCE_GATE, 8));
		DefenceTestSupport.startLine(h, "gate", rel(14, -1, 18), rel(18, -1, 18));
		DefenceTestSupport.advanceAll(h);
		Damage damage = crackAndBreach(h);
		replaceStock(h, repairStock ? new ItemStack[] {new ItemStack(Items.COBBLESTONE, 10), new ItemStack(Items.OAK_FENCE_GATE, 1)} : new ItemStack[0]);
		h.assertTrue(stored(h, Items.COBBLESTONE) == (repairStock ? 10 : 0), "stock cobble " + stored(h, Items.COBBLESTONE));
		h.assertTrue(storedGates(h) == (repairStock ? 1 : 0), "stock gates " + storedGates(h));
		return damage;
	}

	private static Damage crackAndBreach(GameTestHelper h) {
		var server = h.getLevel().getServer();
		DefenceSite site = VillageClaims.current(server).orElseThrow().defences().stream().filter(s -> s.blueprintId().equals("gate")).findFirst().orElseThrow();
		List<DefenceCell> stone = new ArrayList<>();
		List<DefenceCell> gates = new ArrayList<>();
		for (DefenceCell cell : DefenceIndex.of(List.of(site)).cells()) {
			if (cell.kind() == DefenceKind.STONE) stone.add(cell);
			else if (cell.kind() == DefenceKind.GATE) gates.add(cell);
		}
		Comparator<DefenceCell> order = Comparator.comparingInt((DefenceCell c) -> c.pos().y()).thenComparingInt(c -> c.pos().x()).thenComparingInt(c -> c.pos().z());
		stone.sort(order);
		gates.sort(order);
		h.assertTrue(stone.size() >= 5 && gates.size() >= 1, "stone " + stone.size() + " gates " + gates.size());
		Damage damage = new Damage();
		damage.heavy = stone.get(3);
		damage.light = stone.get(4);
		crack(h, damage.heavy, 8);
		crack(h, damage.light, 3);
		h.assertTrue(SiegeDamage.damage(server).damageAt(damage.heavy.packed()) == 8, "heavy setup " + SiegeDamage.damage(server).damageAt(damage.heavy.packed()));
		h.assertTrue(SiegeDamage.damage(server).damageAt(damage.light.packed()) == 3, "light setup " + SiegeDamage.damage(server).damageAt(damage.light.packed()));
		for (int i = 0; i < 3; i++) {
			breach(h, stone.get(i));
			damage.stoneHoles.add(stone.get(i));
		}
		damage.gate = gates.get(0);
		breach(h, damage.gate);
		h.assertTrue(SiegeDamage.damage(server).breached().size() == 4, "breaches " + SiegeDamage.damage(server).breached().size());
		return damage;
	}

	private static void crack(GameTestHelper h, DefenceCell cell, int hits) {
		BlockPos pos = new BlockPos(cell.pos().x(), cell.pos().y(), cell.pos().z());
		for (int i = 0; i < hits; i++) {
			var result = SiegeDamage.hit(h.getLevel().getServer(), h.getLevel(), pos, SiegeHits.Attacker.ZOMBIE);
			h.assertTrue(result == SiegeDamage.Result.APPLIED, "hit " + i + " on " + cell.pos() + " was " + result);
		}
	}

	private static void breach(GameTestHelper h, DefenceCell cell) {
		BlockPos pos = new BlockPos(cell.pos().x(), cell.pos().y(), cell.pos().z());
		h.setBlock(VillageTestSupport.rel(h, pos), Blocks.AIR.defaultBlockState());
		SiegeDamage.recordBreach(h.getLevel().getServer(), pos);
	}

	private static BlockState expected(GameTestHelper h, DefenceCell cell) {
		DefenceSite site = VillageClaims.current(h.getLevel().getServer()).orElseThrow().defence(cell.siteId()).orElseThrow();
		Blueprint bp = Blueprints.resolve(site.blueprintId(), new BlueprintParams(Math.max(0, site.length()))).orElseThrow();
		Placement placement = new Placement(new Pos(site.ground().x(), site.ground().y(), site.ground().z()), site.rotation());
		PaletteEntry entry = placement.entryAt(bp, cell.pos());
		net.minecraft.world.level.block.Block substitute = entry.material() != null && entry.material().isTag() ? Blocks.OAK_FENCE_GATE : null;
		return PaletteStates.resolve(entry, site.rotation(), substitute);
	}

	private static BlockPos relPos(GameTestHelper h, DefenceCell cell) {
		return VillageTestSupport.rel(h, new BlockPos(cell.pos().x(), cell.pos().y(), cell.pos().z()));
	}

	private static void replaceStock(GameTestHelper h, ItemStack... stacks) {
		BlockPos[] chests = {BuildingTestSupport.inside(BuildTestSupport.STORE, 0, 0, 3), BuildingTestSupport.inside(BuildTestSupport.STORE, 4, 0, 3)};
		for (BlockPos chest : chests) {
			var container = StockpileTestSupport.container(h, chest);
			for (int i = 0; i < container.getContainerSize(); i++) container.setItem(i, ItemStack.EMPTY);
			container.setChanged();
		}
		if (stacks.length > 0) BuildTestSupport.stock(h, stacks);
	}

	private static int stored(GameTestHelper h, net.minecraft.world.item.Item item) {
		return (int) Stockpiles.count(h.getLevel().getServer(), item);
	}

	private static int storedGates(GameTestHelper h) {
		return (int) Stockpiles.count(h.getLevel().getServer(), ItemTags.FENCE_GATES);
	}

	private static void claimBell(GameTestHelper h) {
		BlockPos bell = h.absolutePos(BuildingTestSupport.BELL);
		var taken = h.getLevel().getPoiManager().take(type -> type.is(net.minecraft.tags.PoiTypeTags.VILLAGE), (type, pos) -> pos.equals(bell), bell, 1);
		h.assertTrue(taken.isPresent(), "the bell's meeting point was free to claim");
	}

	private static final class Damage {
		DefenceCell gate;
		DefenceCell heavy;
		DefenceCell light;
		final List<DefenceCell> stoneHoles = new ArrayList<>();
	}
}
