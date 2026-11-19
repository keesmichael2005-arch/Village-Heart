package dev.villageheart.core.blueprint;

import dev.villageheart.core.Balance;
import java.util.List;
import java.util.Optional;

/**
 * The blueprints of the game, as text (no IO), parsed once. M20: Small House, Storage Shed, Farm Plot (docs/plans/M20.md 3.4).
 * The farmland is placed moist (moisture 7): dry farmland turns to dirt on its first random tick, and the water, placed last, comes long after.
 * "5x5 room" means a 5x5 floor inside the walls (7x7 outside): a 5x5 outside gives 9 floor cells, under the House minimum of 12.
 */
public final class Blueprints {
	private Blueprints() {}

	private static String shell(String palette, String torchRow, String backRow, String marker) {
		return WorkplaceBlueprints.shell(palette, torchRow, backRow, marker);
	}

	private static final String SMALL_HOUSE_TEXT = """
			id small_house
			name Small House
			tier VILLAGE
			kind BUILDING
			builds house
			base 1
			anchor 3 4
			front NORTH
			size 7 5 8
			""" + shell("""
			B minecraft:white_bed[facing=west,part=foot] <- #minecraft:beds "bed" cell=solid
			b minecraft:white_bed[facing=west,part=head] part=EAST cell=solid +#bed
			T minecraft:wall_torch[facing=north] <- minecraft:torch "torch" "torches" pass=attached cell=open +#light
			M villageheart:house_marker[facing=north,status=unchecked] <- villageheart:house_marker "House marker" pass=attached cell=open
			""", "P___T_P\n", "PbB___P\n", "...M...\n");

	private static final String STORAGE_SHED_TEXT = """
			id storage_shed
			name Storage Shed
			tier VILLAGE
			kind BUILDING
			builds storage
			base 1
			anchor 3 4
			front NORTH
			size 7 5 8
			""" + shell("""
			K minecraft:chest[facing=north,type=single] <- minecraft:chest "chest" cell=solid +#chest
			T minecraft:wall_torch[facing=north] <- minecraft:torch "torch" "torches" pass=attached cell=open +#light
			M villageheart:storage_marker[facing=north,status=unchecked] <- villageheart:storage_marker "Storage marker" pass=attached cell=open
			""", "P__T__P\n", "PK___KP\n", "...M...\n");

	private static final String FARM_PLOT_TEXT = """
			id farm_plot
			name Farm Plot
			tier VILLAGE
			kind BUILDING
			builds none
			base 0
			anchor 5 5
			front NORTH
			size 11 3 11
			palette
			. any
			_ air
			F minecraft:farmland[moisture=7] <- minecraft:dirt "dirt" "dirt" converts
			W minecraft:water <- minecraft:water_bucket "water bucket" "water buckets" pass=last cell=open converts returns=minecraft:bucket
			f minecraft:oak_fence <- #minecraft:wooden_fences "fence"
			G minecraft:oak_fence_gate[facing=north,open=false] <- #minecraft:fence_gates "fence gate"
			layer 0
			...........
			.FFFFFFFFF.
			.FFFFFFFFF.
			.FFFFFFFFF.
			.FFFFFFFFF.
			.FFFFWFFFF.
			.FFFFFFFFF.
			.FFFFFFFFF.
			.FFFFFFFFF.
			.FFFFFFFFF.
			...........
			layer 1
			fffffGfffff
			f_________f
			f_________f
			f_________f
			f_________f
			f_________f
			f_________f
			f_________f
			f_________f
			f_________f
			fffffffffff
			layer 2
			...........
			._________.
			._________.
			._________.
			._________.
			._________.
			._________.
			._________.
			._________.
			._________.
			...........
			end
			""";

	public static final Blueprint SMALL_HOUSE = BlueprintParser.parse(SMALL_HOUSE_TEXT);
	public static final Blueprint STORAGE_SHED = BlueprintParser.parse(STORAGE_SHED_TEXT);
	public static final Blueprint FARM_PLOT = BlueprintParser.parse(FARM_PLOT_TEXT);

	public static final Blueprint WALL_SEGMENT = DefenceBlueprints.wall(1);
	public static final Blueprint GATE = DefenceBlueprints.gate(Balance.GATE_MIN_LENGTH);
	public static final Blueprint LAMP_POST = DefenceBlueprints.LAMP_POST;
	public static final Blueprint WATCHTOWER = DefenceBlueprints.WATCHTOWER;

	/** For a LINE blueprint the entry is the shortest instance: used for names, tier and the table, never for a site. */
	private static final List<Blueprint> ALL = List.of(SMALL_HOUSE, STORAGE_SHED, FARM_PLOT,
			WorkplaceBlueprints.FARMHOUSE, WorkplaceBlueprints.LUMBER_CAMP, WorkplaceBlueprints.KITCHEN, WorkplaceBlueprints.BUILDERS_HUT,
			WorkplaceBlueprints.MINE, WorkplaceBlueprints.HUNTING_LODGE, WorkplaceBlueprints.HUNTING_GROVE, WorkplaceBlueprints.BARRACKS,
			WorkplaceBlueprints.MARKET, WorkplaceBlueprints.SMITHY, WALL_SEGMENT, GATE, LAMP_POST, WATCHTOWER);

	/** In menu order. */
	public static List<Blueprint> all() { return ALL; }

	public static Optional<Blueprint> byId(String id) {
		for (Blueprint b : ALL) if (b.id().equals(id)) return Optional.of(b);
		return Optional.empty();
	}

	/**
	 * The blueprint for an id and its parameters. FOOTPRINT blueprints are fixed (the params are ignored); a LINE blueprint is generated
	 * for {@code params.length()} and is empty when the length is out of range (a saved build with such a length is dropped by the codec).
	 */
	public static Optional<Blueprint> resolve(String id, BlueprintParams params) {
		Optional<Blueprint> base = byId(id);
		if (base.isEmpty() || base.get().mode() != PlacementMode.LINE) return base;
		return DefenceBlueprints.line(id, params.length());
	}

	/** The shortest line of a LINE blueprint, 0 for any other. */
	public static int minLength(Blueprint bp) {
		return bp.mode() == PlacementMode.LINE ? DefenceBlueprints.minLength(bp.id()) : 0;
	}
}
