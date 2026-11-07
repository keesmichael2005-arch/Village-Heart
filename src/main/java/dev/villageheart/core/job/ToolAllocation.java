package dev.villageheart.core.job;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Shares the tools of one camp between its workers: best tool first, workers in hiring order. */
public final class ToolAllocation {
	private ToolAllocation() {}

	/** A tool found in a camp container. {@code order} ranks ties: container order first, then slot order (lower wins). */
	public record Tool<S>(S ref, ToolTier tier, int order) {}

	/** Workers in hiring order get the tools best first; a worker with no tool left is absent from the result. */
	public static <S> Map<UUID, S> assign(List<Tool<S>> tools, List<UUID> workersInHiringOrder) {
		List<Tool<S>> sorted = new ArrayList<>(tools);
		sorted.sort(Comparator.<Tool<S>>comparingInt(t -> -t.tier().ordinal()).thenComparingInt(Tool::order));
		Map<UUID, S> out = new LinkedHashMap<>();
		int i = 0;
		for (UUID w : workersInHiringOrder) {
			if (i >= sorted.size()) break;
			out.put(w, sorted.get(i++).ref());
		}
		return out;
	}
}
