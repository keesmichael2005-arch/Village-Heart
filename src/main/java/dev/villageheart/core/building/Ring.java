package dev.villageheart.core.building;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The 3x3 ring around the Blank Marker: four identical corners and four edge slots. */
public record Ring(String corner, String top, String left, String right, String bottom) {
	public Ring {
		Ingredients.require(corner);
		Ingredients.require(top);
		Ingredients.require(left);
		Ingredients.require(right);
		Ingredients.require(bottom);
	}

	public static Ring uniform(String corner, String edge) { return new Ring(corner, edge, edge, edge, edge); }

	/** The tool sits on the TOP edge (directly above the marker); the other three edges are {@code edge}. */
	public static Ring withTool(String corner, String tool, String edge) { return new Ring(corner, tool, edge, edge, edge); }

	/** 9-slot row-major grid with the Blank Marker in the centre. */
	public List<String> grid() {
		List<String> g = new ArrayList<>();
		g.add(corner); g.add(top); g.add(corner);
		g.add(left); g.add(Ingredients.BLANK_MARKER); g.add(right);
		g.add(corner); g.add(bottom); g.add(corner);
		return g;
	}

	public ShapedSpec recipe(String resultId, int count) {
		Map<String, Character> letters = new LinkedHashMap<>();
		letters.put(corner, 'C');
		letters.putIfAbsent(left, 'E');
		letters.putIfAbsent(top, 'T');
		letters.putIfAbsent(right, 'R');
		letters.putIfAbsent(bottom, 'B');
		letters.putIfAbsent(Ingredients.BLANK_MARKER, '#');
		char c = letters.get(corner), t = letters.get(top), l = letters.get(left), r = letters.get(right), b = letters.get(bottom);
		List<String> pattern = List.of("" + c + t + c, "" + l + '#' + r, "" + c + b + c);
		Map<Character, String> key = new LinkedHashMap<>();
		for (Map.Entry<String, Character> e : letters.entrySet()) key.put(e.getValue(), e.getKey());
		return new ShapedSpec(pattern, key, resultId, count);
	}
}
