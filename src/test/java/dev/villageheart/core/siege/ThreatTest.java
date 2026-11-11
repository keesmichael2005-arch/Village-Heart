package dev.villageheart.core.siege;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Tier;
import org.junit.jupiter.api.Test;

/** T-L1-19. Defence cells divide as integers, which is what makes these two examples land on 2.0 and 8.0. */
class ThreatTest {
	@Test
	void publishedExamples() {
		assertEquals(2.0, Threat.score(10, 50, 100, Tier.VILLAGE));
		assertEquals(8.0, Threat.score(20, 300, 400, Tier.TOWN));
		assertEquals(0.0, Threat.score(0, 0, 0, Tier.HAMLET));
	}
}
