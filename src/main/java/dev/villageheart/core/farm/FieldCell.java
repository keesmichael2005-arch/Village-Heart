package dev.villageheart.core.farm;

import org.jetbrains.annotations.Nullable;

/** What stands in one crop cell (the block above a farmland block). */
public record FieldCell(Kind kind, @Nullable CropKind crop, int age) {
	/** CROP: one of the four crops on farmland. EMPTY_FARMLAND: air above farmland. OTHER: anything else. UNLOADED: the chunk is not loaded. */
	public enum Kind { CROP, EMPTY_FARMLAND, OTHER, UNLOADED }

	public static final FieldCell EMPTY = new FieldCell(Kind.EMPTY_FARMLAND, null, 0);
	public static final FieldCell OTHER = new FieldCell(Kind.OTHER, null, 0);
	public static final FieldCell UNLOADED = new FieldCell(Kind.UNLOADED, null, 0);

	public static FieldCell crop(CropKind crop, int age) { return new FieldCell(Kind.CROP, crop, age); }

	public boolean isCrop() { return kind == Kind.CROP; }

	public boolean isMature() { return kind == Kind.CROP && crop.isMature(age); }

	/** A crop that has not ripened yet. */
	public boolean isYoung() { return kind == Kind.CROP && !crop.isMature(age); }
}
