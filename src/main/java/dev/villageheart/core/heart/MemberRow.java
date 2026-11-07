package dev.villageheart.core.heart;

import dev.villageheart.core.Rank;
import java.util.Objects;
import java.util.UUID;

/** One player in the Ranks tab: who, last known name, rank and whether they are on the server now. */
public record MemberRow(UUID id, String name, Rank rank, boolean online) {
	public MemberRow {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(rank, "rank");
	}
}
