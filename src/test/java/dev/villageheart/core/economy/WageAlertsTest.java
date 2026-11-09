package dev.villageheart.core.economy;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.heart.AlertCode;
import dev.villageheart.core.heart.AlertLine;
import dev.villageheart.core.heart.Severity;
import java.util.List;
import org.junit.jupiter.api.Test;

class WageAlertsTest {
	@Test void oneStrikerIsADangerWithTheSingularText() {
		List<AlertLine> a = WageAlerts.of(5, 1, 1);
		assertEquals(1, a.size());
		assertEquals(AlertCode.STRIKE, a.get(0).code());
		assertEquals(Severity.DANGER, a.get(0).severity());
		assertEquals("gui.villageheart.heart.alert.strike_one", a.get(0).text().key());
		assertEquals("1 worker is on strike: deposit emeralds at the Heart", a.get(0).text().english());
	}

	@Test void severalStrikersNameTheCount() {
		AlertLine a = WageAlerts.of(0, 0, 3).get(0);
		assertEquals("gui.villageheart.heart.alert.strike", a.text().key());
		assertEquals(List.of("3"), a.text().args());
		assertEquals("3 workers are on strike: deposit emeralds at the Heart", a.text().english());
	}

	@Test void aShortTreasuryIsAWarningWithBothNumbers() {
		List<AlertLine> a = WageAlerts.of(0, 3, 0);
		assertEquals(1, a.size());
		assertEquals(AlertCode.TREASURY_SHORT, a.get(0).code());
		assertEquals(Severity.WARNING, a.get(0).severity());
		assertEquals(List.of("0", "3"), a.get(0).text().args());
		assertEquals("Treasury short: 0 of 3 emeralds for tomorrow's wages", a.get(0).text().english());
	}

	@Test void noBillOrEnoughMoneyIsNoAlert() {
		assertTrue(WageAlerts.of(0, 0, 0).isEmpty());
		assertTrue(WageAlerts.of(3, 3, 0).isEmpty());
		assertTrue(WageAlerts.of(10, 3, 0).isEmpty());
	}

	@Test void strikeComesBeforeShort() {
		List<AlertLine> a = WageAlerts.of(0, 2, 1);
		assertEquals(List.of(AlertCode.STRIKE, AlertCode.TREASURY_SHORT), a.stream().map(AlertLine::code).toList());
	}
}
