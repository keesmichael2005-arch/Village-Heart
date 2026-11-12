package dev.villageheart.worker.smith;

import dev.villageheart.building.Buildings;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.WorkerRecord;
import dev.villageheart.core.gear.CampDemand;
import dev.villageheart.core.gear.CampNeed;
import dev.villageheart.core.gear.CampTool;
import dev.villageheart.core.gear.GearDemand;
import dev.villageheart.core.gear.GearInfo;
import dev.villageheart.core.gear.GearNeed;
import dev.villageheart.core.gear.GuardDemand;
import dev.villageheart.core.gear.Materials;
import dev.villageheart.core.gear.OpenOrder;
import dev.villageheart.core.gear.OrderKey;
import dev.villageheart.core.gear.StockItem;
import dev.villageheart.core.gear.VillageGear;
import dev.villageheart.core.smith.SmithCraft;
import dev.villageheart.core.stock.StockSnapshot;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.stock.StackKey;
import dev.villageheart.stock.Stockpiles;
import dev.villageheart.worker.Workers;
import dev.villageheart.worker.gear.GearLead;
import dev.villageheart.worker.gear.GearOrders;
import dev.villageheart.worker.gear.ToolCensus;
import dev.villageheart.worker.gear.ToolStacks;
import dev.villageheart.worker.job.CampContainers;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** What the village is short of, and what the stockpile can spend. One read per plan. */
public final class DemandCensus {
	public record View(List<GearDemand> demands, Map<CampNeed, List<OpenOrder>> open, Materials materials, List<String> storageTools,
			Map<String, Long> stock, Map<String, Integer> fuelCount, Map<String, Integer> fuelHeat, boolean cookHired, VillageGear gear) {}

	private DemandCensus() {}

	public static View of(MinecraftServer server, VillageState village) {
		List<GearDemand> demands = new ArrayList<>();
		Map<CampNeed, List<OpenOrder>> open = new LinkedHashMap<>();
		Map<OrderKey, Boolean> iron = new LinkedHashMap<>();
		List<String> storageTools = storageTools(server);
		List<String> guardPool = new ArrayList<>(storageTools);
		boolean cook = false;
		int guards = 0;
		HeartLocation barracks = null;
		HeartLocation storage = storageMarker(server);

		for (WorkerRecord w : village.workers()) if (w.jobId().equals(JobType.COOK.id())) cook = true;

		for (JobType job : JobType.values()) {
			if (job.tool().isEmpty()) continue;
			GearNeed need = GearNeed.of(job.tool().get().kind());
			for (Buildings.BuildingView b : Buildings.valid(server, job.building())) {
				HeartLocation marker = SmithyLayout.loc(b.level(), b.marker());
				CampDemand camp = ToolCensus.of(server, village, marker, job, GearOrders.crafting(), GearLead.lead(server, village));
				if (!camp.isUnknown()) demands.addAll(camp.slots());
				List<OpenOrder> orders = GearOrders.openOrders(village, marker, need);
				if (!orders.isEmpty()) open.put(new CampNeed(marker, need), orders);
				int hired = ToolCensus.hired(village, marker, job).size();
				int ironTools = 0;
				Optional<List<CampTool>> tools = ToolCensus.toolsOf(server, marker, job);
				if (tools.isPresent()) for (CampTool t : tools.get()) if (GearInfo.rankOf(t.tier()) >= 4) ironTools++;
				for (int i = 0; i < hired; i++) {
					OrderKey key = new OrderKey(marker, need, i);
					iron.put(key, i < ironTools || openIron(village, key));
				}
				for (GearDemand d : camp.slots()) takeOne(guardPool, d.key().need());
			}
		}

		List<GuardDemand.Piece> pieces = new ArrayList<>();
		for (String id : guardPool) pieces.add(new GuardDemand.Piece(GearInfo.of(id).orElseThrow().need(), GuardDemand.Where.STORAGE, 1, 1, false));
		for (WorkerRecord w : village.workers()) {
			if (!w.jobId().equals(JobType.GUARD.id())) continue;
			guards++;
			if (barracks == null) barracks = w.building();
			var loaded = Workers.findLoaded(server, w.villager());
			if (loaded.isEmpty()) {
				for (GearNeed n : List.of(GearNeed.SWORD, GearNeed.HEAD, GearNeed.CHEST, GearNeed.LEGS, GearNeed.FEET))
					iron.put(new OrderKey(w.building(), n, guards - 1), true);
				continue;
			}
			for (GearNeed n : List.of(GearNeed.SWORD, GearNeed.HEAD, GearNeed.CHEST, GearNeed.LEGS, GearNeed.FEET))
				iron.put(new OrderKey(w.building(), n, guards - 1), false);
			var v = loaded.get();
			readWorn(v.getItemBySlot(EquipmentSlot.MAINHAND), pieces, iron, w.building(), guards - 1);
			readWorn(v.getItemBySlot(EquipmentSlot.HEAD), pieces, iron, w.building(), guards - 1);
			readWorn(v.getItemBySlot(EquipmentSlot.CHEST), pieces, iron, w.building(), guards - 1);
			readWorn(v.getItemBySlot(EquipmentSlot.LEGS), pieces, iron, w.building(), guards - 1);
			readWorn(v.getItemBySlot(EquipmentSlot.FEET), pieces, iron, w.building(), guards - 1);
		}
		if (barracks != null) {
			var chests = CampContainers.of(server, barracks).orElse(List.of());
			for (var chest : chests) {
				var c = chest.container();
				for (int i = 0; i < c.getContainerSize(); i++) piece(c.getItem(i), GuardDemand.Where.BARRACKS, pieces);
			}
			if (storage == null) storage = barracks;
			carried(server, pieces);
			demands.addAll(GuardDemand.of(barracks, storage, guards, pieces));
			for (GearNeed n : List.of(GearNeed.SWORD, GearNeed.HEAD, GearNeed.CHEST, GearNeed.LEGS, GearNeed.FEET)) {
				List<OpenOrder> orders = GearOrders.openOrders(village, barracks, n);
				if (!orders.isEmpty()) open.put(new CampNeed(barracks, n), orders);
			}
		}

		return new View(demands, open, materials(server), storageTools, stock(server), fuelCount(server), fuelHeat(server), cook,
				new VillageGear(iron, guards));
	}

