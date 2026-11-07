package dev.villageheart.core.sim;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

/**
 * A parsed assertion such as {@code population >= population@day1 - 1 on every day}. See docs/plans/M2.md section 3.5
 * for the grammar. Parse errors throw {@link AssertionSyntaxException}; evaluation problems are a FAIL result.
 */
public final class Assertion {
	// ---- AST ----
	private sealed interface Expr permits Num, Ref, Func, Bin, Neg {}
	private record Num(double v) implements Expr {}
	/** dayref: null = this day, else "dayN" / "first" / "last" / "prev". */
	private record Ref(String path, String dayref, int dayN) implements Expr {}
	private record Func(String name, Expr arg) implements Expr {}
	private record Bin(char op, Expr l, Expr r) implements Expr {}
	private record Neg(Expr e) implements Expr {}

	private enum ScopeKind { LAST, EVERY, DAY, RANGE, FROM }
	private record Scope(ScopeKind kind, int a, int b) {}

	private final String text;
	private final Expr lhs, rhs;
	private final String lhsText, rhsText, cmp;
	private final Scope scope;
	private final boolean hasFunc;

	private Assertion(String text, Expr lhs, String lhsText, String cmp, Expr rhs, String rhsText, Scope scope, boolean hasFunc) {
		this.text = text;
		this.lhs = lhs;
		this.lhsText = lhsText;
		this.cmp = cmp;
		this.rhs = rhs;
		this.rhsText = rhsText;
		this.scope = scope;
		this.hasFunc = hasFunc;
	}

	public String text() { return text; }

	public static Assertion parse(String text) {
		return new Parser(text).parseAssertion();
	}

	// ---- parsing ----

	private enum T { NUM, ID, AT, LP, RP, PLUS, MINUS, STAR, SLASH, CMP, DOTDOT, EOF }
	private record Tok(T t, String s, int start, int end) {}

	private static final class Parser {
		final String src;
		final List<Tok> toks = new ArrayList<>();
		int p;
		boolean sawFunc;

		Parser(String src) {
			this.src = src;
			lex();
		}

		AssertionSyntaxException err(String m) {
			return new AssertionSyntaxException(m + " in assertion \"" + src + "\"");
		}

		static boolean isLower(char c) { return c >= 'a' && c <= 'z'; }
		static boolean isDigit(char c) { return c >= '0' && c <= '9'; }

		void lex() {
			int i = 0, n = src.length();
			while (i < n) {
				char c = src.charAt(i);
				if (c == ' ' || c == '\t') { i++; continue; }
				int s = i;
				if (isDigit(c)) {
					while (i < n && isDigit(src.charAt(i))) i++;
					if (i + 1 < n && src.charAt(i) == '.' && isDigit(src.charAt(i + 1))) {
						i++;
						while (i < n && isDigit(src.charAt(i))) i++;
					}
					if (i < n && (src.charAt(i) == 'e' || src.charAt(i) == 'E')) {
						int j = i + 1;
						if (j < n && (src.charAt(j) == '+' || src.charAt(j) == '-')) j++;
						if (j < n && isDigit(src.charAt(j))) {
							while (j < n && isDigit(src.charAt(j))) j++;
							i = j;
						}
					}
					toks.add(new Tok(T.NUM, src.substring(s, i), s, i));
				} else if (isLower(c) || c == '_') {
					i++;
					while (i < n && (isLower(src.charAt(i)) || isDigit(src.charAt(i)) || src.charAt(i) == '_')) i++;
					while (i + 1 < n && src.charAt(i) == '.' && segChar(src.charAt(i + 1))) {
						i++;
						while (i < n && segChar(src.charAt(i))) i++;
					}
					toks.add(new Tok(T.ID, src.substring(s, i), s, i));
				} else if (c == '.' && i + 1 < n && src.charAt(i + 1) == '.') {
					i += 2;
					toks.add(new Tok(T.DOTDOT, "..", s, i));
				} else if (c == '@') { i++; toks.add(new Tok(T.AT, "@", s, i)); }
				else if (c == '(') { i++; toks.add(new Tok(T.LP, "(", s, i)); }
				else if (c == ')') { i++; toks.add(new Tok(T.RP, ")", s, i)); }
				else if (c == '+') { i++; toks.add(new Tok(T.PLUS, "+", s, i)); }
				else if (c == '-') { i++; toks.add(new Tok(T.MINUS, "-", s, i)); }
				else if (c == '*') { i++; toks.add(new Tok(T.STAR, "*", s, i)); }
				else if (c == '/') { i++; toks.add(new Tok(T.SLASH, "/", s, i)); }
				else if (c == '>' || c == '<' || c == '=' || c == '!') {
					String two = i + 1 < n && src.charAt(i + 1) == '=' ? src.substring(i, i + 2) : null;
					if (two != null && (two.equals(">=") || two.equals("<=") || two.equals("==") || two.equals("!="))) {
						i += 2;
						toks.add(new Tok(T.CMP, two, s, i));
					} else if (c == '>' || c == '<') {
						i++;
						toks.add(new Tok(T.CMP, String.valueOf(c), s, i));
					} else {
						throw err("bad operator '" + c + "' at " + i);
					}
					if (i < n && (src.charAt(i) == '=' || src.charAt(i) == '>' || src.charAt(i) == '<')) {
						throw err("bad operator near " + i);
					}
				} else {
					throw err("unexpected character '" + c + "' at " + i);
				}
			}
			toks.add(new Tok(T.EOF, "", n, n));
		}

