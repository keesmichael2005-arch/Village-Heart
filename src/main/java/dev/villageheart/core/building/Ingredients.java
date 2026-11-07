package dev.villageheart.core.building;

import java.util.regex.Pattern;

/** Ingredient strings use the vanilla recipe syntax: {@code minecraft:chest} or {@code #minecraft:planks}. */
public final class Ingredients {
	private Ingredients() {}

	public static final Pattern VALID = Pattern.compile("#?[a-z0-9_.-]+:[a-z0-9_/.-]+");
	/** The Blank Marker sits in the centre of every marker recipe. */
	public static final String BLANK_MARKER = "villageheart:blank_marker";

	public static boolean isValid(String s) { return s != null && VALID.matcher(s).matches(); }

	public static String require(String s) {
		if (!isValid(s)) throw new IllegalArgumentException("bad ingredient: " + s);
		return s;
	}
}
