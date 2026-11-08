package dev.villageheart.core.heart;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A text the server builds: a lang key, an English fallback and format args. The client shows it with
 * {@code Component.translatableWithFallback(key, fallback, args)}, so a missing translation shows English, never the key.
 * The fallback is a format string ({@code %s} per arg); {@link #english()} fills it in. All parts are cut to the wire caps.
 */
public record TextLine(String key, String fallback, List<String> args) {
	public TextLine {
		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(fallback, "fallback");
		Objects.requireNonNull(args, "args");
		key = HeartLimits.cut(key, HeartLimits.MAX_KEY);
		fallback = HeartLimits.cut(fallback, HeartLimits.MAX_TEXT);
		List<String> cut = new ArrayList<>();
		for (String a : args) {
			if (cut.size() == HeartLimits.MAX_TEXT_ARGS) break;
			cut.add(HeartLimits.cut(a, HeartLimits.MAX_ARG));
		}
		args = List.copyOf(cut);
	}

	public TextLine(String key, String fallback) { this(key, fallback, List.of()); }

	/** The fallback with each {@code %s} replaced by the next arg and {@code %%} read as a percent sign. */
	public String english() {
		StringBuilder out = new StringBuilder();
		int next = 0;
		for (int i = 0; i < fallback.length(); i++) {
			char c = fallback.charAt(i);
			if (c == '%' && i + 1 < fallback.length() && fallback.charAt(i + 1) == 's') {
				out.append(next < args.size() ? args.get(next++) : "");
				i++;
			} else if (c == '%' && i + 1 < fallback.length() && fallback.charAt(i + 1) == '%') {
				out.append('%');
				i++;
			} else {
				out.append(c);
			}
		}
		return out.toString();
	}
}
