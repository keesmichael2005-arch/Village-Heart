package dev.villageheart.block;

import net.minecraft.util.StringRepresentable;

/** How a marker plaque looks: M5 maps validation results onto it. */
public enum MarkerStatus implements StringRepresentable {
	UNCHECKED("unchecked"),
	INVALID("invalid"),
	VALID("valid");

	private final String name;

	MarkerStatus(String name) { this.name = name; }

	@Override
	public String getSerializedName() { return name; }
}
