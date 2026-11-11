package dev.villageheart.build;

import com.mojang.serialization.Codec;
import dev.villageheart.VillageHeart;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;

/** The two data components of the Blueprint item: which blueprint it is and the site it currently shows. */
public final class ModComponents {
	private ModComponents() {}

	/** The id of the blueprint ({@code small_house}). */
	public static final DataComponentType<String> BLUEPRINT = DataComponentType.<String>builder()
			.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build();

	/** The site shown by the item: set by the first click, changed by a rotation, gone when the item is used up. */
	public static final DataComponentType<BlueprintSite> BLUEPRINT_SITE = DataComponentType.<BlueprintSite>builder()
			.persistent(BlueprintSite.CODEC).networkSynchronized(BlueprintSite.STREAM_CODEC).build();

	public static void register() {
		BlueprintQuill.registerComponent();
		Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Identifier.fromNamespaceAndPath(VillageHeart.MOD_ID, "blueprint"), BLUEPRINT);
		Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Identifier.fromNamespaceAndPath(VillageHeart.MOD_ID, "blueprint_site"), BLUEPRINT_SITE);
	}
}
