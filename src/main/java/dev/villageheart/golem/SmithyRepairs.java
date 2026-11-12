package dev.villageheart.golem;

import dev.villageheart.core.HeartLocation;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;

/**
 * One hurt golem per Smithy. The goal claims an offer when it starts walking and registers itself as waiting once it stands at the spot.
 * Server thread only.
 */
public final class SmithyRepairs {
	public record Offer(HeartLocation smithy, BlockPos spot, BlockPos door) {}

	private static final Map<HeartLocation, UUID> HELD = new HashMap<>();
	private static final Map<UUID, HeartLocation> WAITING = new HashMap<>();
	private static final Map<UUID, Long> COOLDOWN = new HashMap<>();

	private SmithyRepairs() {}

	public static boolean heldByOther(HeartLocation smithy, UUID golem) {
		UUID held = HELD.get(smithy);
		return held != null && !held.equals(golem);
	}

	public static void hold(HeartLocation smithy, UUID golem) { HELD.put(smithy, golem); }

	public static void waiting(HeartLocation smithy, UUID golem) { WAITING.put(golem, smithy); }

	public static Optional<UUID> waitingAt(HeartLocation smithy) {
		for (var e : WAITING.entrySet()) if (e.getValue().equals(smithy)) return Optional.of(e.getKey());
		return Optional.empty();
	}

	public static void release(UUID golem) {
		WAITING.remove(golem);
		HELD.entrySet().removeIf(e -> e.getValue().equals(golem));
	}

	public static long cooldown(UUID golem) { return COOLDOWN.getOrDefault(golem, Long.MIN_VALUE); }

	public static void cooldown(UUID golem, long until) { COOLDOWN.put(golem, until); }

	public static void reset() {
		HELD.clear();
		WAITING.clear();
		COOLDOWN.clear();
	}
}
