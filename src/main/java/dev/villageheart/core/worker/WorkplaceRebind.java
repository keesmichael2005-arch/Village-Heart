package dev.villageheart.core.worker;

import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.WorkerRecord;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What happens to a hired worker when a workplace changes. A building that is still registered, including a red one, keeps its workers.
 * A building that is gone releases them onto the waiting list. In hiring order, each takes the first free job block of their own job, in
 * the same order a new hire would ({@link JobSlots}). One free block and two waiting: the earlier hire gets it. The other keeps the old
 * building location on the record. Level and job stay. The caller passes only valid workplaces of each job, so a House is never a workplace.
 */
public final class WorkplaceRebind {
	private WorkplaceRebind() {}

	/**
	 * @param workers      hiring order
	 * @param registered   marker locations that still have a building record, valid or red
	 * @param sitesByJob   valid workplaces of each job, in the order a new hire sees them
	 */
	public static List<WorkerRecord> apply(List<WorkerRecord> workers, Set<HeartLocation> registered,
			Map<String, List<SiteView>> sitesByJob) {
		List<WorkerRecord> current = new ArrayList<>(workers);
		List<WorkerRecord> out = new ArrayList<>(workers.size());
		for (int i = 0; i < workers.size(); i++) {
			WorkerRecord w = current.get(i);
			if (registered.contains(w.building())) {
				out.add(w);
				continue;
			}
			WorkerRecord moved = take(w, sitesByJob.getOrDefault(w.jobId(), List.of()), current);
			current.set(i, moved);
			out.add(moved);
		}
		return List.copyOf(out);
	}

	/** The first free slot of {@code sites}, or {@code w} unchanged when there is none. */
	private static WorkerRecord take(WorkerRecord w, List<SiteView> sites, List<WorkerRecord> current) {
		Set<HeartLocation> held = new HashSet<>();
		for (WorkerRecord o : current) held.add(o.jobBlock());
		for (SiteView s : sites) {
			for (HeartLocation b : JobSlots.slotBlocks(s, current)) {
				if (held.contains(b)) continue;
				return w.withPlace(s.building(), b);
			}
		}
		return w;
	}
}
