package dev.villageheart.core.guide;

import dev.villageheart.core.Tier;
import dev.villageheart.core.blueprint.DefenceBlueprints;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Which guide tasks the signals have earned, which one is next, and what the village is waiting on. */
public final class GuideBook {
	private GuideBook() {}

	public static final String NEED_EMERALDS = "emeralds";
	public static final String NEED_FIELD = "field";
	public static final String NEED_YARD = "yard";

	public static GuideProgress advance(GuideProgress was, GuideSignals signals) {
		Set<String> met = new LinkedHashSet<>();
		for (GuideTask task : GuideTask.values()) if (met(task, signals)) met.add(task.id());
		return was.plus(met);
	}

	/** The first task not yet done, or empty when the book is finished. */
	public static String current(GuideProgress progress) {
		for (GuideTask task : GuideTask.values()) if (!progress.has(task.id())) return task.id();
		return "";
	}

	/** Done ids in book order, for the screen. */
	public static List<String> doneInOrder(GuideProgress progress) {
		List<String> ids = new ArrayList<>();
		for (GuideTask task : GuideTask.values()) if (progress.has(task.id())) ids.add(task.id());
		return ids;
	}

	/** The next undone tasks, in book order. */
	public static List<String> upcoming(GuideProgress progress, int limit) {
		List<String> ids = new ArrayList<>();
		for (GuideTask task : GuideTask.values()) {
			if (progress.has(task.id())) continue;
			ids.add(task.id());
			if (ids.size() == limit) break;
		}
		return ids;
	}

	/** Tasks {@code after} has that {@code before} did not, in book order. */
	public static List<String> newlyDone(GuideProgress before, GuideProgress after) {
		List<String> ids = new ArrayList<>();
		for (GuideTask task : GuideTask.values()) {
			if (after.has(task.id()) && !before.has(task.id())) ids.add(task.id());
		}
		return ids;
	}

	/** What the village is stuck on right now. These come and go. They do not check off a task. */
	public static List<String> needs(GuideSignals signals) {
		List<String> needs = new ArrayList<>();
		if (signals.wageShort() > 0) needs.add(NEED_EMERALDS);
		if (signals.job("farmer") && signals.valid("farmhouse") && !signals.field()) needs.add(NEED_FIELD);
		if (signals.job("guard") && !signals.valid("training_yard")) needs.add(NEED_YARD);
		return needs;
	}

	static boolean met(GuideTask task, GuideSignals s) {
		if (task.itemId() != null) return crafted(task, s);
		return switch (task) {
			case OPEN_HEART -> s.heartOpen();
			case HOUSE -> s.valid("house");
			case STORAGE -> s.valid("storage");
			case EMERALDS -> s.emeralds();
			case FIELD -> s.field();
			case FARMHOUSE -> s.valid("farmhouse");
			case FARMER -> s.job("farmer");
			case LUMBER_CAMP -> s.valid("lumber_camp");
			case LUMBERJACK -> s.job("lumberjack");
			case KITCHEN -> s.valid("kitchen");
			case COOK -> s.job("cook");
			case DINNER -> s.fedDinner();
			case WAGES -> s.wagesPaid();
			case VILLAGE_TIER -> s.tierAtLeast(Tier.VILLAGE);
			case BUILDERS_HUT -> s.valid("builders_hut");
			case BUILDER -> s.job("builder");
			case HOUSE_MATERIALS -> s.houseStocked();
			case START_BUILD -> s.buildStarted();
			case FINISH_BUILD -> s.buildFinished();
			case MINE -> s.valid("mine");
			case MINESHAFT -> s.valid("mineshaft");
			case MINER -> s.job("miner");
			case HUNTING -> s.valid("hunting_lodge");
			case HUNTING_GROUND -> s.valid("hunting_ground");
			case HUNTER -> s.job("hunter");
			case BARRACKS -> s.valid("barracks");
			case TRAINING_YARD -> s.valid("training_yard");
			case GUARD -> s.job("guard");
			case MARKET -> s.valid("market");
			case MERCHANT -> s.job("merchant");
			case WALL -> s.defence(DefenceBlueprints.WALL_ID);
			case GATE -> s.defence(DefenceBlueprints.GATE_ID);
			case LAMP -> s.defence(DefenceBlueprints.LAMP_POST_ID);
			case TOWN_TIER -> s.tierAtLeast(Tier.TOWN);
			case SMITHY -> s.valid("smithy");
			case BLACKSMITH -> s.job("blacksmith");
			case GOLEM -> s.golem();
			case WATCHTOWER -> s.defence(DefenceBlueprints.WATCHTOWER_ID);
			default -> false;
		};
	}

	/** A craft step. The item in an inventory counts at once. Using it on the building still counts. */
	private static boolean crafted(GuideTask task, GuideSignals s) {
		if (s.carrying(task.itemId())) return true;
		if (task.countsAs() != null && (s.placed(task.countsAs()) || s.valid(task.countsAs()))) return true;
		if (task == GuideTask.BLANK_MARKER && s.markerPlaced()) return true;
		return task == GuideTask.BLUEPRINT_BOOK && s.blueprintBook();
	}
}