		static boolean segChar(char c) {
			return isLower(c) || isDigit(c) || c == '_' || c == ':' || c == '/';
		}

		Tok peek() { return toks.get(p); }
		Tok next() { return toks.get(p++); }
		boolean isId(String s) { return peek().t() == T.ID && peek().s().equals(s); }

		void expectId(String s) {
			if (!isId(s)) throw err("expected '" + s + "' at " + peek().start());
			p++;
		}

		int intTok(String what) {
			Tok t = next();
			if (t.t() != T.NUM || !t.s().chars().allMatch(ch -> ch >= '0' && ch <= '9')) {
				throw err("expected a whole number for " + what + " at " + t.start());
			}
			try { return Integer.parseInt(t.s()); } catch (NumberFormatException e) { throw err("number too large at " + t.start()); }
		}

		Assertion parseAssertion() {
			int lstart = peek().start();
			Expr l = sum(false);
			int lend = toks.get(p - 1).end();
			if (peek().t() != T.CMP) throw err("expected a comparison (>= <= > < == !=) at " + peek().start());
			String cmp = next().s();
			int rstart = peek().start();
			Expr r = sum(false);
			int rend = toks.get(p - 1).end();
			Scope scope = new Scope(ScopeKind.LAST, 0, 0);
			if (isId("on")) {
				p++;
				if (isId("every")) { p++; expectId("day"); scope = new Scope(ScopeKind.EVERY, 0, 0); }
				else if (isId("last")) { p++; expectId("day"); scope = new Scope(ScopeKind.LAST, 0, 0); }
				else if (isId("day")) { p++; int d = intTok("day"); scope = new Scope(ScopeKind.DAY, d, d); }
				else if (isId("days")) {
					p++;
					int a = intTok("range start");
					if (peek().t() != T.DOTDOT) throw err("expected '..' at " + peek().start());
					p++;
					int b = intTok("range end");
					scope = new Scope(ScopeKind.RANGE, a, b);
				} else throw err("expected 'every day', 'last day', 'day N' or 'days A..B' after 'on' at " + peek().start());
			} else if (isId("from")) {
				p++;
				expectId("day");
				scope = new Scope(ScopeKind.FROM, intTok("day"), 0);
			}
			if (peek().t() != T.EOF) throw err("unexpected '" + peek().s() + "' at " + peek().start());
			return new Assertion(src, l, src.substring(lstart, lend).trim(), cmp, r, src.substring(rstart, rend).trim(), scope, sawFunc);
		}

		Expr sum(boolean inFunc) {
			Expr e = product(inFunc);
			while (peek().t() == T.PLUS || peek().t() == T.MINUS) {
				char op = next().s().charAt(0);
				e = new Bin(op, e, product(inFunc));
			}
			return e;
		}

		Expr product(boolean inFunc) {
			Expr e = unary(inFunc);
			while (peek().t() == T.STAR || peek().t() == T.SLASH) {
				char op = next().s().charAt(0);
				e = new Bin(op, e, unary(inFunc));
			}
			return e;
		}

		Expr unary(boolean inFunc) {
			if (peek().t() == T.MINUS) { p++; return new Neg(unary(inFunc)); }
			return atom(inFunc);
		}

		Expr atom(boolean inFunc) {
			Tok t = next();
			switch (t.t()) {
				case NUM:
					return new Num(Double.parseDouble(t.s()));
				case LP: {
					Expr e = sum(inFunc);
					if (next().t() != T.RP) throw err("expected ')'");
					return e;
				}
				case ID: {
					if (peek().t() == T.LP && AssertionFunctions.isFunction(t.s())) {
						if (inFunc) throw err("nested function '" + t.s() + "' is not allowed");
						p++;
						Expr arg = sum(true);
						if (next().t() != T.RP) throw err("expected ')' to close " + t.s() + "(");
						sawFunc = true;
						return new Func(t.s(), arg);
					}
					if (!MetricNames.isKnown(t.s())) throw err("unknown metric '" + t.s() + "'");
					if (peek().t() == T.AT) {
						if (inFunc) throw err("'@' references are not allowed inside a function");
						p++;
						return dayRef(t.s());
					}
					return new Ref(t.s(), null, 0);
				}
				default:
					throw err("unexpected '" + (t.t() == T.EOF ? "end of assertion" : t.s()) + "' at " + t.start());
			}
		}

