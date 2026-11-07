package dev.villageheart.core.building;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.BuildingRecord;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.room.Pos;
import dev.villageheart.core.room.RoomProblem;
import dev.villageheart.core.room.RoomProblem.Kind;
import java.util.List;
import org.junit.jupiter.api.Test;

class VerdictPolicyTest {
	static final HeartLocation MARKER = new HeartLocation("minecraft:overworld", 1, 64, 2);
	static final Reason NOT_LOADED = Reason.notLoaded(new RoomProblem(Kind.NOT_LOADED, new Pos(1, 2, 3)).message());

	@Test void deferredNeverChangesAnything() {
		for (BuildingStatus s : BuildingStatus.values()) {
			BuildingRecord r = new BuildingRecord("storage", MARKER, s, null);
			VerdictPolicy.Outcome o = VerdictPolicy.apply(r, Verdict.deferred(NOT_LOADED));
			assertSame(r, o.record());
			assertFalse(o.changed());
		}
	}

	@Test void sameVerdictIsNotAChange() {
		BuildingRecord r = new BuildingRecord("storage", MARKER, BuildingStatus.VALID, null);
		VerdictPolicy.Outcome o = VerdictPolicy.apply(r, new Verdict(BuildingStatus.VALID, List.of(), 2, null));
		assertFalse(o.changed());
		assertEquals(r, o.record());
	}

	@Test void newStatusIsAChange() {
		BuildingRecord r = new BuildingRecord("storage", MARKER, BuildingStatus.VALID, null);
		VerdictPolicy.Outcome o = VerdictPolicy.apply(r, Verdict.of(List.of(Reason.needsMore(1, Thing.CHEST)), 1, null));
		assertTrue(o.changed());
		assertEquals(BuildingStatus.INVALID, o.record().status());
	}

	@Test void newLinkIsAChangeInTheSameDimension() {
		BuildingRecord r = new BuildingRecord("mineshaft", MARKER, BuildingStatus.VALID, null);
		VerdictPolicy.Outcome o = VerdictPolicy.apply(r, new Verdict(BuildingStatus.VALID, List.of(), 0, new Pos(40, 70, 12)));
		assertTrue(o.changed());
		assertEquals(new HeartLocation("minecraft:overworld", 40, 70, 12), o.record().link());
		VerdictPolicy.Outcome back = VerdictPolicy.apply(o.record(), Verdict.of(List.of(Reason.noParent(BuildingType.MINE, 48)), 0, null));
		assertTrue(back.changed());
		assertNull(back.record().link());
	}

	@Test void deferredVerdictIsRecognised() {
		assertTrue(Verdict.deferred(NOT_LOADED).deferred());
		assertFalse(Verdict.of(List.of(NOT_LOADED), 0, null).deferred(), "an INVALID verdict is a real verdict");
	}
}
