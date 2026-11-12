package dev.villageheart.core.sim;

import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.worker.JobType;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Field shapes of the M11 harness actions (site, prefab, field, grove, hire), checked when the scenario is parsed so a typo fails in a unit
 * test and not two minutes into a simulation. Every error names the field.
 */
public final class ActionSpecs {
	private ActionSpecs() {}

	public static final Set<String> DOORS = Set.of("north", "south", "east", "west");
	public static final Set<String> CONTENTS = Set.of("bed", "light", "chest", "composter", "chopping_block", "smoker", "crafting_table", "grindstone", "target", "blast_furnace", "anvil", "builders_table", "stonecutter", "fletching_table", "market_stall");
	/** The standing-order conditions: the village has reached at least this tier. */
	public static final Set<String> WHEN = Set.of("tier>=village", "tier>=town");
	public static final Set<String> CROPS = Set.of("carrots", "potatoes", "wheat", "beetroots");

	/** @throws IllegalArgumentException naming the bad field; unknown action types are not this class's business. */
	public static void validate(String type, Map<String, Object> f) {
		if (f.containsKey("when")) {
			if (!(f.get("when") instanceof String w) || !WHEN.contains(w)) throw new IllegalArgumentException("when must be one of " + new java.util.TreeSet<>(WHEN) + ": " + f.get("when"));
		}
		switch (type) {
			case "site" -> {
				int[] size = ints(f, "size", 2, 1, 96);
				int min = integer(f, "min_distance", 0, 0, 200), max = integer(f, "max_distance", 72, 1, 200);
				if (min > max) throw new IllegalArgumentException("min_distance must be <= max_distance");
				if (size[0] < 1) throw new IllegalArgumentException("size");
				integer(f, "max_deviation", 3, 1, 8);
				integer(f, "odd_percent", 0, 0, 10);
			}
			case "prefab" -> {
				string(f, "building", BuildingType.class);
				ints(f, "at", 2, 0, 200);
				ints(f, "size", 2, 1, 12);
				String door = str(f, "door");
				if (!DOORS.contains(door)) throw new IllegalArgumentException("door must be one of " + new java.util.TreeSet<>(DOORS) + ": " + door);
				Object c = f.get("contents");
				if (!(c instanceof List<?> l)) throw new IllegalArgumentException("contents must be a list");
				for (Object o : l) {
					if (!(o instanceof String s) || !CONTENTS.contains(s)) {
						throw new IllegalArgumentException("contents: unknown word " + o + " (known: " + new java.util.TreeSet<>(CONTENTS) + ")");
					}
				}
				Object items = f.get("chest_items");
				if (items != null) {
					if (!(items instanceof Map<?, ?> m)) throw new IllegalArgumentException("chest_items must be an object");
					for (Map.Entry<?, ?> e : m.entrySet()) {
						if (!(e.getKey() instanceof String k) || !k.contains(":")) throw new IllegalArgumentException("chest_items: bad item id " + e.getKey());
						if (!(e.getValue() instanceof Long n) || n < 1 || n > 2304) throw new IllegalArgumentException("chest_items: bad count for " + k);
					}
				}
			}
			case "field" -> {
				ints(f, "at", 2, 0, 200);
				int size = integer(f, "size", -1, 3, 9);
				if (size % 2 == 0) throw new IllegalArgumentException("size must be odd: " + size);
				String crop = str(f, "crop");
				if (!CROPS.contains(crop)) throw new IllegalArgumentException("crop must be one of " + new java.util.TreeSet<>(CROPS) + ": " + crop);
				integer(f, "age", 7, 0, 7);
			}
			case "grove" -> {
				ints(f, "area", 4, 0, 200);
				integer(f, "trees", -1, 1, 64);
				integer(f, "spacing", -1, 2, 16);
				integer(f, "seed", 0, Integer.MIN_VALUE, Integer.MAX_VALUE);
			}
			case "hire" -> {
				String job = str(f, "job");
				if (JobType.byId(job).isEmpty()) throw new IllegalArgumentException("job: unknown job " + job);
				string(f, "building", BuildingType.class);
				if (f.containsKey("teleport") && !(f.get("teleport") instanceof Boolean)) throw new IllegalArgumentException("teleport must be true or false");
				if (f.containsKey("replace") && !(f.get("replace") instanceof Boolean)) throw new IllegalArgumentException("replace must be true or false");
			}
			case "tier" -> {
				String t = str(f, "tier");
				if (!java.util.List.of("hamlet", "village", "town").contains(t)) throw new IllegalArgumentException("tier must be hamlet, village or town: " + t);
			}
			case "training_yard", "hunting_ground" -> ints(f, "at", 2, 0, 200);
			case "mineshaft" -> {
				ints(f, "at", 2, 0, 200);
				integer(f, "depth", 14, 12, 40);
			}
			case "wall_ring" -> {
				ints(f, "size", 2, 8, 200);
				integer(f, "gate", 0, 1, 198);
			}
			case "lamps" -> {
				ints(f, "size", 2, 8, 200);
				integer(f, "spacing", 7, 3, 16);
			}
			case "raid" -> {
				integer(f, "omen", -1, 1, 5);
				if (f.containsKey("scaled") && !(f.get("scaled") instanceof Boolean)) throw new IllegalArgumentException("scaled must be true or false");
			}
			case "golems" -> integer(f, "count", -1, 0, 4);
			case "top_up_villagers" -> {
				integer(f, "adults", -1, 1, 50);
				ints(f, "at", 2, -80, 80);
			}
			case "worker_level" -> {
				String job = str(f, "job");
				if (JobType.byId(job).isEmpty()) throw new IllegalArgumentException("job: unknown job " + job);
				integer(f, "level", -1, 1, 3);
			}
			case "siege" -> integer(f, "zombies", -1, 1, 20);
			case "threats" -> {
				bool(f, "random_raids");
				bool(f, "sieges");
			}
			case "build" -> {
				String id = str(f, "blueprint");
				if (dev.villageheart.core.blueprint.Blueprints.byId(id).isEmpty()) throw new IllegalArgumentException("blueprint: unknown " + id);
				ints(f, "from", 2, 0, 200);
				ints(f, "to", 2, 0, 200);
			}
			case "move_in" -> ints(f, "size", 2, 8, 200);
			case "night_spawn" -> {
				String mob = str(f, "mob");
				if (!mob.contains(":")) throw new IllegalArgumentException("mob must be a namespaced entity id: " + mob);
				integer(f, "count", -1, 1, 16);
				integer(f, "radius", 20, 13, 60);
				if (f.containsKey("outside")) ints(f, "outside", 2, 8, 200);
			}
			default -> { }
		}
	}

