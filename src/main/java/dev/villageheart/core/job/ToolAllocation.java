package dev.villageheart.core.job;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.ToIntFunction;

/** Shares the tools of one camp between its workers: best tool first, workers in hiring order. */
public final class ToolAllocation {
	private ToolAllocation() {}

	/**
	 * A tool found in a camp container. {@code order} ranks ties: container order first, then slot order (lower wins). {@code remaining} is
	 * the durability left (M19a): among tools of the same rank the one with less left is given out first, so a worn tool is used up before
	 * its replacement.
	 */
	public record Tool<S>(S ref, ToolTier tier, int order, int remaining) {
		/** The M9 shape: no durability known (0 for every tool, so only the order decides among equals). */
		public Tool(S ref, ToolTier tier, int order) { this(ref, tier, order, 0); }
	}

	/** Workers in hiring order get the tools best first (by tier); a worker with no tool left is absent from the result. */
	public static <S> Map<UUID, S> assign(List<Tool<S>> tools, List<UUID> workersInHiringOrder) {
		return assign(tools, workersInHiringOrder, t -> t.tier().ordinal());
	}

	/** The same, with the job's own ranking: a higher rank is given out first (the hunter ranks bows above every sword, M17). */
	public static <S> Map<UUID, S> assign(List<Tool<S>> tools, List<UUID> workersInHiringOrder, ToIntFunction<Tool<S>> rank) {
		List<Tool<S>> sorted = new ArrayList<>(tools);
		sorted.sort(Comparator.<Tool<S>>comparingInt(t -> -rank.applyAsInt(t)).thenComparingInt(Tool::remaining).thenComparingInt(Tool::order));
		Map<UUID, S> out = new LinkedHashMap<>();
		int i = 0;
		for (UUID w : workersInHiringOrder) {
			if (i >= sorted.size()) break;
			out.put(w, sorted.get(i++).ref());
		}
		return out;
	}
}
