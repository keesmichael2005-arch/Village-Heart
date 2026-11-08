package dev.villageheart.core.job;

import java.util.Objects;

/**
 * What a job asks of a tool. {@link Mode#REQUIRED}: no tool, no work. {@link Mode#OPTIONAL}: works at {@code Balance.NO_TOOL_SPEED} without
 * one. {@link Mode#BONUS} (M10, the farmer's hoe): works at full speed without one and {@code Balance.HOE_BONUS} faster with one; a missing
 * tool is never a problem to report.
 */
public record ToolRequirement(ToolKind kind, Mode mode) {
	public enum Mode { REQUIRED, OPTIONAL, BONUS }

	public ToolRequirement {
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(mode, "mode");
	}

	/** The M9 shape: required or optional. */
	public ToolRequirement(ToolKind kind, boolean required) { this(kind, required ? Mode.REQUIRED : Mode.OPTIONAL); }

	public boolean required() { return mode == Mode.REQUIRED; }
}
