package dev.villageheart.build;

import dev.villageheart.core.blueprint.BuildPlan;
import dev.villageheart.core.blueprint.BuildStatus;
import dev.villageheart.core.blueprint.Material;
import dev.villageheart.stock.StackKey;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.worker.build.BuilderState;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

/**
 * The build system's transient state (plan 3.7): the steps of each build, which builder works which build now (the claim), what each build is
 * missing and why it waits, the builders' own state. Dropped when the server or the claim changes. Server thread only.
 */
public final class BuildRuntime {
	private static BuildRuntime current;

	final MinecraftServer server;
	final int epoch;
	/** Build id to its steps. */
	public final Map<UUID, List<BuildPlan.Step>> plans = new HashMap<>();
	/** Build id to the builder (villager uuid) working on it now. Released when the builder leaves WORK, unloads, is fired or dies. */
	public final Map<UUID, UUID> claims = new HashMap<>();
	/** Build id to what it is missing, worked out by the last refresh, in step order. */
	public final Map<UUID, List<Missing>> missing = new HashMap<>();
	/** Build id to the stock keys reserved for it at the last refresh (the amounts are read live from the reservations). */
	public final Map<UUID, Map<StackKey, Integer>> reservedKeys = new HashMap<>();
	/** Build id to why its builder waits, set by the builder. */
	public final Map<UUID, Pause> pause = new HashMap<>();
	/** Test hook (L4): builds whose claim {@link BuildSystem} never releases, so a staged picture keeps its "Building" status. */
	public final java.util.Set<UUID> pinned = new java.util.HashSet<>();
	/** Villager uuid to the builder's own state. */
	public final Map<UUID, BuilderState> builders = new HashMap<>();

	public record Missing(Material material, int n) {}

	/** A builder waiting: the status, its text args and when it was last set (a stale pause is ignored). */
	public record Pause(BuildStatus status, List<String> args, long at) {
		public Pause {
			args = List.copyOf(args);
		}
	}

	private BuildRuntime(MinecraftServer server, int epoch) {
		this.server = server;
		this.epoch = epoch;
	}

	public static BuildRuntime get(MinecraftServer server) {
		int epoch = VillageClaims.data(server).epoch();
		BuildRuntime rt = current;
		if (rt == null || rt.server != server || rt.epoch != epoch) current = rt = new BuildRuntime(server, epoch);
		return rt;
	}

	/** Tests: forget every claim, plan and status, as after a restart. */
	public static void resetForTests() { current = null; }

	public static void forgetBuild(MinecraftServer server, UUID id) {
		BuildRuntime rt = get(server);
		rt.plans.remove(id);
		rt.claims.remove(id);
		rt.missing.remove(id);
		rt.reservedKeys.remove(id);
		rt.pause.remove(id);
		rt.pinned.remove(id);
		for (BuilderState b : rt.builders.values()) if (id.equals(b.build)) b.reset();
	}

	public BuilderState builder(UUID villager) { return builders.computeIfAbsent(villager, k -> new BuilderState()); }

	/** The builder working on a build now, or null. */
	@Nullable
	public UUID claimant(UUID build) { return claims.get(build); }

	/** A copy of the claims: build id to builder. */
	public Map<UUID, UUID> claims() { return new HashMap<>(claims); }

	/** The stock keys reserved for a build at the last refresh. */
	public java.util.Set<StackKey> reservedKeys(UUID build) { return reservedKeys.getOrDefault(build, Map.of()).keySet(); }

	public List<Missing> missing(UUID build) { return missing.getOrDefault(build, List.of()); }

	@Nullable
	public Pause pause(UUID build) { return pause.get(build); }
}
