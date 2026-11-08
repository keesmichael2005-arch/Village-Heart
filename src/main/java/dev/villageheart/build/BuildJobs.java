package dev.villageheart.build;

import dev.villageheart.VillageHeart;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.blueprint.Blueprint;
import dev.villageheart.core.blueprint.Blueprints;
import dev.villageheart.core.blueprint.BuildJob;
import dev.villageheart.core.blueprint.BuildPlan;
import dev.villageheart.core.blueprint.BuildPlan.Step;
import dev.villageheart.core.blueprint.BuilderPlanner.CellState;
import dev.villageheart.core.blueprint.Material;
import dev.villageheart.core.blueprint.Placement;
import dev.villageheart.core.blueprint.Rotation4;
import dev.villageheart.core.room.Pos;
import dev.villageheart.stock.StackKey;
import dev.villageheart.stock.Stockpiles;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.worker.Workers;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import org.jetbrains.annotations.Nullable;

/**
 * The server API of builds (plan 3.7): start, cancel, list, find, advance (the instant builder of tests and simulations, which runs the same
 * placement code as a builder villager) and the hook for a worker that leaves. The saved data is {@code VillageState.builds}.
 */
public final class BuildJobs {
	private BuildJobs() {}

	public static Blueprint blueprint(BuildJob job) {
		return Blueprints.resolve(job.blueprintId(), job.params()).orElseThrow(() -> new IllegalStateException("unknown blueprint " + job.blueprintId()));
	}

	/** The steps of a build (cached while the claim lasts). */
	public static List<Step> steps(MinecraftServer server, BuildJob job) {
		return BuildRuntime.get(server).plans.computeIfAbsent(job.id(), id -> BuildPlan.of(blueprint(job), job.placement()));
	}

	/** The packed cells the plan of a build touches (cached like the steps). */
	public static java.util.Set<Long> touched(MinecraftServer server, BuildJob job) {
		return BuildRuntime.get(server).touched.computeIfAbsent(job.id(), id -> {
			java.util.Set<Long> out = new java.util.HashSet<>();
			for (Step s : steps(server, job)) {
				out.add(s.pos().pack());
				for (BuildPlan.Part part : s.parts()) out.add(part.pos().pack());
			}
			return out;
		});
	}

	public static List<BuildJob> list(MinecraftServer server) {
		return VillageClaims.current(server).map(VillageState::builds).orElse(List.of());
	}

	public static Optional<BuildJob> find(MinecraftServer server, UUID id) {
		return VillageClaims.current(server).flatMap(v -> v.build(id));
	}

	/** Starts a build at the ground block. The caller has checked the site (SiteRules). */
	public static BuildJob start(ServerLevel level, Blueprint bp, BlockPos ground, Rotation4 rotation, UUID startedBy) {
		HeartLocation loc = VillageClaims.locate(level, ground);
		BuildJob job = BuildJob.start(UUID.randomUUID(), bp.id(), loc, rotation, level.getGameTime(), startedBy);
		VillageClaims.data(level.getServer()).update(v -> v.withBuild(job));
		VillageHeart.LOG.info("Build {} started: {} at {} ({})", job.id(), bp.id(), ground.toShortString(), rotation);
		BuildSystem.refresh(level.getServer()); // its materials are reserved at once
		return job;
	}

	/** Cancels a build: its record and reservations go, placed blocks stay, carried items become spoil (kept = 0 once the record is gone). */
	public static boolean cancel(MinecraftServer server, UUID id) {
		if (find(server, id).isEmpty()) return false;
		VillageClaims.data(server).update(v -> v.withoutBuild(id));
		Stockpiles.reservations(server).release(id);
		BuildRuntime.forgetBuild(server, id);
		VillageHeart.LOG.info("Build {} cancelled", id);
		return true;
	}

	/** A build is done: record and reservations go. Whatever the builder still carries is spoil from now on. */
	public static void finish(MinecraftServer server, BuildJob job) {
		VillageClaims.data(server).update(v -> v.withoutBuild(job.id()));
		Stockpiles.reservations(server).release(job.id());
		BuildRuntime.forgetBuild(server, job.id());
		VillageHeart.LOG.info("Build {} finished: {}", job.id(), job.blueprintId());
	}

	// ---- the instant builder ----

	/** @param steps cursor steps taken; @param stoppedBecause empty when n steps were taken or the build finished */
	public record Advanced(int steps, boolean finished, String stoppedBecause) {}

