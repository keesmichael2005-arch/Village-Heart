package dev.villageheart.gametest;

import static dev.villageheart.gametest.GuardTestSupport.BARRACKS;
import static dev.villageheart.gametest.GuardTestSupport.floor;
import static dev.villageheart.gametest.GuardTestSupport.hireGuard;
import static dev.villageheart.gametest.WorkerTestSupport.begin;
import static dev.villageheart.gametest.WorkerTestSupport.end;

import dev.villageheart.building.Buildings;
import dev.villageheart.core.Tier;
import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.farm.CropKind;
import dev.villageheart.core.room.Pos;
import dev.villageheart.core.room.RoomContents;
import dev.villageheart.core.room.RoomScan;
import dev.villageheart.core.siege.ThreatWarnings;
import dev.villageheart.gametest.raid.RaidStart;
import dev.villageheart.raid.ThreatMessages;
import dev.villageheart.raid.VillageBell;
import dev.villageheart.siege.LureSlots;
import dev.villageheart.siege.ZombieSiege;
import dev.villageheart.worker.WorkerBrain;
import dev.villageheart.worker.guard.GuardActivities;
import dev.villageheart.worker.shelter.ShelterHold;
import dev.villageheart.world.VillageClaims;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * Layer 2 for the owner's shelter decision of 2026-11-11: when a raid or a zombie siege starts the village bell is rung, a title is
 * shown, and EVERY villager in the claim goes indoors (hired or not). Guards that are not training and not wounded stay out and fight.
 * Hired workers go back to work when it is over, and unhired villagers go back to their day.
 */
public class ThreatShelterGameTests {
	private static final String YARD = BuildingTestSupport.YARD;

	private record Note(ServerPlayer player, String key, boolean title) {}

	/** Installs the recording sink. It must run after {@code BuildingTestSupport.begin}, whose reset puts the real sink back. */
	private static void listen(List<Note> notes) {
		ThreatMessages.setSinkForTest(new ThreatMessages.Sink() {
			@Override
			public void chat(ServerPlayer player, String key) { notes.add(new Note(player, key, false)); }

			@Override
			public void title(ServerPlayer player, String key) { notes.add(new Note(player, key, true)); }
		});
	}

	private static long titles(List<Note> notes, String key) {
		return notes.stream().filter(n -> n.title && n.key.equals(key) && n.player == BuildingTestSupport.owner()).count();
	}

