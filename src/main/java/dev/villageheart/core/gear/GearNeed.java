package dev.villageheart.core.gear;

import dev.villageheart.core.job.ToolKind;
import java.util.List;
import java.util.Locale;

/** What a camp or a guard lacks. The four tool needs are M19a's; HEAD, CHEST, LEGS and FEET are appended by M19. */
public enum GearNeed {
	AXE, PICKAXE, HOE, SWORD, HEAD, CHEST, LEGS, FEET;

	/** Tools a worker makes or asks the smith for. Armour is not in this list. */
	public static List<GearNeed> tools() {
		return List.of(AXE, PICKAXE, HOE, SWORD);
	}

	public static List<GearNeed> armourPieces() {
		return List.of(HEAD, CHEST, LEGS, FEET);
	}

	public boolean isArmour() {
		return this == HEAD || this == CHEST || this == LEGS || this == FEET;
	}

	public static GearNeed of(ToolKind k) {
		return switch (k) {
			case AXE -> AXE;
			case PICKAXE -> PICKAXE;
			case HOE -> HOE;
			case BOW_OR_SWORD -> SWORD;
		};
	}

	/** Armour is never a {@link ToolKind}; callers that only handle tools must not pass it. */
	public ToolKind toolKind() {
		return switch (this) {
			case AXE -> ToolKind.AXE;
			case PICKAXE -> ToolKind.PICKAXE;
			case HOE -> ToolKind.HOE;
			case SWORD -> ToolKind.BOW_OR_SWORD;
			case HEAD, CHEST, LEGS, FEET -> throw new IllegalStateException("armour is not a tool: " + this);
		};
	}

	/** Item-path suffix: {@code axe}, {@code helmet}, {@code chestplate}, {@code leggings}, {@code boots}. */
	public String piece() {
		return switch (this) {
			case HEAD -> "helmet";
			case CHEST -> "chestplate";
			case LEGS -> "leggings";
			case FEET -> "boots";
			default -> id();
		};
	}

	public String id() { return name().toLowerCase(Locale.ROOT); }
}