	/**
	 * Runs up to {@code n} steps of the build as a builder would, without walking or waiting: every PLACE/CHECK withdraws one matching item
	 * from the stockpile under the build's reservation, clearing drops go straight to the stockpile. Stops (and says why) at a missing
	 * material, a block in the way, an entity or an unloaded chunk.
	 */
	public static Advanced advance(MinecraftServer server, UUID id, int n) {
		Optional<BuildJob> found = find(server, id);
		if (found.isEmpty()) return new Advanced(0, false, "no such build");
		BuildJob job = found.get();
		Blueprint bp = blueprint(job);
		Placement pl = job.placement();
		List<Step> steps = steps(server, job);
		java.util.Set<Long> touched = touched(server, job);
		ServerLevel level = Workers.levelOf(server, job.ground());
		if (level == null) return new Advanced(0, false, "level not loaded");
		int cursor = job.cursor();
		int done = 0;
		String stop = "";
		while (done < n && cursor < steps.size()) {
			Step s = steps.get(cursor);
			CellState cs = BuildExecutor.cellState(level, s, touched);
			if (cs == CellState.UNLOADED) { stop = "not loaded at " + s.pos(); break; }
			if (cs == CellState.BLOCKED) { stop = "blocked at " + s.pos(); break; }
			if (cs == CellState.ENTITY) { stop = "entity in the way at " + s.pos(); break; }
			if (cs == CellState.ALREADY_RIGHT) { cursor++; done++; continue; }
			if (cs == CellState.CLEARABLE) {
				for (ItemStack drop : BuildExecutor.clear(level, s.pos(), null)) store(level, s.pos(), drop);
			}
			if (s.kind() == BuildPlan.Kind.CLEAR) { cursor++; done++; continue; }
			Material m = s.entry().material();
			ItemStack item = withdraw(level, m, id);
			if (item.isEmpty()) { stop = "needs " + m.name(); break; }
			BuildExecutor.place(level, bp, pl, s, item.getItem(), null);
			if (m.returns() != null) {
				BuiltInRegistries.ITEM.getOptional(Identifier.parse(m.returns())).ifPresent(r -> store(level, s.pos(), new ItemStack(r)));
			}
			afterPlace(server, level, bp, s);
			cursor++;
			done++;
		}
		boolean finished = cursor >= steps.size();
		final int at = cursor;
		if (finished) finish(server, job);
		else if (at != job.cursor()) VillageClaims.data(server).update(v -> v.build(id).isPresent() ? v.withBuild(v.build(id).get().withCursor(at)) : v);
		return new Advanced(done, finished, stop);
	}

	/** One stack item matching a material, taken from the stockpile under the build's reservation; EMPTY when there is none. */
	public static ItemStack withdraw(ServerLevel level, Material m, UUID holder) {
		return Stockpiles.withdrawFirst(level, null, k -> matches(k, m), 1, holder);
	}

	/** Only plain stacks count: named or enchanted items are never consumed. */
	public static boolean matches(StackKey key, Material m) {
		return key.patch().isEmpty() && PaletteStates.satisfies(m, key.item());
	}

	/** After a placement: a marker asks the building system for an immediate check. */
	public static void afterPlace(MinecraftServer server, ServerLevel level, Blueprint bp, Step s) {
		if (bp.isMarker(s.entry())) dev.villageheart.building.Buildings.request(server, VillageClaims.locate(level, BuildExecutor.at(s.pos())));
	}

	/** Gives an item to the stockpile (nearest first); what does not fit is dropped at {@code near}. */
	public static void store(ServerLevel level, Pos near, ItemStack stack) {
		if (stack.isEmpty()) return;
		Stockpiles.deposit(level, BuildExecutor.at(near), stack);
		if (!stack.isEmpty()) Containers.dropItemStack(level, near.x() + 0.5, near.y() + 0.5, near.z() + 0.5, stack);
	}

	// ---- workers that leave ----

	/**
	 * A worker's record is gone (fired, dead, converted). If it carried materials of a build they go back to the stockpile when the
	 * villager is still there (firing); a dead builder's items are lost (vanilla villagers drop nothing). The build stays and is served by
	 * the next builder; the shortfall is simply reserved again.
	 */
	public static void onWorkerRemoved(MinecraftServer server, UUID worker, @Nullable Villager villager) {
		BuildRuntime rt = BuildRuntime.get(server);
		rt.claims.values().removeIf(worker::equals);
		rt.builders.remove(worker);
		for (BuildJob job : new ArrayList<>(list(server))) {
			if (!worker.equals(job.carrier())) continue;
			if (villager != null && villager.level() instanceof ServerLevel level) {
				for (Map.Entry<String, Integer> e : job.carried().entrySet()) {
					Item item = BuiltInRegistries.ITEM.getOptional(Identifier.parse(e.getKey())).orElse(null);
					if (item == null) continue;
					ItemStack back = villager.getInventory().removeItemType(item, e.getValue());
					store(level, new Pos(villager.getBlockX(), villager.getBlockY(), villager.getBlockZ()), back);
				}
			}
			BuildJob cleared = job.withCarried(Map.of()).withCarrier(null);
			VillageClaims.data(server).update(v -> v.build(job.id()).isPresent() ? v.withBuild(cleared) : v);
		}
	}
}
