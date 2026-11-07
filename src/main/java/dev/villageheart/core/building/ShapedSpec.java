package dev.villageheart.core.building;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A shaped crafting recipe as data; renders to the 1.21.11 JSON format. */
public record ShapedSpec(List<String> pattern, Map<Character, String> key, String resultId, int count) {
	public ShapedSpec {
		pattern = List.copyOf(pattern);
		key = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(key));
		if (pattern.isEmpty() || pattern.size() > 3) throw new IllegalArgumentException("1..3 rows");
		int w = pattern.get(0).length();
		if (w < 1 || w > 3) throw new IllegalArgumentException("width 1..3");
		Set<Character> used = new LinkedHashSet<>();
		for (String row : pattern) {
			if (row.length() != w) throw new IllegalArgumentException("ragged rows");
			for (char c : row.toCharArray()) if (c != ' ') used.add(c);
		}
		for (char c : used) if (!key.containsKey(c)) throw new IllegalArgumentException("no key for '" + c + "'");
		for (Map.Entry<Character, String> e : key.entrySet()) {
			if (!used.contains(e.getKey())) throw new IllegalArgumentException("unused key '" + e.getKey() + "'");
			Ingredients.require(e.getValue());
		}
		Ingredients.require(resultId);
		if (count < 1) throw new IllegalArgumentException("count >= 1");
	}

	public Map<String, Object> toJson() {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("type", "minecraft:crafting_shaped");
		m.put("category", "misc");
		Map<String, Object> k = new LinkedHashMap<>();
		for (Map.Entry<Character, String> e : key.entrySet()) k.put(String.valueOf(e.getKey()), e.getValue());
		m.put("key", k);
		m.put("pattern", new java.util.ArrayList<Object>(pattern));
		Map<String, Object> r = new LinkedHashMap<>();
		r.put("count", (long) count);
		r.put("id", resultId);
		m.put("result", r);
		return m;
	}

	/** The 9-slot grid (row-major, null = empty) this recipe expects when placed at the top-left. */
	public List<String> grid() {
		java.util.ArrayList<String> g = new java.util.ArrayList<>();
		for (int r = 0; r < 3; r++) for (int c = 0; c < 3; c++) {
			String ing = null;
			if (r < pattern.size() && c < pattern.get(0).length()) {
				char ch = pattern.get(r).charAt(c);
				if (ch != ' ') ing = key.get(ch);
			}
			g.add(ing);
		}
		return g;
	}
}
