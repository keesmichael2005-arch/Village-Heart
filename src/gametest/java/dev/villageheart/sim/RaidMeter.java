package dev.villageheart.sim;

import dev.villageheart.core.Balance;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.raid.RaidOrigin;
import dev.villageheart.core.raid.RaidOutcome;
import dev.villageheart.core.raid.RaidRecord;
import dev.villageheart.core.raid.RaidSummary;
import dev.villageheart.core.sim.DayMetrics;
import dev.villageheart.core.sim.DeathRecord;
import dev.villageheart.worker.Workers;
import dev.villageheart.world.VillageClaims;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.phys.AABB;

/**
 * Samples the tracked raid while the day is running, then folds one {@link DayMetrics.RaidDay} at the record.
 * Guard census and call-up live only on the live record, so a raid that has already ended is remembered here.
 */
final class RaidMeter {
	private RaidRecord seen;
	private long prevGameTime = Long.MIN_VALUE;
	private long accountedStart = Long.MIN_VALUE;
	private int accountedDeaths;
	private int accountedKills;
	private final List<Episode> episodes = new ArrayList<>();
	private String lastRaiders = "none";

	void sample(MinecraftServer server) {
		VillageState village = VillageClaims.current(server).orElse(null);
		if (village == null) return;
		village.threats().raid().ifPresent(cur -> {
			seen = cur;
			note(cur.startedAt(), cur.origin().id(), cur.wavesSpawned(), "", -1, cur.villagerDeaths(), cur.raidersKilled());
			rememberRaiders(server, village, cur);
		});
		village.threats().lastRaid().ifPresent(last -> note(last.startedAt(), last.origin().id(), last.wavesSpawned(), last.outcome().id(),
				last.endedAt(), last.villagerDeaths(), last.raidersKilled()));
	}

	DayMetrics.RaidDay take(MinecraftServer server, long gameTime) {
		sample(server);
		VillageState village = VillageClaims.current(server).orElse(null);
		RaidRecord cur = village == null ? null : village.threats().raid().orElse(null);
		RaidSummary last = village == null ? null : village.threats().lastRaid().orElse(null);
		RaidSummary ended = last != null && last.endedAt() > prevGameTime && last.endedAt() <= gameTime ? last : null;
		int started = 0;
		if (cur != null && inWindow(cur.startedAt(), gameTime)) started++;
		if (last != null && inWindow(last.startedAt(), gameTime) && (cur == null || last.startedAt() != cur.startedAt())) started++;
		int[] delta = deathsAndKills(cur, ended);
		RaidRecord census = null;
		if (cur != null && inWindow(cur.startedAt(), gameTime)) census = cur;
		else if (seen != null && inWindow(seen.startedAt(), gameTime)) census = seen;
		RaidOrigin origin = cur != null ? cur.origin() : ended != null ? ended.origin() : null;
		int early = 0, extras = 0, callup = 0;
		if (census != null) {
			early = census.plan().early() ? 1 : 0;
			extras = census.plan().extraVindicators() + census.plan().extraRavagers();
			callup = census.allGuardsOnDutyAt() >= 0 ? ticks(census.allGuardsOnDutyAt() - census.startedAt()) : 99999;
		} else if (started > 0) {
			callup = 99999;
		}
		int waves = cur != null ? cur.wavesSpawned() : ended != null ? ended.wavesSpawned() : 0;
		int wavesTotal = cur != null ? cur.wavesTotal() : ended != null ? ended.wavesTotal() : 0;
		int atStart = census != null ? census.villagersAtStart()
				: ended != null && inWindow(ended.startedAt(), gameTime) ? ended.villagersAtStart() : 0;
		prevGameTime = gameTime;
		return new DayMetrics.RaidDay(started, cur != null ? 1 : 0, outcome(ended, RaidOutcome.VICTORY), outcome(ended, RaidOutcome.LOSS),
				outcome(ended, RaidOutcome.STOPPED), outcome(ended, RaidOutcome.GONE), waves, wavesTotal, atStart,
				ended != null ? ended.villagersAtEnd() : 0, census == null ? 0 : census.guardsAtStart(),
				census == null ? 0 : census.guardsQualifiedAtStart(), census == null ? 0 : census.guardsWoundedAtStart(),
				census == null ? 0 : census.guardsUnloadedAtStart(), delta[0], delta[1], origin == RaidOrigin.RANDOM ? 1 : 0, early,
				ended != null ? ended.reward() : 0, extras, -1, callup);
	}

	private static int outcome(RaidSummary ended, RaidOutcome want) { return ended != null && ended.outcome() == want ? 1 : 0; }

