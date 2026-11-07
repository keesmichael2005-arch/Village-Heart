package dev.villageheart.worker;

import dev.villageheart.VillageHeart;
import dev.villageheart.core.worker.WorkerData;
import dev.villageheart.world.WorkerCodecs;
import java.util.Optional;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

/**
 * The worker attachment on a villager: job, building, job block, level, xp, needs. Persistent (saved with the entity). No
 * {@code copyOnDeath} (so it is not copied by mob conversion) and no client sync (the screens use their own packets).
 */
public final class WorkerAttachments {
	public static final AttachmentType<WorkerData> WORKER = AttachmentRegistry.createPersistent(
			Identifier.fromNamespaceAndPath(VillageHeart.MOD_ID, "worker"), WorkerCodecs.WORKER_DATA);

	private WorkerAttachments() {}

	/** Touch the class during mod init so the persistent type is registered before any entity loads. */
	public static void register() {}

	public static boolean isWorker(Entity e) { return e.getAttached(WORKER) != null; }

	public static Optional<WorkerData> get(Entity e) { return Optional.ofNullable(e.getAttached(WORKER)); }
}
