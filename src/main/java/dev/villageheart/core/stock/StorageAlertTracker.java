package dev.villageheart.core.stock;

import dev.villageheart.core.Balance;

/** Remembers the storage alert across refreshes (see DESIGN section 6). */
public final class StorageAlertTracker {
	private StorageAlert state = StorageAlert.OK;
	private long since;
	private long lastShort = Long.MIN_VALUE / 2;

	/**
	 * A spilling deposit returned less than asked. It only means "nothing anywhere had room" when the deposit saw every Storage:
	 * with no readable Storage, or with an unknown one (after a restart, an unloaded chunk), it proves nothing and is ignored.
	 */
	public void noteDepositShort(long gameTime, int sitesSeen, int unknownSites) {
		if (sitesSeen > 0 && unknownSites == 0) lastShort = gameTime;
	}

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
