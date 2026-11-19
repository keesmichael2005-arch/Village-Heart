package dev.villageheart.build;

import dev.villageheart.build.net.SchematicSyncPayload;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.blueprint.BuildJob;
import dev.villageheart.world.VillageClaims;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/** The placed plans a client may draw. Sending is capped; placing is not. */
public final class Schematics {
	private Schematics() {}

	public static void send(ServerPlayer player) {
		List<SchematicSyncPayload.Site> sites = new ArrayList<>();
		Optional<VillageState> village = VillageClaims.current(player.level().getServer());
		if (village.isPresent() && player.level().dimension().identifier().toString().equals(village.get().heart().dimension())) {
			for (BuildJob job : village.get().builds()) {
				if (sites.size() == SchematicSyncPayload.MAX) break;
				var ground = job.ground();
				sites.add(new SchematicSyncPayload.Site(job.blueprintId(), ground.x(), ground.y(), ground.z(), job.rotation().ordinal(),
						job.params().length()));
			}
		}
		ServerPlayNetworking.send(player, new SchematicSyncPayload(sites));
	}
}
