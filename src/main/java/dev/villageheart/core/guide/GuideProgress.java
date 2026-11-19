package dev.villageheart.core.guide;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Tasks the village has already done. Saved with the claim. Unknown ids are dropped on read. */
public record GuideProgress(Set<String> done) {
	public static final GuideProgress EMPTY = new GuideProgress(Set.of());

	public GuideProgress {
		done = Set.copyOf(done);
	}

	public boolean has(String id) { return done.contains(id); }

	public boolean isEmpty() { return done.isEmpty(); }

	/** Stable order for the save. */
	public List<String> ids() { return done.stream().sorted().toList(); }

	public static GuideProgress parse(List<String> ids) {
		Set<String> ok = new LinkedHashSet<>();
		for (String id : ids) if (id != null && GuideTask.byId(id).isPresent()) ok.add(id);
		return new GuideProgress(ok);
	}

	public GuideProgress plus(Set<String> more) {
		if (done.containsAll(more)) return this;
		Set<String> next = new LinkedHashSet<>(done);
		next.addAll(more);
		return new GuideProgress(next);
	}
}
