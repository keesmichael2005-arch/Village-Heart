package dev.villageheart.core.raid;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.villageheart.core.Rank;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The audience half of T-L1-6b. Visitors and offline players hear nothing. */
class RaidNoticesTest {
	@Test void ownerStewardAndResidentWhoAreOnline() {
		UUID owner = new UUID(1, 1);
		UUID steward = new UUID(2, 2);
		UUID resident = new UUID(3, 3);
		UUID visitor = new UUID(4, 4);
		UUID away = new UUID(5, 5);
		var ranks = Map.of(owner, Rank.OWNER, steward, Rank.STEWARD, resident, Rank.RESIDENT, visitor, Rank.VISITOR, away, Rank.STEWARD);
		assertEquals(Set.of(owner, steward, resident), RaidNotices.audience(ranks, Set.of(owner, steward, resident, visitor)));
	}
}
