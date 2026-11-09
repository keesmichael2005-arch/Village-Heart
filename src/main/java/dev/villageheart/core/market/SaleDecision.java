package dev.villageheart.core.market;

/** What the merchant does next: sell one lot of {@code key}, or stop for a reason. */
public sealed interface SaleDecision {
	record Sell<K>(K key, String id, int lot) implements SaleDecision {}

	record Stop(StopReason reason) implements SaleDecision {}
}