	/**
	 * A harvesting farmer and two unhired villagers standing outside, 12 to 25 blocks from a House with two beds. The raid starts: the
	 * bell rings once, the owner sees the title, all three are in the House within 900 ticks and still there 200 ticks later (vanilla's
	 * meeting point would pull the unhired ones out again). The raid stops: the farmer is back at the field and nobody is held.
	 */
	@GameTest(environment = "villageheart-gametest:threat_shelter_everyone", structure = YARD, skyAccess = true, maxTicks = 9000)
	public void raidSendsEveryVillagerIndoors(GameTestHelper h) {
		begin(h);
		BlockPos house = new BlockPos(18, 0, 4);
		BlockPos store = new BlockPos(18, 0, 16);
		BlockPos farm = WorkerTestSupport.ORIGIN_A;
		Villager[] farmer = new Villager[1];
		Villager[] plain = new Villager[2];
		RoomScan[] scan = new RoomScan[1];
		Raid[] raid = new Raid[1];
		List<Note> notes = new ArrayList<>();
		GameTestSequence seq = BuildingTestSupport.begin(h).thenExecute(() -> {
			nearHeart(h);
			listen(notes);
			atTime(h, 3000);
			h.getLevel().getGameRules().set(GameRules.ADVANCE_TIME, false, h.getLevel().getServer());
			LumberTestSupport.grass(h, 0, 0, 31, 31);
			WorkerTestSupport.farmhouse(h, farm, 1);
			BuildingTestSupport.room(h, house, 5, 4);
			WorkerTestSupport.bed(h, BuildingTestSupport.inside(house, 0, 0, 1));
			WorkerTestSupport.bed(h, BuildingTestSupport.inside(house, 2, 0, 1));
			h.setBlock(BuildingTestSupport.inside(house, 4, 0, 3), Blocks.TORCH);
			BuildingTestSupport.room(h, store, 5, 4);
			h.setBlock(BuildingTestSupport.inside(store, 0, 0, 3), Blocks.CHEST);
			h.setBlock(BuildingTestSupport.inside(store, 4, 0, 3), Blocks.CHEST);
			h.setBlock(BuildingTestSupport.inside(store, 2, 0, 3), Blocks.TORCH);
			BuildingTestSupport.placeMarker(h, BuildingType.FARMHOUSE, BuildingTestSupport.markerPos(farm, 5), Direction.NORTH);
			BuildingTestSupport.placeMarker(h, BuildingType.HOUSE, BuildingTestSupport.markerPos(house, 5), Direction.NORTH);
			BuildingTestSupport.placeMarker(h, BuildingType.STORAGE, BuildingTestSupport.markerPos(store, 5), Direction.NORTH);
			FarmTestSupport.field(h, 8, 14, 12, 17, CropKind.WHEAT, 7, false);
		});
		seq = seq.thenWaitUntil(() -> {
			BuildingTestSupport.assertStatus(h, BuildingTestSupport.markerPos(farm, 5), dev.villageheart.block.MarkerStatus.VALID);
			BuildingTestSupport.assertStatus(h, BuildingTestSupport.markerPos(house, 5), dev.villageheart.block.MarkerStatus.VALID);
			BuildingTestSupport.assertStatus(h, BuildingTestSupport.markerPos(store, 5), dev.villageheart.block.MarkerStatus.VALID);
		});
		seq = village(h, seq).thenExecute(() -> {
			farmer[0] = FarmTestSupport.hire(h, new BlockPos(6, 1, 16));
			farmer[0].getInventory().addItem(new ItemStack(Items.IRON_HOE));
			var houses = Buildings.valid(h.getLevel().getServer(), BuildingType.HOUSE);
			h.assertTrue(houses.size() == 1 && houses.get(0).latestScan().isPresent(), "the house has a cached scan");
			scan[0] = houses.get(0).latestScan().get();
			Pos p = scan[0].positions(RoomContents.BED).get(0);
			farmer[0].getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(h.getLevel().dimension(), new BlockPos(p.x(), p.y(), p.z())));
			plain[0] = WorkerTestSupport.spawnVillager(h, new BlockPos(10, 1, 24));
			plain[1] = WorkerTestSupport.spawnVillager(h, new BlockPos(28, 1, 10));
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 800, "the farmer is harvesting outdoors", () -> {
			h.assertTrue(farmer[0].getBrain().isActive(Activity.WORK), "farmer activity " + farmer[0].getBrain().getActiveNonCoreActivity().orElse(null));
			h.assertTrue(h.getLevel().canSeeSky(farmer[0].blockPosition()), "farmer outdoors at " + farmer[0].blockPosition());
		});
		seq = seq.thenExecute(() -> {
			for (Villager v : plain) h.assertTrue(!scan[0].contains(new Pos(v.getBlockX(), v.getBlockY(), v.getBlockZ())), "an unhired villager starts outside");
			raid[0] = start(h);
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 100, "the bell rang once and the owner sees the title", () -> {
			RaidStart.holdWaves(raid[0]);
			h.assertValueEqual(1, VillageBell.rings, "bell rings");
			h.assertTrue(VillageBell.lastBell != null && VillageBell.lastBell.equals(h.absolutePos(BuildingTestSupport.BELL)), "the village bell was the one rung");
			h.assertValueEqual(1L, titles(notes, ThreatWarnings.RAID_TITLE.key()), "titles for the owner");
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 900, "everyone is in the house", () -> {
			RaidStart.holdWaves(raid[0]);
			inside(h, farmer[0], scan[0], "farmer");
			inside(h, plain[0], scan[0], "unhired 1");
			inside(h, plain[1], scan[0], "unhired 2");
		});
		seq = seq.thenExecuteAfter(200, () -> {
			RaidStart.holdWaves(raid[0]);
			inside(h, farmer[0], scan[0], "farmer later");
			inside(h, plain[0], scan[0], "unhired 1 later");
			inside(h, plain[1], scan[0], "unhired 2 later");
			h.assertValueEqual(1, VillageBell.rings, "no second ring");
			raid[0].stop();
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 300, "the farmer is back at work and nobody is held", () -> {
			h.assertTrue(farmer[0].getBrain().isActive(Activity.WORK), "farmer activity " + farmer[0].getBrain().getActiveNonCoreActivity().orElse(null));
			for (Villager v : plain) h.assertTrue(!v.getBrain().isActive(WorkerBrain.SHELTER), "unhired still sheltering");
			h.assertValueEqual(0, ShelterHold.count(), "held brains");
		});
		seq.thenExecute(() -> finish(h));
	}

	/** Guards that are not training and not wounded stay out and fight: on duty, awake, never in the shelter. The unhired villager hides. */
	@GameTest(environment = "villageheart-gametest:threat_shelter_guards", structure = YARD, skyAccess = true, maxTicks = 5000)
	public void raidGuardsStayOutAndUnhiredHide(GameTestHelper h) {
		begin(h);
		BlockPos house = new BlockPos(18, 0, 4);
		Villager[] guards = new Villager[2];
		Villager[] plain = new Villager[1];
		RoomScan[] scan = new RoomScan[1];
		Raid[] raid = new Raid[1];
		BlockPos[] marker = new BlockPos[1];
		GameTestSequence seq = BuildingTestSupport.begin(h).thenExecute(() -> {
			nearHeart(h);
			floor(h);
			atTime(h, 3000);
			h.getLevel().getGameRules().set(GameRules.ADVANCE_TIME, false, h.getLevel().getServer());
			BuildingTestSupport.setTier(h, Tier.VILLAGE);
			marker[0] = BuildingTestSupport.room(h, BARRACKS, 6, 4);
			for (int i = 0; i < 2; i++) {
				h.setBlock(BuildingTestSupport.inside(BARRACKS, i, 0, 0), Blocks.GRINDSTONE.defaultBlockState());
				WorkerTestSupport.bed(h, BuildingTestSupport.inside(BARRACKS, i, 0, 2));
			}
			h.setBlock(BuildingTestSupport.inside(BARRACKS, 5, 0, 0), Blocks.CHEST.defaultBlockState());
			h.setBlock(BuildingTestSupport.inside(BARRACKS, 5, 0, 1), Blocks.TORCH.defaultBlockState());
			BuildingTestSupport.placeMarker(h, BuildingType.BARRACKS, marker[0], Direction.NORTH);
			BuildingTestSupport.room(h, house, 5, 4);
			WorkerTestSupport.bed(h, BuildingTestSupport.inside(house, 0, 0, 1));
			h.setBlock(BuildingTestSupport.inside(house, 4, 0, 3), Blocks.TORCH);
			BuildingTestSupport.placeMarker(h, BuildingType.HOUSE, BuildingTestSupport.markerPos(house, 5), Direction.NORTH);
		});
		seq = seq.thenWaitUntil(() -> {
			BuildingTestSupport.assertStatus(h, marker[0], dev.villageheart.block.MarkerStatus.VALID);
			BuildingTestSupport.assertStatus(h, BuildingTestSupport.markerPos(house, 5), dev.villageheart.block.MarkerStatus.VALID);
		});
		seq = village(h, seq).thenExecute(() -> {
			for (int i = 0; i < 2; i++) guards[i] = hireGuard(h, BuildingTestSupport.inside(BARRACKS, i, 0, 1), Optional.empty());
			scan[0] = Buildings.valid(h.getLevel().getServer(), BuildingType.HOUSE).get(0).latestScan().orElseThrow();
			plain[0] = WorkerTestSupport.spawnVillager(h, new BlockPos(24, 1, 20));
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 800, "both guards are asleep on their night shift", () -> {
			for (Villager g : guards) h.assertTrue(g.isSleeping(), "guard asleep " + g.getBrain().getActiveActivities());
		});
		seq = seq.thenExecute(() -> raid[0] = start(h));
		seq = BuildingTestSupport.awaitWithin(h, seq, 200, "both guards are up, on duty and not in the shelter", () -> {
			RaidStart.holdWaves(raid[0]);
			for (Villager g : guards) {
				h.assertTrue(g.getBrain().isActive(GuardActivities.DUTY) && !g.isSleeping(), "guard duty " + g.getBrain().getActiveActivities());
				h.assertTrue(!g.getBrain().isActive(WorkerBrain.SHELTER) && !ShelterHold.holds(g.getBrain()), "guard must not shelter");
			}
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 900, "the unhired villager is in the house", () -> {
			RaidStart.holdWaves(raid[0]);
			inside(h, plain[0], scan[0], "unhired");
			for (Villager g : guards) h.assertTrue(g.getBrain().isActive(GuardActivities.DUTY), "guard still on duty");
		});
		seq.thenExecute(() -> {
			raid[0].stop();
			finish(h);
		});
	}

	/**
	 * A zombie siege starts: the bell rings, "The dead are coming!" is the title, and an unhired villager outside goes indoors.
	 * The siege's one zombie has its AI off and is invulnerable, so it stays where it spawned and the siege keeps running for the test.
	 */
	@GameTest(environment = "villageheart-gametest:threat_shelter_siege", structure = YARD, skyAccess = true, maxTicks = 5000)
	public void siegeRingsBellAndSendsVillagersIndoors(GameTestHelper h) {
		begin(h);
		BlockPos house = new BlockPos(18, 0, 4);
		Villager[] plain = new Villager[1];
		RoomScan[] scan = new RoomScan[1];
		List<Note> notes = new ArrayList<>();
		GameTestSequence seq = safeDay(h, BuildingTestSupport.begin(h)).thenExecute(() -> {
			nearHeart(h);
			listen(notes);
			h.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
			ensureDay(h);
			LumberTestSupport.grass(h, 0, 0, 31, 31);
			BuildingTestSupport.room(h, house, 5, 4);
			WorkerTestSupport.bed(h, BuildingTestSupport.inside(house, 0, 0, 1));
			h.setBlock(BuildingTestSupport.inside(house, 4, 0, 3), Blocks.TORCH);
			BuildingTestSupport.placeMarker(h, BuildingType.HOUSE, BuildingTestSupport.markerPos(house, 5), Direction.NORTH);
		});
		seq = seq.thenWaitUntil(() -> BuildingTestSupport.assertStatus(h, BuildingTestSupport.markerPos(house, 5), dev.villageheart.block.MarkerStatus.VALID));
		seq = seq.thenExecute(() -> {
			scan[0] = Buildings.valid(h.getLevel().getServer(), BuildingType.HOUSE).get(0).latestScan().orElseThrow();
			plain[0] = WorkerTestSupport.spawnVillager(h, new BlockPos(24, 1, 20));
			ZombieSiegeGameTests.pad(h, ZombieSiegeGameTests.heartRel(h), 44);
		});
		// A chunk forced this tick is not entity-ticking yet, and a siege that finds no ticking cell on its first try does not try again.
		seq = seq.thenIdle(100).thenExecute(() -> ZombieSiege.startNow(h.getLevel().getServer(), 1));
		seq = BuildingTestSupport.awaitWithin(h, seq, 200, "the siege has its zombie", () -> {
			h.assertTrue(VillageClaims.current(h.getLevel().getServer()).orElseThrow().threats().siege().isPresent(), "no siege record yet");
			List<Zombie> zombies = h.getLevel().getEntitiesOfClass(Zombie.class, new net.minecraft.world.phys.AABB(h.absolutePos(BlockPos.ZERO)).inflate(160),
					z -> z.getTags().contains(LureSlots.SIEGE_TAG));
			h.assertTrue(!zombies.isEmpty(), "no siege zombie");
			// No AI keeps it where it spawned, and invulnerable keeps the daylight from burning it: a dead zombie ends the siege and with it the shelter.
			for (Zombie z : zombies) {
				z.setNoAi(true);
				z.setInvulnerable(true);
			}
		});
		seq = seq.thenExecute(() -> {
			h.assertValueEqual(1, VillageBell.rings, "the siege rang the bell once");
			h.assertTrue(VillageBell.lastBell != null && VillageBell.lastBell.equals(h.absolutePos(BuildingTestSupport.BELL)), "the village bell");
			h.assertValueEqual(1L, titles(notes, ThreatWarnings.SIEGE_TITLE.key()), "the siege title");
			h.assertValueEqual(0L, titles(notes, ThreatWarnings.RAID_TITLE.key()), "no raid title");
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 900, "the unhired villager is in the house", () -> inside(h, plain[0], scan[0], "unhired"));
		seq.thenExecute(() -> {
			for (Zombie z : h.getLevel().getEntitiesOfClass(Zombie.class, new net.minecraft.world.phys.AABB(h.absolutePos(BlockPos.ZERO)).inflate(160),
					z -> z.getTags().contains(LureSlots.SIEGE_TAG))) z.discard();
			finish(h);
		});
	}

	/**
	 * No valid House and no valid room is in reach (the old village outside the ring, say). The unhired villager still goes to its own bed
	 * rather than stand in the open: the planner's last resort. It stays there while the raid lasts.
	 */
	@GameTest(environment = "villageheart-gametest:threat_shelter_own_bed", structure = YARD, skyAccess = true, maxTicks = 3000)
	public void unhiredVillagerWithNoHouseGoesToItsOwnBed(GameTestHelper h) {
		begin(h);
		Villager[] plain = new Villager[1];
		Raid[] raid = new Raid[1];
		BlockPos bed = new BlockPos(24, 1, 24);
		GameTestSequence seq = BuildingTestSupport.begin(h).thenExecute(() -> {
			nearHeart(h);
			ensureDay(h);
			LumberTestSupport.grass(h, 0, 0, 31, 31);
			WorkerTestSupport.bed(h, bed.below());
			BlockPos bell = h.absolutePos(BuildingTestSupport.BELL);
			h.getLevel().getPoiManager().take(type -> type.is(net.minecraft.tags.PoiTypeTags.VILLAGE), (type, pos) -> pos.equals(bell), bell, 1);
		});
		seq = village(h, seq).thenExecute(() -> {
			plain[0] = WorkerTestSupport.spawnVillager(h, new BlockPos(6, 1, 6));
			plain[0].getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(h.getLevel().dimension(), h.absolutePos(bed.below())));
			raid[0] = start(h);
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 700, "the villager is at its own bed", () -> {
			RaidStart.holdWaves(raid[0]);
			h.assertTrue(plain[0].getBrain().isActive(WorkerBrain.SHELTER), "activity " + plain[0].getBrain().getActiveNonCoreActivity().orElse(null));
			double d = plain[0].position().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(h.absolutePos(bed.below())));
			h.assertTrue(d <= 2.5, "distance to the bed " + d);
		});
		seq.thenExecute(() -> {
			raid[0].stop();
			finish(h);
		});
	}

	private static void inside(GameTestHelper h, Villager v, RoomScan scan, String who) {
		Pos feet = new Pos(v.getBlockX(), v.getBlockY(), v.getBlockZ());
		h.assertTrue(scan.contains(feet) || scan.contains(new Pos(feet.x(), feet.y() + 1, feet.z())), who + " is not in the house, at " + VillageTestSupport.rel(h, v.blockPosition()) + " activity "
				+ v.getBrain().getActiveNonCoreActivity().orElse(null) + " walk " + v.getBrain().getMemory(MemoryModuleType.WALK_TARGET).map(w -> w.getTarget().currentBlockPosition().toShortString()).orElse("-")
				+ " home " + v.getBrain().getMemory(MemoryModuleType.HOME).map(g -> g.pos().toShortString()).orElse("-") + " has shelter " + dev.villageheart.worker.shelter.ShelterRuntime.hasTarget(v));
		h.assertTrue(!h.getLevel().canSeeSky(v.blockPosition()), who + ": the roof hides the sky at " + feet);
	}

	/** Vanilla only starts a raid at a spot it already calls a village. */
	private static GameTestSequence village(GameTestHelper h, GameTestSequence seq) {
		BlockPos at = new BlockPos(3, 0, 1);
		return BuildingTestSupport.awaitWithin(h, seq, 200, "vanilla treats the spot by the Heart as a village", () ->
				h.assertTrue(h.getLevel().isVillage(h.absolutePos(at)), "not a village yet"));
	}

	private static Raid start(GameTestHelper h) {
		h.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		Raid raid = RaidStart.start(BuildingTestSupport.owner(), h.absolutePos(new BlockPos(3, 0, 1)), 1);
		RaidStart.holdWaves(raid);
		return raid;
	}

	private static void finish(GameTestHelper h) {
		h.getLevel().getGameRules().set(GameRules.ADVANCE_TIME, true, h.getLevel().getServer());
		VillageTestSupport.done(h);
		end(h);
	}
	/** These tests need daylight for a few hundred ticks, not a frozen clock: move the clock only when the day is about to end or has not begun. */
	private static void ensureDay(GameTestHelper h) {
		long t = Math.floorMod(h.getLevel().getDayTime(), 24000L);
		if (t < 2000 || t > 8000) atTime(h, 3000);
	}

	/** A time of day on the current day, so the change never looks like a run of missed dawns to a test running beside this one. */
	private static void atTime(GameTestHelper h, int tick) {
		long now = h.getLevel().getDayTime();
		long base = Math.floorDiv(now, 24000L) * 24000L;
		h.getLevel().setDayTime(base + Math.floorMod(tick, 24000));
	}

	/**
	 * Roll days and the siege's end read the game-time day. A test that crosses a multiple of 24000 game ticks would see its pending
	 * threat belong to yesterday, so it waits for a game day with at least 3000 ticks left. The wait is at most 3000 ticks.
	 */
	private static GameTestSequence safeDay(GameTestHelper h, GameTestSequence seq) {
		return seq.thenWaitUntil(() -> h.assertTrue(Math.floorMod(h.getLevel().getGameTime(), 24000L) < 21000, "waiting for the next game day to begin"));
	}


	/** Threat lines go to the players near the Heart, and the claiming owner is placed wherever the test world spawns them. */
	private static void nearHeart(GameTestHelper h) {
		BlockPos p = h.absolutePos(new BlockPos(3, 1, 3));
		BuildingTestSupport.owner().teleportTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
	}
}
