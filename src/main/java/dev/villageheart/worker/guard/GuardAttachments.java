package dev.villageheart.worker.guard;

import dev.villageheart.VillageHeart;
import dev.villageheart.core.guard.GuardData;
import dev.villageheart.world.GuardCodecs;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

/** The guard attachment (M18 3.3): what a guard remembers across a reload. Persistent, no sync, no copy on conversion. */
public final class GuardAttachments {
	public static final AttachmentType<GuardData> GUARD = AttachmentRegistry.createPersistent(
			Identifier.fromNamespaceAndPath(VillageHeart.MOD_ID, "guard"), GuardCodecs.GUARD_DATA);

	private GuardAttachments() {}

	/** Touch the class during mod init so the persistent type is registered before any entity loads. */
	public static void register() {}

	public static GuardData get(Entity e) {
		GuardData d = e.getAttached(GUARD);
		return d == null ? GuardData.FRESH : d;
	}

	public static void set(Entity e, GuardData d) {
		if (d.equals(GuardData.FRESH)) e.removeAttached(GUARD); else e.setAttached(GUARD, d);
	}
}
