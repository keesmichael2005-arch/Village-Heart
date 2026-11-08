package dev.villageheart.core.farm;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.room.Pos;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BoneMealTargetsTest {
	private static final Pos COMPOSTER = new Pos(0, 64, 0);

	private static FieldIndex.Indexed at(int x, int z, CropKind k, int age) { return new FieldIndex.Indexed(new Pos(x, 64, z), FieldCell.crop(k, age)); }

	@Test
	void nonBeetrootFirst() {
		List<FieldIndex.Indexed> cells = List.of(at(1, 0, CropKind.BEETROOTS, 0), at(9, 0, CropKind.WHEAT, 5));
		assertEquals(Optional.of(new Pos(9, 64, 0)), BoneMealTargets.pick(cells, COMPOSTER, Set.of()));
	}

	@Test
	void lowestAgeFractionThenDistanceThenPos() {
		List<FieldIndex.Indexed> cells = List.of(at(2, 0, CropKind.WHEAT, 3), at(8, 0, CropKind.WHEAT, 1), at(5, 0, CropKind.CARROTS, 1), at(0, 5, CropKind.POTATOES, 1));
		// age 1/7 ties: distances 8, 5, 5 -> the two at distance 5: (5,0) and (0,5); Pos order x first -> (0, 5)
		assertEquals(Optional.of(new Pos(0, 64, 5)), BoneMealTargets.pick(cells, COMPOSTER, Set.of()));
		assertEquals(Optional.of(new Pos(5, 64, 0)), BoneMealTargets.pick(cells, COMPOSTER, Set.of(new Pos(0, 64, 5))));
	}

	@Test
	void matureAndClaimedAreNeverPicked() {
		List<FieldIndex.Indexed> cells = List.of(at(1, 0, CropKind.WHEAT, 7), at(2, 0, CropKind.BEETROOTS, 3), at(3, 0, CropKind.WHEAT, 0));
		assertEquals(Optional.of(new Pos(3, 64, 0)), BoneMealTargets.pick(cells, COMPOSTER, Set.of()));
		assertEquals(Optional.empty(), BoneMealTargets.pick(cells, COMPOSTER, Set.of(new Pos(3, 64, 0))));
		assertEquals(Optional.empty(), BoneMealTargets.pick(List.of(new FieldIndex.Indexed(new Pos(1, 64, 1), FieldCell.EMPTY)), COMPOSTER, Set.of()));
	}

	@Test
	void onlyBeetrootsLeftGivesABeetroot() {
		List<FieldIndex.Indexed> cells = List.of(at(1, 0, CropKind.BEETROOTS, 2), at(2, 0, CropKind.BEETROOTS, 0));
		assertEquals(Optional.of(new Pos(2, 64, 0)), BoneMealTargets.pick(cells, COMPOSTER, Set.of()));
	}
}
