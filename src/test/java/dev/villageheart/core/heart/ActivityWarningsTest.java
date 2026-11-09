package dev.villageheart.core.heart;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import org.junit.jupiter.api.Test;

class ActivityWarningsTest {
	@Test
	void everyKindIsDecidedAndTheCantWorkOnesAreWarnings() {
		Set<ActivityKind> warn = Set.of(ActivityKind.NEEDS_TOOL, ActivityKind.TOOL_NO_MATERIALS, ActivityKind.TOOL_NO_SPACE, ActivityKind.ON_STRIKE,
				ActivityKind.TOO_HUNGRY, ActivityKind.NEEDS_FUEL);
		for (ActivityKind k : ActivityKind.values()) assertEquals(warn.contains(k), ActivityWarnings.isWarning(k), k.name());
		assertFalse(ActivityWarnings.isWarning(ActivityKind.MAKING_STONE_TOOL));
	}
}
