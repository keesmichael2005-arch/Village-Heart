package dev.villageheart.stock;

import dev.villageheart.core.room.Pos;
import dev.villageheart.core.stock.ContainerRef;
import dev.villageheart.core.stock.StorageSet.SiteCandidate;
import dev.villageheart.core.stock.StorageSite;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;

/** Binds the positions of resolved Storage sites to live containers. Shared by the facade and the GameTests. */
public final class ContainerBinder {
	private ContainerBinder() {}

	/**
	 * @param sites            sites with at least one readable container
	 * @param unreadContainers listed positions with no chest/barrel readable now (chunk not loaded, replaced, removed)
	 * @param emptySites       candidate sites left with no readable container (the glue counts them as unknown)
	 */
	public record Bound(List<StorageSite<StackKey>> sites, int unreadContainers, int emptySites) {}

	public static Bound bind(ServerLevel level, List<SiteCandidate> candidates) {
		List<StorageSite<StackKey>> sites = new ArrayList<>();
		int unread = 0, empty = 0;
		for (SiteCandidate c : candidates) {
			List<ContainerRef<StackKey>> refs = new ArrayList<>(c.containers().size());
			for (Pos p : c.containers()) {
				Container container = StorageContainers.at(level, new BlockPos(p.x(), p.y(), p.z()));
				if (container == null) unread++; else refs.add(new ContainerRef<>(p, new ContainerInventory(container)));
			}
			if (refs.isEmpty()) empty++; else sites.add(new StorageSite<>(c.marker(), c.anchor(), refs));
		}
		return new Bound(sites, unread, empty);
	}
}
