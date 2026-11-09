package dev.villageheart.core.market;

/** One thing the market buys: an item id and the size of one lot (a lot sells for 1 emerald). */
public record PriceEntry(String itemId, int lot) {
	public PriceEntry {
		if (lot < 1 || lot > 64) throw new IllegalArgumentException("lot must be in 1..64: " + lot);
	}
}
