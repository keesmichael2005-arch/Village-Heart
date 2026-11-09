package dev.villageheart.worker;

import dev.villageheart.core.Tier;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.job.Leveling;
import dev.villageheart.core.job.Progress;
import dev.villageheart.core.job.WorkUnit;
import dev.villageheart.core.job.XpAccumulator;
import dev.villageheart.core.job.XpChange;
import dev.villageheart.core.worker.WorkerData;
import dev.villageheart.world.VillageClaims;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * Worker XP and levels (M15, DESIGN section 4). Jobs call {@link #award} per unit of work; everything else (the level pass, the
 * guards' kills) goes through {@link Workers#addXp}. Server thread only.
 */
public final class WorkXp {
	private static final XpAccumulator ACCUMULATOR = new XpAccumulator();

	private WorkXp() {}

	/** {@code units} of {@code unit} done by this worker: the XP they are worth (fractions carry) is added. A no-op for a non-worker. */
	public static void award(Villager villager, WorkUnit unit, int units) {
		if (units <= 0 || !WorkerAttachments.isWorker(villager)) return;
		int xp = ACCUMULATOR.add(villager.getUUID(), unit, units);
		if (xp > 0) addXp(villager, xp);
	}

	/** See {@link Workers#addXp}. */
	static Optional<XpChange> addXp(Villager villager, int amount) {
		if (amount < 0) throw new IllegalArgumentException("amount must be >= 0: " + amount);
		WorkerData data = villager.getAttached(WorkerAttachments.WORKER);
		if (data == null || !(villager.level() instanceof ServerLevel level)) return Optional.empty();
		Optional<VillageState> village = VillageClaims.current(level.getServer());
		if (village.isEmpty()) return Optional.empty();
		Progress before = Leveling.sanitize(data.level(), data.xp());
		XpChange change = Leveling.gain(before, amount, village.get().tier());
		if (change.after().equals(before)) return Optional.of(change);
		// a level above the 1..3 table (written directly) is kept as it is
		int level1 = Math.max(change.after().level(), data.level());
		villager.setAttached(WorkerAttachments.WORKER, data.withProgress(level1, change.after().xp()));
		if (level1 != data.level()) {
			syncBadge(villager, level1);
			dev.villageheart.economy.Wages.mirrorLevel(level.getServer(), villager.getUUID(), level1);
		}
		if (change.levelledUp()) WorkerLevels.fireLevelChanged(level, villager, change);
		if (change.reachedCap()) WorkerLevels.fireCapped(level, villager, change);
		return Optional.of(change);
	}

	/**
	 * Sets the level of a worker (the XP stays) and the vanilla level badge. Does not fire listeners. Where a registry mirror of the
	 * level exists (M12) it is written here too.
	 */
	static void setLevel(Villager villager, int newLevel) {
		if (newLevel < 1) throw new IllegalArgumentException("level must be >= 1: " + newLevel);
		WorkerData data = villager.getAttached(WorkerAttachments.WORKER);
		if (data == null) return;
		if (data.level() != newLevel) villager.setAttached(WorkerAttachments.WORKER, data.withProgress(newLevel, data.xp()));
		syncBadge(villager, newLevel);
		if (villager.level() instanceof ServerLevel level) dev.villageheart.economy.Wages.mirrorLevel(level.getServer(), villager.getUUID(), newLevel);
	}

	/**
	 * The level pass (every 20 ticks, from {@link WorkerSystem}): a worker whose banked XP is worth a higher level than it has at the
	 * current tier gets it (a tier-up, or XP of a worker that was unloaded during one). Never lowers a level. Also repairs the badge.
	 */
	static void pass(ServerLevel level, Villager villager, Tier tier) {
		WorkerData data = villager.getAttached(WorkerAttachments.WORKER);
		if (data == null) return;
		Progress before = Leveling.sanitize(data.level(), data.xp());
		int now = Leveling.levelNow(before, tier);
		if (now > data.level()) {
			villager.setAttached(WorkerAttachments.WORKER, data.withProgress(now, data.xp()));
			syncBadge(villager, now);
			dev.villageheart.economy.Wages.mirrorLevel(level.getServer(), villager.getUUID(), now);
			WorkerLevels.fireLevelChanged(level, villager, new XpChange(before, new Progress(now, before.xp()), true, false));
		} else if (villager.getVillagerData().level() != data.level()) {
			syncBadge(villager, data.level());
		}
	}

	/** The vanilla level badge (stone, iron, gold on the belt) follows the worker level. */
	static void syncBadge(Villager villager, int workerLevel) {
		if (villager.getVillagerData().level() != workerLevel) {
			villager.setVillagerData(villager.getVillagerData().withLevel(workerLevel));
		}
	}

	public static void forget(UUID villager) { ACCUMULATOR.forget(villager); }

	public static void clear() { ACCUMULATOR.clear(); }
}
