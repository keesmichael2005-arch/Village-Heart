package dev.villageheart.core.heart;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class RateLimiterTest {
	@Test void capacityThenRefill() {
		RateLimiter r = new RateLimiter();
		UUID p = UUID.randomUUID();
		for (int i = 0; i < 8; i++) assertTrue(r.tryAcquire(p, 0), "acquire " + i);
		assertFalse(r.tryAcquire(p, 0));
		assertFalse(r.tryAcquire(p, 4));
		assertTrue(r.tryAcquire(p, 5));
		assertFalse(r.tryAcquire(p, 5));
		assertFalse(r.tryAcquire(p, 9));
		assertTrue(r.tryAcquire(p, 10));
	}

	@Test void refillIsCappedAtCapacity() {
		RateLimiter r = new RateLimiter();
		UUID p = UUID.randomUUID();
		for (int i = 0; i < 8; i++) assertTrue(r.tryAcquire(p, 0));
		for (int i = 0; i < 8; i++) assertTrue(r.tryAcquire(p, 10_000), "acquire " + i);
		assertFalse(r.tryAcquire(p, 10_000));
	}

	@Test void playersAreIndependent() {
		RateLimiter r = new RateLimiter();
		UUID a = UUID.randomUUID(), b = UUID.randomUUID();
		for (int i = 0; i < 8; i++) assertTrue(r.tryAcquire(a, 0));
		assertFalse(r.tryAcquire(a, 0));
		assertTrue(r.tryAcquire(b, 0));
	}

	@Test void timeGoingBackwardsRefills() {
		RateLimiter r = new RateLimiter();
		UUID p = UUID.randomUUID();
		for (int i = 0; i < 8; i++) assertTrue(r.tryAcquire(p, 5000));
		assertFalse(r.tryAcquire(p, 5000));
		assertDoesNotThrow(() -> r.tryAcquire(p, 3));
		assertTrue(r.tryAcquire(p, 3));
	}

	@Test void forgetAndClear() {
		RateLimiter r = new RateLimiter();
		UUID p = UUID.randomUUID();
		for (int i = 0; i < 8; i++) r.tryAcquire(p, 0);
		assertFalse(r.tryAcquire(p, 0));
		r.forget(p);
		assertTrue(r.tryAcquire(p, 0));
		for (int i = 0; i < 8; i++) r.tryAcquire(p, 0);
		r.clear();
		assertTrue(r.tryAcquire(p, 0));
	}
}
