package dev.villageheart.core.job;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ToolAllocationTest {
	private static final UUID A = new UUID(0, 1), B = new UUID(0, 2), C = new UUID(0, 3), D = new UUID(0, 4);

	private static List<ToolAllocation.Tool<String>> tools() {
		return List.of(new ToolAllocation.Tool<>("iron@3", ToolTier.IRON, 3), new ToolAllocation.Tool<>("stone@0", ToolTier.STONE, 0),
				new ToolAllocation.Tool<>("stone@5", ToolTier.STONE, 5));
	}

	@Test
	void bestToolsGoToTheEarliestHired() {
		Map<UUID, String> m = ToolAllocation.assign(tools(), List.of(A, B, C, D));
		assertEquals("iron@3", m.get(A));
		assertEquals("stone@0", m.get(B));
		assertEquals("stone@5", m.get(C));
		assertNull(m.get(D));
		assertEquals(3, m.size());
	}

	@Test
	void removingTheFirstWorkerPromotesTheNext() {
		Map<UUID, String> m = ToolAllocation.assign(tools(), List.of(B, C, D));
		assertEquals("iron@3", m.get(B));
		assertEquals("stone@0", m.get(C));
		assertEquals("stone@5", m.get(D));
	}

	@Test
	void tiesAreOrderedByContainerThenSlotAndAreStable() {
		List<ToolAllocation.Tool<String>> a = List.of(new ToolAllocation.Tool<>("late", ToolTier.STONE, 40),
				new ToolAllocation.Tool<>("early", ToolTier.STONE, 4));
		for (int i = 0; i < 5; i++) {
			Map<UUID, String> m = ToolAllocation.assign(a, List.of(A, B));
			assertEquals("early", m.get(A));
			assertEquals("late", m.get(B));
		}
	}

	@Test
	void aHigherRankBeatsAnyTierAndTheOldAssignIsRankedByTier() {
		List<ToolAllocation.Tool<String>> t = List.of(new ToolAllocation.Tool<>("netherite", ToolTier.NETHERITE, 0),
				new ToolAllocation.Tool<>("bow", ToolTier.WOOD, 9), new ToolAllocation.Tool<>("bow2", ToolTier.WOOD, 3));
		Map<UUID, String> ranked = ToolAllocation.assign(t, List.of(A, B, C), tool -> tool.ref().startsWith("bow") ? 100 : tool.tier().ordinal());
		assertEquals("bow2", ranked.get(A)); // equal ranks keep container/slot order
		assertEquals("bow", ranked.get(B));
		assertEquals("netherite", ranked.get(C));
		assertEquals("netherite", ToolAllocation.assign(t, List.of(A)).get(A));
		assertEquals(ToolAllocation.assign(t, List.of(A, B, C)), ToolAllocation.assign(t, List.of(A, B, C), tool -> tool.tier().ordinal()));
	}

	@Test
	void noToolsMeansNoAssignment() {
		assertTrue(ToolAllocation.assign(List.<ToolAllocation.Tool<String>>of(), List.of(A)).isEmpty());
	}
}
