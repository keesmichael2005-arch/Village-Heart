package dev.villageheart.core.market;

import dev.villageheart.core.Balance;

/** What the market must leave: days of food for everyone, and this many of every other item (set on the Stockpile tab). */
public record Reserves(int foodDays, int itemReserve) {
	public static final Reserves DEFAULT = new Reserves(Balance.FOOD_RESERVE_DAYS, Balance.ITEM_RESERVE);

	public Reserves {
		if (!valid(foodDays, itemReserve)) throw new IllegalArgumentException("reserves out of range: " + foodDays + ", " + itemReserve);
	}

	public static boolean valid(int foodDays, int itemReserve) {
		return foodDays >= 0 && foodDays <= Balance.MAX_FOOD_RESERVE_DAYS && itemReserve >= 0 && itemReserve <= Balance.MAX_ITEM_RESERVE;
	}

	/** Out-of-range values (a damaged save) pulled into range. */
	public static Reserves clamped(int foodDays, int itemReserve) {
		return new Reserves(Math.max(0, Math.min(foodDays, Balance.MAX_FOOD_RESERVE_DAYS)), Math.max(0, Math.min(itemReserve, Balance.MAX_ITEM_RESERVE)));
	}
}
