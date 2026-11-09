package dev.villageheart.core.economy;

import dev.villageheart.core.heart.AlertCode;
import dev.villageheart.core.heart.AlertLine;
import dev.villageheart.core.heart.Severity;
import dev.villageheart.core.heart.TextLine;
import java.util.ArrayList;
import java.util.List;

/** The Overview alerts of the wage system (M12 plan 3.2 #11). */
public final class WageAlerts {
	private WageAlerts() {}

	private static final String ALERT = "gui.villageheart.heart.alert.";

	public static List<AlertLine> of(int treasury, int bill, int strikers) {
		List<AlertLine> out = new ArrayList<>();
		if (strikers > 0) {
			out.add(new AlertLine(AlertCode.STRIKE, Severity.DANGER, strikers == 1
					? new TextLine(ALERT + "strike_one", "1 worker is on strike: deposit emeralds at the Heart")
					: new TextLine(ALERT + "strike", "%s workers are on strike: deposit emeralds at the Heart", List.of(Integer.toString(strikers)))));
		}
		if (bill > 0 && treasury < bill) {
			out.add(new AlertLine(AlertCode.TREASURY_SHORT, Severity.WARNING, new TextLine(ALERT + "treasury_short",
					"Treasury short: %s of %s emeralds for tomorrow's wages", List.of(Integer.toString(treasury), Integer.toString(bill)))));
		}
		return out;
	}
}
