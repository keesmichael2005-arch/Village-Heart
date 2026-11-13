package dev.villageheart.economy;

import dev.villageheart.VillageHeart;
import dev.villageheart.core.Rank;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.economy.PayResult;
import dev.villageheart.core.economy.PayRun;
import dev.villageheart.core.economy.Payee;
import dev.villageheart.core.economy.Payroll;
import dev.villageheart.core.time.DailyClock;
import dev.villageheart.heart.HeartFacts;
import dev.villageheart.heart.HeartMenu;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.world.VillageSavedData;
import dev.villageheart.world.VillageSystem;
import dev.villageheart.worker.Workers;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Pays the workers once a day, at the first tick of a new day index (M12 plan 3.2 #3-#5). One day-time read per tick when nothing is due.
 * Wages run only while the Heart's chunk is loaded, never catch up over skipped days, and (like dinner) are consumed, not run, while a test
 * switched them off. Registered after the needs system, so the mirror sees the new payroll.
 */
public final class WageSystem implements VillageSystem {
	public static final WageSystem INSTANCE = new WageSystem();

	private WageSystem() {}

	@Override
	public void tick(MinecraftServer server, VillageState village) {
		village = VillageClaims.current(server).orElse(village); // earlier systems of this tick may have changed it
		ServerLevel level = Workers.levelOf(server, village.heart());
		if (level == null || !level.isLoaded(new BlockPos(village.heart().x(), village.heart().y(), village.heart().z()))) return;
		long dayTime = level.getDayTime();
		VillageSavedData data = VillageClaims.data(server);
		Payroll payroll = data.payroll().orElse(null);
		if (payroll == null) {
			// a new claim (or a save from before M12): claiming must not charge at once
			data.setPayroll(Payroll.initial(Wages.CLOCK.initialLastDay(dayTime)));
			return;
		}
		if (!Wages.CLOCK.due(dayTime, payroll.lastWageDay())) return;
		long day = DailyClock.dayIndex(dayTime);
		if (!Wages.enabled()) {
			data.setPayroll(payroll.consumeDawn(day));
			VillageDawn.fire(server, village, day, PayResult.none(day, village.treasury()));
			return;
		}
		runDawn(server, village, day, payroll);
	}

	private static void runDawn(MinecraftServer server, VillageState village, long day, Payroll payroll) {
		VillageSavedData data = VillageClaims.data(server);
		List<Payee> payees = new ArrayList<>(dev.villageheart.core.economy.WageRoster.of(village.workers()));
		PayResult result = PayRun.run(day, village.treasury(), payees);
		// one place, one tick: the treasury and the payroll are never saved apart
		data.update(v -> v.withTreasury(result.treasuryAfter()));
		data.setPayroll(payroll.afterDawn(result));
		Wages.mirrorNeeds(server);
		HeartFacts.invalidate();
		HeartMenu.markAllDirty(server);
		VillageHeart.LOG.info("[wages] day {}: paid {}/{}, spent {}, treasury {} -> {}, strikers {}", day, result.paid().size(), payees.size(),
				result.spent(), result.treasuryBefore(), result.treasuryAfter(), result.unpaidIds());
		if (!result.unpaid().isEmpty()) tellManagers(server, village, result.unpaid().size());
		WageStats.record(result);
		VillageDawn.fire(server, VillageClaims.current(server).orElse(village), day, result);
	}

	/** Every online Owner and Steward hears about a strike. */
	private static void tellManagers(MinecraftServer server, VillageState village, int strikers) {
		Component text = strikers == 1 ? Component.translatable("message.villageheart.treasury.strike_notice_one")
				: Component.translatable("message.villageheart.treasury.strike_notice", strikers);
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (village.rankOf(p.getUUID()).atLeast(Rank.STEWARD)) p.sendSystemMessage(text);
		}
	}
}
