package dev.villageheart.core.heart;

import java.util.Objects;

public record AlertLine(AlertCode code, Severity severity, TextLine text) {
	public AlertLine {
		Objects.requireNonNull(code, "code");
		Objects.requireNonNull(severity, "severity");
		Objects.requireNonNull(text, "text");
	}
}
