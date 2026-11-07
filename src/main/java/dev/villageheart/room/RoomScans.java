package dev.villageheart.room;

import dev.villageheart.core.room.Dir;
import dev.villageheart.core.room.Pos;
import dev.villageheart.core.room.RoomScan;
import dev.villageheart.core.room.RoomScanner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/** The glue entry point (M5 calls this from markers). */
public final class RoomScans {
	private RoomScans() {}

	/**
	 * Scan the room behind the door that the marker at {@code marker}, hanging out of its wall towards {@code outward}
	 * (a marker block's FACING), marks. Must run on the server thread: off-thread, chunks cannot be read.
	 */
	public static RoomScan scan(ServerLevel level, BlockPos marker, Direction outward) {
		if (!level.getServer().isSameThread()) throw new IllegalStateException("Room scans must run on the server thread");
		return RoomScanner.scanFromMarker(new LevelBlockView(level), pos(marker), Dir.valueOf(outward.name()));
	}

	public static Pos pos(BlockPos p) { return new Pos(p.getX(), p.getY(), p.getZ()); }

	public static BlockPos blockPos(Pos p) { return new BlockPos(p.x(), p.y(), p.z()); }
}
