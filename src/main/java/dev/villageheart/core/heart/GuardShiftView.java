package dev.villageheart.core.heart;

import dev.villageheart.core.guard.GuardShift;
import dev.villageheart.core.guard.Shift;
import java.util.Optional;

/** A guard's shift as the Villagers tab shows it (M18): none for a villager that is no guard. Sent as an ordinal: append only. */
public enum GuardShiftView {
	NONE, DAY_AUTO, NIGHT_AUTO, DAY_PINNED, NIGHT_PINNED;

	public static GuardShiftView of(Optional<GuardShift> s) {
		if (s.isEmpty()) return NONE;
		boolean day = s.get().shift() == Shift.DAY;
		return s.get().pinned() ? (day ? DAY_PINNED : NIGHT_PINNED) : (day ? DAY_AUTO : NIGHT_AUTO);
	}

	public boolean pinned() { return this == DAY_PINNED || this == NIGHT_PINNED; }

	public boolean isGuard() { return this != NONE; }

	/** Lang key suffix of the label. */
	public String langId() { return name().toLowerCase(java.util.Locale.ROOT); }

	/** The next choice of the button: auto (0) -> day (1) -> night (2) -> auto. */
	public int nextChoice() {
		return switch (this) {
			case NONE -> 0;
			case DAY_AUTO, NIGHT_AUTO -> 1;
			case DAY_PINNED -> 2;
			case NIGHT_PINNED -> 0;
		};
	}
}
