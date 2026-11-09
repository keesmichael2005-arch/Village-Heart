package dev.villageheart.worker.guard;

import dev.villageheart.core.ClaimGeometry;
import dev.villageheart.core.VillageState;
import dev.villageheart.world.VillageClaims;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;

/**
 * Is a raid on the village (M18 3.2, A7)? A raid counts when it is not over or stopped, and its centre is in the claim or one of its raiders
 * is: {@code getRaidAt} alone matches any raid within 96 blocks. A seam, so a test can feed a fake raid view.
 */
public final class RaidCheck {
	/** What the check needs to know about a raid. */
	public interface View {
		boolean active();
		BlockPos center();
		Iterable<? extends net.minecraft.world.entity.Entity> raiders();
	}

	/** Test hook: when set, replaces the real raid lookup. */
	public interface Source { View raidAt(ServerLevel level, BlockPos heart); }

	private static Source source = RaidCheck::real;

	private RaidCheck() {}

	public static Source setSourceForTest(Source s) {
		Source old = source;
		source = s == null ? RaidCheck::real : s;
		return old;
	}

	private static View real(ServerLevel level, BlockPos heart) {
		Raid raid = level.getRaidAt(heart);
		if (raid == null) return null;
		return new View() {
			@Override public boolean active() { return !raid.isOver() && !raid.isStopped(); }
			@Override public BlockPos center() { return raid.getCenter(); }
			@Override public Iterable<? extends net.minecraft.world.entity.Entity> raiders() { return raid.getAllRaiders(); }
		};
	}

	/** True when guards must come on duty. */
	public static boolean active(ServerLevel level, VillageState village) {
		BlockPos heart = new BlockPos(village.heart().x(), village.heart().y(), village.heart().z());
		View raid = source.raidAt(level, heart);
		if (raid == null || !raid.active()) return false;
		BlockPos c = raid.center();
		if (ClaimGeometry.inClaim(village.heart(), VillageClaims.locate(level, c))) return true;
		for (var r : raid.raiders()) {
			if (r instanceof Raider && ClaimGeometry.inClaim(village.heart(), VillageClaims.locate(level, r.blockPosition()))) return true;
		}
		return false;
	}
}
