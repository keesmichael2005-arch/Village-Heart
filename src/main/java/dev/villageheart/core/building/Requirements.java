package dev.villageheart.core.building;

import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/**
 * The rules of one room building (DESIGN section 3.3).
 *
 * @param jobKey       content key of the building's job block (null: none)
 * @param slots        what {@link Verdict#slots()} counts
 * @param requiredSite outdoor site that must be linked and valid (Mine, Hunting Lodge)
 * @param detail       which number the right-click report shows
 */
public record Requirements(int minFloor, List<ContentRule> contents, @Nullable String jobKey, SlotRule slots,
		@Nullable BuildingType requiredSite, DetailLine detail) {

	public Requirements {
		contents = List.copyOf(contents);
	}

	/**
	 * One content requirement: the sum of counts over {@code keys} must reach {@code max(min, count(scaleKey))} when
	 * {@code scaleKey != null}, else {@code min}. Plain data, so records compare equal.
	 */
	public record ContentRule(Thing thing, Set<String> keys, int min, @Nullable String scaleKey) {
		public ContentRule {
			keys = Set.copyOf(keys);
		}

		public static ContentRule need(Thing thing, int min, String... keys) { return new ContentRule(thing, Set.of(keys), min, null); }
	}

	/** What {@link Verdict#slots()} counts. */
	public enum SlotRule { NONE, JOB_BLOCKS, BEDS, CONTAINERS, MIN_JOB_BLOCKS_AND_BEDS }

	/** Which number the report shows. */
	public enum DetailLine { WORKER_SLOTS, HOMES, CONTAINERS }
}
