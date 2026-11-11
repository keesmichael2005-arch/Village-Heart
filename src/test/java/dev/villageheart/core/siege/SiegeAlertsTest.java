package dev.villageheart.core.siege;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Rank;
import dev.villageheart.core.Tier;
import dev.villageheart.core.heart.AlertCode;
import dev.villageheart.core.heart.AlertLine;
import dev.villageheart.core.heart.HeartSnapshotBuilder;
import dev.villageheart.core.heart.Severity;
import dev.villageheart.core.heart.TextLine;
import dev.villageheart.core.heart.VillageFacts;
import dev.villageheart.core.stock.StorageAlert;
import java.util.List;
import org.junit.jupiter.api.Test;

/** T-L1-23. The lines, and the snapshot order once the codes exist. */
class SiegeAlertsTest {
	@Test
	void lines() {
		assertEquals("Walls damaged: 3 broken, 7 cracked", SiegeAlerts.line(3, 7, false, true).orElseThrow().text());
		assertFalse(SiegeAlerts.line(3, 7, false, true).orElseThrow().danger());
		var breach = SiegeAlerts.line(3, 7, true, true).orElseThrow();
		assertEquals("Wall breached! 3 holes", breach.text());
		assertTrue(breach.danger());
		assertTrue(SiegeAlerts.line(0, 0, false, true).isEmpty());
		assertEquals("Walls damaged: 3 broken, 7 cracked (no builder)", SiegeAlerts.line(3, 7, false, false).orElseThrow().text());
		var row = SiegeAlerts.alert(3, 7, false, true).orElseThrow();
		assertEquals(AlertCode.DEFENCE_DAMAGED, row.code());
		assertEquals(List.of("3", "7"), row.text().args());
		assertEquals("Walls damaged: 3 broken, 7 cracked", row.text().english());
		assertTrue(SiegeAlerts.alert(0, 0, true, true).isEmpty());
	}

	@Test
	void snapshotListsRaidFirst() {
		List<AlertLine> extra = List.of(
				new AlertLine(AlertCode.DEFENCE_DAMAGED, Severity.WARNING, new TextLine("d", "d")),
				new AlertLine(AlertCode.STRIKE, Severity.DANGER, new TextLine("s", "s")),
				new AlertLine(AlertCode.SIEGE, Severity.DANGER, new TextLine("g", "g")),
				new AlertLine(AlertCode.RAID, Severity.DANGER, new TextLine("r", "r")));
		var stock = new VillageFacts.StockFacts(List.of(), 0, 0, 0, 0, 0, StorageAlert.OK);
		VillageFacts facts = new VillageFacts("Owner", Tier.HAMLET, 0, 0, 0, 0, 0, List.of(), List.of(), List.of(), stock, extra, List.of());
		var codes = HeartSnapshotBuilder.build(facts, Rank.OWNER).overview().alerts().stream().map(AlertLine::code).toList();
		assertEquals(List.of(AlertCode.RAID, AlertCode.SIEGE, AlertCode.DEFENCE_DAMAGED, AlertCode.STRIKE), codes);
	}
}
