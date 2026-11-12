package dev.villageheart.core.economy;

import dev.villageheart.core.worker.JobType;

/**
 * Who may go on strike (M26-R, the smallest fair rule against the death spiral). A merchant never does: its sales are what pay every wage, so a
 * merchant on strike with an empty treasury ends the only income for good (`full_raids`: the raid empties the treasury, the merchant strikes, nothing
 * is ever sold again). An unpaid merchant keeps selling and its first sales settle the unpaid wages in hiring order, itself first.
 */
public final class StrikeRules {
	private StrikeRules() {}

	public static boolean mayStrike(JobType job) {
		return job != JobType.MERCHANT;
	}
}
