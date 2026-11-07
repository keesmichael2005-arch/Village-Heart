package dev.villageheart.core.heart;

import dev.villageheart.core.HeartLocation;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** A change a player asks for through the Heart screen. M18 adds SetGuardShift. */
public sealed interface HeartAction permits HeartAction.Assign, HeartAction.Fire, HeartAction.Recheck {
	Permission permission();

	record Assign(UUID villager, String jobId, Optional<HeartLocation> building) implements HeartAction {
		public Assign {
			Objects.requireNonNull(villager, "villager");
			Objects.requireNonNull(jobId, "jobId");
			Objects.requireNonNull(building, "building");
		}

		@Override public Permission permission() { return Permission.HIRE; }
	}

	record Fire(UUID villager) implements HeartAction {
		public Fire { Objects.requireNonNull(villager, "villager"); }

		@Override public Permission permission() { return Permission.FIRE; }
	}

	record Recheck(HeartLocation marker) implements HeartAction {
		public Recheck { Objects.requireNonNull(marker, "marker"); }

		@Override public Permission permission() { return Permission.RECHECK_BUILDING; }
	}
}
