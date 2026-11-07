package dev.villageheart.core.building;

import dev.villageheart.core.BuildingRecord;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.room.Pos;

/**
 * What a verdict does to the saved record: nothing for a deferred verdict (a partly unloaded area must not flip a building),
 * nothing when status and link are unchanged (so the save is not marked dirty by every check). Pure.
 */
public final class VerdictPolicy {
	private VerdictPolicy() {}

	public record Outcome(BuildingRecord record, boolean changed) {}

	public static Outcome apply(BuildingRecord record, Verdict verdict) {
		if (verdict.deferred()) return new Outcome(record, false);
		Pos p = verdict.link();
		HeartLocation link = p == null ? null : new HeartLocation(record.marker().dimension(), p.x(), p.y(), p.z());
		if (verdict.status() == record.status() && java.util.Objects.equals(link, record.link())) return new Outcome(record, false);
		return new Outcome(record.withStatus(verdict.status()).withLink(link), true);
	}
}
