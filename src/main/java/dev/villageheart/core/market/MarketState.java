package dev.villageheart.core.market;

/** The market's saved state: the reserve settings and today's sales. Lives with the claim. */
public record MarketState(Reserves reserves, SalesLedger ledger) {
	public static MarketState initial() { return new MarketState(Reserves.DEFAULT, SalesLedger.empty(-1)); }

	public MarketState withReserves(Reserves r) { return new MarketState(r, ledger); }

	public MarketState withLedger(SalesLedger l) { return new MarketState(reserves, l); }
}
