package dev.villageheart.gametest;

import static dev.villageheart.gametest.WorkerTestSupport.begin;
import static dev.villageheart.gametest.WorkerTestSupport.end;

import dev.villageheart.core.Balance;
import dev.villageheart.core.Tier;
import dev.villageheart.core.siege.PendingRaid;
import dev.villageheart.core.siege.ThreatRoll;
import dev.villageheart.core.siege.ThreatWarnings;
import dev.villageheart.raid.ThreatMessages;
import dev.villageheart.world.VillageClaims;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.GameType;

/**
 * Layer 2 for the warning lead-up (owner, 2026-11-11): the ominous chat lines before a random raid and before a zombie siege reach the
 * players in or near the claim at the scheduled times, once each, and nobody far away.
 */
public class ThreatWarningGameTests {
	private static final String YARD = BuildingTestSupport.YARD;

	private record Note(ServerPlayer player, String key, ThreatWarnings.Tone tone) {}

	/** Installs the recording sink. It must run after {@code BuildingTestSupport.begin}, whose reset puts the real sink back. */
	private static void listen(List<Note> notes) {
		ThreatMessages.setSinkForTest(new ThreatMessages.Sink() {
			@Override
			public void chat(ServerPlayer player, String key) { notes.add(new Note(player, key, ThreatWarnings.Tone.NOTICE)); }

			@Override
			public void chat(ServerPlayer player, String key, ThreatWarnings.Tone tone) { notes.add(new Note(player, key, tone)); }

			@Override
			public void title(ServerPlayer player, String key) {}
		});
	}

	/** How many times {@code who} got this line AS A GOLD WARNING (owner, 2026-11-12): a line in any other tone does not count. */
	private static long heard(List<Note> notes, ServerPlayer who, String key) {
		return notes.stream().filter(n -> n.player == who && n.key.equals(key) && n.tone == ThreatWarnings.Tone.WARNING).count();
	}

	private static long heardAny(List<Note> notes, ServerPlayer who) {
		return notes.stream().filter(n -> n.player == who && n.key.startsWith("message.villageheart.warning.")).count();
	}

	/**
	 * A raid is pending to start at tick 11000 of this day. The warning window is the 6000 ticks before it, so the lines are due at
	 * 5000, 7000 and 9000. Nothing before 5000, each line once, and a player 200 blocks away hears none of them.
	 */
	@GameTest(environment = "villageheart-gametest:threat_warn_raid", structure = YARD, skyAccess = true, maxTicks = 3000)
	public void warningsLeadUpToARaid(GameTestHelper h) {
		begin(h);
		ServerPlayer[] far = new ServerPlayer[1];
		List<Note> notes = new ArrayList<>();
		// Tests run side by side and some of them move the shared clock: this one pins it every tick, from the first step to the last.
		long[] pin = {-1};
		h.onEachTick(() -> {
			if (pin[0] >= 0) atTime(h, (int) pin[0]);
		});
		GameTestSequence seq = safeDay(h, BuildingTestSupport.begin(h)).thenExecute(() -> {
			nearHeart(h);
			listen(notes);
			h.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
			BuildingTestSupport.setTier(h, Tier.VILLAGE);
			h.getLevel().getGameRules().set(GameRules.ADVANCE_TIME, false, h.getLevel().getServer());
			pin[0] = 4000;
		}).thenIdle(3).thenExecute(() -> {
			// The clock is pinned and has been for a tick: a threat created now cannot be warned about at the real time of day.
			int day = ThreatRoll.day(h.getLevel().getGameTime());
			VillageClaims.data(h.getLevel().getServer()).update(s -> s.withThreats(s.threats().withPending(Optional.of(new PendingRaid(day, 11000)))));
			far[0] = VillageTestSupport.player(h, "far", GameType.SURVIVAL);
			BlockPos away = h.absolutePos(new BlockPos(3, 1, 400));
			far[0].teleportTo(away.getX() + 0.5, away.getY(), away.getZ() + 0.5);
		}).thenIdle(30);
		seq = seq.thenExecute(() -> h.assertValueEqual(0L, heardAny(notes, BuildingTestSupport.owner()), "before the window opens (tick 4000 < 5000)"));
		seq = seq.thenExecute(() -> pin[0] = 5100).thenIdle(10);
		seq = seq.thenExecute(() -> {
			h.assertValueEqual(1L, heard(notes, BuildingTestSupport.owner(), "message.villageheart.warning.raid.1"), "line 1 at 5000");
			h.assertValueEqual(1L, heardAny(notes, BuildingTestSupport.owner()), "only line 1 so far");
		});
		seq = seq.thenExecute(() -> pin[0] = 6900).thenIdle(10);
		seq = seq.thenExecute(() -> h.assertValueEqual(1L, heardAny(notes, BuildingTestSupport.owner()), "line 2 is not due before 7000, line 1 is not repeated"));
		seq = seq.thenExecute(() -> pin[0] = 7100).thenIdle(10);
		seq = seq.thenExecute(() -> {
			h.assertValueEqual(1L, heard(notes, BuildingTestSupport.owner(), "message.villageheart.warning.raid.2"), "line 2 at 7000");
			h.assertValueEqual(2L, heardAny(notes, BuildingTestSupport.owner()), "two lines so far");
		});
		seq = seq.thenExecute(() -> pin[0] = 9100).thenIdle(10);
		seq = seq.thenExecute(() -> {
			h.assertValueEqual(1L, heard(notes, BuildingTestSupport.owner(), "message.villageheart.warning.raid.3"), "line 3 at 9000");
			h.assertValueEqual(3L, heardAny(notes, BuildingTestSupport.owner()), "three lines in all");
		});
		seq = seq.thenIdle(60).thenExecute(() -> {
			h.assertValueEqual(3L, heardAny(notes, BuildingTestSupport.owner()), "no line is sent twice");
			h.assertValueEqual(0L, heardAny(notes, far[0]), "a player 400 blocks away hears nothing");
			VillageClaims.data(h.getLevel().getServer()).update(s -> s.withThreats(s.threats().withPending(Optional.empty())));
			pin[0] = -1;
			h.getLevel().getGameRules().set(GameRules.ADVANCE_TIME, true, h.getLevel().getServer());
			VillageTestSupport.done(h);
			end(h);
		});
	}

