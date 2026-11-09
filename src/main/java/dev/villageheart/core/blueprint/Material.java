package dev.villageheart.core.blueprint;

import java.util.Objects;
import org.jetbrains.annotations.Nullable;

/**
 * What a blueprint cell is made of: an exact item id ({@code minecraft:torch}) or an item tag ({@code #minecraft:planks}), with the
 * English words used in texts. {@code returns} is an item the builder gets back (the bucket of a water bucket).
 */
public record Material(String id, String name, String plural, @Nullable String returns) {
	public Material {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(plural, "plural");
	}

	public boolean isTag() { return id.startsWith("#"); }

	/** The id without the leading '#'. */
	public String bareId() { return isTag() ? id.substring(1) : id; }

	/** {@code n} of this material in words: "1 door", "12 planks". */
	public String words(int n) { return n + " " + (n == 1 ? name : plural); }
}
