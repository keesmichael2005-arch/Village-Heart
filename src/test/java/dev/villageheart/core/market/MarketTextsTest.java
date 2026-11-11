package dev.villageheart.core.market;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.villageheart.core.heart.TextLine;
import dev.villageheart.core.sim.Json;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Singular and plural everywhere the market writes an emerald count (screen line and {@code market status}). */
class MarketTextsTest {
	@Test
	void oneEmeraldIsSingularAndTheRestArePlural() throws Exception {
		assertLine("Market: sold 1 emerald today", MarketTexts.soldToday(1));
		assertLine("Market: sold 0 emeralds today", MarketTexts.soldToday(0));
		assertLine("Market: sold 2 emeralds today", MarketTexts.soldToday(2));
		assertEquals("Market: sold 1 emerald today, 2 merchants, keeps 3 days of food and 4 of every other item",
				MarketTexts.status(1, 2, 3, 4).english());
		assertEquals("Market: sold 5 emeralds today, 1 merchants, keeps 0 days of food and 64 of every other item",
				MarketTexts.status(5, 1, 0, 64).english());
		@SuppressWarnings("unchecked")
		Map<String, Object> lang = (Map<String, Object>) Json.parse(langJson());
		for (TextLine line : new TextLine[] {MarketTexts.soldToday(1), MarketTexts.soldToday(2)}) {
			assertEquals(line.fallback(), lang.get(line.key()), line.key());
		}
		for (MarketTexts.Line line : new MarketTexts.Line[] {MarketTexts.status(1, 1, 1, 1), MarketTexts.status(2, 1, 1, 1)}) {
			assertEquals(line.fallback(), lang.get(line.key()), line.key());
		}
	}

	private static void assertLine(String english, TextLine line) {
		assertEquals(english, line.english(), line.key());
	}

	private static String langJson() throws Exception {
		try (InputStream in = MarketTextsTest.class.getResourceAsStream("/assets/villageheart/lang/en_us.json")) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