		Expr dayRef(String path) {
			Tok t = next();
			if (t.t() != T.ID) throw err("expected a day reference after '@'");
			String s = t.s();
			if (s.equals("first") || s.equals("last") || s.equals("prev")) return new Ref(path, s, 0);
			if (s.startsWith("day") && s.length() > 3) {
				try {
					return new Ref(path, "day", Integer.parseInt(s.substring(3)));
				} catch (NumberFormatException e) {
					// fall through
				}
			}
			throw err("bad day reference '@" + s + "' (use @day1, @first, @last or @prev, without spaces)");
		}
	}

	// ---- evaluation ----

	private static final class EvalFail extends RuntimeException {
		EvalFail(String m) { super(m, null, false, false); }
	}

	public AssertionResult evaluate(MetricsTable table) {
		int last = table.lastDay();
		int from, to;
		switch (scope.kind()) {
			case EVERY -> { from = 1; to = last; }
			case LAST -> { from = last; to = last; }
			case DAY -> { from = scope.a(); to = scope.a(); }
			case RANGE -> { from = scope.a(); to = scope.b(); }
			default -> { from = scope.a(); to = last; }
		}
		if (from > to || from < 1 || to > last) {
			return new AssertionResult(text, false, "empty scope: days " + from + ".." + to + " outside 1.." + last);
		}
		try {
			if (!hasFunc) {
				for (int d = from; d <= to; d++) {
					double a = eval(lhs, table, d, d, d);
					double b = eval(rhs, table, d, d, d);
					if (!holds(a, b)) return new AssertionResult(text, false, "day " + d + ": " + describe(a, b));
				}
				return new AssertionResult(text, true, (to - from + 1) + "/" + (to - from + 1) + " days");
			}
			double a = eval(lhs, table, to, from, to);
			double b = eval(rhs, table, to, from, to);
			if (!holds(a, b)) return new AssertionResult(text, false, describe(a, b));
			return new AssertionResult(text, true, fmt(lhsText, a) + " " + cmp + " " + fmt(rhsText, b));
		} catch (EvalFail f) {
			return new AssertionResult(text, false, f.getMessage());
		}
	}

	private static String fmt(String t, double v) { return t + "=" + v; }

	private String describe(double a, double b) {
		String rel = Math.abs(a - b) < 1e-9 ? "==" : (a < b ? "<" : ">");
		return fmt(lhsText, a) + " " + rel + " " + fmt(rhsText, b);
	}

	private boolean holds(double a, double b) {
		boolean eq = Math.abs(a - b) < 1e-9;
		return switch (cmp) {
			case ">=" -> a > b || eq;
			case "<=" -> a < b || eq;
			case ">" -> a > b && !eq;
			case "<" -> a < b && !eq;
			case "==" -> eq;
			default -> !eq;
		};
	}

	/**
	 * @param day the day a bare ref reads (outside a function); @prev is day-1
	 * @param from, to the scope (used by functions)
	 */
	private double eval(Expr e, MetricsTable t, int day, int from, int to) {
		double v = evalInner(e, t, day, from, to);
		if (Double.isNaN(v) || Double.isInfinite(v)) throw new EvalFail("not a finite number (" + v + ") in " + text);
		return v;
	}

	private double evalInner(Expr e, MetricsTable t, int day, int from, int to) {
		if (e instanceof Num n) return n.v();
		if (e instanceof Neg n) return -eval(n.e(), t, day, from, to);
		if (e instanceof Bin b) {
			double l = eval(b.l(), t, day, from, to), r = eval(b.r(), t, day, from, to);
			return switch (b.op()) {
				case '+' -> l + r;
				case '-' -> l - r;
				case '*' -> l * r;
				default -> {
					if (r == 0) throw new EvalFail("division by zero in " + text);
					yield l / r;
				}
			};
		}
		if (e instanceof Ref r) {
			int d = switch (r.dayref() == null ? "" : r.dayref()) {
				case "" -> day;
				case "first" -> 1;
				case "last" -> t.lastDay();
				case "prev" -> day - 1;
				default -> r.dayN();
			};
			OptionalDouble v = t.value(r.path(), d);
			if (v.isEmpty()) throw new EvalFail("no data for " + r.path() + " on day " + d + " (run has days 0.." + t.lastDay() + ")");
			return v.getAsDouble();
		}
		Func f = (Func) e;
		double[] vals = new double[to - from + 1];
		for (int d = from; d <= to; d++) vals[d - from] = eval(f.arg(), t, d, from, to);
		return AssertionFunctions.apply(f.name(), vals);
	}
}