	/** A siege rolled for tonight spawns at 18000. The window opens at the roll (13000): line 1 at 13000, line 2 at 15500, nothing at 18000. */
	@GameTest(environment = "villageheart-gametest:threat_warn_siege", structure = YARD, skyAccess = true, maxTicks = 3000)
	public void warningsLeadUpToASiege(GameTestHelper h) {
		begin(h);
		List<Note> notes = new ArrayList<>();
		// Tests run side by side and some of them move the shared clock: this one pins it every tick, from the first step to the last.
		long[] pin = {-1};
		h.onEachTick(() -> {
			if (pin[0] >= 0) atTime(h, (int) pin[0]);
		});
		GameTestSequence seq = safeDay(h, BuildingTestSupport.begin(h)).thenExecute(() -> {
			nearHeart(h);
			listen(notes);
			h.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
			BuildingTestSupport.setTier(h, Tier.VILLAGE);
			h.getLevel().getGameRules().set(GameRules.ADVANCE_TIME, false, h.getLevel().getServer());
			pin[0] = 12900;
		}).thenIdle(3).thenExecute(() -> {
			int day = ThreatRoll.day(h.getLevel().getGameTime());
			VillageClaims.data(h.getLevel().getServer()).update(s -> s.withThreats(s.threats().withPendingSiegeDay(day)));
		}).thenIdle(30);
		seq = seq.thenExecute(() -> h.assertValueEqual(0L, heardAny(notes, BuildingTestSupport.owner()), "before the roll mark"));
		seq = seq.thenExecute(() -> pin[0] = 13100).thenIdle(10);
		seq = seq.thenExecute(() -> {
			h.assertValueEqual(1L, heard(notes, BuildingTestSupport.owner(), "message.villageheart.warning.siege.1"), "siege line 1");
			h.assertValueEqual(1L, heardAny(notes, BuildingTestSupport.owner()), "one line");
		});
		seq = seq.thenExecute(() -> pin[0] = 15600).thenIdle(10);
		seq = seq.thenExecute(() -> {
			h.assertValueEqual(1L, heard(notes, BuildingTestSupport.owner(), "message.villageheart.warning.siege.2"), "siege line 2");
			h.assertValueEqual(2L, heardAny(notes, BuildingTestSupport.owner()), "two lines in all");
			h.assertValueEqual(0L, notes.stream().filter(n -> n.key.contains(".raid.")).count(), "no raid line before a siege");
		});
		seq = seq.thenIdle(60).thenExecute(() -> {
			h.assertValueEqual(2L, heardAny(notes, BuildingTestSupport.owner()), "no repeat");
			VillageClaims.data(h.getLevel().getServer()).update(s -> s.withThreats(s.threats().withPendingSiegeDay(dev.villageheart.core.siege.ThreatState.NO_DAY)));
			pin[0] = -1;
			h.getLevel().getGameRules().set(GameRules.ADVANCE_TIME, true, h.getLevel().getServer());
			VillageTestSupport.done(h);
			end(h);
		});
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
