package dev.villageheart.core.job;

import dev.villageheart.core.Balance;
import dev.villageheart.core.worker.Needs;
import java.util.Optional;

public final class WorkSpeed {
	private WorkSpeed() {}

	/**
	 * Tool x level x needs (DESIGN section 4). The tool factor is 0 when a required tool is missing and
	 * {@link Balance#NO_TOOL_SPEED} when an optional one is.
	 */
	public static double multiplier(Optional<ToolTier> tool, ToolRequirement req, int level, Needs needs) {
		double toolFactor;
		if (tool.isPresent()) toolFactor = ToolRules.multiplier(tool.get());
		else toolFactor = req.required() ? 0.0 : Balance.NO_TOOL_SPEED;
		int idx = Math.max(0, Math.min(Balance.LEVEL_SPEED.length - 1, level - 1));
		return toolFactor * Balance.LEVEL_SPEED[idx] * needs.speed();
	}

	/** A job that uses no tool (the builder's): level x needs only. */
	public static double untooled(int level, Needs needs) {
		int idx = Math.max(0, Math.min(Balance.LEVEL_SPEED.length - 1, level - 1));
		return Balance.LEVEL_SPEED[idx] * needs.speed();
	}
}
