package dev.villageheart.core.rank;

import static dev.villageheart.core.heart.Rejection.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Balance;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.Rank;
import dev.villageheart.core.VillageState;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RankRulesTest {
	static final UUID OWNER = new UUID(1, 1);
	static UUID id(int n) { return new UUID(0, n); }

	static VillageState base() {
		return VillageState.founded(new HeartLocation("minecraft:overworld", 0, 64, 0), OWNER, "Olga", 0)
				.withRank(id(1), "Steve", Rank.STEWARD, 1).withRank(id(2), "Res", Rank.RESIDENT, 2).seen(id(3), "Vis", 3);
	}

	@Test void setRankRefusals() {
		VillageState v = base();
		assertEquals(Optional.of(BAD_RANK), RankRules.setRank(v, id(3), Rank.OWNER, true));
		assertEquals(Optional.of(OWNER_RANK_FIXED), RankRules.setRank(v, OWNER, Rank.STEWARD, true));
		assertEquals(Optional.of(UNKNOWN_PLAYER), RankRules.setRank(v, id(99), Rank.RESIDENT, false));
		assertEquals(Optional.empty(), RankRules.setRank(v, id(99), Rank.RESIDENT, true), "an online stranger can be ranked");
		assertEquals(Optional.empty(), RankRules.setRank(v, id(3), Rank.RESIDENT, false), "known offline");
		assertEquals(Optional.empty(), RankRules.setRank(v, id(1), Rank.STEWARD, true), "same rank is fine");
	}

	@Test void memberCap() {
		VillageState v = VillageState.founded(new HeartLocation("minecraft:overworld", 0, 64, 0), OWNER, "Olga", 0);
		for (int i = 1; i <= Balance.MAX_KNOWN_PLAYERS - 1; i++) v = v.withRank(id(i), "m" + i, Rank.RESIDENT, i);
		assertEquals(Optional.of(TOO_MANY_MEMBERS), RankRules.setRank(v, id(500), Rank.RESIDENT, true));
		assertEquals(Optional.empty(), RankRules.setRank(v, id(5), Rank.STEWARD, true), "re-ranking a member at the cap");
		assertEquals(Optional.empty(), RankRules.setRank(v, id(500), Rank.VISITOR, true));
	}

	@Test void transferRules() {
		VillageState v = base();
		assertEquals(Optional.of(OWNER_RANK_FIXED), RankRules.transfer(v, OWNER, OWNER, Optional.empty()));
		assertEquals(Optional.of(NOT_A_MEMBER), RankRules.transfer(v, OWNER, id(3), Optional.empty()));
		assertEquals(Optional.of(NOT_A_MEMBER), RankRules.transfer(v, OWNER, id(99), Optional.empty()));
		assertEquals(Optional.of(TARGET_CHANGED), RankRules.transfer(v, OWNER, id(1), Optional.of(Rank.RESIDENT)));
		assertEquals(Optional.empty(), RankRules.transfer(v, OWNER, id(1), Optional.of(Rank.STEWARD)));
		assertEquals(Optional.empty(), RankRules.transfer(v, OWNER, id(2), Optional.of(Rank.RESIDENT)));
		assertEquals(Optional.empty(), RankRules.transfer(v, OWNER, id(2), Optional.empty()));
	}
}
