package dev.villageheart.worker.guard;

import dev.villageheart.core.ClaimGeometry;
import dev.villageheart.core.VillageState;
import dev.villageheart.world.VillageClaims;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;

/**
 * Is a raid on the village, so every guard comes on duty (M18 3.2, A7)? The default counts a raid that is not over or stopped and whose
 * centre is in the claim or one of whose raiders is ({@code getRaidAt} alone matches any raid within 96 blocks). Two seams: {@link #setSource}
 * replaces the whole answer (M25 plugs its raid tracker in here, so there is one raid truth), {@link #setViewSourceForTest} feeds a fake
 * raid to the default.
 */
public final class RaidCheck {
	/** What the default check needs to know about a raid. */
	public interface View {
		boolean active();
		BlockPos center();
		Iterable<? extends net.minecraft.world.entity.Entity> raiders();
	}

	/** Where the default finds a raid at the Heart. */
	public interface ViewSource { View raidAt(ServerLevel level, BlockPos heart); }

	/** The answer guards schedule by. */
	@FunctionalInterface
	public interface Source { boolean underRaid(ServerLevel level, VillageState village); }

	private static ViewSource viewSource = RaidCheck::real;
	private static Source source = RaidCheck::vanilla;

	private RaidCheck() {}

	/** Replaces the answer (null restores the default). Returns the previous one. */
	public static Source setSource(Source s) {
		Source old = source;
		source = s == null ? RaidCheck::vanilla : s;
		return old;
	}

	public static ViewSource setViewSourceForTest(ViewSource s) {
		ViewSource old = viewSource;
		viewSource = s == null ? RaidCheck::real : s;
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

	private static boolean vanilla(ServerLevel level, VillageState village) {
		BlockPos heart = new BlockPos(village.heart().x(), village.heart().y(), village.heart().z());
		View raid = viewSource.raidAt(level, heart);
		if (raid == null || !raid.active()) return false;
		if (ClaimGeometry.inClaim(village.heart(), VillageClaims.locate(level, raid.center()))) return true;
		for (var r : raid.raiders()) {
			if (r instanceof Raider && ClaimGeometry.inClaim(village.heart(), VillageClaims.locate(level, r.blockPosition()))) return true;
		}
		return false;
	}

	/** True when guards must come on duty. */
	public static boolean active(ServerLevel level, VillageState village) { return source.underRaid(level, village); }
}
