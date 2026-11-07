package dev.villageheart.core.rank;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.rank.ContainerGuard.GuardArea;
import dev.villageheart.core.room.Dir;
import dev.villageheart.core.room.FakeGrid;
import dev.villageheart.core.room.Pos;
import dev.villageheart.core.room.RoomScan;
import dev.villageheart.core.room.RoomScanner;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ContainerGuardTest {
	static final String OVERWORLD = "minecraft:overworld";

	static RoomScan scan() { return RoomScanner.scanFromMarker(FakeGrid.room5x5x3(), FakeGrid.MARKER, Dir.NORTH); }

	@Test void interiorAndShellAreProtectedOutsideIsNot() {
		RoomScan s = scan();
		assertTrue(s.enclosed());
		List<GuardArea> areas = List.of(new GuardArea(OVERWORLD, FakeGrid.MARKER, Optional.of(s)));
		assertTrue(ContainerGuard.isProtected(OVERWORLD, new Pos(3, 1, 1), areas), "interior");
		assertTrue(ContainerGuard.isProtected(OVERWORLD, new Pos(0, 2, 2), areas), "wall (shell)");
		assertTrue(ContainerGuard.isProtected(OVERWORLD, new Pos(3, 0, 3), areas), "floor (shell)");
		assertFalse(ContainerGuard.isProtected(OVERWORLD, new Pos(-1, 2, 2), areas), "one block outside the wall");
		assertFalse(ContainerGuard.isProtected(OVERWORLD, new Pos(3, 1, -2), areas));
	}

	@Test void otherDimensionIsNotProtected() {
		List<GuardArea> areas = List.of(new GuardArea(OVERWORLD, FakeGrid.MARKER, Optional.of(scan())));
		assertFalse(ContainerGuard.isProtected("minecraft:the_nether", new Pos(3, 1, 1), areas));
		List<GuardArea> noScan = List.of(new GuardArea(OVERWORLD, FakeGrid.MARKER, Optional.empty()));
		assertFalse(ContainerGuard.isProtected("minecraft:the_nether", FakeGrid.MARKER, noScan));
	}

	@Test void withoutAnEnclosedScanTheBoxAroundTheMarkerFailsClosed() {
		Pos m = new Pos(100, 64, 100);
		List<GuardArea> areas = List.of(new GuardArea(OVERWORLD, m, Optional.empty()));
		assertTrue(ContainerGuard.isProtected(OVERWORLD, new Pos(116, 64, 100), areas), "16 is inside");
		assertTrue(ContainerGuard.isProtected(OVERWORLD, new Pos(84, 80, 116), areas), "Chebyshev: all three axes at 16");
		assertFalse(ContainerGuard.isProtected(OVERWORLD, new Pos(117, 64, 100), areas), "17 is outside");
		assertFalse(ContainerGuard.isProtected(OVERWORLD, new Pos(100, 64, 83), areas));
	}

	@Test void noBoxOnceThereIsAnEnclosedScan() {
		RoomScan s = scan();
		List<GuardArea> areas = List.of(new GuardArea(OVERWORLD, FakeGrid.MARKER, Optional.of(s)));
		// 10 blocks from the marker but outside the room: the fallback box must not apply any more
		assertFalse(ContainerGuard.isProtected(OVERWORLD, new Pos(3, 3, -11), areas));
	}

	@Test void noAreasMeansNothingIsProtected() {
		assertFalse(ContainerGuard.isProtected(OVERWORLD, new Pos(0, 0, 0), List.of()));
	}

	@Test void markersAreRecognised() {
		List<GuardArea> areas = List.of(new GuardArea(OVERWORLD, FakeGrid.MARKER, Optional.empty()));
		assertTrue(ContainerGuard.isMarker(OVERWORLD, FakeGrid.MARKER, areas));
		assertFalse(ContainerGuard.isMarker(OVERWORLD, new Pos(1, 1, 1), areas));
		assertFalse(ContainerGuard.isMarker("minecraft:the_nether", FakeGrid.MARKER, areas));
	}
}
