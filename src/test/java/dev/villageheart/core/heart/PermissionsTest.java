package dev.villageheart.core.heart;

import static dev.villageheart.core.heart.Permission.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.Rank;
import dev.villageheart.core.VillageState;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PermissionsTest {
	private static void pin(Rank rank, Set<Permission> allowed) {
		for (Permission p : Permission.values()) {
			assertEquals(allowed.contains(p), Permissions.allows(rank, p), rank + " / " + p);
		}
	}

	@Test void matrixIsPinned() {
		pin(Rank.VISITOR, EnumSet.of(VIEW_OVERVIEW));
		pin(Rank.RESIDENT, EnumSet.of(VIEW_OVERVIEW, VIEW_DETAILS));
		pin(Rank.STEWARD, EnumSet.allOf(Permission.class));
		pin(Rank.OWNER, EnumSet.allOf(Permission.class));
	}

	@Test void strangerIsVisitor() {
		UUID owner = UUID.randomUUID();
		VillageState v = VillageState.founded(new HeartLocation("minecraft:overworld", 0, 64, 0), owner, "O", 0);
		Rank stranger = v.rankOf(UUID.randomUUID());
		assertEquals(Rank.VISITOR, stranger);
		pin(stranger, EnumSet.of(VIEW_OVERVIEW));
		pin(v.rankOf(owner), EnumSet.allOf(Permission.class));
	}
}
