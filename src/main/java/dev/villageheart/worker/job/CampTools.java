package dev.villageheart.worker.job;

import dev.villageheart.core.VillageState;
import dev.villageheart.core.WorkerRecord;
import dev.villageheart.core.job.ToolAllocation;
import dev.villageheart.core.job.ToolKind;
import dev.villageheart.core.job.ToolRules;
import dev.villageheart.core.job.ToolTier;
import dev.villageheart.core.worker.WorkerData;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.worker.WorkerAttachments;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.ToIntFunction;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Tools stay in the camp chest (M9 decision 4): a worker uses its allotted tool in place and wears it out there. It is never moved to the
 * villager, so it cannot be lost to a death drop or a zombie conversion. With several workers in one camp the tools are shared best-first in
 * hiring order ({@link ToolAllocation}).
 */
public final class CampTools {
	private CampTools() {}

	/** A tool in a camp chest. */
	public record Lease(Container container, int slot, ItemStack stack, ToolTier tier) {}

	public sealed interface Result {}

	public record Held(Lease lease) implements Result {}

	/** The camp holds no tool for this worker: the status becomes "Needs <tool>". */
	public enum Missing implements Result { NONE, UNKNOWN }

	record Ref(int container, int slot) {}

	public static boolean isTool(ToolKind kind, ItemStack s) {
		if (s.isEmpty()) return false;
		return switch (kind) {
			case AXE -> s.is(ItemTags.AXES);
			case PICKAXE -> s.is(ItemTags.PICKAXES);
			case HOE -> s.is(ItemTags.HOES);
			case BOW_OR_SWORD -> s.is(ItemTags.SWORDS) || s.is(Items.BOW) || s.is(Items.CROSSBOW);
		};
	}

	/** Unknown modded tools count as wood. */
	public static ToolTier tierOf(ItemStack s) {
		return ToolRules.tierOf(BuiltInRegistries.ITEM.getKey(s.getItem()).toString()).orElse(ToolTier.WOOD);
	}

	/** The tool the allocation gives this worker: {@link Missing#UNKNOWN} when the camp's chests cannot be read (no scan, unloaded). */
	public static Result lease(MinecraftServer server, Villager villager, ToolKind kind) { return lease(server, villager, kind, null); }

	/**
	 * The same with the job's own ranking of the tools in the chest (higher is given out first, ties by container and slot order): the
	 * hunter ranks any bow or crossbow before any sword (M17). {@code null} = by tier.
	 */
	public static Result lease(MinecraftServer server, Villager villager, ToolKind kind, @Nullable ToIntFunction<ItemStack> rank) {
		Optional<WorkerData> data = WorkerAttachments.get(villager);
		Optional<VillageState> village = VillageClaims.current(server);
		if (data.isEmpty() || village.isEmpty()) return Missing.UNKNOWN;
		Optional<List<CampContainers.Chest>> chests = CampContainers.of(server, data.get().building());
		if (chests.isEmpty()) return Missing.UNKNOWN;
		List<ToolAllocation.Tool<Ref>> tools = new ArrayList<>();
		for (int ci = 0; ci < chests.get().size(); ci++) {
			Container c = chests.get().get(ci).container();
			for (int si = 0; si < c.getContainerSize(); si++) {
				ItemStack s = c.getItem(si);
				if (isTool(kind, s)) tools.add(new ToolAllocation.Tool<>(new Ref(ci, si), tierOf(s), ci * 1000 + si));
			}
		}
		List<UUID> workers = new ArrayList<>();
		for (WorkerRecord w : village.get().workers()) {
			if (w.building().equals(data.get().building()) && w.jobId().equals(data.get().jobId())) workers.add(w.villager());
		}
		if (!workers.contains(villager.getUUID())) workers.add(villager.getUUID());
		Map<UUID, Ref> assigned = rank == null ? ToolAllocation.assign(tools, workers)
				: ToolAllocation.assign(tools, workers, t -> rank.applyAsInt(chests.get().get(t.ref().container()).container().getItem(t.ref().slot())));
		Ref mine = assigned.get(villager.getUUID());
		if (mine == null) return Missing.NONE;
		Container c = chests.get().get(mine.container()).container();
		ItemStack stack = c.getItem(mine.slot());
		return new Held(new Lease(c, mine.slot(), stack, tierOf(stack)));
	}

	/**
	 * Wears the tool out by {@code damage} (Unbreaking applies); a tool that breaks leaves the slot empty. Re-reads the slot first: false
	 * when the player took the tool out meanwhile.
	 */
	public static boolean use(ServerLevel level, Lease lease, int damage) {
		ItemStack now = lease.container().getItem(lease.slot());
		if (now.isEmpty() || now.getItem() != lease.stack().getItem()) return false;
		now.hurtAndBreak(damage, level, null, item -> { });
		if (now.isEmpty()) lease.container().setItem(lease.slot(), ItemStack.EMPTY);
		lease.container().setChanged();
		return true;
	}
}
