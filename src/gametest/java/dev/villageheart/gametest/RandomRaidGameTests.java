package dev.villageheart.gametest;

import static dev.villageheart.gametest.GuardTestSupport.BARRACKS;
import static dev.villageheart.gametest.GuardTestSupport.floor;
import static dev.villageheart.gametest.GuardTestSupport.hireGuard;
import static dev.villageheart.gametest.GuardTestSupport.setTime;

import dev.villageheart.core.Balance;
import dev.villageheart.core.Rank;
import dev.villageheart.core.Tier;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.heart.HeartSnapshotBuilder;
import dev.villageheart.core.raid.RaidNotices;
import dev.villageheart.core.raid.RaidOrigin;
import dev.villageheart.core.raid.RaidOutcome;
import dev.villageheart.core.raid.RaidPlan;
import dev.villageheart.core.siege.Threat;
import dev.villageheart.core.siege.ThreatRules;
import dev.villageheart.gametest.mixin.RaidTestAccess;
import dev.villageheart.mixin.RaidGroups;
import dev.villageheart.raid.ThreatScheduler;
import dev.villageheart.raid.VillageRaids;
import dev.villageheart.siege.SiegeGamerules;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.world.VillageCodecs;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * Layer 2 for random raids (M25 step 16): a raid waits until a player is near, a strong village gets extra raiders,
 * a victory pays once, and a young village is capped.
 */
public class RandomRaidGameTests {
	private static final String YARD = BuildingTestSupport.YARD;
	private final List<String> lines = new ArrayList<>();

