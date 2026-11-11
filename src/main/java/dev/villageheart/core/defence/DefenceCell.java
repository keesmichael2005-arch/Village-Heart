package dev.villageheart.core.defence;

import dev.villageheart.core.room.Pos;
import java.util.Objects;
import java.util.UUID;

/** One counted cell of a recorded defence: where it is, what it is, and the blueprint state it should hold. */
public record DefenceCell(UUID siteId, long packed, DefenceKind kind, String expectedState) {
	public DefenceCell {
		Objects.requireNonNull(siteId, "siteId");
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(expectedState, "expectedState");
	}

	public Pos pos() { return Pos.unpack(packed); }
}
