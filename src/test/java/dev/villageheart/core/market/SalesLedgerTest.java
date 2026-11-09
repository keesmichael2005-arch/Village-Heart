package dev.villageheart.core.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.villageheart.core.HeartLocation;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SalesLedgerTest {
	private static final HeartLocation A = new HeartLocation("minecraft:overworld", 1, 64, 1), B = new HeartLocation("minecraft:overworld", 9, 64, 9);
	private static final UUID M1 = UUID.randomUUID(), M2 = UUID.randomUUID();

	@Test
	void forDayKeepsOrEmpties() {
		SalesLedger l = SalesLedger.empty(5).record(M1, A, 3);
		assertSame(l, l.forDay(5));
		assertEquals(SalesLedger.empty(6), l.forDay(6));
		assertEquals(SalesLedger.empty(0), l.forDay(0), "backwards too");
	}

	@Test
	void recordAddsToBothAndSums() {
		SalesLedger l = SalesLedger.empty(1).record(M1, A, 2).record(M1, A, 1).record(M2, B, 4);
		assertEquals(3, l.soldBy(M1));
		assertEquals(4, l.soldBy(M2));
		assertEquals(3, l.soldAt(A));
		assertEquals(4, l.soldAt(B));
		assertEquals(7, l.soldToday());
	}

	@Test
	void merchantMovedKeepsCountAndNewVillagerSeesTheStallCount() {
		SalesLedger l = SalesLedger.empty(1).record(M1, A, 5);
		assertEquals(5, l.soldBy(M1));
		assertEquals(0, l.soldAt(B), "stall B is fresh, but M1's own count follows it");
		assertEquals(5, l.soldAt(A), "a new villager at A sees A's count");
		assertEquals(0, l.soldBy(M2));
	}

	@Test
	void immutableCopies() {
		Map<UUID, Integer> m = new HashMap<>(Map.of(M1, 1));
		SalesLedger l = new SalesLedger(1, m, Map.of());
		m.put(M2, 9);
		assertEquals(1, l.soldToday());
		assertThrows(UnsupportedOperationException.class, () -> l.byMerchant().put(M2, 1));
	}
}
