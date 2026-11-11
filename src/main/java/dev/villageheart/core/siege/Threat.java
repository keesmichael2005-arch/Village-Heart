package dev.villageheart.core.siege;

import dev.villageheart.core.Tier;

/**
 * How threatening the village looks (docs/plans/M25.md 3.14).
 * Defence cells divide as integers, so 100 cells add nothing and 400 add 2: that is what makes the published examples
 * 2.0 and 8.0 while the village bonus stays 0.5.
 */
public final class Threat {
	private Threat() {}

	public static double score(int adults, double wealth, int defenceCells, Tier tier) {
		double bonus = switch (tier) {
			case HAMLET -> 0;
			case VILLAGE -> 0.5;
			case TOWN -> 1;
		};
		return adults / 10.0 + wealth / 100.0 + defenceCells / 200 + bonus;
	}
}
