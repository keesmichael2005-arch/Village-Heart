package dev.villageheart.core.hunt;

import java.util.Map;

/**
 * How likely each raw meat is on a hunting trip: the vanilla creature spawn weights of the biomes around the Hunting Ground (M17 plan
 * 3.2 #9). Pure; the glue reads the spawn weights from the game and passes them as entity id to weight.
 */
public record MeatWeights(int beef, int porkchop, int mutton, int chicken) {
	/** Vanilla {@code farmAnimals}: cow 8, pig 10, sheep 12, chicken 10. Used where no farm animal spawns (desert, oceans). */
	public static final MeatWeights DEFAULT = new MeatWeights(8, 10, 12, 10);

	public MeatWeights {
		if (beef < 0 || porkchop < 0 || mutton < 0 || chicken < 0) throw new IllegalArgumentException("weights must be >= 0");
	}

	/** Cow and mooshroom give beef, pig pork, sheep mutton, chicken chicken; every other mob is ignored. Nothing counted: {@link #DEFAULT}. */
	public static MeatWeights fromSpawns(Map<String, Integer> entityWeights) {
		int beef = 0, pork = 0, mutton = 0, chicken = 0;
		for (Map.Entry<String, Integer> e : entityWeights.entrySet()) {
			int w = Math.max(0, e.getValue());
			switch (e.getKey()) {
				case "minecraft:cow", "minecraft:mooshroom" -> beef += w;
				case "minecraft:pig" -> pork += w;
				case "minecraft:sheep" -> mutton += w;
				case "minecraft:chicken" -> chicken += w;
				default -> { }
			}
		}
		MeatWeights w = new MeatWeights(beef, pork, mutton, chicken);
		return w.total() == 0 ? DEFAULT : w;
	}

	public int total() { return beef + porkchop + mutton + chicken; }

	/** The kind a roll in {@code [0, total)} falls on (cumulative, in the order beef, porkchop, mutton, chicken). */
	public MeatKind pick(int roll) {
		if (roll < 0 || roll >= total()) throw new IllegalArgumentException("roll out of range: " + roll);
		if (roll < beef) return MeatKind.BEEF;
		roll -= beef;
		if (roll < porkchop) return MeatKind.PORKCHOP;
		roll -= porkchop;
		if (roll < mutton) return MeatKind.MUTTON;
		return MeatKind.CHICKEN;
	}

	public int weight(MeatKind k) {
		return switch (k) {
			case BEEF -> beef;
			case PORKCHOP -> porkchop;
			case MUTTON -> mutton;
			case CHICKEN -> chicken;
		};
	}
}
