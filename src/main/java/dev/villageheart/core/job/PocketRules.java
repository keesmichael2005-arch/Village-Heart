package dev.villageheart.core.job;

import dev.villageheart.core.worker.JobType;

/**
 * The eight slots a hired villager carries. Slot 0 is reserved for the tool of a lumberjack, miner, hunter or farmer, so a broken tool
 * still has a place and logs never fill it. The other jobs use all eight. A dump starts when every cargo slot is taken, not after one stack.
 */
public final class PocketRules {
	public static final int SLOTS = 8;
	public static final int TOOL_SLOT = 0;

	private PocketRules() {}

	public static boolean reservesToolSlot(JobType job) {
		return switch (job) {
			case LUMBERJACK, MINER, HUNTER, FARMER -> true;
			case BUILDER, COOK, MERCHANT, BLACKSMITH, GUARD -> false;
		};
	}

	public static int cargoSlots(JobType job) { return reservesToolSlot(job) ? SLOTS - 1 : SLOTS; }

	/** Gathered goods may sit in this slot. The tool slot of a tool job is never one of them. */
	public static boolean acceptsGoods(JobType job, int slot) {
		return slot >= 0 && slot < SLOTS && !(reservesToolSlot(job) && slot == TOOL_SLOT);
	}

	/**
	 * True when every cargo slot is occupied. {@code occupied[i]} is whether slot {@code i} holds something. An empty tool slot does not
	 * count as room for goods.
	 */
	public static boolean cargoFull(JobType job, boolean[] occupied) {
		for (int i = 0; i < SLOTS; i++) {
			if (!acceptsGoods(job, i)) continue;
			if (i >= occupied.length || !occupied[i]) return false;
		}
		return true;
	}

	/** The first empty cargo slot, or -1 when the cargo is full. {@code occupied[i]} means that slot already holds something. */
	public static int placeGoods(JobType job, boolean[] occupied) {
		for (int i = 0; i < SLOTS; i++) {
			if (!acceptsGoods(job, i)) continue;
			if (i >= occupied.length || !occupied[i]) return i;
		}
		return -1;
	}
}
