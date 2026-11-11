package dev.villageheart.core.market;

import dev.villageheart.core.Plural;
import dev.villageheart.core.heart.HeartTexts;
import dev.villageheart.core.heart.TextLine;
import java.util.List;

/** English the market shows for a count of emeralds. One emerald is singular; zero and every other count are plural. */
public final class MarketTexts {
	private MarketTexts() {}

	public static final String STATUS = "message.villageheart.market.status";
	public static final String STATUS_FALLBACK = "Market: sold %s today, %s, keeps %s days of food and %s of every other item";

	/** The Stockpile tab line (one format arg, so it fits a Heart {@link TextLine}). */
	public static TextLine soldToday(int emeralds) {
		return new TextLine(HeartTexts.key("stock.market_sold"), HeartTexts.english("stock.market_sold"), List.of(Plural.emeralds(emeralds)));
	}

	/**
	 * {@code /villageheart market status}. Not a {@link TextLine}: that record keeps two args for the Heart wire, and this
	 * sentence names the emerald count plus the merchants and both reserves.
	 */
	public static Line status(int sold, long merchants, int foodDays, int itemReserve) {
		return new Line(STATUS, STATUS_FALLBACK, List.of(Plural.emeralds(sold), Plural.merchants(merchants), Integer.toString(foodDays), Integer.toString(itemReserve)));
	}

	/** A chat line: lang key, English fallback, format args. */
	public record Line(String key, String fallback, List<String> args) {
		public Line {
			args = List.copyOf(args);
		}

		/** The fallback with each {@code %s} replaced by the next arg. */
		public String english() {
			StringBuilder out = new StringBuilder();
			int next = 0;
			for (int i = 0; i < fallback.length(); i++) {
				char c = fallback.charAt(i);
				if (c == '%' && i + 1 < fallback.length() && fallback.charAt(i + 1) == 's') {
					out.append(next < args.size() ? args.get(next++) : "");
					i++;
				} else {
					out.append(c);
				}
			}
			return out.toString();
		}
	}
}
