package dev.villageheart.core;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** The village's shared pool of resources that every worker deposits into and draws from. */
public final class Stockpile {
	private final Map<String, Integer> counts = new HashMap<>();

	public void deposit(String resource, int amount) {
		if (amount < 0) throw new IllegalArgumentException("amount must be >= 0");
		counts.merge(resource, amount, Integer::sum);
	}

	/** Takes up to {@code amount}; returns how many were actually taken. */
	public int withdraw(String resource, int amount) {
		int have = counts.getOrDefault(resource, 0);
		int taken = Math.min(have, amount);
		if (taken == have) counts.remove(resource); else counts.put(resource, have - taken);
		return taken;
	}

	public int count(String resource) {
		return counts.getOrDefault(resource, 0);
	}

	public Map<String, Integer> snapshot() {
		return Collections.unmodifiableMap(new HashMap<>(counts));
	}
}
