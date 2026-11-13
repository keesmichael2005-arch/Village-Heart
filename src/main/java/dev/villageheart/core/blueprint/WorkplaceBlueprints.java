package dev.villageheart.core.blueprint;

import dev.villageheart.core.Tier;

/**
 * One starter plan for every workplace, drawn from the plains village cottage (log corners, cobblestone, glass, a stair roof)
 * so a player can set the shape down and already have the right blocks inside. The Barracks cottage is wider and carries the
 * Training Yard on its east side. The Hunting Grove is the outdoor ground that goes with the lodge. The Mineshaft stays
 * player-dug: a 12-block hole is beyond the builder's reach from the stand ring.
 */
public final class WorkplaceBlueprints {
	private WorkplaceBlueprints() {}

	/** The 7×8×5 plank shell shared with the Small House. Layer 0 is the floor, the door faces south, the marker hangs on the front wall. */
	static String shell(String palette, String torchRow, String backRow, String marker) {
		return """
				palette
				. any
				_ air
				C minecraft:cobblestone <- minecraft:cobblestone "cobblestone" "cobblestone"
				P minecraft:oak_planks <- #minecraft:planks "planks" "planks"
				D minecraft:oak_door[facing=south,half=lower] <- #minecraft:wooden_doors "door" cell=solid +#door
				d minecraft:oak_door[facing=south,half=upper] part=DOWN cell=solid
				""" + palette + """
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

	/**
	 * The plains cottage, door on the front, cropped out of the vanilla house and turned to face the player.
	 * Six layers: the ridge of the roof is flattened so a builder can still reach it. {@code z = 4} on layer 1 is the
	 * interior row the workplace's blocks replace.
	 */
	private static final String[][] COTTAGE = {
			{".....", "LCCCL", "CPPPC", "CPPPC", "CPPPC", "LCCCL"},
			{".....", "LCDCL", "C___C", "C___C", "C___C", "LCCCL"},
			{".....", "LCdCL", "C___C", "G___G", "C___C", "LCGCL"},
			{"..M..", "LCCCL", "C___C", "C___C", "C_T_C", "LCCCL"},
			{".....", "PPPPP", "P___P", "P___P", "P___P", "PPPPP"},
			{".....", "SSSSS", "EPPPW", "EPPPW", "EPPPW", "ENNNW"},
	};

	private static Blueprint village(String id, String name, Tier tier, String builds, String furniture, String furn, boolean widen, boolean deep, boolean yard) {
		String[][] layers = new String[COTTAGE.length][];
		for (int y = 0; y < COTTAGE.length; y++) {
			String[] rows = COTTAGE[y].clone();
			if (widen) for (int z = 0; z < rows.length; z++) rows[z] = rows[z].substring(0, 4) + rows[z].charAt(3) + rows[z].substring(4);
			if (y == 1) {
				String row = rows[4];
				int a = row.indexOf('_'), b = row.lastIndexOf('_');
				rows[4] = row.substring(0, a) + furn + row.substring(b + 1);
			}
			if (deep) {
				String[] grown = new String[rows.length + 1];
				System.arraycopy(rows, 0, grown, 0, 3);
				grown[3] = rows[2];
				System.arraycopy(rows, 3, grown, 4, rows.length - 3);
				rows = grown;
			}
			if (yard) for (int z = 0; z < rows.length; z++) rows[z] = rows[z] + yardCells(y, z);
			layers[y] = rows;
		}
		int width = layers[0][0].length();
		int depth = layers[0].length;
		StringBuilder body = new StringBuilder();
		for (int y = 0; y < layers.length; y++) {
			body.append("layer ").append(y).append('\n');
			for (String row : layers[y]) body.append(row).append('\n');
		}
		body.append("end\n");
		String text = """
				id %s
				name %s
				tier %s
				kind BUILDING
				builds %s
				base 1
				anchor 2 1
				front NORTH
				size %d %d %d
				palette
				. any
				_ air
				C minecraft:cobblestone <- minecraft:cobblestone "cobblestone" "cobblestone"
				P minecraft:oak_planks <- #minecraft:planks "planks" "planks"
				L minecraft:stripped_oak_log[axis=y] <- minecraft:stripped_oak_log "stripped log" "stripped logs" cell=solid +#natural
				S minecraft:oak_stairs[facing=south,half=bottom,shape=straight] <- minecraft:oak_stairs "oak stairs" "oak stairs"
				E minecraft:oak_stairs[facing=east,half=bottom,shape=straight] <- minecraft:oak_stairs "oak stairs" "oak stairs"
				W minecraft:oak_stairs[facing=west,half=bottom,shape=straight] <- minecraft:oak_stairs "oak stairs" "oak stairs"
				N minecraft:oak_stairs[facing=north,half=bottom,shape=straight] <- minecraft:oak_stairs "oak stairs" "oak stairs"
				G minecraft:glass_pane <- minecraft:glass_pane "glass pane" "glass panes"
				D minecraft:oak_door[facing=south,half=lower] <- #minecraft:wooden_doors "door" cell=solid +#door
				d minecraft:oak_door[facing=south,half=upper] part=DOWN cell=solid
				T minecraft:wall_torch[facing=north] <- minecraft:torch "torch" "torches" pass=attached cell=open +#light
				M villageheart:%s_marker[facing=north,status=unchecked] <- villageheart:%s_marker "%s marker" pass=attached cell=open
				""".formatted(id, name, tier.name(), builds, width, layers.length, depth, builds, builds, name)
				+ furniture + body;
		return BlueprintParser.parse(text);
	}

	/** A fenced pad on the east side of the widened cottage. The yard marker hangs on the cottage wall. */
	private static String yardCells(int y, int z) {
		if (y == 0 && z >= 2 && z <= 4) return "CCCCC";
		if (y == 1 && z >= 2 && z <= 4) return "f_ttf";
		if (y == 2 && z == 2) return "Y___f";
		if (y == 2 && z == 3) return "f___f";
		if (y == 2 && z == 4) return "fffff";
		return ".....";
	}

	private static final String CHEST =
			"K minecraft:chest[facing=north,type=single] <- minecraft:chest \"chest\" cell=solid +#chest\n";

	public static final Blueprint FARMHOUSE = village("farmhouse", "Farmhouse", Tier.VILLAGE, "farmhouse", """
			O minecraft:composter <- minecraft:composter "composter" cell=solid
			""" + CHEST, "O_K", false, false, false);

	public static final Blueprint LUMBER_CAMP = village("lumber_camp", "Lumber Camp", Tier.VILLAGE, "lumber_camp", """
			J villageheart:chopping_block[facing=north] <- villageheart:chopping_block "chopping block" cell=solid
			""" + CHEST, "J_K", false, false, false);

	public static final Blueprint KITCHEN = village("kitchen", "Kitchen", Tier.VILLAGE, "kitchen", """
			Q minecraft:smoker[facing=north,lit=false] <- minecraft:smoker "smoker" cell=solid
			R minecraft:crafting_table <- minecraft:crafting_table "crafting table" cell=solid
			""" + CHEST, "QRK", false, false, false);

	public static final Blueprint BUILDERS_HUT = village("builders_hut", "Builder's Hut", Tier.VILLAGE, "builders_hut", """
			J villageheart:builders_table[facing=north] <- villageheart:builders_table "builder's table" cell=solid
			""" + CHEST, "J_K", false, false, false);

	public static final Blueprint MINE = village("mine", "Mine", Tier.VILLAGE, "mine", """
			J minecraft:stonecutter <- minecraft:stonecutter "stonecutter" cell=solid
			""" + CHEST, "J_K", false, false, false);

	public static final Blueprint HUNTING_LODGE = village("hunting_lodge", "Hunting Lodge", Tier.VILLAGE, "hunting_lodge", """
			J minecraft:fletching_table <- minecraft:fletching_table "fletching table" cell=solid
			""" + CHEST, "J_K", false, false, false);

	public static final Blueprint MARKET = village("market", "Market", Tier.VILLAGE, "market", """
			J villageheart:market_stall[facing=north] <- villageheart:market_stall "market stall" cell=solid
			""" + CHEST, "J_K", false, false, false);

	public static final Blueprint SMITHY = village("smithy", "Smithy", Tier.TOWN, "smithy", """
			F minecraft:blast_furnace[facing=north,lit=false] <- minecraft:blast_furnace "blast furnace" cell=solid
			A minecraft:anvil[facing=north] <- minecraft:anvil "anvil" cell=solid +#anvil
			""" + CHEST, "FA_K", true, false, false);

	public static final Blueprint BARRACKS = village("barracks", "Barracks", Tier.VILLAGE, "barracks", """
			B minecraft:white_bed[facing=west,part=foot] <- #minecraft:beds "bed" cell=solid
			b minecraft:white_bed[facing=west,part=head] part=EAST cell=solid +#bed
			g minecraft:grindstone[facing=north,face=floor] <- minecraft:grindstone "grindstone" cell=solid
			""" + CHEST + """
			f minecraft:oak_fence <- #minecraft:wooden_fences "fence"
			t minecraft:target <- minecraft:target "target" "targets" cell=solid
			Y villageheart:training_yard_marker[facing=east,status=unchecked] <- villageheart:training_yard_marker "Training Yard marker" pass=attached cell=open
			""", "bBgK", true, true, true);

	/** Logs and a leaf canopy. The marker hangs on the log row just inside the north edge, outside any room. */
	private static final String GROVE_TEXT = """
			id hunting_grove
			name Hunting Grove
			tier VILLAGE
			kind BUILDING
			builds hunting_ground
			base 1
			anchor 3 3
			front NORTH
			size 7 3 7
			palette
			. any
			_ air
			L minecraft:oak_log[axis=y] <- minecraft:oak_log "oak log" "oak logs" cell=solid +#natural
			V minecraft:oak_leaves[persistent=true] <- minecraft:oak_leaves "oak leaves" "oak leaves" cell=solid +#natural
			M villageheart:hunting_ground_marker[facing=north,status=unchecked] <- villageheart:hunting_ground_marker "Hunting Ground marker" pass=attached cell=open
			layer 0
			_L_L_L_
			L_L_L_L
			_L_L_L_
			L_L_L_L
			_L_L_L_
			L_L_L_L
			_L_L_L_
			layer 1
			...M...
			LLLLLLL
			_______
			_______
			_______
			_______
			_______
			layer 2
			VVVVVVV
			VVVVVVV
			VVVVVVV
			VVV_VVV
			VVVVVVV
			VVVVVVV
			VVVVVVV
			end
			""";

	public static final Blueprint HUNTING_GROVE = BlueprintParser.parse(GROVE_TEXT);
}
