package dev.villageheart.core.heart;

import static dev.villageheart.core.heart.Rejection.BAD_RESERVE;
import static dev.villageheart.core.heart.Rejection.NO_PERMISSION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.villageheart.core.Rank;
import dev.villageheart.core.Tier;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.heart.VillageFacts.StockFacts;
import dev.villageheart.core.heart.VillageFacts.StockItemFact;
import dev.villageheart.core.market.Reserves;
import dev.villageheart.core.stock.StorageAlert;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** M22: the reserve action through the gate (T-L1-8), the market's figures in the snapshot (T-L1-9) and the words (T-L1-10). */
class MarketHeartTest {
	private static final UUID OWNER = new UUID(0, 1), STEWARD = new UUID(0, 2), RESIDENT = new UUID(0, 3), VISITOR = new UUID(0, 4);
	private static final VillageState STATE = VillageState.founded(new dev.villageheart.core.HeartLocation("minecraft:overworld", 0, 64, 0), OWNER, "o", 1)
			.withRank(STEWARD, Rank.STEWARD).withRank(RESIDENT, Rank.RESIDENT);
	private static final Optional<VillageState> VILLAGE = Optional.of(STATE);

	private static Optional<Rejection> ok(UUID who, HeartAction a) { return ActionGate.check(true, true, true, VILLAGE, who, a); }

	@Test void ownerAndStewardMaySetReserves() {
		HeartAction a = new HeartAction.SetReserves(3, 64);
		assertEquals(Optional.empty(), ok(OWNER, a));
		assertEquals(Optional.empty(), ok(STEWARD, a));
		assertEquals(Optional.of(NO_PERMISSION), ok(RESIDENT, a));
		assertEquals(Optional.of(NO_PERMISSION), ok(VISITOR, a));
	}

	@Test void aCraftedReserveNeverReachesTheSavedData() {
		for (int[] bad : new int[][] {{31, 64}, {-1, 64}, {3, -1}, {3, 1025}, {Integer.MAX_VALUE, 0}, {0, Integer.MIN_VALUE}}) {
			HeartAction a = new HeartAction.SetReserves(bad[0], bad[1]);
			assertEquals(Optional.of(BAD_RESERVE), ok(OWNER, a), bad[0] + ", " + bad[1]);
			assertEquals(Optional.of(BAD_RESERVE), ok(RESIDENT, a), "malformed is checked before the permission");
		}
		for (int[] good : new int[][] {{0, 0}, {30, 1024}, {3, 64}}) assertEquals(Optional.empty(), ok(OWNER, new HeartAction.SetReserves(good[0], good[1])));
		assertEquals(new Reserves(3, 128), new HeartAction.SetReserves(3, 128).reserves());
	}

	@Test void aResidentsPermissionCheckUsesTheSetReservesRow() {
		assertEquals(Rank.STEWARD, Permission.SET_RESERVES.minimum());
	}

	private static VillageFacts facts(StockFacts s) {
		return new VillageFacts("Owner", Tier.VILLAGE, 7, 2, 0, 2, 1, List.of(), List.of(), List.of(), s, List.of(), List.of());
	}

	@Test void forSaleAndMarketFiguresReachAResidentAndNotAVisitor() {
		StockFacts sf = new StockFacts(List.of(new StockItemFact("minecraft:cobblestone", 200, 0, true, 136), new StockItemFact("minecraft:wheat", 30, 0, true, 0)),
				1, 0, 2, 54, 40, StorageAlert.OK, new Reserves(2, 80), 5, 1);
		HeartSnapshot resident = HeartSnapshotBuilder.build(facts(sf), Rank.RESIDENT);
		assertEquals(136, resident.stock().items().get(0).forSale());
		assertEquals(0, resident.stock().items().get(1).forSale());
		assertEquals(new Reserves(2, 80), resident.stock().reserves());
		assertEquals(5, resident.stock().soldToday());
		assertEquals(1, resident.stock().merchants());
		HeartSnapshot visitor = HeartSnapshotBuilder.build(facts(sf), Rank.VISITOR);
		assertEquals(Stock.EMPTY, visitor.stock(), "a Visitor sees no stock");
		assertTrue(visitor.stock().items().isEmpty());
	}

	@Test void theWordsOfTheMerchantsStatuses() {
		assertEquals("Nothing to sell (all stock is within the reserve)", HeartTexts.EN.get(HeartTexts.activityKey(ActivityKind.NO_WORK, "merchant")));
		assertEquals("Can't reach its stall", HeartTexts.EN.get(HeartTexts.activityKey(ActivityKind.SITE_UNREACHABLE, "merchant")));
		assertEquals("Sold today's limit", HeartTexts.EN.get(HeartTexts.activityKey(ActivityKind.DAILY_LIMIT, "merchant")));
		assertEquals("Treasury full", HeartTexts.EN.get(HeartTexts.activityKey(ActivityKind.TREASURY_FULL, "merchant")));
		assertEquals("Nothing to do", HeartTexts.EN.get(HeartTexts.activityKey(ActivityKind.NO_WORK, "unlisted_job")));
	}
}
