package dev.villageheart.worker.gear;

import dev.villageheart.core.gear.Materials;
import dev.villageheart.core.gear.MaterialRole;
import dev.villageheart.core.gear.StockItem;
import dev.villageheart.core.stock.Reservations;
import dev.villageheart.core.stock.StockSnapshot;
import dev.villageheart.stock.StackKey;
import dev.villageheart.stock.Stockpiles;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/** What Storage can give a worker for a tool: one entry per component-free stockpile item with a role (M19a plan 3.4). */
public final class MaterialCensus {
	private MaterialCensus() {}

	/** {@code available = count - reserved by others}, counting what {@code self} reserved already as available to it. */
	public static Materials of(MinecraftServer server, UUID self) {
		StockSnapshot<StackKey> snap = Stockpiles.snapshot(server);
		Reservations<StackKey> res = Stockpiles.reservations(server);
		List<StockItem> items = new ArrayList<>();
		for (StockSnapshot.Total<StackKey> t : snap.totals()) {
			Optional<MaterialRole> role = ToolStacks.roleOf(t.key());
			if (role.isEmpty()) continue;
			long available = t.count() - res.reserved(t.key()) + res.reservedBy(self, t.key());
			if (available > 0) items.add(new StockItem(t.key().id(), role.get(), available));
		}
		return new Materials(items);
	}
}
