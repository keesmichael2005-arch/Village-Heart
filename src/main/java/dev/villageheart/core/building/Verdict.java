package dev.villageheart.core.building;

import dev.villageheart.core.room.Pos;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * The outcome of one building check. {@code slots} is the number the report shows (worker slots, homes ...), computed even
 * when invalid. {@code link} is the parent marker for a site. A deferred verdict means "no verdict this time".
 */
public record Verdict(BuildingStatus status, List<Reason> reasons, int slots, @Nullable Pos link) {
	public Verdict {
		reasons = List.copyOf(reasons);
	}

	/** Status UNCHECKED with the single NOT_LOADED reason: nothing may change because of it. */
	public static Verdict deferred(Reason notLoaded) {
		return new Verdict(BuildingStatus.UNCHECKED, List.of(notLoaded), 0, null);
	}

	public boolean deferred() {
		return status == BuildingStatus.UNCHECKED && reasons.size() == 1 && reasons.get(0).code() == Reason.Code.NOT_LOADED;
	}

	public static Verdict of(List<Reason> reasons, int slots, @Nullable Pos link) {
		return new Verdict(reasons.isEmpty() ? BuildingStatus.VALID : BuildingStatus.INVALID, reasons, slots, link);
	}
}
