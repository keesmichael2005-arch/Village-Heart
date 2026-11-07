package dev.villageheart.core.sim;

/** An assertion that does not parse (bad syntax or unknown metric). Raised at parse time, never at evaluation. */
public final class AssertionSyntaxException extends RuntimeException {
	public AssertionSyntaxException(String message) { super(message); }
}
