package dev.villageheart.core.economy;

import dev.villageheart.core.WorkerRecord;
import java.util.ArrayList;
import java.util.List;

/** Who is on the payroll. Every registered worker, whatever has happened to their workplace. */
public final class WageRoster {
	private WageRoster() {}

	public static List<Payee> of(List<WorkerRecord> workers) {
		List<Payee> out = new ArrayList<>(workers.size());
		for (WorkerRecord w : workers) out.add(new Payee(w.villager(), WageRules.wage(w.level())));
		return List.copyOf(out);
	}
}
