package dev.villageheart.core.heart;

import dev.villageheart.core.stock.StorageAlert;
import java.util.List;
import java.util.Objects;

/**
 * @param moreKinds item kinds beyond the {@link HeartLimits#MAX_STOCK_KINDS} listed
 * @param nutrition over all kinds, shown or not
 */
public record Stock(List<StockLine> items, int moreKinds, long totalItems, long nutrition, int storages, int unknownStorages,
		int containers, int freeSlots, int slots, StorageAlert alert) {
	public static final Stock EMPTY = new Stock(List.of(), 0, 0, 0, 0, 0, 0, 0, 0, StorageAlert.OK);

	public Stock {
		items = List.copyOf(items);
		Objects.requireNonNull(alert, "alert");
	}
}
