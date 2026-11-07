package dev.villageheart.core.heart;

import dev.villageheart.core.Balance;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Token bucket per player over game ticks. Not thread safe: server thread only. */
public final class RateLimiter {
	private static final class Bucket {
		int tokens;
		long last;
	}

	private final int capacity;
	private final int refillTicks;
	private final Map<UUID, Bucket> buckets = new HashMap<>();

	public RateLimiter() { this(Balance.HEART_RATE_CAPACITY, Balance.HEART_RATE_REFILL_TICKS); }

	public RateLimiter(int capacity, int refillTicks) {
		if (capacity < 1 || refillTicks < 1) throw new IllegalArgumentException("capacity and refillTicks must be >= 1");
		this.capacity = capacity;
		this.refillTicks = refillTicks;
	}

	public boolean tryAcquire(UUID player, long gameTime) {
		Bucket b = buckets.get(player);
		if (b == null) {
			b = new Bucket();
			b.tokens = capacity;
			b.last = gameTime;
			buckets.put(player, b);
		} else if (gameTime < b.last) {
			// Time went backwards (another world on a reused server): start fresh rather than lock the player out.
			b.tokens = capacity;
			b.last = gameTime;
		} else {
			long add = (gameTime - b.last) / refillTicks;
			if (add > 0) {
				b.tokens = (int) Math.min(capacity, b.tokens + add);
				b.last += add * refillTicks;
				if (b.tokens == capacity) b.last = gameTime;
			}
		}
		if (b.tokens <= 0) return false;
		b.tokens--;
		return true;
	}

	public void forget(UUID player) { buckets.remove(player); }

	public void clear() { buckets.clear(); }
}
