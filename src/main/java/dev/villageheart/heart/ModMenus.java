package dev.villageheart.heart;

import dev.villageheart.VillageHeart;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

public final class ModMenus {
	private ModMenus() {}

	/** The Heart screen: a menu with no slots, opened with the first snapshot as opening data. */
	public static final ExtendedScreenHandlerType<HeartMenu, HeartOpenData> HEART = Registry.register(BuiltInRegistries.MENU,
			Identifier.fromNamespaceAndPath(VillageHeart.MOD_ID, "heart"),
			new ExtendedScreenHandlerType<>(HeartMenu::new, HeartOpenData.STREAM_CODEC));

	/** M20: the Builder's Table screen, also a menu with no slots. */
	public static final ExtendedScreenHandlerType<dev.villageheart.build.BuildersTableMenu, dev.villageheart.build.BuildersTableOpenData> BUILDERS_TABLE =
			Registry.register(BuiltInRegistries.MENU, Identifier.fromNamespaceAndPath(VillageHeart.MOD_ID, "builders_table"),
					new ExtendedScreenHandlerType<>(dev.villageheart.build.BuildersTableMenu::new, dev.villageheart.build.BuildersTableOpenData.STREAM_CODEC));

	/** A hired villager's pocket. Read-only. */
	public static final ExtendedScreenHandlerType<dev.villageheart.worker.pocket.PocketMenu, dev.villageheart.worker.pocket.PocketMenu.View> POCKET =
			Registry.register(BuiltInRegistries.MENU, Identifier.fromNamespaceAndPath(VillageHeart.MOD_ID, "pocket"),
					new ExtendedScreenHandlerType<>(dev.villageheart.worker.pocket.PocketMenu::new, dev.villageheart.worker.pocket.PocketMenu.VIEW));

	/** Touch the class during mod init so the menu type is registered before any client joins. */
	public static void register() {}
}
