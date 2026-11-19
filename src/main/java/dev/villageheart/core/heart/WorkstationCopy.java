package dev.villageheart.core.heart;

/**
 * The Heart's words for a worker with no workplace, and which line wins. On strike wins over a missing workplace, including on the
 * Overview: an unpaid worker is the emerald line, not the workstation line. A paid worker with no workplace is "No workstation".
 */
public final class WorkstationCopy {
	private WorkstationCopy() {}

	private static final String ALERT = "gui.villageheart.heart.alert.";

	/** The Overview danger line. {@code n} is the number of paid workers with no workplace. */
	public static TextLine overview(int n) {
		if (n == 1) return new TextLine(ALERT + "workplace_invalid_one", "1 worker has no workstation");
		return new TextLine(ALERT + "workplace_invalid", "%s workers have no workstation", java.util.List.of(Integer.toString(n)));
	}

	/** True when this worker adds to the workstation danger line. A striker does not, even with no workplace. */
	public static boolean countsOnOverview(ActivityKind activity, boolean workplaceInvalid, NeedsFlags needs) {
		if (!workplaceInvalid) return false;
		if (activity == ActivityKind.ON_STRIKE) return false;
		if (needs.known() && !needs.paid()) return false;
		return true;
	}

	/**
	 * The Villagers-row key. On strike wins even when the workplace is also missing.
	 * @throws IllegalArgumentException when neither applies
	 */
	public static String rowKey(boolean onStrike, boolean noWorkstation) {
		if (onStrike) return HeartTexts.key("activity.on_strike");
		if (noWorkstation) return HeartTexts.key("activity.workplace_invalid");
		throw new IllegalArgumentException("the row is neither on strike nor missing a workplace");
	}

	/** The English of {@link #rowKey}. */
	public static String rowEnglish(boolean onStrike, boolean noWorkstation) {
		return HeartTexts.EN.get(rowKey(onStrike, noWorkstation));
	}
}
