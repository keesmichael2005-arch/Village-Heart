package dev.villageheart.worker.job;

import dev.villageheart.core.worker.WorkerStatus;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * The status a job reports for its worker ("Needs axe", "No trees within 32 blocks"). Transient (not saved): the job sets it again within
 * a second of the worker loading. {@code Workers.status} merges it with the slot and deposit-trip statuses. Server thread only.
 */
public final class WorkerStatuses {
	private static final Map<UUID, WorkerStatus> STATUS = new HashMap<>();

	private WorkerStatuses() {}

	/** Null clears. */
	public static void set(UUID villager, @Nullable WorkerStatus status) {
		if (status == null || status == WorkerStatus.OK) STATUS.remove(villager);
		else STATUS.put(villager, status);
	}

	@Nullable
	public static WorkerStatus get(UUID villager) { return STATUS.get(villager); }

	public static void clear(UUID villager) { STATUS.remove(villager); }

	public static void clearAll() { STATUS.clear(); }
}
