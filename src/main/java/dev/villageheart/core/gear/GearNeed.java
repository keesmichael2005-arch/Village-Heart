package dev.villageheart.core.gear;

import dev.villageheart.core.job.ToolKind;
import java.util.Locale;

/** What a worker lacks (M19a, the names of the M19 plan). M19 appends HEAD, CHEST, LEGS, FEET. */
public enum GearNeed {
	AXE, PICKAXE, HOE, SWORD;

	public static GearNeed of(ToolKind k) {
		return switch (k) {
			case AXE -> AXE;
			case PICKAXE -> PICKAXE;
			case HOE -> HOE;
			case BOW_OR_SWORD -> SWORD;
		};
	}

	public ToolKind toolKind() {
		return switch (this) {
			case AXE -> ToolKind.AXE;
			case PICKAXE -> ToolKind.PICKAXE;
			case HOE -> ToolKind.HOE;
			case SWORD -> ToolKind.BOW_OR_SWORD;
		};
	}

	public String id() { return name().toLowerCase(Locale.ROOT); }
}
