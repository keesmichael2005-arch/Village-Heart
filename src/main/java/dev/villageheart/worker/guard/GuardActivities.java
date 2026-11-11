package dev.villageheart.worker.guard;

import dev.villageheart.VillageHeart;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.schedule.Activity;

/** The two mod activities of a guard's brain (M18 3.2 #3); the third, sleeping, is vanilla REST. */
public final class GuardActivities {
	/** On shift: equip, patrol, fight. */
	public static final Activity DUTY = Registry.register(BuiltInRegistries.ACTIVITY,
			Identifier.fromNamespaceAndPath(VillageHeart.MOD_ID, "guard_duty"), new Activity("guard_duty"));
	/** Off shift: hand in gear, spar, defend itself. The brain's default, so a brain dropped to its default never freezes. */
	public static final Activity OFF = Registry.register(BuiltInRegistries.ACTIVITY,
			Identifier.fromNamespaceAndPath(VillageHeart.MOD_ID, "guard_off"), new Activity("guard_off"));

	private GuardActivities() {}

	/** Touch the class during mod init so the activities are registered before any brain is built. */
	public static void register() {}
}