	String section(List<DayMetrics> days) {
		StringBuilder sb = new StringBuilder("\n## Raid\n\n");
		if (episodes.isEmpty()) sb.append("No raid.\n");
		for (Episode e : episodes) {
			sb.append("- start tick ").append(e.startedAt).append(", origin ").append(e.origin).append(", waves ").append(e.waves)
					.append(", outcome ").append(e.outcome.isEmpty() ? "still running" : e.outcome).append(", end tick ")
					.append(e.endedAt < 0 ? "n/a" : Long.toString(e.endedAt)).append(", deaths ").append(e.deaths).append(", kills ")
					.append(e.kills).append("\n");
		}
		sb.append("Deaths:\n");
		boolean any = false;
		for (DayMetrics d : days) {
			DayMetrics.RaidDay r = d.raid();
			if (r.started() == 0 && r.active() == 0 && r.victories() + r.losses() + r.stopped() + r.gone() == 0) continue;
			for (DeathRecord x : d.deaths()) {
				any = true;
				DeathRecord.Detail t = x.detail();
				String name = t == null ? "unknown" : t.name();
				String job = t == null || t.job().isEmpty() ? "unemployed" : t.job();
				String pos = t == null ? "no position" : t.x() + "," + t.y() + "," + t.z();
				String wall = t == null || t.wall().isEmpty() ? "no walls" : t.wall();
				sb.append("- ").append(name).append(" (").append(job).append("): ").append(x.cause()).append(" at ").append(pos).append(", ")
						.append(wall).append("\n");
			}
		}
		if (!any) sb.append("none\n");
		sb.append("Last raiders: ").append(lastRaiders).append("\n");
		return sb.toString();
	}

	private int[] deathsAndKills(RaidRecord cur, RaidSummary ended) {
		int deaths = 0, kills = 0;
		if (ended != null) {
			int[] d = grow(ended.startedAt(), ended.villagerDeaths(), ended.raidersKilled());
			deaths += d[0];
			kills += d[1];
		}
		if (cur != null) {
			int[] d = grow(cur.startedAt(), cur.villagerDeaths(), cur.raidersKilled());
			deaths += d[0];
			kills += d[1];
		}
		return new int[] {deaths, kills};
	}

	private int[] grow(long start, int deathTotal, int killTotal) {
		int[] out = dev.villageheart.core.sim.RaidDayWindow.delta(start, deathTotal, killTotal, accountedStart, accountedDeaths, accountedKills);
		accountedStart = start;
		accountedDeaths = deathTotal;
		accountedKills = killTotal;
		return out;
	}

	private boolean inWindow(long at, long gameTime) {
		return dev.villageheart.core.sim.RaidDayWindow.contains(at, prevGameTime, gameTime);
	}

	/** The reload's first day must not count a raid, or its deaths, that the previous process already recorded. */
	void resume(long gameTime, Long raidStartedAt, int deaths, int kills) {
		prevGameTime = gameTime;
		if (raidStartedAt == null) return;
		accountedStart = raidStartedAt;
		accountedDeaths = deaths;
		accountedKills = kills;
	}

	private static int ticks(long delta) {
		if (delta < 0) return 0;
		return delta > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) delta;
	}

	private void rememberRaiders(MinecraftServer server, VillageState village, RaidRecord cur) {
		ServerLevel level = Workers.levelOf(server, village.heart());
		if (level == null) return;
		Raid live = level.getRaids().get(cur.vanillaId());
		if (live == null) return;
		BlockPos c = live.getCenter();
		int rad = Balance.RAID_REACH;
		AABB box = new AABB(c.getX() - rad, c.getY() - rad, c.getZ() - rad, c.getX() + rad + 1, c.getY() + rad + 1, c.getZ() + rad + 1);
		List<Raider> raiders = level.getEntitiesOfClass(Raider.class, box, Raider::isAlive);
		if (!raiders.isEmpty()) lastRaiders = raiders.stream().map(e -> e.blockPosition().toShortString()).collect(Collectors.joining(", "));
	}

	private void note(long startedAt, String origin, int waves, String outcome, long endedAt, int deaths, int kills) {
		for (Episode e : episodes) {
			if (e.startedAt != startedAt) continue;
			e.origin = origin;
			e.waves = waves;
			if (!outcome.isEmpty()) e.outcome = outcome;
			if (endedAt >= 0) e.endedAt = endedAt;
			e.deaths = deaths;
			e.kills = kills;
			return;
		}
		episodes.add(new Episode(startedAt, origin, waves, outcome, endedAt, deaths, kills));
	}

	private static final class Episode {
		final long startedAt;
		String origin;
		int waves;
		String outcome;
		long endedAt;
		int deaths;
		int kills;

		Episode(long startedAt, String origin, int waves, String outcome, long endedAt, int deaths, int kills) {
			this.startedAt = startedAt;
			this.origin = origin;
			this.waves = waves;
			this.outcome = outcome;
			this.endedAt = endedAt;
			this.deaths = deaths;
			this.kills = kills;
		}
	}
}
