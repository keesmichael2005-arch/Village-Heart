package dev.villageheart.core.blueprint;

import dev.villageheart.core.Tier;
import java.util.ArrayList;
import java.util.List;

/**
 * Authored job buildings and a few visual defence pieces (P4). Same shell as the old small house, with a log frame, glass windows and a
 * stair roof, so every one stays inside the builder's reach from the stand ring. The old box houses stay out of the menu.
 */
public final class JobBlueprints {
	private JobBlueprints() {}

	private static final String PALETTE = """
			palette
			. any
			_ air
			C minecraft:cobblestone <- minecraft:cobblestone "cobblestone" "cobblestone"
			P minecraft:oak_planks <- #minecraft:planks "planks" "planks"
			L minecraft:oak_log[axis=y] <- #minecraft:logs "log" "logs" +#natural
			W minecraft:glass_pane <- minecraft:glass_pane "glass pane" "glass panes"
			N minecraft:oak_stairs[facing=north,half=bottom,shape=straight] <- minecraft:oak_stairs "stair" "stairs"
			D minecraft:oak_door[facing=south,half=lower] <- #minecraft:wooden_doors "door" cell=solid +#door
			d minecraft:oak_door[facing=south,half=upper] part=DOWN cell=solid
			T minecraft:wall_torch[facing=north] <- minecraft:torch "torch" "torches" pass=attached cell=open +#light
			""";

	public static Blueprint building(String id, String name, Tier tier, String builds, String furniturePalette, String furnishRow) {
		String marker = "M villageheart:" + builds + "_marker[facing=north,status=unchecked] <- villageheart:" + builds + "_marker \"" + name
				+ " marker\" pass=attached cell=open\n";
		String text = """
				id %s
				name %s
				tier %s
				kind BUILDING
				builds %s
				base 1
				anchor 3 4
				front NORTH
				size 7 5 8
				""".formatted(id, name, tier.name(), builds) + PALETTE + furniturePalette + marker + """
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
				LPPDPLP
				P_____P
				P_____P
				P_____P
				P_____P
				""" + furnishRow + """
				LPPPPPL
				layer 2
				.......
				LPPdPWL
				P_____P
				W_____W
				P_____P
				W_____W
				""" + "P___T_P\n" + """
				LPPPPPL
				layer 3
				...M...
				LPPPPPL
				P_____P
				P_____P
				P_____P
				P_____P
				P_____P
				LPPPPPL
				layer 4
				.......
				NNNNNNN
				NNNNNNN
				NNNNNNN
				NNNNNNN
				NNNNNNN
				NNNNNNN
				NNNNNNN
				end
				""";
		return BlueprintParser.parse(text);
	}

	private static String row(String cells) { return cells + "\n"; }

	public static final Blueprint KITCHEN = building("kitchen", "Kitchen", Tier.VILLAGE, "kitchen", """
			K minecraft:smoker[facing=north,lit=false] <- minecraft:smoker "smoker"
			R minecraft:crafting_table <- minecraft:crafting_table "crafting table"
			H minecraft:chest[facing=north,type=single] <- minecraft:chest "chest" cell=solid +#chest
			""", row("PKR_H_P"));

	public static final Blueprint BARRACKS = building("barracks", "Barracks", Tier.VILLAGE, "barracks", """
			G minecraft:grindstone[facing=north] <- minecraft:grindstone "grindstone"
			B minecraft:white_bed[facing=west,part=foot] <- #minecraft:beds "bed" cell=solid
			b minecraft:white_bed[facing=west,part=head] part=EAST cell=solid +#bed
			H minecraft:chest[facing=north,type=single] <- minecraft:chest "chest" cell=solid +#chest
			""", row("PbBHG_P"));

	public static final Blueprint LUMBER_CAMP = building("lumber_camp", "Lumber Camp", Tier.VILLAGE, "lumber_camp", """
			X villageheart:chopping_block <- villageheart:chopping_block "chopping block"
			H minecraft:chest[facing=north,type=single] <- minecraft:chest "chest" cell=solid +#chest
			""", row("PX__H_P"));

	public static final Blueprint FARMHOUSE = building("farmhouse", "Farmhouse", Tier.VILLAGE, "farmhouse", """
			O minecraft:composter[level=0] <- minecraft:composter "composter"
			H minecraft:chest[facing=north,type=single] <- minecraft:chest "chest" cell=solid +#chest
			""", row("PO__H_P"));

	public static final Blueprint MINE = building("mine", "Mine", Tier.VILLAGE, "mine", """
			U minecraft:stonecutter <- minecraft:stonecutter "stonecutter"
			H minecraft:chest[facing=north,type=single] <- minecraft:chest "chest" cell=solid +#chest
			""", row("PU__H_P"));

	public static final Blueprint HUNTING_LODGE = building("hunting_lodge", "Hunting Lodge", Tier.VILLAGE, "hunting_lodge", """
			F minecraft:fletching_table <- minecraft:fletching_table "fletching table"
			H minecraft:chest[facing=north,type=single] <- minecraft:chest "chest" cell=solid +#chest
			""", row("PF__H_P"));

	public static final Blueprint MARKET = building("market", "Market", Tier.VILLAGE, "market", """
			A villageheart:market_stall <- villageheart:market_stall "market stall"
			H minecraft:chest[facing=north,type=single] <- minecraft:chest "chest" cell=solid +#chest
			""", row("PA__H_P"));

