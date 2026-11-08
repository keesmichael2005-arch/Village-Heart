package dev.villageheart.core.blueprint;

import dev.villageheart.core.Balance;
import java.util.Optional;

/**
 * The defence blueprints of M21 (docs/plans/M21.md 3.2 #2-#4), as text parsed by the normal {@link BlueprintParser}. The wall and the gate
 * are generated for every length (1..32 and 5..32) at class init; the lamp post and the watchtower are fixed.
 */
public final class DefenceBlueprints {
	private DefenceBlueprints() {}

	public static final String WALL_ID = "wall_segment";
	public static final String GATE_ID = "gate";
	public static final String LAMP_POST_ID = "lamp_post";
	public static final String WATCHTOWER_ID = "watchtower";

	private static final String COBBLE = "C minecraft:cobblestone <- minecraft:cobblestone \"cobblestone\" \"cobblestone\"\n";

	private static String header(String id, String name, String tier, String mode, String size, String anchor) {
		return "id " + id + "\nname " + name + "\ntier " + tier + "\nkind DEFENCE\n" + (mode == null ? "" : "mode " + mode + "\n")
				+ "builds none\nbase 1\nanchor " + anchor + "\nfront NORTH\nsize " + size + "\n";
	}

	/** @param n blocks along the line, 1 or more */
	static String wallText(int n) {
		String row = "C".repeat(n) + "\n";
		return header(WALL_ID, "Wall Segment", "VILLAGE", "LINE", n + " 3 1", "0 0") + "palette\n" + COBBLE
				+ "layer 0\n" + row + "layer 1\n" + row + "layer 2\n" + row + "end\n";
	}

	/** The opening is the three columns {@code o .. o + 2}, o = (n - 3) / 2: gate in layer 0, air above, a lintel on top. */
	static String gateText(int n) {
		int o = (n - 3) / 2;
		String low = "C".repeat(o) + "GGG" + "C".repeat(n - o - 3) + "\n";
		String mid = "C".repeat(o) + "___" + "C".repeat(n - o - 3) + "\n";
		return header(GATE_ID, "Gate", "VILLAGE", "LINE", n + " 3 1", "0 0") + "palette\n_ air\n" + COBBLE
				+ "G minecraft:oak_fence_gate[facing=north,open=false] <- #minecraft:fence_gates \"fence gate\"\n"
				+ "layer 0\n" + low + "layer 1\n" + mid + "layer 2\n" + "C".repeat(n) + "\nend\n";
	}

	private static final String LAMP_POST_TEXT = header(LAMP_POST_ID, "Lamp Post", "VILLAGE", null, "1 3 1", "0 0") + """
			palette
			f minecraft:oak_fence <- #minecraft:wooden_fences "fence"
			L minecraft:lantern[hanging=false] <- minecraft:lantern "lantern" pass=attached cell=open
			layer 0
			f
			layer 1
			f
			layer 2
			L
			end
			""";

	private static final String WATCHTOWER_TEXT = header(WATCHTOWER_ID, "Watchtower", "TOWN", null, "5 7 5", "2 2") + """
			palette
			. any
			_ air
			C minecraft:cobblestone <- minecraft:cobblestone "cobblestone" "cobblestone"
			P minecraft:oak_planks <- #minecraft:planks "planks" "planks"
			f minecraft:oak_fence <- #minecraft:wooden_fences "fence"
			H minecraft:ladder[facing=north] <- minecraft:ladder "ladder" pass=attached cell=open
			layer 0
			.....
			.C_C.
			.CHC.
			.CCC.
			.....
			layer 1
			.....
			.C_C.
			.CHC.
			.CCC.
			.....
			layer 2
			.....
			.CCC.
			.CHC.
			.CCC.
			.....
			layer 3
			.....
			.CCC.
			.CHC.
			.CCC.
			.....
			layer 4
			.....
			.CCC.
			.CHC.
			.CCC.
			.....
			layer 5
			PPPPP
			PPPPP
			PPHPP
			PPPPP
			PPPPP
			layer 6
			fffff
			f___f
			f_._f
			f___f
			fffff
			end
			""";

	private static final Blueprint[] WALLS = new Blueprint[Balance.MAX_WALL_LENGTH + 1];
	private static final Blueprint[] GATES = new Blueprint[Balance.MAX_WALL_LENGTH + 1];
	public static final Blueprint LAMP_POST = BlueprintParser.parse(LAMP_POST_TEXT);
	public static final Blueprint WATCHTOWER = BlueprintParser.parse(WATCHTOWER_TEXT);

	static {
		for (int n = 1; n <= Balance.MAX_WALL_LENGTH; n++) WALLS[n] = BlueprintParser.parse(wallText(n));
		for (int n = Balance.GATE_MIN_LENGTH; n <= Balance.MAX_WALL_LENGTH; n++) GATES[n] = BlueprintParser.parse(gateText(n));
	}

	/** The wall of {@code n} blocks, 1..32. */
	public static Blueprint wall(int n) {
		if (n < 1 || n > Balance.MAX_WALL_LENGTH) throw new IllegalArgumentException("wall length " + n);
		return WALLS[n];
	}

	/** The gate section of {@code n} blocks, 5..32. */
	public static Blueprint gate(int n) {
		if (n < Balance.GATE_MIN_LENGTH || n > Balance.MAX_WALL_LENGTH) throw new IllegalArgumentException("gate length " + n);
		return GATES[n];
	}

	/** The shortest allowed line of a LINE blueprint id, or 0 when the id is not a line blueprint. */
	public static int minLength(String id) {
		return switch (id) {
			case WALL_ID -> 1;
			case GATE_ID -> Balance.GATE_MIN_LENGTH;
			default -> 0;
		};
	}

	/** The generated instance of a LINE blueprint, empty when the id is not one or the length is out of range. */
	public static Optional<Blueprint> line(String id, int length) {
		int min = minLength(id);
		if (min == 0 || length < min || length > Balance.MAX_WALL_LENGTH) return Optional.empty();
		return Optional.of(id.equals(WALL_ID) ? WALLS[length] : GATES[length]);
	}
}
