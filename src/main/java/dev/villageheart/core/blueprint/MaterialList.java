package dev.villageheart.core.blueprint;

import java.util.List;

/** What a blueprint costs: one line per material, in order of first use. */
public record MaterialList(List<Line> lines) {
	public record Line(Material material, int count) {}

	public MaterialList {
		lines = List.copyOf(lines);
	}

	public int count(String materialId) {
		for (Line l : lines) if (l.material().id().equals(materialId)) return l.count();
		return 0;
	}

	public int total() {
		int n = 0;
		for (Line l : lines) n += l.count();
		return n;
	}
}
