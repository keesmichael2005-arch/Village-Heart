package dev.villageheart.worker.gear;

import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.gear.GearNeed;
import dev.villageheart.core.gear.OpenOrder;
import dev.villageheart.core.gear.OrderKey;
import dev.villageheart.core.gear.SelfCraft;
import dev.villageheart.core.smith.SmithCraft;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.HashSet;

/**
 * The open orders of the village, one per slot (M19a plan 3.2 #9): who serves each key, from the reservation to the finish. Transient;
 * {@link #rebuild} restores the committed ones from the saved records after a start or a claim change. M19's smith uses it too, so
 * a self-crafter never takes a key the smith holds and the other way round. Server thread only.
 */
public final class GearOrders {
	/** {@code committed}: the items have been taken (a saved record exists). */
	public record Order(OrderKey key, UUID holder, long startTick, boolean committed) {}

	private static final Map<OrderKey, Order> ORDERS = new LinkedHashMap<>();

	private GearOrders() {}

	public static void claim(OrderKey key, UUID holder, long startTick, boolean committed) { ORDERS.put(key, new Order(key, holder, startTick, committed)); }

	/** Marks the order as committed (the record exists now). */
	public static void markCommitted(OrderKey key) {
		Order o = ORDERS.get(key);
		if (o != null) ORDERS.put(key, new Order(key, o.holder(), o.startTick(), true));
	}

	public static void release(OrderKey key) { ORDERS.remove(key); }

	/** Frees whatever this worker holds. */
	public static void releaseHolder(UUID holder) { ORDERS.values().removeIf(o -> o.holder().equals(holder)); }

	public static Optional<Order> of(OrderKey key) { return Optional.ofNullable(ORDERS.get(key)); }

	public static Optional<Order> heldBy(UUID holder) {
		for (Order o : ORDERS.values()) if (o.holder().equals(holder)) return Optional.of(o);
		return Optional.empty();
	}

	/** Every worker with an open order (fetching or committed): they are left out of the tool allocation. */
	public static Set<UUID> crafting() {
		Set<UUID> out = new HashSet<>();
		for (Order o : ORDERS.values()) out.add(o.holder());
		return out;
	}

	/** The open orders of one camp and need. */
	public static List<Order> openOf(HeartLocation building, GearNeed need) {
		List<Order> out = new ArrayList<>();
		for (Order o : ORDERS.values()) if (o.key().building().equals(building) && o.key().need() == need) out.add(o);
		return out;
	}

	public static List<OpenOrder> openOrders(VillageState village, HeartLocation building, GearNeed need) {
		List<OpenOrder> out = new ArrayList<>();
		for (Order o : openOf(building, need)) {
			int progress = village.selfCraft(o.holder()).map(c -> c.craft().progress()).orElse(0);
			for (SmithCraft c : village.smithCrafts().values()) if (c.craft().key().equals(o.key())) progress = Math.max(progress, c.craft().progress());
			if (dev.villageheart.worker.smith.SmithRuntime.carrying(o.key())) progress = Math.max(progress, 1_000_000);
			out.add(new OpenOrder(o.key(), progress, o.startTick()));
		}
		return out;
	}

	public static int size() { return ORDERS.size(); }

	/** Restores the committed orders from the saved records (pre-commit orders stay as they are). */
	public static void rebuild(VillageState village) {
		ORDERS.values().removeIf(Order::committed);
		Map<UUID, Order> pending = new HashMap<>();
		for (Order o : ORDERS.values()) pending.put(o.holder(), o);
		for (SelfCraft c : village.selfCrafts()) {
			if (pending.containsKey(c.worker())) continue;
			ORDERS.putIfAbsent(c.craft().key(), new Order(c.craft().key(), c.worker(), 0, true));
		}
		for (var e : village.smithCrafts().entrySet()) {
			SmithCraft c = e.getValue();
			UUID holder = java.util.UUID.nameUUIDFromBytes(("smith-furnace:" + e.getKey()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
			ORDERS.putIfAbsent(c.craft().key(), new Order(c.craft().key(), holder, 0, true));
		}
	}

	public static void rebuild(net.minecraft.server.MinecraftServer server) {
		dev.villageheart.world.VillageClaims.current(server).ifPresentOrElse(GearOrders::rebuild, GearOrders::reset);
	}

	/** Tests and a claim change: forget everything. */
	public static void reset() { ORDERS.clear(); }
}