	private static void bool(Map<String, Object> f, String k) {
		if (!(f.get(k) instanceof Boolean)) throw new IllegalArgumentException(k + " must be true or false");
	}

	private static String str(Map<String, Object> f, String k) {
		if (!(f.get(k) instanceof String s)) throw new IllegalArgumentException(k + " must be a string");
		return s;
	}

	private static void string(Map<String, Object> f, String k, Class<BuildingType> unused) {
		String s = str(f, k);
		if (BuildingType.byId(s).isEmpty()) throw new IllegalArgumentException(k + ": unknown building " + s);
	}

	private static int integer(Map<String, Object> f, String k, int dflt, int lo, int hi) {
		Object o = f.get(k);
		if (o == null) {
			if (dflt == -1 && lo > -1) throw new IllegalArgumentException(k + " is required");
			return dflt;
		}
		if (!(o instanceof Long l)) throw new IllegalArgumentException(k + " must be an integer");
		if (l < lo || l > hi) throw new IllegalArgumentException(k + " must be " + lo + ".." + hi + ": " + l);
		return l.intValue();
	}

	private static int[] ints(Map<String, Object> f, String k, int n, int lo, int hi) {
		if (!(f.get(k) instanceof List<?> l) || l.size() != n) throw new IllegalArgumentException(k + " must be a list of " + n + " integers");
		int[] out = new int[n];
		for (int i = 0; i < n; i++) {
			if (!(l.get(i) instanceof Long v) || v < lo || v > hi) throw new IllegalArgumentException(k + "[" + i + "] must be an integer " + lo + ".." + hi);
			out[i] = v.intValue();
		}
		return out;
	}
}
