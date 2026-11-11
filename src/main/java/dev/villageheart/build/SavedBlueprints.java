package dev.villageheart.build;

import com.mojang.serialization.Codec;
import dev.villageheart.VillageHeart;
import dev.villageheart.core.blueprint.Blueprint;
import dev.villageheart.core.blueprint.BlueprintParser;
import dev.villageheart.core.blueprint.Blueprints;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Named blueprints a player saved with the Quill or the design world. No cap. Loaded with the world. */
public final class SavedBlueprints extends SavedData {
	public static final Codec<SavedBlueprints> CODEC = Codec.STRING.listOf().xmap(SavedBlueprints::new, SavedBlueprints::texts)
			.fieldOf("texts").codec();
	public static final SavedDataType<SavedBlueprints> TYPE = new SavedDataType<>("villageheart_blueprints", SavedBlueprints::new, CODEC, null);

	private final List<String> texts = new ArrayList<>();

	public SavedBlueprints() {}

	private SavedBlueprints(List<String> saved) { texts.addAll(saved); }

	private List<String> texts() { return List.copyOf(texts); }

	public static SavedBlueprints of(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public static void loadInto(MinecraftServer server) {
		for (String text : of(server).texts) {
			try {
				Blueprint bp = BlueprintParser.parse(text);
				Blueprints.addDynamic(bp);
			} catch (RuntimeException e) {
				VillageHeart.LOG.warn("Dropped a saved blueprint: {}", e.getMessage());
			}
		}
	}

	public void add(String text) {
		texts.add(text);
		setDirty();
	}

	public void removeId(String id) {
		if (texts.removeIf(t -> t.startsWith("id " + id + "\n") || t.startsWith("id " + id + " "))) setDirty();
	}
}
