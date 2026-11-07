package dev.villageheart.core.sim;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal strict JSON model. Values are Map (LinkedHashMap), List, String, Long, Double, Boolean or null.
 * Integers without fraction/exponent parse as Long, everything else numeric as Double.
 */
public final class Json {
	private Json() {}

	public static final class JsonException extends RuntimeException {
		public JsonException(String message) { super(message); }
	}

	public static Object parse(String text) {
		Parser p = new Parser(text);
		p.skipWs();
		Object v = p.value();
		p.skipWs();
		if (p.pos < text.length()) throw p.error("trailing garbage");
		return v;
	}

	// ---- writing ----

	public static String write(Object value, boolean pretty) {
		StringBuilder sb = new StringBuilder();
		writeValue(sb, value, pretty, 0);
		if (pretty) sb.append('\n');
		return sb.toString();
	}

	private static void indent(StringBuilder sb, int n) {
		for (int i = 0; i < n; i++) sb.append("  ");
	}

	private static void writeValue(StringBuilder sb, Object v, boolean pretty, int depth) {
		if (v == null) {
			sb.append("null");
		} else if (v instanceof String s) {
			writeString(sb, s);
		} else if (v instanceof Boolean b) {
			sb.append(b.booleanValue());
		} else if (v instanceof Double || v instanceof Float) {
			double d = ((Number) v).doubleValue();
			if (Double.isNaN(d) || Double.isInfinite(d)) sb.append("null");
			else sb.append(Double.toString(d));
		} else if (v instanceof Number n) {
			sb.append(n.longValue());
		} else if (v instanceof Map<?, ?> m) {
			if (m.isEmpty()) { sb.append("{}"); return; }
			sb.append('{');
			boolean first = true;
			for (Map.Entry<?, ?> e : m.entrySet()) {
				if (!first) sb.append(',');
				first = false;
				if (pretty) { sb.append('\n'); indent(sb, depth + 1); }
				writeString(sb, String.valueOf(e.getKey()));
				sb.append(pretty ? ": " : ":");
				writeValue(sb, e.getValue(), pretty, depth + 1);
			}
			if (pretty) { sb.append('\n'); indent(sb, depth); }
			sb.append('}');
		} else if (v instanceof List<?> l) {
			if (l.isEmpty()) { sb.append("[]"); return; }
			sb.append('[');
			boolean first = true;
			for (Object o : l) {
				if (!first) sb.append(',');
				first = false;
				if (pretty) { sb.append('\n'); indent(sb, depth + 1); }
				writeValue(sb, o, pretty, depth + 1);
			}
			if (pretty) { sb.append('\n'); indent(sb, depth); }
			sb.append(']');
		} else {
			throw new JsonException("cannot serialise " + v.getClass().getName());
		}
	}

