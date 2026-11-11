package dev.villageheart.worker.smith;

import dev.villageheart.VillageHeart;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.gear.GearCraft;
import dev.villageheart.core.gear.ItemCount;
import dev.villageheart.core.gear.OrderKey;
import dev.villageheart.core.smith.SmithCraft;
import dev.villageheart.stock.Stockpiles;
import dev.villageheart.worker.Workers;
import dev.villageheart.worker.gear.GearOrders;
import dev.villageheart.world.VillageClaims;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
 * Saved smith crafts, keyed by the blast furnace. Only {@link #commit} inserts. Progress updates a record that still matches.
 * Finish and refund remove by compare-and-remove.
 */
public final class SmithCrafts {
	private SmithCrafts() {}

	public static void commit(MinecraftServer server, HeartLocation furnace, SmithCraft craft) {
		VillageClaims.data(server).update(v -> v.withSmithCraft(furnace, craft));
		GearOrders.markCommitted(craft.craft().key());
	}

	/** Update-only. A missing record, or one with a different recipe or key, is left alone. */
	public static void progress(MinecraftServer server, HeartLocation furnace, String recipeId, OrderKey key, int progress) {
		Optional<SmithCraft> cur = VillageClaims.current(server).flatMap(v -> v.smithCraft(furnace));
		if (cur.isEmpty() || !matches(cur.get(), recipeId, key) || cur.get().craft().progress() == progress) return;
		VillageClaims.data(server).update(v -> v.smithCraft(furnace).filter(c -> matches(c, recipeId, key))
				.map(c -> v.withSmithCraft(furnace, c.withProgress(progress))).orElse(v));
	}

	/** Compare-and-remove. True when this call removed the record. */
	public static boolean finish(MinecraftServer server, HeartLocation furnace, String recipeId, OrderKey key) {
		boolean[] removed = {false};
		VillageClaims.data(server).update(v -> v.smithCraft(furnace).filter(c -> matches(c, recipeId, key)).map(c -> {
			removed[0] = true;
			return v.withoutSmithCraft(furnace);
		}).orElse(v));
		if (removed[0]) GearOrders.release(key);
		return removed[0];
	}

	private static boolean matches(SmithCraft c, String recipeId, OrderKey key) {
		return c.craft().recipeId().equals(recipeId) && c.craft().key().equals(key);
	}

	/**
	 * Gives the committed items back: Storage, else the smith's inventory, else a drop at the furnace when that chunk is loaded.
	 * What cannot move stays on the record with {@code refundPending}. True when the record is gone.
	 */
	public static boolean refund(MinecraftServer server, HeartLocation furnace, SmithCraft sc) {
		OrderKey key = sc.craft().key();
		Optional<ServerLevel> storage = Stockpiles.storageLevel(server);
		ServerLevel level = storage.orElse(Workers.levelOf(server, furnace));
		BlockPos at = new BlockPos(furnace.x(), furnace.y(), furnace.z());
		List<ItemCount> left = new ArrayList<>();
		Villager v = null;
		var village = VillageClaims.current(server);
		if (village.isPresent()) {
			for (var w : village.get().workers()) {
				if (!w.jobId().equals("blacksmith")) continue;
				Villager loaded = Workers.findLoaded(server, w.villager()).orElse(null);
				if (loaded == null) continue;
				var data = loaded.getAttached(dev.villageheart.worker.WorkerAttachments.WORKER);
				if (data != null && furnace.equals(data.jobBlock())) v = loaded;
			}
		}
		for (ItemCount ic : sc.craft().committed()) {
			Item it = BuiltInRegistries.ITEM.getValue(Identifier.parse(ic.id()));
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
			finish(server, furnace, sc.craft().recipeId(), key);
			return true;
		}
		List<ItemCount> pending = merge(left);
		VillageClaims.data(server).update(vs -> vs.smithCraft(furnace).filter(c -> matches(c, sc.craft().recipeId(), key))
				.map(c -> vs.withSmithCraft(furnace, new SmithCraft(new GearCraft(c.craft().recipeId(), key, c.craft().progress(), pending),
						c.destination(), true)))
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

	/** The claim goes, or a Smithy is gone: every craft is refunded first. */
	public static void refundAll(MinecraftServer server) {
		var v = VillageClaims.current(server);
		if (v.isEmpty()) return;
		for (var e : v.get().smithCrafts().entrySet()) {
			if (!refund(server, e.getKey(), e.getValue())) VillageHeart.LOG.warn("Smith craft at {}: the refund is not complete", e.getKey());
		}
	}

	/** Refunds the crafts whose furnace is loaded and is not inside any registered Smithy. */
	public static void refundOrphans(MinecraftServer server) {
		var v = VillageClaims.current(server);
		if (v.isEmpty()) return;
		for (var e : List.copyOf(v.get().smithCrafts().entrySet())) {
			ServerLevel level = Workers.levelOf(server, e.getKey());
			if (level == null) continue;
			BlockPos at = new BlockPos(e.getKey().x(), e.getKey().y(), e.getKey().z());
			if (level.getChunkSource().getChunkNow(at.getX() >> 4, at.getZ() >> 4) == null) continue;
			if (SmithyLayout.smithyAt(server, e.getKey()).isPresent()) continue;
			refund(server, e.getKey(), e.getValue());
		}
	}
}
