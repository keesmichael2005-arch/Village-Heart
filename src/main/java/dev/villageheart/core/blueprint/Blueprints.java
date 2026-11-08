package dev.villageheart.core.blueprint;

import java.util.List;
import java.util.Optional;

/**
 * The blueprints of the game, as text (no IO), parsed once. M20: Small House, Storage Shed, Farm Plot (docs/plans/M20.md 3.4).
 * "5x5 room" means a 5x5 floor inside the walls (7x7 outside): a 5x5 outside gives 9 floor cells, under the House minimum of 12.
 */
public final class Blueprints {
	private Blueprints() {}

	private static final String PALETTE_COMMON = """
			palette
			. any
			_ air
			C minecraft:cobblestone <- minecraft:cobblestone "cobblestone" "cobblestone"
			P minecraft:oak_planks <- #minecraft:planks "planks" "planks"
			D minecraft:oak_door[facing=south,half=lower] <- #minecraft:wooden_doors "door" cell=solid +#door
			d minecraft:oak_door[facing=south,half=upper] part=DOWN cell=solid
			""";

	private static String shell(String palette, String torchRow, String backRow, String marker) {
		return PALETTE_COMMON + palette + """
				layer 0
				.......
				CCCCCCC
				CCCCCCC
				CCCCCCC
				CCCCCCC
				CCCCCCC
				CCCCCCC
				CCCCCCC
				layer 1
				.......
				PPPDPPP
				P_____P
				P_____P
				P_____P
				P_____P
				""" + backRow + """
				PPPPPPP
				layer 2
				.......
				PPPdPPP
				P_____P
				P_____P
				P_____P
				P_____P
				""" + torchRow + """
				PPPPPPP
				layer 3
				""" + marker + """
				PPPPPPP
				P_____P
				P_____P
				P_____P
				P_____P
				P_____P
				PPPPPPP
				layer 4
				.......
				PPPPPPP
				PPPPPPP
				PPPPPPP
				PPPPPPP
				PPPPPPP
				PPPPPPP
				PPPPPPP
				end
				""";
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
			F minecraft:farmland[moisture=0] <- minecraft:dirt "dirt" "dirt" converts
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

	private static final List<Blueprint> ALL = List.of(SMALL_HOUSE, STORAGE_SHED, FARM_PLOT);

	/** In menu order. */
	public static List<Blueprint> all() { return ALL; }

	public static Optional<Blueprint> byId(String id) {
		for (Blueprint b : ALL) if (b.id().equals(id)) return Optional.of(b);
		return Optional.empty();
	}

	/**
	 * The blueprint for an id and its parameters. M20's blueprints are fixed (length 0); M21's wall segment will generate a
	 * {@link Blueprint} from {@code params.length()} here.
	 */
	public static Optional<Blueprint> resolve(String id, BlueprintParams params) {
		return byId(id);
	}
}