	private static void writeString(StringBuilder sb, String s) {
		sb.append('"');
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
				case '"' -> sb.append("\\\"");
				case '\\' -> sb.append("\\\\");
				case '\n' -> sb.append("\\n");
				case '\r' -> sb.append("\\r");
				case '\t' -> sb.append("\\t");
				case '\b' -> sb.append("\\b");
				case '\f' -> sb.append("\\f");
				default -> {
					if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
					else sb.append(c);
				}
			}
		}
		sb.append('"');
	}

	// ---- typed helpers ----

	@SuppressWarnings("unchecked")
	public static Map<String, Object> obj(Object o, String what) {
		if (o instanceof Map<?, ?> m) return (Map<String, Object>) m;
		throw new JsonException(what + " must be an object");
	}

	@SuppressWarnings("unchecked")
	public static List<Object> arr(Object o, String what) {
		if (o instanceof List<?> l) return (List<Object>) l;
		throw new JsonException(what + " must be an array");
	}

	// ---- parser ----

	private static final class Parser {
		final String s;
		int pos;

		Parser(String s) { this.s = s; }

		JsonException error(String msg) {
			int line = 1, col = 1;
			for (int i = 0; i < Math.min(pos, s.length()); i++) {
				if (s.charAt(i) == '\n') { line++; col = 1; } else col++;
			}
			return new JsonException(msg + " at " + line + ":" + col);
		}

		void skipWs() {
			while (pos < s.length()) {
				char c = s.charAt(pos);
				if (c == ' ' || c == '\t' || c == '\n' || c == '\r') pos++; else break;
			}
		}

		Object value() {
			if (pos >= s.length()) throw error("unexpected end of input");
			char c = s.charAt(pos);
			switch (c) {
				case '{': return object();
				case '[': return array();
				case '"': return string();
				case 't': literal("true"); return Boolean.TRUE;
				case 'f': literal("false"); return Boolean.FALSE;
				case 'n': literal("null"); return null;
				default:
					if (c == '-' || (c >= '0' && c <= '9')) return number();
					throw error("unexpected character '" + c + "'");
			}
		}

		void literal(String lit) {
			if (!s.startsWith(lit, pos)) throw error("expected " + lit);
			pos += lit.length();
		}

		Map<String, Object> object() {
			pos++;
			Map<String, Object> m = new LinkedHashMap<>();
			skipWs();
			if (pos < s.length() && s.charAt(pos) == '}') { pos++; return m; }
			while (true) {
				skipWs();
				if (pos >= s.length() || s.charAt(pos) != '"') throw error("expected string key");
				String k = string();
				skipWs();
				if (pos >= s.length() || s.charAt(pos) != ':') throw error("expected ':'");
				pos++;
				skipWs();
				m.put(k, value());
				skipWs();
				if (pos >= s.length()) throw error("unterminated object");
				char c = s.charAt(pos++);
				if (c == '}') return m;
				if (c != ',') { pos--; throw error("expected ',' or '}'"); }
			}
		}

		List<Object> array() {
			pos++;
			List<Object> l = new ArrayList<>();
			skipWs();
			if (pos < s.length() && s.charAt(pos) == ']') { pos++; return l; }
			while (true) {
				skipWs();
				l.add(value());
				skipWs();
				if (pos >= s.length()) throw error("unterminated array");
				char c = s.charAt(pos++);
				if (c == ']') return l;
				if (c != ',') { pos--; throw error("expected ',' or ']'"); }
			}
		}

		String string() {
			int start = pos;
			pos++;
			StringBuilder sb = new StringBuilder();
			while (true) {
				if (pos >= s.length()) { pos = start; throw error("unclosed string"); }
				char c = s.charAt(pos++);
				if (c == '"') return sb.toString();
				if (c < 0x20) { pos--; throw error("control character in string"); }
				if (c != '\\') { sb.append(c); continue; }
				if (pos >= s.length()) { pos = start; throw error("unclosed string"); }
				char e = s.charAt(pos++);
				switch (e) {
					case '"' -> sb.append('"');
					case '\\' -> sb.append('\\');
					case '/' -> sb.append('/');
					case 'n' -> sb.append('\n');
					case 'r' -> sb.append('\r');
					case 't' -> sb.append('\t');
					case 'b' -> sb.append('\b');
					case 'f' -> sb.append('\f');
					case 'u' -> {
						if (pos + 4 > s.length()) throw error("bad \\u escape");
						try {
							sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
						} catch (NumberFormatException ex) {
							throw error("bad \\u escape");
						}
						pos += 4;
					}
					default -> { pos--; throw error("bad escape '\\" + e + "'"); }
				}
			}
		}

		Object number() {
			int start = pos;
			if (s.charAt(pos) == '-') pos++;
			if (pos >= s.length() || !Character.isDigit(s.charAt(pos))) throw error("bad number");
			if (s.charAt(pos) == '0' && pos + 1 < s.length() && Character.isDigit(s.charAt(pos + 1))) throw error("leading zero");
			while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
			boolean isDouble = false;
			if (pos < s.length() && s.charAt(pos) == '.') {
				isDouble = true;
				pos++;
				if (pos >= s.length() || !Character.isDigit(s.charAt(pos))) throw error("bad fraction");
				while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
			}
			if (pos < s.length() && (s.charAt(pos) == 'e' || s.charAt(pos) == 'E')) {
				isDouble = true;
				pos++;
				if (pos < s.length() && (s.charAt(pos) == '+' || s.charAt(pos) == '-')) pos++;
				if (pos >= s.length() || !Character.isDigit(s.charAt(pos))) throw error("bad exponent");
				while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
			}
			String t = s.substring(start, pos);
			if (!isDouble) {
				try { return Long.parseLong(t); } catch (NumberFormatException ex) { return Double.parseDouble(t); }
			}
			return Double.parseDouble(t);
		}
	}
}
