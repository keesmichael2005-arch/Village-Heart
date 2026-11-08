package dev.villageheart.needs;

import dev.villageheart.core.Balance;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.WorkerRecord;
import dev.villageheart.core.needs.Diet;
import dev.villageheart.core.needs.HouseBeds;
import dev.villageheart.core.needs.Rehome;
import dev.villageheart.core.room.Pos;
import dev.villageheart.core.worker.Needs;
import dev.villageheart.core.worker.WorkerData;
import dev.villageheart.food.DietAttachments;
import dev.villageheart.world.VillageCensus;
import dev.villageheart.world.VillageSystem;
import dev.villageheart.worker.WorkerAttachments;
import dev.villageheart.worker.Workers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The one writer of {@code WorkerData.needs} (M11 plan 3.5): a derived copy of the diet, the housing and the pay. Every
 * {@link Balance#NEEDS_REFRESH_TICKS}: rebuild the House bed index, mirror the needs of every loaded worker, and move workers without a House
 * bed into one. Dinner calls {@link #mirror} right after it wrote the diets, so the next work tick already runs at the new speed.
 */
public final class NeedsSystem implements VillageSystem {
	public static final NeedsSystem INSTANCE = new NeedsSystem();

	private NeedsSystem() {}

	@Override
	public void tick(MinecraftServer server, VillageState village) {
		if (server.getTickCount() % Balance.NEEDS_REFRESH_TICKS != 0) return;
		Housing.rebuild(server);
		if (!NeedsOverride.forceDefaultNeeds) rehome(server, village);
		mirror(server, village);
	}

	/** The needs a worker has right now. */
	public static Needs needsFor(MinecraftServer server, VillageState village, Villager v, WorkerData data, WorkerRecord rec) {
		if (NeedsOverride.forceDefaultNeeds) return Needs.DEFAULT;
		Diet diet = DietAttachments.read(v, village.claimedAtGameTime());
		boolean housed = Housing.housed(server, v, Optional.of(data.building()));
		return Needs.of(diet.hungryDays(), housed, PaidSource.current().test(rec));
	}

	/** Writes the derived needs of every loaded worker (only when they changed). */
	public static void mirror(MinecraftServer server, VillageState village) {
		for (WorkerRecord rec : village.workers()) {
			Villager v = Workers.findLoaded(server, rec.villager()).orElse(null);
			if (v == null) continue;
			WorkerData data = v.getAttached(WorkerAttachments.WORKER);
			if (data == null) continue;
			Needs n = needsFor(server, village, v, data, rec);
			if (!n.equals(data.needs())) v.setAttached(WorkerAttachments.WORKER, data.withNeeds(n));
		}
	}

	/** Villagers whose HOME is a House bed, by packed bed position. */
	private static Map<Long, Villager> holders(List<Villager> census, ServerLevel level) {
		Map<Long, Villager> out = new HashMap<>();
		for (Villager v : census) {
			Optional<GlobalPos> home = Housing.home(v);
			if (home.isPresent() && home.get().dimension().equals(level.dimension())) out.put(home.get().pos().asLong(), v);
		}
		return out;
	}

	private static boolean isFree(ServerLevel level, BlockPos p) {
		BlockState s = level.getBlockState(p);
		return s.is(BlockTags.BEDS) && !s.getValue(BedBlock.OCCUPIED);
	}

	private static boolean hasSpace(PoiManager poi, BlockPos p) {
		return poi.getInSquare(t -> t.is(PoiTypes.HOME), p, 0, PoiManager.Occupancy.HAS_SPACE).anyMatch(r -> r.getPos().equals(p));
	}

	/** Moves every loaded, awake worker that sleeps outside a House into the nearest House bed (plan 3.2 #9). */
	static void rehome(MinecraftServer server, VillageState village) {
		HouseBeds houseBeds = Housing.beds(server);
		if (houseBeds.houseBeds().isEmpty()) return;
		ServerLevel level = Workers.levelOf(server, village.heart());
		if (level == null) return;
		List<Villager> census = null;
		for (WorkerRecord rec : village.workers()) {
			Villager v = Workers.findLoaded(server, rec.villager()).orElse(null);
			if (v == null || v.isSleeping() || v.level() != level) continue;
			WorkerData data = v.getAttached(WorkerAttachments.WORKER);
			if (data == null || Housing.housed(server, v, Optional.of(data.building()))) continue;
			if (census == null) census = VillageCensus.of(server, village);
			Map<Long, Villager> holders = holders(census, level);
			PoiManager poi = level.getPoiManager();
			List<Rehome.BedState> states = new ArrayList<>();
			for (long bed : houseBeds.houseBeds()) {
				BlockPos bp = BlockPos.of(bed);
				if (!level.isLoaded(bp) || !isFree(level, bp) || !poi.exists(bp, t -> t.is(PoiTypes.HOME))) continue;
				Villager holder = holders.get(bed);
				Rehome.Holder kind;
				if (hasSpace(poi, bp)) kind = Rehome.Holder.NONE;
				else if (holder == null || holder == v || WorkerAttachments.isWorker(holder)) kind = Rehome.Holder.WORKER;
				else kind = holder.isSleeping() ? Rehome.Holder.RESIDENT_ASLEEP : Rehome.Holder.RESIDENT_AWAKE;
				states.add(new Rehome.BedState(new Pos(bp.getX(), bp.getY(), bp.getZ()), kind));
			}
			BlockPos here = v.blockPosition();
			Optional<Rehome.Choice> choice = Rehome.choose(new Pos(here.getX(), here.getY(), here.getZ()), states);
			if (choice.isEmpty()) continue;
			BlockPos bed = new BlockPos(choice.get().bed().x(), choice.get().bed().y(), choice.get().bed().z());
			if (choice.get().evict()) {
				Villager holder = holders.get(bed.asLong());
				holder.releasePoi(MemoryModuleType.HOME);
				holder.getBrain().eraseMemory(MemoryModuleType.HOME);
			}
			v.releasePoi(MemoryModuleType.HOME);
			v.getBrain().eraseMemory(MemoryModuleType.HOME);
			Optional<BlockPos> got = poi.take(t -> t.is(PoiTypes.HOME), (t, p) -> p.equals(bed), bed, 1);
			if (got.isPresent()) v.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), bed));
			// the loop re-reads holders for the next worker: HOME memories just changed
		}
	}
}
