package dev.villageheart.core.blueprint;

import java.util.LinkedHashMap;
import java.util.Map;

/** The words of the Builder's Table screen and of its replies: every key with its English text (the lang file must say the same). */
public final class TableTexts {
	private TableTexts() {}

	public static final String PREFIX = "gui.villageheart.table.";
	public static final Map<String, String> EN = new LinkedHashMap<>();

	static {
		EN.put("title", "Builder's Table");
		EN.put("blueprints", "Blueprints");
		EN.put("materials", "Materials (in Storage / needed)");
		EN.put("size", "Size: %s x %s x %s");
		EN.put("take", "Take blueprint");
		EN.put("builds", "Builds");
		EN.put("builds_none", "No builds in progress");
		EN.put("cancel", "Cancel");
		EN.put("locked", "Requires %s tier");
		EN.put("no_permission", "Only a Steward or the Owner can start a blueprint");
		EN.put("too_many", "Too many builds at once (max 8)");
		EN.put("taken", "You took the %s blueprint");
		EN.put("cancelled", "Build cancelled");
		EN.put("unknown", "Unknown blueprint");
		EN.put("no_such_build", "That build is gone");
		EN.put("too_fast", "Too fast");
		EN.put("builder", "Builder: %s");
		EN.put("no_builder", "No builder yet");
		EN.put("select", "Select a blueprint");
		EN.put("stale", "This screen is out of date");
		EN.put("line_hint", "Two clicks: start and end, up to 32 blocks");
		EN.put("materials_wall", "Per block (in Storage / needed)");
		EN.put("materials_gate", "At 5 blocks; +3 cobblestone per extra block");
		EN.put("gate_note", "Villagers cannot open fence gates");
		EN.put("line_tag", "line");
	}

	public static String key(String suffix) {
		if (!EN.containsKey(suffix)) throw new IllegalArgumentException("unknown table text: " + suffix);
		return PREFIX + suffix;
	}

	public static String english(String suffix) { return EN.get(suffix); }
}