	private static void carried(MinecraftServer server, List<GuardDemand.Piece> pieces) {
		var village = dev.villageheart.world.VillageClaims.current(server);
		if (village.isEmpty()) return;
		for (WorkerRecord w : village.get().workers()) {
			if (!w.jobId().equals(JobType.BLACKSMITH.id())) continue;
			var loaded = Workers.findLoaded(server, w.villager());
			if (loaded.isEmpty()) continue;
			var inv = loaded.get().getInventory();
			for (int i = 0; i < inv.getContainerSize(); i++) piece(inv.getItem(i), GuardDemand.Where.CARRIED, pieces);
		}
	}

	private static void readWorn(ItemStack stack, List<GuardDemand.Piece> pieces, Map<OrderKey, Boolean> iron, HeartLocation barracks, int index) {
		GearInfo info = info(stack);
		if (info == null) return;
		int max = Math.max(1, stack.getMaxDamage());
		pieces.add(new GuardDemand.Piece(info.need(), GuardDemand.Where.WORN, ToolStacks.remaining(stack), max, false));
		if (info.ironOrBetter()) iron.put(new OrderKey(barracks, info.need(), index), true);
	}

	private static void piece(ItemStack stack, GuardDemand.Where where, List<GuardDemand.Piece> pieces) {
		GearInfo info = info(stack);
		if (info == null) return;
		int max = Math.max(1, stack.getMaxDamage());
		pieces.add(new GuardDemand.Piece(info.need(), where, ToolStacks.remaining(stack), max, false));
	}

	private static GearInfo info(ItemStack stack) {
		if (stack.isEmpty()) return null;
		return GearInfo.of(SmithCatalog.id(stack.getItem())).orElse(null);
	}

	private static void takeOne(List<String> pool, GearNeed need) {
		for (int i = 0; i < pool.size(); i++) {
			GearInfo info = GearInfo.of(pool.get(i)).orElse(null);
			if (info != null && info.need() == need) {
				pool.remove(i);
				return;
			}
		}
	}

	private static boolean openIron(VillageState village, OrderKey key) {
		for (SmithCraft c : village.smithCrafts().values()) {
			if (!c.craft().key().equals(key)) continue;
			String id = c.craft().recipeId();
			if (id.contains("iron_") || id.contains("diamond_") || id.contains("netherite_")) return true;
		}
		return false;
	}

	private static List<String> storageTools(MinecraftServer server) {
		List<String> out = new ArrayList<>();
		var res = Stockpiles.reservations(server);
		for (StockSnapshot.Total<StackKey> t : Stockpiles.snapshot(server).totals()) {
			StackKey key = t.key();
			if (!ToolStacks.storedGear(key.toStack(1))) continue;
			long free = Math.max(0, t.count() - res.reserved(key));
			for (long n = 0; n < free && n < 64; n++) out.add(key.id());
		}
		return out;
	}

	private static Materials materials(MinecraftServer server) {
		List<StockItem> items = new ArrayList<>();
		var res = Stockpiles.reservations(server);
		for (StockSnapshot.Total<StackKey> t : Stockpiles.snapshot(server).totals()) {
			var role = ToolStacks.roleOf(t.key());
			if (role.isEmpty() || role.get() == dev.villageheart.core.gear.MaterialRole.LOGS) continue;
			long free = Math.max(0, t.count() - res.reserved(t.key()));
			if (free > 0) items.add(new StockItem(t.key().id(), role.get(), free));
		}
		return new Materials(items);
	}

	private static Map<String, Long> stock(MinecraftServer server) {
		Map<String, Long> out = new LinkedHashMap<>();
		var res = Stockpiles.reservations(server);
		for (StockSnapshot.Total<StackKey> t : Stockpiles.snapshot(server).totals()) {
			if (!t.key().patch().isEmpty()) continue;
			long free = Math.max(0, t.count() - res.reserved(t.key()));
			if (free > 0) out.merge(t.key().id(), free, Long::sum);
		}
		return out;
	}

	private static Map<String, Integer> fuelCount(MinecraftServer server) {
		Map<String, Integer> out = new LinkedHashMap<>();
		for (var e : stock(server).entrySet()) if (SmithCatalog.heatOrZero(e.getKey()) > 0) out.put(e.getKey(), e.getValue().intValue());
		return out;
	}

	private static Map<String, Integer> fuelHeat(MinecraftServer server) {
		Map<String, Integer> out = new LinkedHashMap<>();
		for (String id : fuelCount(server).keySet()) out.put(id, SmithCatalog.heatOrZero(id));
		return out;
	}

	private static HeartLocation storageMarker(MinecraftServer server) {
		for (Buildings.BuildingView b : Buildings.valid(server, dev.villageheart.core.building.BuildingType.STORAGE))
			return SmithyLayout.loc(b.level(), b.marker());
		return null;
	}
}
