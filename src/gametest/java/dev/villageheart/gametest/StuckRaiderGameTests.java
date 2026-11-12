package dev.villageheart.gametest;

import static dev.villageheart.gametest.WorkerTestSupport.begin;
import static dev.villageheart.gametest.WorkerTestSupport.end;

import dev.villageheart.core.Tier;
import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.siege.ThreatWarnings;
import dev.villageheart.gametest.raid.RaidStart;
import dev.villageheart.raid.StuckRaiderSystem;
import dev.villageheart.raid.ThreatMessages;
import dev.villageheart.world.VillageClaims;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * Layer 2 for stuck raiders (owner, 2026-11-11): the only removal of a raider. The waits are shortened with
 * {@link StuckRaiderSystem#setLimitsForTest} (the 12000 and 1200 defaults are pinned by the unit test).
 * Nothing in these tests moves, pulls or kills a raider.
 */
public class StuckRaiderGameTests {
	private static final String YARD = BuildingTestSupport.YARD;
	private static final BlockPos HOUSE = new BlockPos(18, 0, 4);
	private static final int STUCK = 100;
	private static final int GRACE = 60;

	private record Note(ServerPlayer player, String key) {}

	/** Installs the recording sink. It must run after {@code BuildingTestSupport.begin}, whose reset puts the real sink back. */
	private static void listen(List<Note> notes) {
		ThreatMessages.setSinkForTest(new ThreatMessages.Sink() {
			@Override
			public void chat(ServerPlayer player, String key) { notes.add(new Note(player, key)); }

			@Override
			public void title(ServerPlayer player, String key) {}
		});
	}

	private static long heard(List<Note> notes, String key) {
		return notes.stream().filter(n -> n.player == BuildingTestSupport.owner() && n.key.equals(key)).count();
	}

	/** Seals a 1x1x2 hole with cobblestone around the cell at {@code feet} so nothing can walk out of it. */
	private static void seal(GameTestHelper h, BlockPos feet) {
		for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = -1; dy <= 2; dy++) {
			if (dx == 0 && dz == 0 && dy >= 0 && dy <= 1) continue;
			h.setBlock(feet.offset(dx, dy, dz), Blocks.COBBLESTONE);
		}
	}

	private static Raid start(GameTestHelper h) {
		h.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		Raid raid = RaidStart.start(BuildingTestSupport.owner(), h.absolutePos(new BlockPos(3, 0, 1)), 1);
		RaidStart.holdWaves(raid);
		return raid;
	}

	/** Vanilla counts a spot as a village only while one of its POIs is occupied. Nobody is hired here, so the bell is taken. */
	private static GameTestSequence village(GameTestHelper h, GameTestSequence seq) {
		seq = seq.thenExecute(() -> {
			BlockPos bell = h.absolutePos(BuildingTestSupport.BELL);
			h.getLevel().getPoiManager().take(type -> type.is(net.minecraft.tags.PoiTypeTags.VILLAGE), (type, pos) -> pos.equals(bell), bell, 1);
		});
		return BuildingTestSupport.awaitWithin(h, seq, 200, "vanilla treats the spot by the Heart as a village", () ->
				h.assertTrue(h.getLevel().isVillage(h.absolutePos(new BlockPos(3, 0, 1))), "not a village yet"));
	}

	private static GameTestSequence scene(GameTestHelper h) {
		return BuildingTestSupport.begin(h).thenExecute(() -> {
			nearHeart(h);
			BuildingTestSupport.setTier(h, Tier.VILLAGE);
			LumberTestSupport.grass(h, 0, 0, 63, 63);
			// Vanilla starts a raid only where it already sees a village: a valid House with a bed, beside the bell.
			BuildingTestSupport.room(h, HOUSE, 5, 4);
			WorkerTestSupport.bed(h, BuildingTestSupport.inside(HOUSE, 0, 0, 1));
			h.setBlock(BuildingTestSupport.inside(HOUSE, 4, 0, 3), Blocks.TORCH);
			BuildingTestSupport.placeMarker(h, BuildingType.HOUSE, BuildingTestSupport.markerPos(HOUSE, 5), Direction.NORTH);
			StuckRaiderSystem.setLimitsForTest(STUCK, GRACE);
		}).thenWaitUntil(() -> BuildingTestSupport.assertStatus(h, BuildingTestSupport.markerPos(HOUSE, 5), dev.villageheart.block.MarkerStatus.VALID));
	}

	/**
	 * A pillager of the raid sealed in a hole, 40 blocks from anyone. After {@code STUCK} ticks it glows and the owner is told; after
	 * {@code GRACE} more it flees: gone, with the chat line, not counted as a guard kill, and the raid no longer counts it.
	 */
	@GameTest(environment = "villageheart-gametest:stuck_raider_flees", structure = YARD, skyAccess = true, maxTicks = 3000)
	public void sealedRaiderGlowsThenFlees(GameTestHelper h) {
		begin(h);
		Raider[] raider = new Raider[1];
		Raid[] raid = new Raid[1];
		List<Note> notes = new ArrayList<>();
		int[] t0 = new int[1];
		GameTestSequence seq = village(h, scene(h)).thenExecute(() -> {
			listen(notes);
			BlockPos feet = new BlockPos(28, 1, 28);
			seal(h, feet);
			raid[0] = start(h);
			Raider pillager = h.spawn(EntityType.PILLAGER, feet);
			raid[0].joinRaid(h.getLevel(), 1, pillager, pillager.blockPosition(), false);
			raider[0] = pillager;
			t0[0] = (int) h.getTick();
		});
		h.onEachTick(() -> {
			if (raid[0] != null && !raid[0].isStopped()) RaidStart.holdWaves(raid[0]);
		});
		seq = seq.thenIdle(STUCK - 40).thenExecute(() -> {
			h.assertTrue(!raider[0].hasEffect(MobEffects.GLOWING), "no glow before the wait is over");
			h.assertValueEqual(0L, heard(notes, ThreatWarnings.STUCK_GLOW.key()), "no warning yet");
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 120, "the raider glows and the owner is told", () -> {
			h.assertTrue(raider[0].hasEffect(MobEffects.GLOWING), "not glowing yet");
			h.assertValueEqual(1L, heard(notes, ThreatWarnings.STUCK_GLOW.key()), "hiding line");
		});
		seq = seq.thenExecute(() -> {
			h.assertTrue(!raider[0].isRemoved(), "it only glows first");
			h.assertValueEqual(0L, heard(notes, ThreatWarnings.STUCK_FLED.key()), "not fled yet");
		});
		// The grace is a wait, not a pause: for GRACE - 20 ticks after the glow is seen (the check runs every 20 ticks, so the glow is at
		// most one tick old here and the flight cannot come before GRACE) the raider is still there, still glowing, and nothing has fled.
		// A flight that ignores the grace happens at the next check, 20 ticks on, and fails here.
		seq = seq.thenExecuteFor(GRACE - 20, () -> {
			h.assertTrue(!raider[0].isRemoved(), "the raider fled inside the grace period");
			h.assertTrue(raider[0].hasEffect(MobEffects.GLOWING), "the raider stopped glowing inside the grace period");
			h.assertValueEqual(0L, heard(notes, ThreatWarnings.STUCK_FLED.key()), "fled line inside the grace period");
			h.assertValueEqual(0, StuckRaiderSystem.fled, "fled count inside the grace period");
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, GRACE + 60, "the raider flees", () -> {
			h.assertTrue(raider[0].isRemoved(), "still there");
			h.assertValueEqual(1L, heard(notes, ThreatWarnings.STUCK_FLED.key()), "fled line");
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 120, "the raid no longer counts it, and it was not a kill", () -> {
			RaidStart.holdWaves(raid[0]);
			// The raid drops a removed raider on its next 20-tick update. Tests run side by side, and another test's reset stops every raid
			// within 160 blocks (RaidStart.stopAll); a stopped raid never updates again, and then there is nothing left to count.
			h.assertTrue(raid[0].isStopped() || raid[0].getTotalRaidersAlive() == 0, "raiders alive in the raid: " + raid[0].getTotalRaidersAlive());
			var rec = VillageClaims.current(h.getLevel().getServer()).orElseThrow().threats().raid().orElseThrow();
			h.assertValueEqual(0, rec.raidersKilled(), "kills: a fled raider is lost, not killed");
			h.assertValueEqual(1, StuckRaiderSystem.fled, "fled count");
		});
		seq.thenExecute(() -> {
			raid[0].stop();
			VillageTestSupport.done(h);
			end(h);
		});
	}

	/**
	 * A raider that is walking toward the village is never removed. It walks with its own navigation, aimed ten blocks at a time (a
	 * path longer than its follow range is not found), across the yard from one corner toward the Heart, and keeps walking for longer
	 * than the stuck wait plus the grace. The owner stands far from the route so nobody is near it.
	 */
	@GameTest(environment = "villageheart-gametest:stuck_raider_walks", structure = YARD, skyAccess = true, maxTicks = 3000)
	public void walkingRaiderIsNeverRemoved(GameTestHelper h) {
		begin(h);
		Raider[] raider = new Raider[1];
		Raid[] raid = new Raid[1];
		double[] start = new double[1];
		BlockPos heart = new BlockPos(3, 1, 1);
		GameTestSequence seq = village(h, scene(h)).thenExecute(() -> {
			StuckRaiderSystem.setLimitsForTest(40, 40);
			BlockPos far = h.absolutePos(new BlockPos(3, 1, 30));
			BuildingTestSupport.owner().teleportTo(far.getX() + 0.5, far.getY(), far.getZ() + 0.5);
			raid[0] = start(h);
			Raider pillager = h.spawn(EntityType.PILLAGER, new BlockPos(30, 1, 30));
			raid[0].joinRaid(h.getLevel(), 1, pillager, pillager.blockPosition(), false);
			raider[0] = pillager;
			start[0] = Math.sqrt(pillager.blockPosition().distSqr(h.absolutePos(heart)));
		});
		h.onEachTick(() -> {
			if (raider[0] == null || raider[0].isRemoved()) return;
			if (raid[0] != null && !raid[0].isStopped()) RaidStart.holdWaves(raid[0]);
			BlockPos goal = h.absolutePos(new BlockPos(4, 1, 4));
			double dx = goal.getX() + 0.5 - raider[0].getX(), dz = goal.getZ() + 0.5 - raider[0].getZ();
			double len = Math.sqrt(dx * dx + dz * dz);
			if (len < 1.5) return;
			double step = Math.min(10.0, len);
			raider[0].getNavigation().moveTo(raider[0].getX() + dx / len * step, goal.getY(), raider[0].getZ() + dz / len * step, 1.0);
		});
		for (int i = 0; i < 5; i++) {
			seq = seq.thenIdle(20).thenExecute(() -> {
				h.assertTrue(!raider[0].isRemoved(), "a walking raider was removed");
				// The bell resonates for raiders within 48 blocks and makes them glow for 60 ticks: that is vanilla. The stuck rule is the counter.
				h.assertValueEqual(0, StuckRaiderSystem.glowed, "a walking raider was made to glow, at " + VillageTestSupport.rel(h, raider[0].blockPosition()));
				double now = Math.sqrt(raider[0].blockPosition().distSqr(h.absolutePos(heart)));
				h.assertTrue(now < start[0] - 2, "it is walking toward the Heart: " + now + " from " + start[0] + " at " + VillageTestSupport.rel(h, raider[0].blockPosition()));
			});
		}
		seq.thenExecute(() -> {
			h.assertValueEqual(0, StuckRaiderSystem.fled, "nobody fled");
			h.assertValueEqual(0, StuckRaiderSystem.glowed, "nobody glowed");
			raider[0].discard();
			raid[0].stop();
			VillageTestSupport.done(h);
			end(h);
		});
	}

	/** A sealed raider with a villager 8 blocks away is not hiding: it is not glowed or removed, however long it waits. */
	@GameTest(environment = "villageheart-gametest:stuck_raider_found", structure = YARD, skyAccess = true, maxTicks = 3000)
	public void raiderWithAVillagerNearIsNotRemoved(GameTestHelper h) {
		begin(h);
		Raider[] raider = new Raider[1];
		Raid[] raid = new Raid[1];
		GameTestSequence seq = village(h, scene(h)).thenExecute(() -> {
			BlockPos feet = new BlockPos(28, 1, 28);
			seal(h, feet);
			raid[0] = start(h);
			Villager v = WorkerTestSupport.spawnVillager(h, new BlockPos(36, 1, 28));
			v.setNoAi(true);
			Raider pillager = h.spawn(EntityType.PILLAGER, feet);
			raid[0].joinRaid(h.getLevel(), 1, pillager, pillager.blockPosition(), false);
			raider[0] = pillager;
		});
		h.onEachTick(() -> {
			if (raid[0] != null && !raid[0].isStopped()) RaidStart.holdWaves(raid[0]);
		});
		seq = seq.thenIdle(STUCK + GRACE + 100).thenExecute(() -> {
			h.assertTrue(!raider[0].isRemoved(), "the raider with a villager near fled");
			h.assertTrue(!raider[0].hasEffect(MobEffects.GLOWING), "the raider with a villager near glowed");
			h.assertValueEqual(0, StuckRaiderSystem.glowed, "glowed");
			h.assertValueEqual(0, StuckRaiderSystem.fled, "fled");
			raider[0].discard();
			raid[0].stop();
			VillageTestSupport.done(h);
			end(h);
		});
	}

	/** Threat lines go to the players near the Heart, and the claiming owner is placed wherever the test world spawns them. */
	private static void nearHeart(GameTestHelper h) {
		BlockPos p = h.absolutePos(new BlockPos(3, 1, 3));
		BuildingTestSupport.owner().teleportTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
	}
}
