package dev.villageheart.worker.smith;

import dev.villageheart.building.Buildings;
import dev.villageheart.core.Balance;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.building.BuildingRequirements;
import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.room.Pos;
import dev.villageheart.core.smith.RepairRules;
import dev.villageheart.core.smith.SmithFacts;
import dev.villageheart.stock.StackKey;
import dev.villageheart.stock.Stockpiles;
import dev.villageheart.worker.guard.GuardGear;
import dev.villageheart.worker.job.CampContainers;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Damaged gear the smith can mend: the Smithy chest in place, Barracks and Storage by carrying them home. */
public final class RepairScan {
	public record Hit(BlockPos chest, int slot, SmithFacts.RepairJob job, boolean inPlace, boolean barracks) {}

	private static final Item[] MATERIALS = {Items.IRON_INGOT, Items.COPPER_INGOT, Items.GOLD_INGOT, Items.LEATHER, Items.DIAMOND};

	private RepairScan() {}

	public static List<Hit> inPlace(MinecraftServer server, HeartLocation smithy) {
		List<Hit> out = new ArrayList<>();
		var view = CampContainers.building(server, smithy);
		if (view.isEmpty() || view.get().latestScan().isEmpty()) return out;
		for (Pos p : view.get().latestScan().get().positions(BuildingRequirements.CHEST)) {
			BlockPos chest = new BlockPos(p.x(), p.y(), p.z());
			Container c = container(server, chest);
			if (c == null) continue;
			for (int i = 0; i < c.getContainerSize(); i++) add(out, c.getItem(i), chest, i, true, false);
		}
		return out;
	}

	public static List<Hit> fetchable(MinecraftServer server, VillageState village) {
		List<Hit> out = new ArrayList<>();
		for (Buildings.BuildingView b : Buildings.valid(server, BuildingType.BARRACKS)) {
			HeartLocation marker = SmithyLayout.loc(b.level(), b.marker());
			var chests = CampContainers.of(server, marker).orElse(List.of());
			for (var chest : chests) {
				Container c = chest.container();
				for (int i = 0; i < c.getContainerSize(); i++) {
					if (GuardGear.isClaimed(chest.pos(), i)) continue;
					ItemStack s = c.getItem(i);
					if (s.isEmpty() || s.getMaxDamage() <= 0) continue;
					if (s.getDamageValue() < s.getMaxDamage() / 4) continue;
					add(out, s, chest.pos(), i, false, true);
				}
			}
		}
		var level = dev.villageheart.world.VillageClaims.current(server).flatMap(v -> java.util.Optional.ofNullable(dev.villageheart.worker.Workers.levelOf(server, v.heart())));
		BlockPos from = level.map(l -> BlockPos.ZERO).orElse(BlockPos.ZERO);
		if (level.isPresent()) from = new BlockPos(dev.villageheart.world.VillageClaims.current(server).get().heart().x(),
				dev.villageheart.world.VillageClaims.current(server).get().heart().y(),
				dev.villageheart.world.VillageClaims.current(server).get().heart().z());
		for (var total : Stockpiles.snapshot(server).totals()) {
			ItemStack probe = total.key().toStack(1);
			if (probe.getMaxDamage() <= 0 || !probe.isDamaged()) continue;
			double left = 1.0 - (double) probe.getDamageValue() / probe.getMaxDamage();
			if (left >= 0.5) continue;
			if (level.isEmpty()) continue;
			BlockPos chest = Stockpiles.nearestHolding(level.get(), from, total.key(), null).orElse(null);
			if (chest == null) continue;
			add(out, probe, chest, -1, false, false);
		}
		return out;
	}

	private static void add(List<Hit> out, ItemStack stack, BlockPos chest, int slot, boolean inPlace, boolean barracks) {
		if (stack.isEmpty() || stack.getMaxDamage() <= 0 || !stack.isDamaged()) return;
		int units = RepairRules.units(stack.getDamageValue(), stack.getMaxDamage());
		if (units < 1) return;
		String material = materialOf(stack);
		if (material == null) return;
		String id = SmithCatalog.id(stack.getItem());
		out.add(new Hit(chest, slot, new SmithFacts.RepairJob(id, stack.getDamageValue(), stack.getMaxDamage(), material), inPlace, barracks));
	}

	private static String materialOf(ItemStack stack) {
		for (Item m : MATERIALS) if (stack.isValidRepairItem(new ItemStack(m))) return SmithCatalog.id(m);
		return null;
	}

	/** Diamond repairs only when what remains is still above the reserve. */
	public static boolean affordable(Hit hit, java.util.Map<String, Long> stock) {
		long have = stock.getOrDefault(hit.job().materialId(), 0L);
		if (hit.job().materialId().equals("minecraft:diamond")) return have - hit.job().units() > Balance.SMITH_DIAMOND_RESERVE;
		return have >= hit.job().units();
	}

	private static Container container(MinecraftServer server, BlockPos chest) {
		for (var level : server.getAllLevels()) {
			if (level.getChunkSource().getChunkNow(chest.getX() >> 4, chest.getZ() >> 4) == null) continue;
			if (level.getBlockEntity(chest) instanceof Container c) return c;
		}
		return null;
	}
}
