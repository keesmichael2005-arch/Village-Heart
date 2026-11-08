package dev.villageheart.core.blueprint;

/** Parameters of a generated blueprint (M21's wall segment length). M20's blueprints are fixed: length 0. */
public record BlueprintParams(int length) {
	public static final BlueprintParams NONE = new BlueprintParams(0);

	public BlueprintParams {
		if (length < 0) throw new IllegalArgumentException("length must be >= 0: " + length);
	}
}
