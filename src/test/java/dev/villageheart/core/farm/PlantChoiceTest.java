package dev.villageheart.core.farm;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.room.Pos;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlantChoiceTest {
	private static final Set<CropKind> ALL = Set.of(CropKind.values());

	private static Map<Pos, FieldCell> around(int wheat, int carrots, int potatoes, int beets) {
		Map<Pos, FieldCell> m = new HashMap<>();
		int i = 0;
		for (int n = 0; n < wheat; n++) m.put(new Pos(i++, 0, 0), FieldCell.crop(CropKind.WHEAT, 3));
		for (int n = 0; n < carrots; n++) m.put(new Pos(i++, 0, 0), FieldCell.crop(CropKind.CARROTS, 3));
		for (int n = 0; n < potatoes; n++) m.put(new Pos(i++, 0, 0), FieldCell.crop(CropKind.POTATOES, 3));
		for (int n = 0; n < beets; n++) m.put(new Pos(i++, 0, 0), FieldCell.crop(CropKind.BEETROOTS, 1));
		return m;
	}

	@Test
	void theMajorityWins() {
		assertEquals(Optional.of(CropKind.CARROTS), PlantChoice.pick(around(3, 5, 0, 0), ALL));
		assertEquals(Optional.of(CropKind.BEETROOTS), PlantChoice.pick(around(1, 1, 1, 4), ALL));
	}

	@Test
	void aTieFollowsTheKindOrder() {
		assertEquals(Optional.of(CropKind.WHEAT), PlantChoice.pick(around(2, 2, 0, 0), ALL));
		assertEquals(Optional.of(CropKind.CARROTS), PlantChoice.pick(around(0, 2, 2, 0), ALL));
	}

	@Test
	void noNeighboursTakesTheFirstAvailable() {
		assertEquals(Optional.of(CropKind.WHEAT), PlantChoice.pick(Map.of(), ALL));
		assertEquals(Optional.of(CropKind.POTATOES), PlantChoice.pick(Map.of(), Set.of(CropKind.POTATOES, CropKind.BEETROOTS)));
	}

	@Test
	void aMajorityWithoutASeedFallsToTheNextAvailable() {
		assertEquals(Optional.of(CropKind.WHEAT), PlantChoice.pick(around(3, 5, 0, 0), Set.of(CropKind.WHEAT, CropKind.POTATOES)));
		assertEquals(Optional.of(CropKind.POTATOES), PlantChoice.pick(around(0, 5, 0, 0), Set.of(CropKind.POTATOES)));
	}

	@Test
	void nothingAvailableGivesNothing() {
		assertEquals(Optional.empty(), PlantChoice.pick(around(8, 0, 0, 0), Set.of()));
	}
}
