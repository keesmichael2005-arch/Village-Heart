package dev.villageheart.core.guard;

import java.util.Optional;

/** The half of the day a guard stands duty (M18). The ids are part of the save format. */
public enum Shift {
	DAY("day"), NIGHT("night");

	private final String id;

	Shift(String id) { this.id = id; }

	public String id() { return id; }

	public Shift other() { return this == DAY ? NIGHT : DAY; }

	public static Optional<Shift> byId(String id) {
		for (Shift s : values()) if (s.id.equals(id)) return Optional.of(s);
		return Optional.empty();
	}
}
