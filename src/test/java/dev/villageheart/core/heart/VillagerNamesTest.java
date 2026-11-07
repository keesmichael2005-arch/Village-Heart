package dev.villageheart.core.heart;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VillagerNamesTest {
	@Test void deterministic() {
		UUID id = UUID.fromString("00000000-0000-0000-0000-00000000abcd");
		assertEquals(VillagerNames.of(id), VillagerNames.of(id));
	}

	@Test void variedAndShortAndLettersOnly() {
		Random rnd = new Random(42);
		Set<String> names = new HashSet<>();
		for (int i = 0; i < 1000; i++) {
			String n = VillagerNames.of(new UUID(rnd.nextLong(), rnd.nextLong()));
			assertTrue(n.length() >= 3 && n.length() <= 10, n);
			assertTrue(n.matches("[A-Z][a-z]+"), n);
			names.add(n);
		}
		assertTrue(names.size() >= 50, "distinct names: " + names.size());
	}

	@Test void longestPossibleNameFitsTheColumn() {
		assertTrue(VillagerNames.maxLength() <= 10, "max " + VillagerNames.maxLength());
	}
}
