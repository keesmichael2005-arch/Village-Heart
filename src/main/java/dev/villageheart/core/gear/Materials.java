package dev.villageheart.core.gear;

import java.util.List;

/** What the stockpile can give a worker now, item by item, in the order they should be taken (nearest first). */
public record Materials(List<StockItem> items) {
	public Materials {
		items = List.copyOf(items);
	}

	public long total(MaterialRole r) {
		long n = 0;
		for (StockItem i : items) if (i.role() == r) n += Math.max(0, i.available());
		return n;
	}
}
