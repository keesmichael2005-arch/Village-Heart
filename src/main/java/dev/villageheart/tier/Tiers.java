package dev.villageheart.tier;

import dev.villageheart.VillageHeart;
import dev.villageheart.building.Buildings;
import dev.villageheart.core.Rank;
import dev.villageheart.core.Tier;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.economy.PayResult;
import dev.villageheart.core.tier.DawnOutcome;
import dev.villageheart.core.tier.TierDawn;
import dev.villageheart.core.tier.TierInputs;
import dev.villageheart.core.tier.TierProgress;
import dev.villageheart.economy.VillageDawn;
import dev.villageheart.heart.HeartFacts;
import dev.villageheart.world.VillageCensus;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.worker.Workers;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * Village tiers (M14): the fed streak is counted from the dinner, the best dawn treasury and the tier step at the dawn
 * (after wages), one step per dawn, never down. The rules are in {@code core.tier}; this is the glue.
 */
public final class Tiers {
	private Tiers() {}

	public static void register() {
		VillageDawn.register("villageheart:tiers", Tiers::onDawn);
	}

	/** Called by the dinner with the real fed count. */
	public static void onDinner(MinecraftServer server, long day, int diners, int fed) {
		int[] before = {-1};
		TierProgress[] after = {null};
		VillageClaims.data(server).update(v -> {
			TierProgress p = v.tierProgress();
			TierProgress next = p.afterDinner(day, diners, fed);
			before[0] = p.fedStreak();
			after[0] = next;
			return next.equals(p) ? v : v.withTierProgress(next);
		});
		if (after[0] != null && before[0] > 0 && after[0].fedStreak() == 0) {
			TierStats.streakResets++;
			VillageHeart.LOG.info("[tiers] day {}: hungry dinner ({}/{} fed), fed streak reset from {}", day, fed, diners, before[0]);
		}
	}

	static void onDawn(MinecraftServer server, VillageState village, long day, PayResult wages) {
		Set<UUID> ids = new HashSet<>();
		for (Villager v : VillageCensus.of(server, village)) ids.add(v.getUUID());
		TierInputs inputs = TierFacts.inputs(server, village, ids);
		DawnOutcome[] out = {null};
		Tier[] was = {village.tier()};
		// the outcome is computed on the state as it is at write time, never on the listener's snapshot
		VillageClaims.data(server).update(v -> {
			was[0] = v.tier();
			DawnOutcome o = TierDawn.run(v.tier(), v.tierProgress(), inputs, v.treasury());
			out[0] = o;
			VillageState next = o.progress().equals(v.tierProgress()) ? v : v.withTierProgress(o.progress());
			return o.tieredUp() ? next.tierUp() : next;
		});
		DawnOutcome o = out[0];
		if (o == null) return;
		int treasury = VillageClaims.current(server).map(VillageState::treasury).orElse(wages.treasuryAfter());
		if (wages.treasuryAfter() != treasury) VillageHeart.LOG.warn("[tiers] day {}: the wage result ({}) and the treasury ({}) disagree", day, wages.treasuryAfter(), treasury);
		if (!o.tieredUp()) return;
		TierStats.tierUps++;
		VillageHeart.LOG.info("[tiers] day {}: {} -> {} (population {}, workers {}, fed streak {})", day, was[0].displayName(),
				o.tier().displayName(), inputs.population(), inputs.workers(), o.progress().fedStreak());
		announce(server, village, o.tier());
	}

	/** The tier-up effects (everything but the state change). */
	static void announce(MinecraftServer server, VillageState village, Tier tier) {
		Buildings.requestAll(server);
		HeartFacts.invalidate();
		Component tierName = Component.translatableWithFallback("tier.villageheart." + tier.name().toLowerCase(Locale.ROOT), tier.displayName());
		Component line = Component.translatableWithFallback("message.villageheart.tier_up",
				"Your village is now a %s! New buildings and jobs are unlocked.", tierName);
		VillageState state = VillageClaims.current(server).orElse(village);
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (state.rankOf(p.getUUID()).atLeast(Rank.RESIDENT)) p.sendSystemMessage(line);
		}
		ServerLevel level = Workers.levelOf(server, village.heart());
		if (level != null) {
			level.playSound(null, new BlockPos(village.heart().x(), village.heart().y(), village.heart().z()), SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1.0F, 1.0F);
		}
	}
}
