package dev.villageheart.core.farm;

import dev.villageheart.core.Balance;
import dev.villageheart.core.room.Pos;

/** The farmer's work area (M10 plan 3.2 #4): a disc around the job block (the composter), limited in height. */
public final class FieldArea {
	private FieldArea() {}

	/** True when {@code cropCell} is within {@link Balance#FARM_RADIUS} blocks (xz, Euclidean, inclusive) and {@link Balance#FARM_DY} in y. */
	public static boolean contains(Pos jobBlock, Pos cropCell) {
		long dx = cropCell.x() - jobBlock.x();
		long dz = cropCell.z() - jobBlock.z();
		return dx * dx + dz * dz <= (long) Balance.FARM_RADIUS * Balance.FARM_RADIUS && Math.abs(cropCell.y() - jobBlock.y()) <= Balance.FARM_DY;
	}
}
