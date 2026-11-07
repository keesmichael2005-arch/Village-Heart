package dev.villageheart.core.stock;

import dev.villageheart.core.Balance;

/** Remembers the storage alert across refreshes (see DESIGN section 6). */
public final class StorageAlertTracker {
	private StorageAlert state = StorageAlert.OK;
	private long since;
	private long lastShort = Long.MIN_VALUE / 2;

	/** A spilling deposit returned less than asked: nothing anywhere had room for that item. */
	public void noteDepositShort(long gameTime) { lastShort = gameTime; }

	public StorageAlert refresh(StockSnapshot<?> s, long gameTime) {
		StorageAlert next;
		if (s.sites() + s.unknownSites() == 0) {
			next = StorageAlert.NO_STORAGE;
		} else if (s.containers() > 0) {
			boolean held = gameTime < lastShort + Balance.STORAGE_FULL_HOLD_TICKS;
			next = s.freeSlots() == 0 || held ? StorageAlert.FULL : StorageAlert.OK;
		} else {
			// only unknown Storages: keep what we knew (no false "full", no false "ok"); but a Storage now exists
			next = state == StorageAlert.NO_STORAGE ? StorageAlert.OK : state;
		}
		if (next != state) {
			state = next;
			since = gameTime;
		}
		return state;
	}

	public StorageAlert state() { return state; }

	/** Game time the current state began. */
	public long since() { return since; }
}
