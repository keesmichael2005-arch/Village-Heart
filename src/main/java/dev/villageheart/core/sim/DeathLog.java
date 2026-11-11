package dev.villageheart.core.sim;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The death log and hire counts of a simulation summary (M26 prep): one row per villager lost, then the day's {@code work.sim.*} counters
 * ({@code hires}, {@code rehires}, and {@code rehire_wait.<job>.<reason>}: 100-tick samples in which a standing hire had an empty slot and could
 * not fill it), then the hostile mobs that spawned naturally near the village by side of the wall ({@code hostile_spawn.<side>}).
 */
public final class DeathLog {
	public static final String HIRES = "sim.hires", REHIRES = "sim.rehires", WAIT_PREFIX = "sim.rehire_wait.",
			SPAWN_PREFIX = "sim.hostile_spawn.", INSIDE_PREFIX = "sim.hostile_inside.",
			PRESENT_PREFIX = "sim.hostile_samples.";

	private DeathLog() {}

	private static String dash(String s) { return s.isEmpty() ? "-" : s; }

	public static String markdown(List<DayMetrics> days) {
		StringBuilder sb = new StringBuilder("\n## Deaths\n\n");
		int n = 0;
		for (DayMetrics d : days) n += d.deaths().size();
		if (n == 0) {
			sb.append("None.\n");
		} else {
			sb.append("| day | tick | name | job | guard | cause | damage | attacker | attacker origin | pos | wall | attacker wall | zombified |\n"
					+ "|---|---|---|---|---|---|---|---|---|---|---|---|---|\n");
			for (DayMetrics d : days) {
				for (DeathRecord r : d.deaths()) {
					DeathRecord.Detail t = r.detail();
					if (t == null) {
						sb.append(String.format("| %d | ? | ? | ? | ? | %s | ? | ? | ? | ? | ? | ? | %s |%n", d.day(), r.cause(), r.zombified() ? "yes" : "no"));
					} else {
						sb.append(String.format("| %d | %d | %s | %s | %s | %s | %s | %s | %s | %d %d %d | %s | %s | %s |%n", d.day(), t.dayTime(), t.name(),
								dash(t.job()), r.guard() ? "yes" : "no", r.cause(), t.damage(), dash(t.attacker()), dash(t.origin()),
								t.x(), t.y(), t.z(), dash(t.wall()), dash(t.attackerWall()), r.zombified() ? "yes" : "no"));
					}
				}
			}
		}
		sb.append("\n## Hires\n\n| day | hires | re-hires | empty slot, not filled (100-tick samples) |\n|---|---|---|---|\n");
		for (DayMetrics d : days) {
			Map<String, Long> wait = new TreeMap<>();
			for (Map.Entry<String, Long> e : d.work().entrySet()) {
				if (e.getKey().startsWith(WAIT_PREFIX)) wait.put(e.getKey().substring(WAIT_PREFIX.length()), e.getValue());
			}
			StringBuilder w = new StringBuilder();
			for (Map.Entry<String, Long> e : wait.entrySet()) w.append(w.isEmpty() ? "" : ", ").append(e.getKey()).append(' ').append(e.getValue());
			sb.append(String.format("| %d | %d | %d | %s |%n", d.day(), d.work().getOrDefault(HIRES, 0L), d.work().getOrDefault(REHIRES, 0L),
					w.isEmpty() ? "-" : w));
		}
		sb.append("\n## Hostiles (natural spawns within the claim radius of the Heart, by side of the wall; mobs first seen inside the wall, by origin)\n\n"
				+ "| day | spawned inside | on wall | outside | no ring | got inside: scripted | converted | natural | present (100-tick samples): scripted "
				+ "| converted | natural |\n|---|---|---|---|---|---|---|---|---|---|---|\n");
		for (DayMetrics d : days) {
			Map<String, Long> w = d.work();
			sb.append(String.format("| %d | %d | %d | %d | %d | %d | %d | %d | %d | %d | %d |%n", d.day(), w.getOrDefault(SPAWN_PREFIX + WallRing.INSIDE, 0L),
					w.getOrDefault(SPAWN_PREFIX + WallRing.ON_WALL, 0L), w.getOrDefault(SPAWN_PREFIX + WallRing.OUTSIDE, 0L),
					w.getOrDefault(SPAWN_PREFIX + "none", 0L), w.getOrDefault(INSIDE_PREFIX + DeathRecord.SCRIPTED, 0L),
					w.getOrDefault(INSIDE_PREFIX + DeathRecord.CONVERTED, 0L), w.getOrDefault(INSIDE_PREFIX + DeathRecord.NATURAL, 0L),
					w.getOrDefault(PRESENT_PREFIX + DeathRecord.SCRIPTED, 0L), w.getOrDefault(PRESENT_PREFIX + DeathRecord.CONVERTED, 0L),
					w.getOrDefault(PRESENT_PREFIX + DeathRecord.NATURAL, 0L)));
		}
		return sb.toString();
	}
}
