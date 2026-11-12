package dev.villageheart.core.siege;

import dev.villageheart.core.Balance;
import java.util.List;
import java.util.Optional;

/**
 * The ominous lead-up before a random raid or a zombie siege, Terraria-style (owner, 2026-11-11). Pure schedule and wording: the lines
 * are spread evenly over the {@link Balance#WARNING_LEAD_TICKS} before the start, but never earlier than the roll that
 * created the threat. The glue sends them, once each, to the players in or near the claim.
 */
public final class ThreatWarnings {
	private ThreatWarnings() {}

	/** A lang key and the English text that key holds. A test checks the two agree. */
	public record Line(String key, String text) {}

	/**
	 * How a chat line looks (owner, 2026-11-12). The glue maps WARNING to gold, ALERT to bold red and NOTICE to plain chat colour.
	 */
	public enum Tone { WARNING, ALERT, NOTICE }

	public enum Kind {
		RAID(List.of(
				new Line("message.villageheart.warning.raid.1", "Scouts have been spotted among the hills..."),
				new Line("message.villageheart.warning.raid.2", "Distant war horns echo across the valley..."),
				new Line("message.villageheart.warning.raid.3", "Torches flicker on the ridge tonight..."))),
		SIEGE(List.of(
				new Line("message.villageheart.warning.siege.1", "The dead stir beyond the walls..."),
				new Line("message.villageheart.warning.siege.2", "A foul wind carries the stench of rot...")));

		private final List<Line> lines;

		Kind(List<Line> lines) { this.lines = lines; }

		public List<Line> lines() { return lines; }

		/** The big on-screen title at the start: a raid has one, a siege has none (owner, 2026-11-12). */
		public Optional<Line> title() { return this == RAID ? Optional.of(RAID_TITLE) : Optional.empty(); }

		/** The chat line sent when the threat starts, in {@link Tone#ALERT}. Both kinds have one. */
		public Line start() { return this == RAID ? RAID_START : SIEGE_START; }

		/** The lead-up lines are gold. */
		public Tone warningTone() { return Tone.WARNING; }

		/** The start line is bold red. */
		public Tone startTone() { return Tone.ALERT; }
	}

	public static final Line RAID_TITLE = new Line("message.villageheart.title.raid", "A raid is starting!");
	public static final Line RAID_START = new Line("message.villageheart.start.raid", "Raiders are attacking the village!");
	public static final Line SIEGE_START = new Line("message.villageheart.start.siege", "The dead are coming!");
	public static final Line STUCK_GLOW = new Line("message.villageheart.raider_hiding", "A raider is hiding nearby...");
	public static final Line STUCK_FLED = new Line("message.villageheart.raider_fled", "A raider fled the battle.");

	/** Where the warning window opens: the lead before the start, but not before the roll. */
	public static int windowStart(int rollTick, int startTick, int lead) {
		return Math.max(rollTick, startTick - Math.max(0, lead));
	}

	/** The day tick at which line {@code index} (0-based) is due. Lines divide the window evenly, the first at its opening. */
	public static int lineTick(Kind kind, int rollTick, int startTick, int lead, int index) {
		int from = windowStart(rollTick, startTick, lead);
		int span = Math.max(0, startTick - from);
		int n = kind.lines().size();
		return from + (int) ((long) index * span / n);
	}

	/** How many lines are due by {@code dayTime}. Nothing is due once the start tick has come: that is the title's moment. */
	public static int due(Kind kind, int rollTick, int startTick, int lead, int dayTime) {
		if (dayTime >= startTick) return 0;
		int n = 0;
		for (int i = 0; i < kind.lines().size(); i++) if (lineTick(kind, rollTick, startTick, lead, i) <= dayTime) n++;
		return n;
	}

	/**
	 * The index of the line to send now, or -1. {@code sent} is how many were sent before. Several lines due at once (the
	 * clock jumped, for instance a sleep) send only the latest: a burst of three lines is noise.
	 */
	public static int next(Kind kind, int rollTick, int startTick, int lead, int dayTime, int sent) {
		int due = due(kind, rollTick, startTick, lead, dayTime);
		return due > sent ? due - 1 : -1;
	}
}
