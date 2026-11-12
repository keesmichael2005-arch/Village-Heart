package dev.villageheart.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.villageheart.core.heart.HeartTexts;
import dev.villageheart.core.sim.Json;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** One emerald is singular wherever a count is shown: the helper, and no lang text that puts a plural noun after a number. */
class PluralTest {
	@Test
	void oneIsSingularEverythingElseIsPlural() {
		assertEquals("1 emerald", Plural.emeralds(1));
		assertEquals("0 emeralds", Plural.emeralds(0));
		assertEquals("2 emeralds", Plural.emeralds(2));
		assertEquals("64 emeralds", Plural.emeralds(64));
		assertEquals("1 merchant", Plural.merchants(1));
		assertEquals("3 merchants", Plural.merchants(3));
	}

	/** A text such as "Withdrew %s emeralds" says "1 emeralds" for one emerald. The count goes in as a phrase from {@link Plural}. */
	@Test
	void noTextPutsAFixedEmeraldOrMerchantNounAfterACount() throws Exception {
		Pattern bad = Pattern.compile("%s\\s+(emeralds?|merchants?)\\b", Pattern.CASE_INSENSITIVE);
		@SuppressWarnings("unchecked")
		Map<String, Object> lang = (Map<String, Object>) Json.parse(langJson());
		for (var e : lang.entrySet()) assertFalse(bad.matcher(String.valueOf(e.getValue())).find(), e.getKey() + ": " + e.getValue());
		for (var e : HeartTexts.EN.entrySet()) assertFalse(bad.matcher(e.getValue()).find(), e.getKey() + ": " + e.getValue());
	}

	private static String langJson() throws Exception {
		try (InputStream in = PluralTest.class.getResourceAsStream("/assets/villageheart/lang/en_us.json")) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
