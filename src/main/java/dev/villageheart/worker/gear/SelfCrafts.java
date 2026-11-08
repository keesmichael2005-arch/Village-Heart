package dev.villageheart.worker.gear;

import dev.villageheart.VillageHeart;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.gear.GearCraft;
import dev.villageheart.core.gear.ItemCount;
import dev.villageheart.core.gear.OrderKey;
import dev.villageheart.core.gear.SelfCraft;
import dev.villageheart.stock.Stockpiles;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.worker.Workers;
import dev.villageheart.worker.job.CampContainers;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Saved-data access for the self-craft records (M19a plan 3.2 #7): only {@link #commit} inserts; progress writes only update a record that
 * still matches (worker, key, recipe); finish and refund remove by compare-and-remove, so a late write can never bring a refunded record
 * back (and with it the committed items).
 */
public final class SelfCrafts {
	private SelfCrafts() {}

	/** The only insert: the worker has taken its materials. */
	public static void commit(MinecraftServer server, SelfCraft craft) {
		VillageClaims.data(server).update(v -> v.withSelfCraft(craft));
		GearOrders.markCommitted(craft.craft().key());
	}

	/** Update-only: writes the progress when the stored record still has this worker, key and recipe. */
	public static void progress(MinecraftServer server, UUID worker, String recipeId, OrderKey key, int progress) {
		Optional<SelfCraft> cur = VillageClaims.current(server).flatMap(v -> v.selfCraft(worker));
		if (cur.isEmpty() || !matches(cur.get(), recipeId, key) || cur.get().craft().progress() == progress) return;
		VillageClaims.data(server).update(v -> v.selfCraft(worker).filter(c -> matches(c, recipeId, key))
				.map(c -> v.withSelfCraft(c.withCraft(c.craft().withProgress(progress)))).orElse(v));
	}

	/** Compare-and-remove: true when this call removed the record. */
	public static boolean finish(MinecraftServer server, UUID worker, String recipeId, OrderKey key) {
		boolean[] removed = {false};
		VillageClaims.data(server).update(v -> v.selfCraft(worker).filter(c -> matches(c, recipeId, key)).map(c -> {
			removed[0] = true;
			return v.withoutSelfCraft(worker);
		}).orElse(v));
		if (removed[0]) GearOrders.release(key);
		return removed[0];
	}

	private static boolean matches(SelfCraft c, String recipeId, OrderKey key) { return c.craft().recipeId().equals(recipeId) && c.craft().key().equals(key); }

	private static Item item(String id) { return BuiltInRegistries.ITEM.getValue(Identifier.parse(id)); }

	/**
	 * Gives the committed items back: to Storage, else the worker's inventory (loaded), else dropped at the building marker (chunk loaded).
	 * What cannot be delivered now stays in the record with {@code refundPending}. True when the record is gone.
	 */
	public static boolean refund(MinecraftServer server, SelfCraft sc) {
		UUID worker = sc.worker();
		OrderKey key = sc.craft().key();
		Optional<ServerLevel> storage = Stockpiles.storageLevel(server);
		Optional<net.minecraft.server.level.ServerLevel> home = CampContainers.building(server, key.building()).map(b -> b.level());
		ServerLevel level = storage.orElse(home.orElse(null));
		BlockPos at = pos(key.building());
		List<ItemCount> left = new ArrayList<>();
		Villager v = Workers.findLoaded(server, worker).orElse(null);
		for (ItemCount ic : sc.craft().committed()) {
			Item it = item(ic.id());
			int remaining = ic.count();
			while (remaining > 0) {
				ItemStack stack = new ItemStack(it, Math.min(remaining, it.getDefaultMaxStackSize()));
				remaining -= stack.getCount();
				if (level != null && storage.isPresent()) Stockpiles.deposit(level, at, stack);
				if (!stack.isEmpty() && v != null) stack = v.getInventory().addItem(stack);
				if (!stack.isEmpty() && level != null && level.getChunkSource().getChunkNow(at.getX() >> 4, at.getZ() >> 4) != null) {
					Containers.dropItemStack(level, at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5, stack);
					stack = ItemStack.EMPTY;
				}
				if (!stack.isEmpty()) left.add(new ItemCount(ic.id(), stack.getCount()));
			}
		}
		if (left.isEmpty()) {
			finish(server, worker, sc.craft().recipeId(), key);
			Stockpiles.reservations(server).release(worker);
			return true;
		}
		VillageClaims.data(server).update(vs -> vs.selfCraft(worker).filter(c -> matches(c, sc.craft().recipeId(), key))
				.map(c -> vs.withSelfCraft(new SelfCraft(worker, new GearCraft(c.craft().recipeId(), key, c.craft().progress(), merge(left)), true)))
				.orElse(vs));
		return false;
	}

	private static List<ItemCount> merge(List<ItemCount> in) {
		List<ItemCount> out = new ArrayList<>();
		outer:
		for (ItemCount ic : in) {
			for (int i = 0; i < out.size(); i++) {
				if (out.get(i).id().equals(ic.id())) {
					out.set(i, new ItemCount(ic.id(), out.get(i).count() + ic.count()));
					continue outer;
				}
			}
			out.add(ic);
		}
		return out;
	}

	/** The claim goes: every committed craft is refunded first. */
	public static void refundAll(MinecraftServer server) {
		Optional<VillageState> v = VillageClaims.current(server);
		if (v.isEmpty()) return;
		for (SelfCraft sc : v.get().selfCrafts()) {
			if (!refund(server, sc)) VillageHeart.LOG.warn("Self-craft of {}: the refund is not complete", sc.worker());
		}
	}

	static BlockPos pos(HeartLocation l) { return new BlockPos(l.x(), l.y(), l.z()); }
}
