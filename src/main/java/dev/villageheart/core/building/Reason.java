package dev.villageheart.core.building;

import dev.villageheart.core.Tier;
import dev.villageheart.core.room.RoomProblem;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * One reason a building is not valid. English text lives here (like the room scanner's); the glue sends it as a
 * translatable-with-fallback component keyed by {@link #translationKey()}.
 *
 * <p>Args per code: REQUIRES_TIER [Tier]; OUTSIDE_CLAIM [Integer distance (rounded up), Integer max]; OTHER_DIMENSION [];
 * ROOM [RoomProblem.Kind, String message]; FLOOR_TOO_SMALL [Integer have, Integer need]; NEEDS_MORE [Integer n, Thing];
 * NEEDS_LINKED_SITE [BuildingType site]; NO_PARENT [BuildingType parent, Integer range (MAX_VALUE = whole village)];
 * SHAFT_TOO_SHALLOW [Integer have, Integer need]; INSIDE_HOME_ROOM []; NOT_LOADED [String message]; NO_VILLAGE [].
 */
public record Reason(Code code, List<Object> args) {
	public enum Code {
		REQUIRES_TIER, OUTSIDE_CLAIM, OTHER_DIMENSION, ROOM, FLOOR_TOO_SMALL, NEEDS_MORE, NEEDS_LINKED_SITE, NO_PARENT,
		SHAFT_TOO_SHALLOW, INSIDE_HOME_ROOM, NOT_LOADED, NO_VILLAGE
	}

	public Reason {
		Objects.requireNonNull(code, "code");
		args = List.copyOf(args);
	}

	public static Reason requiresTier(Tier t) { return new Reason(Code.REQUIRES_TIER, List.of(t)); }
	public static Reason outsideClaim(int distance, int max) { return new Reason(Code.OUTSIDE_CLAIM, List.of(distance, max)); }
	public static Reason otherDimension() { return new Reason(Code.OTHER_DIMENSION, List.of()); }
	public static Reason room(RoomProblem p) { return new Reason(Code.ROOM, List.of(p.kind(), p.message())); }
	public static Reason floorTooSmall(int have, int need) { return new Reason(Code.FLOOR_TOO_SMALL, List.of(have, need)); }
	public static Reason needsMore(int n, Thing t) { return new Reason(Code.NEEDS_MORE, List.of(n, t)); }
	public static Reason needsLinkedSite(BuildingType site) { return new Reason(Code.NEEDS_LINKED_SITE, List.of(site)); }
	public static Reason noParent(BuildingType parent, int range) { return new Reason(Code.NO_PARENT, List.of(parent, range)); }
	public static Reason shaftTooShallow(int have, int need) { return new Reason(Code.SHAFT_TOO_SHALLOW, List.of(have, need)); }
	public static Reason insideHomeRoom() { return new Reason(Code.INSIDE_HOME_ROOM, List.of()); }
	public static Reason notLoaded(String roomMessage) { return new Reason(Code.NOT_LOADED, List.of(roomMessage)); }
	public static Reason noVillage() { return new Reason(Code.NO_VILLAGE, List.of()); }

	public String message() {
		return switch (code) {
			case REQUIRES_TIER -> "Requires " + ((Tier) args.get(0)).displayName() + " tier";
			case OUTSIDE_CLAIM -> "Outside the village: " + args.get(0) + " blocks from the Heart (max " + args.get(1) + ")";
			case OTHER_DIMENSION -> "Not in the village's dimension";
			case ROOM -> (String) args.get(1);
			case FLOOR_TOO_SMALL -> "Room too small: floor " + args.get(0) + " of " + args.get(1);
			case NEEDS_MORE -> "Needs " + args.get(0) + " more " + ((Thing) args.get(1)).phrase((Integer) args.get(0));
			case NEEDS_LINKED_SITE -> "Needs a linked " + ((BuildingType) args.get(0)).displayName();
			case NO_PARENT -> {
				BuildingType parent = (BuildingType) args.get(0);
				int range = (Integer) args.get(1);
				yield range == Integer.MAX_VALUE
						? "No " + parent.displayName() + " in the village"
						: "No " + parent.displayName() + " within " + range + " blocks";
			}
			case SHAFT_TOO_SHALLOW -> "Shaft too shallow: " + args.get(0) + " of " + args.get(1) + " blocks deep";
			case INSIDE_HOME_ROOM -> "Must be outside House and Farmhouse rooms";
			case NOT_LOADED -> "Cannot check now: " + args.get(0);
			case NO_VILLAGE -> "No village yet: place a Village Heart near a bell, then right-click this marker again";
		};
	}

	public String translationKey() { return "message.villageheart.reason." + code.name().toLowerCase(Locale.ROOT); }

	/** The room problem kind of a ROOM reason; null for every other code. */
	public RoomProblem.Kind roomKind() { return code == Code.ROOM ? (RoomProblem.Kind) args.get(0) : null; }

	@Override
	public String toString() { return code + args.toString(); }
}
