package dev.villageheart.economy;

import dev.villageheart.core.VillageState;
import dev.villageheart.core.WorkerRecord;
import dev.villageheart.core.economy.Payee;
import dev.villageheart.core.economy.Payroll;
import dev.villageheart.core.economy.Settlement;
import dev.villageheart.core.economy.WageRules;
import dev.villageheart.core.time.DailyClock;
import dev.villageheart.needs.NeedsOverride;
import dev.villageheart.needs.NeedsSystem;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.world.VillageSavedData;
import dev.villageheart.worker.Workers;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** The small operations on the payroll that more than one place needs (M12). Server thread only. */
public final class Wages {
	/** The wage clock: due when the day index changes ({@link dev.villageheart.core.Balance#WAGE_TICK} = 0). */
	public static final DailyClock CLOCK = new DailyClock(dev.villageheart.core.Balance.WAGE_TICK);

	private Wages() {}

	/** Whether wages are charged and paid at all (a test switch; always true in production). */
	public static boolean enabled() { return NeedsOverride.wagesEnabled; }

	/** The day time of the Heart's level, or the overworld's when that level is not there. */
	public static long dayTime(MinecraftServer server, VillageState village) {
		ServerLevel level = Workers.levelOf(server, village.heart());
		return (level != null ? level : server.overworld()).getDayTime();
	}

	/** The payroll; created for today on first use (a new claim or a save from before M12 has none). */
	public static Payroll payroll(MinecraftServer server, VillageState village) {
		VillageSavedData data = VillageClaims.data(server);
		Optional<Payroll> p = data.payroll();
		if (p.isPresent()) return p.get();
		Payroll fresh = Payroll.initial(CLOCK.initialLastDay(dayTime(server, village)));
		data.setPayroll(fresh);
		return fresh;
	}

	/** Is this villager on strike right now? False for anyone not in today's unpaid list. */
	public static boolean onStrike(MinecraftServer server, UUID villager) {
		return VillageClaims.data(server).payroll().map(p -> p.isUnpaid(villager)).orElse(false);
	}

	/** The wage a worker costs: its recorded level's wage. */
	public static Payee payee(WorkerRecord rec) { return new Payee(rec.villager(), WageRules.wage(rec.level())); }

	/**
	 * A hire is charged at once (plan 3.2 #6): the wage is taken from the treasury if it fits, else the worker is on strike from its first tick.
	 * A villager already paid today (fired and hired again the same day) costs nothing. Does nothing when wages are off.
	 */
	public static void onHired(MinecraftServer server, WorkerRecord rec) {
		if (!enabled()) return;
		Optional<VillageState> village = VillageClaims.current(server);
		if (village.isEmpty()) return;
		VillageSavedData data = VillageClaims.data(server);
		Settlement s = payroll(server, village.get()).hire(village.get().treasury(), payee(rec));
		data.update(v -> v.withTreasury(s.treasuryAfter()));
		data.setPayroll(s.payroll());
	}

	/** The harness set the clock back to day 0: no wage run for that day, the first is at the next day change. */
	public static void resetClock(MinecraftServer server) {
		VillageClaims.current(server).ifPresent(v -> {
			Payroll p = payroll(server, v);
			VillageClaims.data(server).setPayroll(new Payroll(CLOCK.initialLastDay(dayTime(server, v)), p.paidToday(), p.unpaid()));
		});
	}

	/** Writes the new needs of every loaded worker at once (a striker stops at the dawn tick, a settled worker resumes on the deposit tick). */
	static void mirrorNeeds(MinecraftServer server) {
		VillageClaims.current(server).ifPresent(v -> NeedsSystem.mirror(server, v));
	}

	/** Records a worker's level in its registry entry (the wage bill of a worker that is away reads it). */
	public static void mirrorLevel(MinecraftServer server, UUID villager, int level) {
		if (VillageClaims.current(server).flatMap(v -> v.worker(villager)).map(w -> w.level() != level).orElse(false)) {
			VillageClaims.data(server).update(v -> v.withWorkerLevel(villager, level));
		}
	}
}
