package dev.villageheart.core.blueprint;

import java.util.LinkedHashMap;
import java.util.Map;

/** Words of the Blueprint Book screen. The lang file must say the same English. */
public final class BookTexts {
	private BookTexts() {}

	public static final String PREFIX = "gui.villageheart.book.";
	public static final Map<String, String> EN = new LinkedHashMap<>();

	static {
		EN.put("title", "Blueprint Book");
		EN.put("buildings", "Buildings");
		EN.put("production", "Production");
		EN.put("defence", "Defence");
		EN.put("pick", "Click a plan, then the ground. Sneak turns it.");
		EN.put("schematics", "V shows plans that are placed and not finished.");
		EN.put("size", "Size: %s x %s x %s");
		EN.put("line", "A straight line, up to 32 blocks.");
		EN.put("tier", "%s tier");
		EN.put("armed", "%s. Click the ground. The book stays with you.");
		EN.put("no_permission", "Only a Steward or the Owner can start a blueprint");
		EN.put("locked", "Requires %s tier");
		EN.put("unknown", "Unknown blueprint");
		EN.put("no_book", "Hold the Blueprint Book");
	}

	public static String key(String suffix) { return PREFIX + suffix; }

	public static String english(String suffix) { return EN.get(suffix); }
}
