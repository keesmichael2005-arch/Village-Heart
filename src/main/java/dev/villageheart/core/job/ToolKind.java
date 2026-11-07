package dev.villageheart.core.job;

import java.util.Locale;

/** The tool a job needs. The lowercase name is part of the lang key {@code status.villageheart.needs_tool.<id>}. */
public enum ToolKind {
	AXE, PICKAXE, HOE, BOW_OR_SWORD;

	public String id() { return name().toLowerCase(Locale.ROOT); }
}
