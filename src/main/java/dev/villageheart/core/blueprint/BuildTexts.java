package dev.villageheart.core.blueprint;

import dev.villageheart.core.heart.TextLine;
import java.util.List;

/** The words of the build system (docs/plans/M20.md 3.6). Every text is a {@link TextLine}: lang key, English fallback, string args. */
public final class BuildTexts {
	private BuildTexts() {}

	public static final String PREFIX = "build.villageheart.";
	public static final String NEEDS_KEY = PREFIX + "needs";

	/** Block names in texts are the path of the block id: {@code minecraft:oak_leaves} reads "oak_leaves". */
	public static String blockName(String blockId) { return blockId.substring(blockId.indexOf(':') + 1); }

	/** "Needs 12 more planks" (the plural unless n is 1). */
	public static TextLine missing(Material material, int n) {
		return new TextLine(NEEDS_KEY, "Needs %s more %s", List.of(Integer.toString(n), n == 1 ? material.name() : material.plural()));
	}

	/** The English fallback of each status key, for the lang test. */
	public static String fallback(BuildStatus s) {
		return switch (s) {
			case WAITING_FOR_BUILDER -> "Waiting for a builder";
			case CLEARING -> "Clearing the site";
			case BUILDING -> "Building: %s%%";
			case CHECKING -> "Checking the work";
			case NEEDS_MATERIALS -> "Needs %s more %s";
			case BLOCKED -> "%s in the way at %s";
			case IN_THE_WAY -> "Something is in the way at %s";
			case UNREACHABLE -> "Can't reach %s";
			case NOT_LOADED -> "Site not loaded";
		};
	}

	public static String key(BuildStatus s) { return s == BuildStatus.NEEDS_MATERIALS ? NEEDS_KEY : PREFIX + "status." + s.langSuffix(); }

	/**
	 * The status line of a build. {@code args} depends on the status: BUILDING [percent], NEEDS_MATERIALS [n, words], BLOCKED [block id path,
	 * position], IN_THE_WAY and UNREACHABLE [position].
	 */
	public static TextLine status(BuildStatus s, List<String> args) { return new TextLine(key(s), fallback(s), args); }

	public static TextLine building(int percent) { return status(BuildStatus.BUILDING, List.of(Integer.toString(percent))); }

	public static TextLine blueprintName(Blueprint bp) { return new TextLine(PREFIX + "blueprint." + bp.id(), bp.name()); }
}
