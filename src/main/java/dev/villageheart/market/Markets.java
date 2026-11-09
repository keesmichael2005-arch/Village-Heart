package dev.villageheart.market;

import dev.villageheart.VillageHeart;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.job.WorkUnit;
import dev.villageheart.core.market.MarketInputs;
import dev.villageheart.core.market.MarketState;
import dev.villageheart.core.market.PriceTable;
import dev.villageheart.core.market.Reserves;
import dev.villageheart.core.market.SaleDecision;
import dev.villageheart.core.market.SalesLedger;
import dev.villageheart.core.market.StockItem;
import dev.villageheart.core.stock.Reservations;
import dev.villageheart.core.stock.StockSnapshot;
import dev.villageheart.core.time.DailyClock;
import dev.villageheart.core.worker.WorkerData;
import dev.villageheart.core.worker.WorkerStatus;
import dev.villageheart.economy.Treasury;
import dev.villageheart.food.FoodCatalog;
import dev.villageheart.stock.StackKey;
import dev.villageheart.stock.Stockpiles;
import dev.villageheart.world.VillageCensus;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.worker.WorkXp;
import dev.villageheart.worker.WorkerAttachments;
import dev.villageheart.worker.job.WorkStats;
import dev.villageheart.worker.job.WorkerStatuses;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.IntUnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The market (M22 plan 3.2 #8, #11, 3.4): builds the merchant's view of the stockpile and is the only writer of sales. Server thread only.
 * A sale plans on a fully fresh view, withdraws the goods, pays the treasury and writes the ledger inside one tick, so nothing is lost or
 * made: if the withdraw comes up short or the treasury refuses, the goods go back.
 */
public final class Markets {
	private Markets() {}

	/** Test seam (null in production): caps what one sale's withdraw may ask for. */
	public static volatile IntUnaryOperator withdrawLimitForTest;
	/** Counters read by GameTests: stock views built, withdraw calls made. */
	public static int freshViews, withdrawCalls;

	private record Cached(MinecraftServer server, int tick, List<StockItem<StackKey>> stock) {}

	private record CachedCount(MinecraftServer server, int tick, VillageCensus.Headcount count) {}

	private static Cached cachedView;
	private static CachedCount cachedCount;

	public static void clearRuntime() {
		cachedView = null;
		cachedCount = null;
		MerchantJob.clearAll();
	}

	/** The stockpile as the market sees it: one item per variant, claims of others subtracted, nutrition from the village's own catalog. */
	static List<StockItem<StackKey>> stockItems(MinecraftServer server) {
		freshViews++;
		StockSnapshot<StackKey> snap = Stockpiles.snapshot(server);
		Reservations<StackKey> reserved = Stockpiles.reservations(server);
		List<StockItem<StackKey>> out = new ArrayList<>();
		for (StockSnapshot.Total<StackKey> t : snap.totals()) {
			StackKey k = t.key();
			String id = k.id();
			int nutrition = FoodCatalog.of(k).map(v -> v.nutrition()).orElse(0);
			if (nutrition == 0 && PriceTable.lot(id).isEmpty()) continue; // neither food nor priced: the market never looks at it
			out.add(new StockItem<>(k, id, k.patch().isEmpty(), t.count(), Math.min(reserved.reserved(k), t.count()), nutrition));
		}
		return out;
	}

	private static List<StockItem<StackKey>> stock(MinecraftServer server, boolean fresh) {
		int tick = server.getTickCount();
		Cached c = cachedView;
		if (!fresh && c != null && c.server == server && tick >= c.tick && tick - c.tick < dev.villageheart.core.Balance.MARKET_VIEW_TTL) return c.stock;
		List<StockItem<StackKey>> s = stockItems(server);
		cachedView = new Cached(server, tick, s);
		return s;
	}

	private static VillageCensus.Headcount headcount(MinecraftServer server, VillageState village, boolean fresh) {
		int tick = server.getTickCount();
		CachedCount c = cachedCount;
		if (!fresh && c != null && c.server == server && tick >= c.tick && tick - c.tick < dev.villageheart.core.Balance.MARKET_CENSUS_TTL) return c.count;
		VillageCensus.Headcount h = VillageCensus.headcount(server, village);
		cachedCount = new CachedCount(server, tick, h);
		return h;
	}

	/** Day index the ledger is kept by. */
	public static long today(ServerLevel level) { return DailyClock.dayIndex(level.getDayTime()); }

	/** The saved state with today's ledger (a new day is an empty ledger). */
	public static MarketState state(MinecraftServer server) { return VillageClaims.data(server).market(); }

	public static SalesLedger ledgerToday(ServerLevel level) { return state(level.getServer()).ledger().forDay(today(level)); }

	public static void setReserves(MinecraftServer server, Reserves reserves) {
		VillageClaims.data(server).setMarket(state(server).withReserves(reserves));
		dev.villageheart.heart.HeartFacts.invalidate();
		dev.villageheart.heart.HeartMenu.markAllDirty(server);
	}

	/** What this merchant would do now. {@code fresh} = no cached stock or headcount. Empty when there is no village. */
	static Optional<SaleDecision> plan(ServerLevel level, Villager merchant, WorkerData data, boolean fresh) {
		MinecraftServer server = level.getServer();
		Optional<VillageState> village = VillageClaims.current(server);
		if (village.isEmpty()) return Optional.empty();
		MarketState market = state(server);
		SalesLedger ledger = market.ledger().forDay(today(level));
		HeartLocation stall = data.jobBlock();
		MarketInputs<StackKey> in = new MarketInputs<>(stock(server, fresh), market.reserves(), headcount(server, village.get(), fresh).demand(),
				village.get().treasury(), data.level(), ledger.soldBy(merchant.getUUID()), ledger.soldAt(stall), ledger.soldToday());
		return Optional.of(dev.villageheart.core.market.MarketPlanner.decide(in));
	}

	/** How an attempted sale ended. */
	public enum Outcome { SOLD, STOPPED, SHORT, NO_VILLAGE }

	public record Result(Outcome outcome, @Nullable SaleDecision decision) {}

	/** The only writer of sales: plan afresh, withdraw, pay the treasury, write the ledger. */
	public static Result trySell(ServerLevel level, Villager merchant, BlockPos stallPos) {
		MinecraftServer server = level.getServer();
		WorkerData data = merchant.getAttached(WorkerAttachments.WORKER);
		if (data == null) return new Result(Outcome.NO_VILLAGE, null);
		Optional<SaleDecision> planned = plan(level, merchant, data, true);
		if (planned.isEmpty()) return new Result(Outcome.NO_VILLAGE, null);
		if (!(planned.get() instanceof SaleDecision.Sell<?> sell)) return new Result(Outcome.STOPPED, planned.get());
		StackKey key = (StackKey) sell.key();
		int lot = sell.lot();

		int ask = lot;
		IntUnaryOperator limit = withdrawLimitForTest;
		if (limit != null) ask = Math.max(0, Math.min(lot, limit.applyAsInt(lot)));
		withdrawCalls++;
		ItemStack taken = ask == 0 ? ItemStack.EMPTY : Stockpiles.withdraw(level, stallPos, key, ask, merchant.getUUID());
		if (taken.getCount() < lot) {
			putBack(level, merchant, stallPos, taken);
			return new Result(Outcome.SHORT, planned.get());
		}
		Treasury.DepositResult paid = Treasury.deposit(server, 1, null);
		if (paid.accepted() == 0) {
			putBack(level, merchant, stallPos, taken);
			return new Result(Outcome.STOPPED, planned.get());
		}
		MarketState market = state(server);
		SalesLedger ledger = market.ledger().forDay(today(level)).record(merchant.getUUID(), data.jobBlock(), 1);
		VillageClaims.data(server).setMarket(market.withLedger(ledger));
		WorkXp.award(merchant, WorkUnit.TRADE_MADE, 1);
		WorkStats.add("merchant", "sales", 1);
		WorkStats.add("merchant", "emeralds", 1);
		WorkerStatuses.set(merchant.getUUID(), null);
		cachedView = null; // the stock changed
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, merchant.getX(), merchant.getY() + 2.1, merchant.getZ(), 6, 0.3, 0.2, 0.3, 0.0);
		level.playSound(null, stallPos, SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 1.0F);
		dev.villageheart.heart.HeartFacts.invalidate();
		return new Result(Outcome.SOLD, planned.get());
	}

	/** Goods that did not sell go back: into the stockpile, else the merchant's own inventory (a deposit trip hands them in), else on the ground. */
	private static void putBack(ServerLevel level, Villager merchant, BlockPos stallPos, ItemStack taken) {
		if (taken.isEmpty()) return;
		ItemStack rest = taken.copy();
		Stockpiles.deposit(level, stallPos, rest);
		if (rest.isEmpty()) return;
		rest = merchant.getInventory().addItem(rest);
		if (rest.isEmpty()) return;
		VillageHeart.LOG.warn("[market] {} items of {} could not be put back; dropped at the stall", rest.getCount(), rest.getItem());
		level.addFreshEntity(new ItemEntity(level, stallPos.getX() + 0.5, stallPos.getY() + 1.0, stallPos.getZ() + 0.5, rest));
	}

	/** Maps a stop to the worker status shown (null = selling normally). */
	static @Nullable WorkerStatus statusOf(SaleDecision d) {
		if (d instanceof SaleDecision.Stop stop) {
			return switch (stop.reason()) {
				case DAILY_LIMIT -> WorkerStatus.DAILY_LIMIT;
				case TREASURY_FULL -> WorkerStatus.TREASURY_FULL;
				case NOTHING_TO_SELL -> WorkerStatus.NO_WORK;
			};
		}
		return null;
	}
}
