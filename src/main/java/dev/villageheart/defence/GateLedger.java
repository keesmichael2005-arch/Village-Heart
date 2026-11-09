package dev.villageheart.defence;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.VillageState;
import dev.villageheart.world.VillageCodecs;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * The fence gates a villager opened and the keeper still has to close (human decision 2026-11-09). Saved with the world, so a gate that
 * was open when the server stopped is closed after the reload, and a gate in an unloaded chunk is closed when it loads. Gates are listed
 * by position; the ledger knows nothing else.
 */
public final class GateLedger extends SavedData {
	public static final Codec<GateLedger> CODEC = RecordCodecBuilder.create(i -> i.group(
			VillageCodecs.HEART_LOCATION.listOf().optionalFieldOf("open_gates", List.of()).forGetter(GateLedger::sorted)
	).apply(i, GateLedger::new));
	// DataFixTypes is null on purpose, as for the village data
	public static final SavedDataType<GateLedger> TYPE = new SavedDataType<>("villageheart_gates", GateLedger::new, CODEC, null);

	private final Set<HeartLocation> open = new LinkedHashSet<>();

	public GateLedger() {}

	private GateLedger(List<HeartLocation> gates) { open.addAll(gates); }

	private List<HeartLocation> sorted() {
		List<HeartLocation> l = new ArrayList<>(open);
		l.sort(VillageState.locationOrder());
		return l;
	}

	public static GateLedger of(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public Set<HeartLocation> gates() { return new LinkedHashSet<>(open); }

	public void add(HeartLocation g) {
		if (open.add(g)) setDirty();
	}

	public void remove(HeartLocation g) {
		if (open.remove(g)) setDirty();
	}

	public void clear() {
		if (!open.isEmpty()) {
			open.clear();
			setDirty();
		}
	}
}
