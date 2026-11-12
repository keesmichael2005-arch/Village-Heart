package dev.villageheart.core;

/**
 * English counts with the right noun: "1 emerald", "0 emeralds", "2 merchants". Every text that shows an emerald or merchant count builds
 * the phrase here and passes it as ONE format arg, so a lang string holds no noun that depends on the number.
 */
public final class Plural {
	private Plural() {}

	public static String emeralds(long n) { return count(n, "emerald", "emeralds"); }

	public static String merchants(long n) { return count(n, "merchant", "merchants"); }

	/** One is singular; zero, negatives and every other count are plural. */
	public static String count(long n, String one, String many) { return n + " " + (n == 1 ? one : many); }
}
