package dev.villageheart.core.stock;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class StorageAlertTest {
	private static StockSnapshot<String> snap(int sites, int unknown, int containers, int free) {
		return new StockSnapshot<>(0, List.of(), sites, unknown, containers, 0, containers * 27, free, StorageAlert.OK);
	}

	@Test void noStorage() {
		StorageAlertTracker t = new StorageAlertTracker();
		assertEquals(StorageAlert.NO_STORAGE, t.refresh(snap(0, 0, 0, 0), 40));
		assertEquals(40, t.since());
		t.refresh(snap(0, 0, 0, 0), 60);
		assertEquals(40, t.since(), "since moves only when the state changes");
	}

	@Test void fullAndClear() {
		StorageAlertTracker t = new StorageAlertTracker();
		assertEquals(StorageAlert.FULL, t.refresh(snap(1, 0, 2, 0), 100));
		assertEquals(100, t.since());
		assertEquals(StorageAlert.FULL, t.refresh(snap(1, 0, 2, 0), 120));
		assertEquals(100, t.since());
		assertEquals(StorageAlert.OK, t.refresh(snap(1, 0, 2, 3), 140));
		assertEquals(140, t.since());

		t.noteDepositShort(1000, 1, 0);
		assertEquals(StorageAlert.FULL, t.refresh(snap(1, 0, 2, 3), 1000));
		assertEquals(StorageAlert.FULL, t.refresh(snap(1, 0, 2, 3), 1199));
		assertEquals(StorageAlert.OK, t.refresh(snap(1, 0, 2, 3), 1200));
	}

	@Test void unknownKeepsState() {
		StorageAlertTracker t = new StorageAlertTracker();
		t.refresh(snap(1, 0, 2, 0), 10);
		assertEquals(StorageAlert.FULL, t.refresh(snap(0, 2, 0, 0), 30));
		assertEquals(10, t.since());

		StorageAlertTracker fresh = new StorageAlertTracker();
		assertEquals(StorageAlert.OK, fresh.refresh(snap(0, 2, 0, 0), 30));
		assertEquals(StorageAlert.OK, fresh.refresh(snap(0, 1, 0, 0), 50), "an unloaded Storage is not full");
	}

	@Test void unknownStorageAfterNoStorageIsOk() {
		StorageAlertTracker t = new StorageAlertTracker();
		t.refresh(snap(0, 0, 0, 0), 0);
		assertEquals(StorageAlert.OK, t.refresh(snap(0, 1, 0, 0), 20));
	}

	@Test void shortDepositThatSawNoStorageProvesNothing() {
		StorageAlertTracker t = new StorageAlertTracker();
		t.noteDepositShort(1000, 0, 0); // no Storage at all
		t.noteDepositShort(1000, 0, 2); // only unknown Storages (after a restart)
		t.noteDepositShort(1000, 1, 1); // one readable, one unknown: it did not see everything
		assertEquals(StorageAlert.OK, t.refresh(snap(1, 0, 2, 54), 1010));
		assertEquals(StorageAlert.OK, t.refresh(snap(1, 0, 2, 54), 1100));
	}
}
