package dev.villageheart.core.heart;

import dev.villageheart.core.market.Reserves;
import dev.villageheart.core.stock.StorageAlert;
import java.util.List;
import java.util.Objects;

/**
 * @param moreKinds item kinds beyond the {@link HeartLimits#MAX_STOCK_KINDS} listed
 * @param nutrition over all kinds, shown or not
 * @param reserves  what the market keeps (M22)
 * @param soldToday emeralds the market earned today
 * @param merchants registered merchants (0: nothing is sold)
 */
public record Stock(List<StockLine> items, int moreKinds, long totalItems, long nutrition, int storages, int unknownStorages,
		int containers, int freeSlots, int slots, StorageAlert alert, Reserves reserves, int soldToday, int merchants) {
	public static final Stock EMPTY = new Stock(List.of(), 0, 0, 0, 0, 0, 0, 0, 0, StorageAlert.OK);

	public Stock {
		items = List.copyOf(items);
		Objects.requireNonNull(alert, "alert");
		Objects.requireNonNull(reserves, "reserves");
	}

	/** Without the market's figures (the shape before M22). */
	public Stock(List<StockLine> items, int moreKinds, long totalItems, long nutrition, int storages, int unknownStorages,
			int containers, int freeSlots, int slots, StorageAlert alert) {
		this(items, moreKinds, totalItems, nutrition, storages, unknownStorages, containers, freeSlots, slots, alert, Reserves.DEFAULT, 0, 0);
	}
}
