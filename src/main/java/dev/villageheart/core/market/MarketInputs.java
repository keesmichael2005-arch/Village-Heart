package dev.villageheart.core.market;

import java.util.List;

/** Everything one decision needs. {@code demand} = adults x 10 + children x 5. */
public record MarketInputs<K>(List<StockItem<K>> stock, Reserves reserves, long demand, int treasury, int level, int soldByMerchant, int soldAtStall,
		int soldByVillage) {}
