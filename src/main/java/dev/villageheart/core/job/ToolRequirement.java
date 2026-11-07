package dev.villageheart.core.job;

import java.util.Objects;

/** What a job asks of a tool. {@code required = false}: the job works at {@code Balance.NO_TOOL_SPEED} without one. */
public record ToolRequirement(ToolKind kind, boolean required) {
	public ToolRequirement {
		Objects.requireNonNull(kind, "kind");
	}
}
