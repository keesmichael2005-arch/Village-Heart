package dev.villageheart.core.death;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.Tier;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.heart.HeartLimits;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** T-L1-1. The log keeps 32 rows, counts every in-claim death, and merges a conversion inside one tick. */
class DeathLogTest {
	private static final UUID ID = new UUID(0, 1);
	private static final UUID OTHER = new UUID(0, 2);

	private static DeathEntry row(UUID id, String name, long time, boolean inClaim) {
		return new DeathEntry(0, id, name, "farmer", "minecraft:zombie", true, time, 1, inClaim, false, false);
	}

	@Test void aDeathAfterASameTickConversionIsNotASecondLoss() {
		DeathLog converted = DeathLog.EMPTY.markConverted(ID, 21, "zombified", "Farmer", "farmer", 1, true, false, false);
		DeathLog same = converted.appendUnlessSameLoss(row(ID, "Farmer", 21, true));
		assertEquals(1, same.entries().size());
		assertEquals("zombified", same.entries().get(0).cause());
		assertEquals(1, same.nextSeq());
		DeathLog later = same.appendUnlessSameLoss(row(ID, "Farmer", 23, true));
		assertEquals(2, later.entries().size());
		assertEquals("minecraft:zombie", later.entries().get(1).cause());
	}

	@Test void thirtyThreeAppendsKeepThirtyTwo() {
		DeathLog log = DeathLog.EMPTY;
		for (int i = 0; i < 33; i++) log = log.append(row(ID, "n" + i, i, true));
		assertEquals(32, log.entries().size());
		assertEquals(33, log.nextSeq());
		assertEquals(1, log.entries().get(0).seq(), "the oldest row (seq 0) is gone");
		assertEquals(32, log.entries().get(31).seq());
		assertEquals("n1", log.entries().get(0).name());
		assertEquals("n32", log.entries().get(31).name());
	}

	@Test void inClaimDeathsCountPastTheCap() {
		DeathLog log = DeathLog.EMPTY;
		for (int i = 0; i < 40; i++) log = log.append(row(ID, "a", i, true));
		assertEquals(40, log.inClaimLogged());
		assertEquals(32, log.entries().size());
		DeathLog outside = DeathLog.EMPTY;
		for (int i = 0; i < 5; i++) outside = outside.append(row(ID, "a", i, false));
		assertEquals(0, outside.inClaimLogged());
		assertEquals(5, outside.entries().size());
	}

	@Test void sinceIsNewerOnlyAndRecentIsNewestFirst() {
		DeathLog log = DeathLog.EMPTY;
		for (int i = 0; i < 5; i++) log = log.append(row(ID, "n" + i, 100 + i, true));
		assertEquals(2, log.since(2).size());
		assertEquals(3, log.since(2).get(0).seq());
		assertEquals(4, log.since(2).get(1).seq());
		assertTrue(log.since(4).isEmpty());
		assertEquals(ListSeq(log.recent(3), 4, 3, 2), true);
		assertEquals(5, log.recent(9).size());
		assertEquals(4, log.recent(9).get(0).seq());
	}

	private static boolean ListSeq(java.util.List<DeathEntry> rows, long... seqs) {
		if (rows.size() != seqs.length) return false;
		for (int i = 0; i < seqs.length; i++) if (rows.get(i).seq() != seqs[i]) return false;
		return true;
	}

	@Test void conversionInsideOneTickIsOneZombifiedEntry() {
		DeathLog log = DeathLog.EMPTY.append(row(ID, "Ada", 100, true));
		DeathLog merged = log.markConverted(ID, 101);
		assertEquals(1, merged.entries().size());
		assertEquals("zombified", merged.entries().get(0).cause());
		assertTrue(merged.entries().get(0).byMob());
		assertEquals("Ada", merged.entries().get(0).name());
		assertEquals(1, merged.nextSeq(), "a merge is not a second death");
		assertEquals(1, merged.inClaimLogged());
		assertEquals(1, log.markConverted(ID, 100).entries().size());
	}

	@Test void conversionTwoTicksLaterIsASecondEntry() {
		DeathLog log = DeathLog.EMPTY.append(row(ID, "Ada", 100, true)).markConverted(ID, 102);
		assertEquals(2, log.entries().size());
		assertEquals("minecraft:zombie", log.entries().get(0).cause());
		assertEquals("zombified", log.entries().get(1).cause());
		assertEquals(2, log.nextSeq());
		assertEquals(2, log.inClaimLogged());
	}

	@Test void conversionWithoutADeathAppendsOneEntry() {
		DeathLog log = DeathLog.EMPTY.markConverted(OTHER, 50);
		assertEquals(1, log.entries().size());
		assertEquals("zombified", log.entries().get(0).cause());
		assertTrue(log.entries().get(0).byMob());
		assertEquals(OTHER, log.entries().get(0).villager());
		assertEquals(1, log.nextSeq());
		assertEquals(1, log.inClaimLogged());
		assertEquals(DeathLog.dayOf(50), log.entries().get(0).day());
	}

	@Test void aDifferentVillagerIsNotMerged() {
		DeathLog log = DeathLog.EMPTY.append(row(ID, "Ada", 100, true)).markConverted(OTHER, 100);
		assertEquals(2, log.entries().size());
		assertEquals("minecraft:zombie", log.entries().get(0).cause());
		assertEquals("zombified", log.entries().get(1).cause());
	}

	@Test void namesCutTo32() {
		String longName = "N".repeat(40);
		DeathEntry e = new DeathEntry(0, ID, longName, "J".repeat(40), "C".repeat(80), false, 1, 1, true, false, false);
		assertEquals(HeartLimits.MAX_NAME, e.name().length());
		assertEquals(HeartLimits.MAX_JOB_ID, e.jobId().length());
		assertEquals(HeartLimits.MAX_ID, e.cause().length());
		assertEquals(32, DeathLog.EMPTY.append(e).entries().get(0).name().length());
	}

	@Test void deathsSurviveATreasuryEdit() {
		HeartLocation heart = new HeartLocation("minecraft:overworld", 0, 64, 0);
		DeathLog log = DeathLog.EMPTY.append(row(ID, "Ada", 10, true));
		VillageState v = VillageState.founded(heart, new UUID(1, 1), "owner", 0).withDeaths(log);
		assertEquals(log, v.withTreasury(3).deaths());
		assertEquals(log, v.withTier(Tier.VILLAGE).deaths());
		assertEquals(log, v.withDeaths(log).deaths());
		assertFalse(v.withDeaths(DeathLog.EMPTY).deaths().equals(log));
	}
}
