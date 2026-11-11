package dev.villageheart.core.guard;

import dev.villageheart.core.Balance;

/**
 * The training day of a new guard (M18 r3, owner design): a guard hired in the morning or afternoon spends the rest of that day training
 * (sparring at the Training Yard when the village has one), is qualified at dusk and can fight that night. {@code trainingUntil == 0} means
 * qualified. A hire at night is refused ({@link #mayHire}); the next morning's hire trains that day.
 */
public final class GuardTraining {
	public static final long DAY = 24000;

	private GuardTraining() {}

	/** Day time (the world clock, which the schedule uses) at which a guard hired at {@code hireTime} is qualified: dusk of the day it was hired (the next day's if hired after dusk). */
	public static long qualifiesAt(long hireTime) {
		long dayStart = hireTime - Math.floorMod(hireTime, DAY);
		long end = dayStart + Balance.GUARD_TRAINING_END;
		return hireTime < end ? end : end + DAY;
	}

	public static boolean training(long now, long trainingUntil) { return trainingUntil > 0 && now < trainingUntil; }

	/** Guards are hired by day only: a guard that fell at night is replaced the next morning. */
	public static boolean mayHire(long gameTime) { return Math.floorMod(gameTime, DAY) < Balance.GUARD_NO_HIRE_FROM; }
}
