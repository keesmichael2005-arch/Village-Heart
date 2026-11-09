package dev.villageheart.core.blueprint;

/** A blueprint text is wrong. {@code line} is 1-based; 0 when the fault is not on one line. */
public final class BlueprintSyntaxException extends RuntimeException {
	private final int line;

	public BlueprintSyntaxException(int line, String message) {
		super((line > 0 ? "line " + line + ": " : "") + message);
		this.line = line;
	}

	public int line() { return line; }
}
