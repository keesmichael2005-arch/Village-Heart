package dev.villageheart.food;

import dev.villageheart.VillageHeart;
import dev.villageheart.core.needs.Diet;
import dev.villageheart.world.NeedsCodecs;
import java.util.Optional;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

/**
 * The hunger of a villager, worker or not. Persistent; no {@code copyOnDeath}, so a cured zombie villager starts fed; no client sync.
 */
public final class DietAttachments {
	public static final AttachmentType<Diet> DIET = AttachmentRegistry.createPersistent(
			Identifier.fromNamespaceAndPath(VillageHeart.MOD_ID, "diet"), NeedsCodecs.DIET);

	private DietAttachments() {}

	/** Touch the class during mod init so the persistent type is registered before any entity loads. */
	public static void register() {}

	/** The villager's diet under this claim; fresh (fed) when it has none or it is from another claim. */
	public static Diet read(Entity e, long claim) { return Diet.read(Optional.ofNullable(e.getAttached(DIET)), claim); }

	public static void write(Entity e, Diet d) { e.setAttached(DIET, d); }
}
