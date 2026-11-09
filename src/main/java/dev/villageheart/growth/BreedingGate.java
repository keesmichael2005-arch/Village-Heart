package dev.villageheart.growth;

import dev.villageheart.core.Balance;
import dev.villageheart.core.growth.License;
import dev.villageheart.core.time.DailyClock;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.Level;

/**
 * No unlicensed breeding inside the claim (M23 plan 3.2 #7, and the orchestrator's decision that vanilla breeding is blocked there for good).
 * A static snapshot of the claim sphere and today's licence, refreshed by {@link GrowthSystem}; {@code Villager.canBreed} (and so every
 * vanilla breeding filter) and {@code getBreedOffspring} (the one place a villager baby is made) read it. The check is a few field reads and
 * one sphere test: no allocation, no map lookup by position. One snapshot is enough because the mod has one village per world.
 */
public final class BreedingGate {
	/** The claim sphere and the licence of the day. {@code licence} is empty when there is none. */
	private record Snapshot(ResourceKey<Level> dimension, int hx, int hy, int hz, long r2, Optional<License> licence) {}

	private static volatile Snapshot snapshot;

	private BreedingGate() {}

	/** No village: vanilla is untouched. */
	public static void reset() { snapshot = null; }

	static void set(ResourceKey<Level> dimension, int hx, int hy, int hz, Optional<License> licence) {
		long r = Balance.CLAIM_RADIUS;
		Snapshot old = snapshot;
		if (old != null && old.dimension.equals(dimension) && old.hx == hx && old.hy == hy && old.hz == hz && old.licence.equals(licence)) return;
		snapshot = new Snapshot(dimension, hx, hy, hz, r * r, licence);
	}

	public static boolean active() { return snapshot != null; }

	/** True when {@code v} is in the claim: breeding there needs a licence. */
	private static boolean inClaim(Snapshot s, Villager v) {
		if (!s.dimension.equals(v.level().dimension())) return false;
		long dx = v.getBlockX() - s.hx, dy = v.getBlockY() - s.hy, dz = v.getBlockZ() - s.hz;
		return dx * dx + dy * dy + dz * dz <= s.r2;
	}

	/** True when {@code v} is a parent of today's licence that has not been born yet. */
	private static boolean licensed(Snapshot s, Villager v) {
		if (s.licence.isEmpty()) return false;
		License l = s.licence.get();
		return l.active() && l.pair().contains(v.getUUID()) && !l.staleOn(DailyClock.dayIndex(v.level().getDayTime()));
	}

	/** The gate: true when vanilla must not let {@code v} breed (it stands in the claim and holds no licence). Server side only. */
	public static boolean blocks(Villager v) {
		Snapshot s = snapshot;
		if (s == null || !(v.level() instanceof ServerLevel)) return false;
		return inClaim(s, v) && !licensed(s, v);
	}

	/** Today's licence when {@code v} is one of its parents. */
	public static Optional<License> licenceOf(Villager v) {
		Snapshot s = snapshot;
		if (s == null || !licensed(s, v)) return Optional.empty();
		return s.licence;
	}

	/** True when the block is inside the claim sphere of the snapshot (the bed an outsider must not take). */
	public static boolean claimContains(ResourceKey<Level> dimension, BlockPos pos) {
		Snapshot s = snapshot;
		if (s == null || !s.dimension.equals(dimension)) return false;
		long dx = pos.getX() - s.hx, dy = pos.getY() - s.hy, dz = pos.getZ() - s.hz;
		return dx * dx + dy * dy + dz * dz <= s.r2;
	}
}