	public static final Blueprint SMITHY = building("smithy", "Smithy", Tier.TOWN, "smithy", """
			F minecraft:blast_furnace[facing=north,lit=false] <- minecraft:blast_furnace "blast furnace"
			A minecraft:anvil[facing=north] <- minecraft:anvil "anvil" +#anvil
			H minecraft:chest[facing=north,type=single] <- minecraft:chest "chest" cell=solid +#chest
			""", row("PFAH__P"));

	public static final Blueprint STORAGE = building("storage_room", "Storage", Tier.VILLAGE, "storage", """
			H minecraft:chest[facing=north,type=single] <- minecraft:chest "chest" cell=solid +#chest
			""", row("PH___HP"));

	public static final Blueprint BUILDERS_HUT = building("builders_hut", "Builder's Hut", Tier.VILLAGE, "builders_hut", """
			A villageheart:builders_table <- villageheart:builders_table "builder's table"
			H minecraft:chest[facing=north,type=single] <- minecraft:chest "chest" cell=solid +#chest
			""", row("PA__H_P"));

	/** An outdoor yard: a fence, two targets and the marker. Not a room. */
	public static final Blueprint TRAINING_YARD = BlueprintParser.parse("""
			id training_yard
			name Training Yard
			tier VILLAGE
			kind BUILDING
			builds training_yard
			base 1
			anchor 2 2
			front NORTH
			size 5 2 5
			palette
			. any
			_ air
			f minecraft:oak_fence <- #minecraft:wooden_fences "fence"
			t minecraft:target <- minecraft:target "target"
			M villageheart:training_yard_marker[facing=north,status=unchecked] <- villageheart:training_yard_marker "Training Yard marker" cell=open
			layer 0
			fffff
			f_t_f
			f___f
			f_t_f
			ffMff
			layer 1
			_____
			_____
			_____
			_____
			_____
			end
			""");

	public static final Blueprint POSTED_WALL = defence("posted_wall", "Posted Wall", """
			L minecraft:oak_fence <- #minecraft:wooden_fences "fence"
			C minecraft:cobblestone <- minecraft:cobblestone "cobblestone" "cobblestone"
			""", "5 4 1", "LCLCL", "CCCCC", "CCCCC", "CCCCC");

	public static final Blueprint BATTLEMENT = defence("battlement", "Battlement", """
			C minecraft:cobblestone <- minecraft:cobblestone "cobblestone" "cobblestone"
			""", "5 4 1", "C_C_C", "CCCCC", "CCCCC", "CCCCC");

	public static final Blueprint WALKWAY = defence("walkway", "Walkway", """
			P minecraft:oak_planks <- #minecraft:planks "planks" "planks"
			f minecraft:oak_fence <- #minecraft:wooden_fences "fence"
			""", "5 2 2", "fffff\n.....", "PPPPP\nPPPPP");

	public static final Blueprint TOWER_PIECE = BlueprintParser.parse("""
			id tower_piece
			name Tower Piece
			tier VILLAGE
			kind DEFENCE
			builds none
			base 1
			anchor 1 1
			front NORTH
			size 3 5 3
			palette
			. any
			_ air
			C minecraft:cobblestone <- minecraft:cobblestone "cobblestone" "cobblestone"
			P minecraft:oak_planks <- #minecraft:planks "planks" "planks"
			layer 0
			CCC
			C_C
			CCC
			layer 1
			CCC
			C_C
			CCC
			layer 2
			CCC
			C_C
			CCC
			layer 3
			CCC
			CCC
			CCC
			layer 4
			PPP
			PPP
			PPP
			end
			""");

	public static final Blueprint GATE_PIECE = BlueprintParser.parse("""
			id gate_piece
			name Gate Piece
			tier VILLAGE
			kind DEFENCE
			builds none
			base 1
			anchor 2 0
			front NORTH
			size 5 3 1
			palette
			. any
			_ air
			C minecraft:cobblestone <- minecraft:cobblestone "cobblestone" "cobblestone"
			G minecraft:oak_fence_gate[facing=north,open=false] <- #minecraft:fence_gates "fence gate"
			layer 0
			CGGGC
			layer 1
			C___C
			layer 2
			CCCCC
			end
			""");

	private static Blueprint defence(String id, String name, String palette, String size, String... layers) {
		StringBuilder text = new StringBuilder();
		text.append("id ").append(id).append('\n');
		text.append("name ").append(name).append('\n');
		text.append("""
				tier VILLAGE
				kind DEFENCE
				builds none
				base 1
				anchor 0 0
				front NORTH
				size %s
				palette
				. any
				_ air
				""".formatted(size));
		text.append(palette);
		int height = Integer.parseInt(size.split(" ")[1]);
		int perLayer = layers.length / height;
		int i = 0;
		for (int y = 0; y < height; y++) {
			text.append("layer ").append(y).append('\n');
			for (int r = 0; r < perLayer; r++) text.append(layers[i++]).append('\n');
		}
		text.append("end\n");
		return BlueprintParser.parse(text.toString());
	}

	public static List<Blueprint> all() {
		List<Blueprint> out = new ArrayList<>();
		out.add(KITCHEN);
		out.add(BARRACKS);
		out.add(LUMBER_CAMP);
		out.add(FARMHOUSE);
		out.add(MINE);
		out.add(HUNTING_LODGE);
		out.add(MARKET);
		out.add(TRAINING_YARD);
		out.add(SMITHY);
		out.add(STORAGE);
		out.add(BUILDERS_HUT);
		out.add(POSTED_WALL);
		out.add(BATTLEMENT);
		out.add(WALKWAY);
		out.add(TOWER_PIECE);
		out.add(GATE_PIECE);
		return List.copyOf(out);
	}
}
