package dev.villageheart.core.blueprint;

/**
 * The builder's decision, pure (docs/plans/M20.md 3.6): given what the glue read from the world, what to do next. First match wins. It is
 * consulted after each action and every 20 ticks, never inside a work-clock unit.
 */
public final class BuilderPlanner {
	private BuilderPlanner() {}

	/** What the glue found at the cell of the current step. */
	public enum CellState {
		/** The chunk is not loaded. */
		UNLOADED,
		/** The cell already holds what the step wants (air for CLEAR, the block for PLACE/CHECK): costs nothing. */
		ALREADY_RIGHT,
		/** A natural block that may be cleared (CLEAR) or that must go before the block is placed. */
		CLEARABLE,
		/** A block that is not natural: never broken. */
		BLOCKED,
		/** Another entity stands in the cell. */
		ENTITY,
		/** Air or replaceable: ready for the step. */
		FREE
	}

	public enum Type { IDLE, SKIP, YIELD, FETCH, WALK, CLEAR, PLACE, WAIT, FINISH }

	public enum WaitReason { NEEDS_MATERIALS, BLOCKED, IN_THE_WAY, UNREACHABLE, NOT_LOADED }

	public record Action(Type type, WaitReason reason) {
		static Action of(Type t) { return new Action(t, null); }
	}

	/**
	 * @param hasBuild         the builder has a build to work on
	 * @param finished         the cursor is past the last step
	 * @param kind             kind of the current step (null when there is none)
	 * @param cell             what the cell holds
	 * @param materialInHand   the builder carries the material of the step
	 * @param materialReserved the material is reserved for the build in Storage
	 * @param depositWanted    {@code DepositTrip.wanted}: the inventory wants a trip
	 * @param insideBox        the builder stands inside the site box
	 * @param inReach          the builder stands within reach of the cell
	 * @param unreachable      every stand point near the cell was tried and none could be reached lately
	 */
	public record Facts(boolean hasBuild, boolean finished, BuildPlan.Kind kind, CellState cell, boolean materialInHand,
			boolean materialReserved, boolean depositWanted, boolean insideBox, boolean inReach, boolean unreachable) {}

	public static Action next(Facts f) {
		if (!f.hasBuild()) return Action.of(Type.IDLE);
		if (f.finished() || f.kind() == null) return Action.of(Type.FINISH);
		if (f.cell() == CellState.UNLOADED) return new Action(Type.WAIT, WaitReason.NOT_LOADED);
		if (f.cell() == CellState.ALREADY_RIGHT) return Action.of(Type.SKIP);
		if (f.cell() == CellState.BLOCKED) return new Action(Type.WAIT, WaitReason.BLOCKED);
		boolean clearing = f.kind() == BuildPlan.Kind.CLEAR || f.cell() == CellState.CLEARABLE;
		// a natural block under a PLACE step is replaced in one action (clear and place together), so the material must be in hand first
		if (f.kind() != BuildPlan.Kind.CLEAR && !f.materialInHand()) {
			return f.materialReserved() ? (f.depositWanted() ? Action.of(Type.YIELD) : Action.of(Type.FETCH))
					: new Action(Type.WAIT, WaitReason.NEEDS_MATERIALS);
		}
		if (clearing && f.depositWanted()) return Action.of(Type.YIELD);
		if (f.unreachable()) return new Action(Type.WAIT, WaitReason.UNREACHABLE);
		if (f.insideBox() || !f.inReach()) return Action.of(Type.WALK);
		if (f.cell() == CellState.ENTITY) return new Action(Type.WAIT, WaitReason.IN_THE_WAY);
		return Action.of(clearing ? Type.CLEAR : Type.PLACE);
	}
}