	/** T-L2-31. Far away the roll only stores a pending raid. Coming back warns at once and starts it. */
	@GameTest(environment = "villageheart-gametest:random_raid_loaded", structure = YARD, skyAccess = true, maxTicks = 9000)
	public void randomRaidOnlyWhenLoaded(GameTestHelper h) {
		int[] warnings = new int[1];
		Raid[] other = new Raid[1];
		boolean[] away = {false};
		h.onEachTick(() -> {
			if (!away[0]) return;
			h.assertTrue(state(h).threats().raid().isEmpty(), "a raid started while the player was away");
		});
		GameTestSequence seq = yard(h, Balance.RAID_FULL_GUARDS).thenExecute(() -> {
			prepare(h, 500);
			place(h, 200, 1, 0);
			ThreatRules.graceAndGapForTest(0, 0);
			ThreatRules.forceRoll();
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 40, "the roll is stored and no raid has started", () -> {
			h.assertTrue(state(h).threats().pending().isPresent(), "no pending raid");
			h.assertTrue(state(h).threats().raid().isEmpty(), "a raid started too early");
		});
		seq = seq.thenExecute(() -> away[0] = true).thenIdle(2400).thenExecute(() -> {
			away[0] = false;
			h.assertTrue(state(h).threats().pending().isPresent(), "the pending raid expired");
			h.assertTrue(state(h).threats().raid().isEmpty(), "a raid started while the player was 200 blocks away");
			listen();
			clock(h, Balance.PENDING_START_MAX);
			place(h, 3, 1, 1);
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 40, "the warning goes out as the wait begins", () ->
				h.assertTrue(lines.stream().anyMatch(t -> t.contains("gathering")), "no warning: " + lines));
		seq = BuildingTestSupport.awaitWithin(h, seq, Balance.RAID_LOADED_TICKS + 80, "the random raid starts", () -> {
			var raid = state(h).threats().raid();
			h.assertTrue(raid.isPresent() && raid.get().origin() == RaidOrigin.RANDOM, "origin " + raid.map(r -> r.origin().name()).orElse("none"));
			Raid live = h.getLevel().getRaids().get(raid.get().vanillaId());
			h.assertTrue(live != null && live.getRaidOmenLevel() == raid.get().plan().omen(), "omen");
		});
		seq = seq.thenExecute(() -> {
			stopTracked(h);
			prepare(h, 500);
			place(h, 200, 1, 0);
			ThreatRules.forceRoll();
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 40, "a second roll is pending", () ->
				h.assertTrue(state(h).threats().pending().isPresent(), "no second pending raid"));
		seq = seq.thenExecute(() -> {
			warnings[0] = ThreatScheduler.warnings;
			h.getLevel().getGameRules().set(GameRules.RAIDS, false, h.getLevel().getServer());
			clock(h, Balance.PENDING_START_MAX);
			place(h, 3, 1, 1);
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, Balance.RAID_LOADED_TICKS + 80, "raids off drops the pending raid", () -> {
			h.assertTrue(state(h).threats().raid().isEmpty(), "a raid started with the rule off");
			h.assertTrue(state(h).threats().pending().isEmpty(), "the pending raid was kept");
			h.assertTrue(ThreatScheduler.warnings > warnings[0], "no warning was logged");
		});
		seq = seq.thenExecute(() -> {
			h.getLevel().getGameRules().set(GameRules.RAIDS, true, h.getLevel().getServer());
			prepare(h, 500);
			place(h, 200, 1, 0);
			ThreatRules.forceRoll();
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 40, "a third roll is pending", () ->
				h.assertTrue(state(h).threats().pending().isPresent(), "no third pending raid"));
		seq = seq.thenExecute(() -> placeFarVillage(h));
		seq = BuildingTestSupport.awaitWithin(h, seq, 40, "the far bell is a village", () -> {
			claimFarBell(h);
			h.assertTrue(h.getLevel().isVillage(farBell(h)), "not a village yet");
		});
		seq = seq.thenExecute(() -> {
			other[0] = startFarRaid(h);
			int omen = other[0].getRaidOmenLevel();
			h.assertValueEqual(4, omen, "the other raid's omen");
			clock(h, Balance.PENDING_START_MAX);
			place(h, 3, 1, 1);
		});
		seq = seq.thenIdle(1200).thenExecute(() -> {
			h.assertTrue(state(h).threats().pending().isPresent(), "the nearby raid did not keep ours pending");
			h.assertTrue(state(h).threats().raid().isEmpty(), "the other raid was tracked");
			h.assertValueEqual(4, other[0].getRaidOmenLevel(), "the other raid was re-levelled");
			VillageTestSupport.done(h);
		});
	}

	/** T-L2-32. Threat 8 adds four vindicators on wave 2, and omen 3 means six groups. */
	@GameTest(environment = "villageheart-gametest:random_raid_extras", structure = YARD, skyAccess = true, maxTicks = 4000)
	public void strongVillageGetsExtraRaiders(GameTestHelper h) {
		Raid[] raid = new Raid[1];
		GameTestSequence seq = yard(h, Balance.RAID_FULL_GUARDS).thenExecute(() -> {
			Threat.forceForTest(8);
			beginRaid(h);
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, Balance.RAID_LOADED_TICKS + 80, "the strong raid starts", () -> {
			var rec = state(h).threats().raid();
			h.assertTrue(rec.isPresent() && rec.get().origin() == RaidOrigin.RANDOM, "not random");
			raid[0] = h.getLevel().getRaids().get(rec.get().vanillaId());
			h.assertTrue(raid[0] != null, "the raid is gone");
			h.assertValueEqual(3, raid[0].getRaidOmenLevel(), "omen");
			h.assertValueEqual(6, dev.villageheart.core.raid.RaidFacts.wavesTotal(((RaidGroups) raid[0]).villageheart$numGroups(), 3), "groups");
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 2500, "wave 2 has the four extra vindicators", () -> {
			releaseUntil(h, raid[0], 2);
			h.assertTrue(raid[0].getGroupsSpawned() >= 2, "wave " + raid[0].getGroupsSpawned());
			int extras = 0;
			for (Raider raider : raid[0].getAllRaiders()) {
				h.assertTrue(raider.getCurrentRaid() == raid[0], "a raider left the raid");
				if (raider.getType() == EntityType.VINDICATOR && raider.getTags().contains(ThreatScheduler.EXTRA_TAG)) extras++;
			}
			h.assertValueEqual(4, extras, "extra vindicators");
		});
		seq.thenExecute(() -> VillageTestSupport.done(h));
	}

	/** T-L2-33. A random victory pays the T-L1-20 figure once. A stop and a Bad Omen victory pay nothing. */
	@GameTest(environment = "villageheart-gametest:random_raid_reward", structure = YARD, skyAccess = true, maxTicks = 3000)
	public void victoryPaysReward(GameTestHelper h) {
		Raid[] raid = new Raid[1];
		GameTestSequence seq = yard(h, 1);
		seq = BuildingTestSupport.awaitWithin(h, seq, 200, "the bell is a village", () ->
				h.assertTrue(h.getLevel().isVillage(h.absolutePos(BuildingTestSupport.BELL)), "not a village"));
		seq = seq.thenExecute(() -> {
			listen();
			withTreasury(h, 10);
			raid[0] = dev.villageheart.gametest.raid.RaidStart.start(BuildingTestSupport.owner(), h.absolutePos(BuildingTestSupport.BELL), 1);
			var id = h.getLevel().getRaids().getId(raid[0]);
			h.assertTrue(id.isPresent(), "no id");
			VillageRaids.mark(id.getAsInt(), RaidOrigin.RANDOM, new RaidPlan(1, 0, 0, 0, false));
			((RaidTestAccess) raid[0]).villageheart$setRaidCooldownTicks(1_000_000);
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 40, "the random raid is tracked", () ->
				h.assertTrue(state(h).threats().raid().isPresent() && state(h).threats().raid().get().origin() == RaidOrigin.RANDOM, "not tracked"));
		seq = seq.thenExecute(() -> {
			for (int i = 0; i < 2; i++) {
				Raider extra = EntityType.VINDICATOR.create(h.getLevel(), EntitySpawnReason.EVENT);
				extra.addTag(ThreatScheduler.EXTRA_TAG);
				raid[0].joinRaid(h.getLevel(), 1, extra, h.absolutePos(new BlockPos(4, 1, 4)), false);
				extra.kill(h.getLevel());
			}
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 20, "the two extras are counted", () ->
				h.assertValueEqual(2, state(h).threats().raid().orElseThrow().extraRaidersKilled(), "extras"));
		seq = seq.thenExecute(() -> finishVanilla(h, raid[0]));
		seq = BuildingTestSupport.awaitWithin(h, seq, 200, "the victory pays 16", () -> {
			var last = state(h).threats().lastRaid();
			h.assertTrue(last.isPresent() && last.get().outcome() == RaidOutcome.VICTORY, "outcome");
			h.assertValueEqual(26, state(h).treasury(), "treasury");
			h.assertValueEqual(1, (int) lines.stream().filter(t -> t.startsWith("Victory!")).count(), "chats " + lines);
		});
		seq = seq.thenExecute(() -> {
			lines.clear();
			int before = state(h).treasury();
			for (Raider raider : List.copyOf(raid[0].getAllRaiders())) raider.discard();
			raid[0].stop();
			raid[0] = dev.villageheart.gametest.raid.RaidStart.start(BuildingTestSupport.owner(), h.absolutePos(BuildingTestSupport.BELL), 1);
			((RaidTestAccess) raid[0]).villageheart$setRaidCooldownTicks(1_000_000);
			h.assertValueEqual(before, state(h).treasury(), "a new raid changed the treasury");
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 40, "the second raid is tracked", () ->
				h.assertTrue(state(h).threats().raid().isPresent(), "not tracked"));
		seq = seq.thenExecute(() -> raid[0].stop());
		seq = BuildingTestSupport.awaitWithin(h, seq, 40, "stopping pays nothing", () -> {
			h.assertTrue(state(h).threats().lastRaid().isPresent() && state(h).threats().lastRaid().get().outcome() == RaidOutcome.STOPPED, "not stopped");
			h.assertValueEqual(26, state(h).treasury(), "a stop paid");
			h.assertValueEqual(0, (int) lines.stream().filter(t -> t.startsWith("Victory!")).count(), "a stop chatted");
		});
		seq = seq.thenExecute(() -> {
			VillageRaids.clearForTests();
			for (Raider raider : List.copyOf(raid[0].getAllRaiders())) raider.discard();
			raid[0].stop();
			raid[0] = dev.villageheart.gametest.raid.RaidStart.start(BuildingTestSupport.owner(), h.absolutePos(BuildingTestSupport.BELL), 1);
			VillageRaids.clearForTests();
			((RaidTestAccess) raid[0]).villageheart$setRaidCooldownTicks(1_000_000);
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 40, "the unmarked raid is a bad omen raid", () ->
				h.assertTrue(state(h).threats().raid().isPresent() && state(h).threats().raid().get().origin() == RaidOrigin.BAD_OMEN, "origin"));
		seq = seq.thenExecute(() -> finishVanilla(h, raid[0]));
		seq = BuildingTestSupport.awaitWithin(h, seq, 200, "a bad omen victory pays nothing", () -> {
			h.assertTrue(state(h).threats().lastRaid().orElseThrow().outcome() == RaidOutcome.VICTORY, "not a victory");
			h.assertValueEqual(26, state(h).treasury(), "bad omen paid");
			VillageTestSupport.done(h);
		});
	}

	/** T-L2-41. Saving the village between the spawn and the next look does not add the four vindicators twice. */
	@GameTest(environment = "villageheart-gametest:random_raid_reload", structure = YARD, skyAccess = true, maxTicks = 4000)
	public void extrasOnceAcrossReload(GameTestHelper h) {
		Raid[] raid = new Raid[1];
		GameTestSequence seq = yard(h, Balance.RAID_FULL_GUARDS).thenExecute(() -> {
			Threat.forceForTest(8);
			beginRaid(h);
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, Balance.RAID_LOADED_TICKS + 80, "the raid starts", () -> {
			var rec = state(h).threats().raid();
			h.assertTrue(rec.isPresent(), "not started");
			raid[0] = h.getLevel().getRaids().get(rec.get().vanillaId());
		});
		seq = seq.thenExecute(() -> ((RaidTestAccess) raid[0]).villageheart$setGroupsSpawned(2));
		seq = BuildingTestSupport.awaitWithin(h, seq, 40, "wave 2 gained four extras", () -> h.assertValueEqual(4, extras(raid[0]), "extras"));
		seq = seq.thenExecute(() -> {
			var json = VillageCodecs.VILLAGE_STATE.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, state(h)).getOrThrow();
			VillageState back = VillageCodecs.VILLAGE_STATE.parse(com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow();
			VillageClaims.data(h.getLevel().getServer()).update(s -> back);
		});
		seq = seq.thenIdle(40).thenExecute(() -> {
			h.assertValueEqual(4, extras(raid[0]), "the reload spawned them again");
			h.assertValueEqual(2, state(h).threats().raid().orElseThrow().extrasForWave(), "the wave was forgotten");
			stopTracked(h);
			Threat.forceForTest(8);
			VillageRaids.clearForTests();
			raid[0] = dev.villageheart.gametest.raid.RaidStart.start(BuildingTestSupport.owner(), h.absolutePos(BuildingTestSupport.BELL), 1);
			VillageRaids.clearForTests();
			((RaidTestAccess) raid[0]).villageheart$setGroupsSpawned(2);
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 40, "a bad omen raid gets no extras", () -> {
			h.assertTrue(state(h).threats().raid().isPresent() && state(h).threats().raid().get().origin() == RaidOrigin.BAD_OMEN, "origin");
			h.assertValueEqual(0, extras(raid[0]), "bad omen extras");
		});
		seq.thenExecute(() -> VillageTestSupport.done(h));
	}

	/** T-L2-45. Two guards get two waves and 8 emeralds. A town with two guards is capped too (M26-R). No guards still get one wave. */
	@GameTest(environment = "villageheart-gametest:random_raid_early", structure = YARD, skyAccess = true, maxTicks = 8000)
	public void earlyRandomRaidIsSmall(GameTestHelper h) {
		Raid[] raid = new Raid[1];
		int[] treasury = new int[1];
		GameTestSequence seq = yard(h, 0).thenExecute(() -> beginRaid(h));
		seq = BuildingTestSupport.awaitWithin(h, seq, Balance.RAID_LOADED_TICKS + 80, "no guards is still one wave", () -> {
			raid[0] = live(h);
			h.assertValueEqual(1, ((RaidGroups) raid[0]).villageheart$numGroups(), "groups");
			h.assertTrue(state(h).threats().raid().orElseThrow().plan().early(), "not early");
		});
		seq = seq.thenExecute(() -> {
			stopTracked(h);
			for (int i = 0; i < 2; i++) hireGuard(h, BuildingTestSupport.inside(BARRACKS, i, 0, 1), Optional.empty());
			beginRaid(h);
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, Balance.RAID_LOADED_TICKS + 80, "two guards cap the raid at two waves", () -> {
			raid[0] = live(h);
			var rec = state(h).threats().raid().orElseThrow();
			h.assertValueEqual(2, ((RaidGroups) raid[0]).villageheart$numGroups(), "groups");
			h.assertValueEqual(1, raid[0].getRaidOmenLevel(), "omen");
			h.assertTrue(rec.plan().early(), "not early");
			h.assertValueEqual(0, extras(raid[0]), "a young raid has extras");
			var saved = Raid.MAP_CODEC.encoder().encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, raid[0]).getOrThrow();
			Raid loaded = Raid.MAP_CODEC.decoder().decode(net.minecraft.nbt.NbtOps.INSTANCE, saved).getOrThrow().getFirst();
			h.assertValueEqual(2, ((RaidGroups) loaded).villageheart$numGroups(), "group_count survived a save");
		});
		seq = seq.thenExecute(() -> treasury[0] = state(h).treasury());
		seq = BuildingTestSupport.awaitWithin(h, seq, 800, "wave 1 is 1/2", () -> {
			releaseUntil(h, raid[0], 1);
			h.assertTrue(raid[0].getGroupsSpawned() >= 1, "no wave yet");
			var facts = dev.villageheart.heart.HeartFacts.collect(h.getLevel().getServer());
			var alerts = HeartSnapshotBuilder.build(facts.orElseThrow(), Rank.OWNER).overview().alerts();
			h.assertTrue(!alerts.isEmpty() && alerts.get(0).text().english().contains("1/2"), "alert " + (alerts.isEmpty() ? "none" : alerts.get(0).text().english()));
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 2500, "the capped raid is won", () -> {
			releaseUntil(h, raid[0], 2);
			if (raid[0].getGroupsSpawned() >= 2) {
				for (Raider raider : List.copyOf(raid[0].getAllRaiders())) raider.kill(h.getLevel());
			}
			var last = state(h).threats().lastRaid();
			h.assertTrue(last.isPresent() && last.get().outcome() == RaidOutcome.VICTORY, "not won");
			h.assertValueEqual(treasury[0] + 8, state(h).treasury(), "reward");
		});
		seq = seq.thenExecute(() -> {
			stopTracked(h);
			BuildingTestSupport.setTier(h, Tier.TOWN);
			beginRaid(h);
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, Balance.RAID_LOADED_TICKS + 80, "a town with two guards is still capped (M26-R)", () -> {
			raid[0] = live(h);
			h.assertValueEqual(2, ((RaidGroups) raid[0]).villageheart$numGroups(), "town groups");
			h.assertTrue(state(h).threats().raid().orElseThrow().plan().early(), "a town with two guards is not early");
			VillageTestSupport.done(h);
		});
	}

	private static GameTestSequence yard(GameTestHelper h, int guards) {
		BlockPos[] marker = new BlockPos[1];
		return BuildingTestSupport.begin(h).thenExecute(() -> {
			floor(h);
			BuildingTestSupport.setTier(h, Tier.VILLAGE);
			// M26-R: the full raid needs Balance.RAID_FULL_GUARDS (6) qualified guards, so the yard's Barracks has seven bed and grindstone slots
			marker[0] = BuildingTestSupport.room(h, BARRACKS, 8, 4);
			for (int i = 0; i < 7; i++) {
				h.setBlock(BuildingTestSupport.inside(BARRACKS, i, 0, 0), Blocks.GRINDSTONE.defaultBlockState());
				WorkerTestSupport.bed(h, BuildingTestSupport.inside(BARRACKS, i, 0, 2));
			}
			h.setBlock(BuildingTestSupport.inside(BARRACKS, 7, 0, 0), Blocks.CHEST.defaultBlockState());
			BuildingTestSupport.placeMarker(h, BuildingType.BARRACKS, marker[0], Direction.NORTH);
		}).thenWaitUntil(() -> BuildingTestSupport.assertStatus(h, marker[0], dev.villageheart.block.MarkerStatus.VALID))
				.thenExecute(() -> {
					for (int i = 0; i < guards; i++) hireGuard(h, BuildingTestSupport.inside(BARRACKS, i, 0, 1), Optional.empty());
				});
	}

	private void listen() {
		lines.clear();
		dev.villageheart.raid.RaidNotices.setSinkForTest((player, text) -> lines.add(text));
	}

	private static void prepare(GameTestHelper h, long dayTime) {
		h.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		h.getLevel().getGameRules().set(GameRules.RAIDS, true, h.getLevel().getServer());
		h.getLevel().getGameRules().set(SiegeGamerules.RANDOM_RAIDS, true, h.getLevel().getServer());
		clock(h, dayTime);
	}

	private static void clock(GameTestHelper h, long dayTime) {
		setTime(h, dayTime);
		h.getLevel().getGameRules().set(GameRules.ADVANCE_TIME, false, h.getLevel().getServer());
	}

	private static void beginRaid(GameTestHelper h) {
		prepare(h, 500);
		place(h, 3, 1, 1);
		ThreatRules.graceAndGapForTest(0, 0);
		ThreatRules.forceRoll();
		ThreatScheduler.clearForTests();
		ThreatRules.graceAndGapForTest(0, 0);
		ThreatRules.forceRoll();
		clock(h, Balance.PENDING_START_MAX);
	}

	private static void place(GameTestHelper h, int x, int y, int z) {
		ServerPlayer player = BuildingTestSupport.owner();
		BlockPos at = h.absolutePos(new BlockPos(x, y, z));
		player.teleportTo(h.getLevel(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5, Set.<Relative>of(), 0.0F, 0.0F, true);
	}

	private static BlockPos farBell(GameTestHelper h) { return h.absolutePos(new BlockPos(0, 1, 90)); }

	/** A bell 90 blocks out, with its meeting point taken, is a village vanilla will start a raid on. */
	private static void placeFarVillage(GameTestHelper h) {
		ServerLevel level = h.getLevel();
		BlockPos at = farBell(h);
		level.getChunk(at);
		level.setBlockAndUpdate(at, Blocks.BELL.defaultBlockState());
		Villager villager = EntityType.VILLAGER.create(level, EntitySpawnReason.EVENT);
		villager.snapTo(at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 0, 0);
		level.addFreshEntity(villager);
	}

	private static void claimFarBell(GameTestHelper h) {
		BlockPos bell = farBell(h);
		h.getLevel().getPoiManager().take(type -> type.is(net.minecraft.tags.PoiTypeTags.VILLAGE),
				(type, pos) -> pos.equals(bell), bell, 1);
	}

	private static Raid startFarRaid(GameTestHelper h) {
		Raid raid = dev.villageheart.gametest.raid.RaidStart.start(BuildingTestSupport.owner(), farBell(h), 4);
		((RaidTestAccess) raid).villageheart$setRaidCooldownTicks(1_000_000);
		return raid;
	}

	/**
	 * Lets the next vanilla wave out. A cooldown of 0 while a wave has already spawned makes vanilla restart the
	 * 300-tick wait, so the cooldown is set to 1 only after the current wave is dead.
	 */
	private static void releaseUntil(GameTestHelper h, Raid raid, int wave) {
		if (raid.getGroupsSpawned() >= wave) return;
		if (raid.getTotalRaidersAlive() > 0) {
			for (Raider raider : List.copyOf(raid.getAllRaiders())) raider.kill(h.getLevel());
			return;
		}
		RaidTestAccess access = (RaidTestAccess) raid;
		if (access.villageheart$raidCooldownTicks() != 1) access.villageheart$setRaidCooldownTicks(1);
	}

	private static void stopTracked(GameTestHelper h) {
		var rec = state(h).threats().raid();
		if (rec.isEmpty()) return;
		Raid raid = h.getLevel().getRaids().get(rec.get().vanillaId());
		if (raid != null) {
			for (Raider raider : List.copyOf(raid.getAllRaiders())) raider.discard();
			raid.stop();
		}
	}

	private static void finishVanilla(GameTestHelper h, Raid raid) {
		RaidTestAccess access = (RaidTestAccess) raid;
		access.villageheart$setStarted(true);
		access.villageheart$setGroupsSpawned(((RaidGroups) raid).villageheart$numGroups());
		access.villageheart$setRaidCooldownTicks(0);
		for (Raider raider : List.copyOf(raid.getAllRaiders())) raider.kill(h.getLevel());
	}

	private static void withTreasury(GameTestHelper h, int emeralds) {
		VillageClaims.data(h.getLevel().getServer()).update(s -> s.withTreasury(emeralds));
	}

	private static Raid live(GameTestHelper h) {
		var rec = state(h).threats().raid();
		h.assertTrue(rec.isPresent(), "no raid");
		Raid raid = h.getLevel().getRaids().get(rec.get().vanillaId());
		h.assertTrue(raid != null, "the vanilla raid is gone");
		return raid;
	}

	private static int extras(Raid raid) {
		int n = 0;
		for (Raider raider : raid.getAllRaiders()) {
			if (raider.getTags().contains(ThreatScheduler.EXTRA_TAG)) n++;
		}
		return n;
	}

	private static VillageState state(GameTestHelper h) {
		var village = VillageClaims.current(h.getLevel().getServer());
		h.assertTrue(village.isPresent(), "a village is claimed");
		return village.get();
	}
}
