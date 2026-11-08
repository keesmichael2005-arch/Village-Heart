package dev.villageheart.build;

import dev.villageheart.core.Rank;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.blueprint.Blueprint;
import dev.villageheart.core.blueprint.Blueprints;
import dev.villageheart.core.blueprint.TableSnapshot;
import dev.villageheart.core.blueprint.TableSnapshot.TableBlueprint;
import dev.villageheart.core.blueprint.TableSnapshot.TableBuild;
import dev.villageheart.core.blueprint.TableSnapshot.TableMaterial;
import dev.villageheart.core.heart.BuildRow;
import dev.villageheart.core.heart.HeartLimits;
import dev.villageheart.core.stock.StockSnapshot;
import dev.villageheart.stock.StackKey;
import dev.villageheart.stock.Stockpiles;
import dev.villageheart.world.VillageClaims;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** The Builder's Table snapshot for one viewer, from the village, the stockpile and the builds. */
public final class BuildersTableFacts {
	private BuildersTableFacts() {}

	public static Optional<TableSnapshot> snapshot(MinecraftServer server, ServerPlayer player) {
		Optional<VillageState> village = VillageClaims.current(server);
		if (village.isEmpty()) return Optional.empty();
		VillageState v = village.get();
		Rank rank = v.rankOf(player.getUUID());
		var reservations = Stockpiles.reservations(server);
		StockSnapshot<StackKey> stock = Stockpiles.snapshot(server);
		List<TableBlueprint> bps = new ArrayList<>();
		for (Blueprint bp : Blueprints.all()) {
			List<TableMaterial> mats = new ArrayList<>();
			for (var l : bp.materials().lines()) {
				long have = 0;
				for (StockSnapshot.Total<StackKey> t : stock.totals()) {
					if (BuildJobs.matches(t.key(), l.material())) have += Math.max(0, t.count() - reservations.reserved(t.key()));
				}
				mats.add(new TableMaterial(l.material().name(), l.count(), have));
			}
			bps.add(new TableBlueprint(bp.id(), v.tier().compareTo(bp.tier()) < 0, bp.width(), bp.height(), bp.depth(), mats));
		}
		List<TableBuild> builds = new ArrayList<>();
		for (BuildSystem.BuildView b : BuildSystem.views(server)) {
			if (builds.size() == HeartLimits.MAX_BUILDS) break;
			builds.add(new TableBuild(b.job().id(), new BuildRow(b.blueprint().id(), b.job().ground().x(), b.job().ground().y(), b.job().ground().z(),
					b.status(), b.placed(), b.total(), b.line(), b.missing().subList(0, Math.min(2, b.missing().size())),
					HeartLimits.cut(b.builderName(), HeartLimits.MAX_NAME))));
		}
		return Optional.of(new TableSnapshot(rank, v.tier(), bps, builds));
	}

	/** The id of a build row's job, for the cancel button (a UUID of the snapshot's list). */
	public static Optional<UUID> idOf(TableSnapshot s, int index) {
		return index >= 0 && index < s.builds().size() ? Optional.of(s.builds().get(index).id()) : Optional.empty();
	}
}
