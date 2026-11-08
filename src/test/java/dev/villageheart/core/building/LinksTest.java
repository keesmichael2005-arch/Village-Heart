package dev.villageheart.core.building;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.building.Links.Candidate;
import dev.villageheart.core.room.Pos;
import java.util.List;
import org.junit.jupiter.api.Test;

class LinksTest {
	static final String OW = "minecraft:overworld";
	static final Pos SITE = new Pos(0, 64, 0);

	static Candidate c(int x, int y, int z) { return new Candidate(new Pos(x, y, z), OW); }

	@Test void nearestWins() {
		assertEquals(new Pos(20, 64, 0), Links.nearest(SITE, OW, List.of(c(30, 64, 0), c(20, 64, 0)), 48));
	}

	@Test void tieGoesToTheSmallerPos() {
		assertEquals(new Pos(-20, 64, 0), Links.nearest(SITE, OW, List.of(c(20, 64, 0), c(-20, 64, 0), c(0, 64, 20)), 48));
		assertEquals(new Pos(0, 44, 0), Links.nearest(SITE, OW, List.of(c(0, 84, 0), c(0, 44, 0)), 48));
	}

	@Test void rangeIsInclusiveAndLimited() {
		assertNull(Links.nearest(SITE, OW, List.of(c(49, 64, 0)), 48));
		assertEquals(new Pos(48, 64, 0), Links.nearest(SITE, OW, List.of(c(48, 64, 0)), 48));
		assertNull(Links.nearest(SITE, OW, List.of(), 48));
	}

	@Test void huntingGroundRangeIs40AndMineshaftRangeStays48() {
		assertEquals(new Pos(40, 64, 0), Links.nearest(SITE, OW, List.of(c(40, 64, 0)), dev.villageheart.core.Balance.HUNTING_GROUND_LINK_RANGE));
		assertNull(Links.nearest(SITE, OW, List.of(c(41, 64, 0)), dev.villageheart.core.Balance.HUNTING_GROUND_LINK_RANGE));
		assertEquals(48, dev.villageheart.core.Balance.MINESHAFT_LINK_RANGE);
		assertEquals(40, dev.villageheart.core.Balance.HUNTING_GROUND_LINK_RANGE);
	}

	@Test void otherDimensionIgnored() {
		assertNull(Links.nearest(SITE, OW, List.of(new Candidate(new Pos(1, 64, 0), "minecraft:the_nether")), Integer.MAX_VALUE));
	}

	@Test void unlimitedRangePicksFarParent() {
		assertEquals(new Pos(500, 64, 0), Links.nearest(SITE, OW, List.of(c(500, 64, 0)), Integer.MAX_VALUE));
	}
}
