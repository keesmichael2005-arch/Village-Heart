package dev.villageheart.core.sim;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonTest {
	@Test
	void roundTripNested() {
		String src = "{\"a\":[1,2.5,\"x\",true,false,null,{\"b\":{}}],\"c\":[],\"d\":\"é\"}";
		Object v = Json.parse(src);
		assertEquals(v, Json.parse(Json.write(v, false)));
		assertEquals(v, Json.parse(Json.write(v, true)));
		assertEquals(src, Json.write(v, false));
	}

	@Test
	void numberTypes() {
		assertEquals(1L, Json.parse("1"));
		assertEquals(-7L, Json.parse("-7"));
		assertEquals(1.5, Json.parse("1.5"));
		assertEquals(100000.0, Json.parse("1E5"));
		assertEquals(-2500.0, Json.parse("-2.5e3"));
		assertInstanceOf(Double.class, Json.parse("1E5"));
		for (double d : new double[] {3.0E-4, 0.1, 1.0E10, 123456.789, 5e-324}) {
			assertEquals(d, Json.parse(Double.toString(d)));
			assertEquals(d, Json.parse(Json.write(d, false)));
		}
	}

	@Test
	void stringEscapes() {
		assertEquals("a\"b\\c\ndé", Json.parse("\"a\\\"b\\\\c\\nd\\u00e9\""));
		String s = "q\"\\\n\t\u0001é";
		assertEquals(s, Json.parse(Json.write(s, false)));
	}

	@Test
	void nanAndInfinityWriteNull() {
		assertEquals("null", Json.write(Double.NaN, false));
		assertEquals("[null]", Json.write(List.of(Double.POSITIVE_INFINITY), false));
	}

	@Test
	void literalsAndMap() {
		Map<?, ?> m = (Map<?, ?>) Json.parse(" {\"t\" : true , \"n\":null} ");
		assertEquals(Boolean.TRUE, m.get("t"));
		assertTrue(m.containsKey("n"));
		assertNull(m.get("n"));
	}

	private static void rejects(String text, String where) {
		Json.JsonException e = assertThrows(Json.JsonException.class, () -> Json.parse(text), text);
		assertTrue(e.getMessage().contains(where), text + " -> " + e.getMessage());
	}

	@Test
	void rejectsGarbage() {
		rejects("{\"a\":1,}", "1:8");
		rejects("[1 2]", "1:4");
		rejects("\"abc", "1:1");
		rejects("{} x", "1:4");
		rejects(".5", "1:1");
		rejects("[1,]", "1:4");
		rejects("{\"a\":1\n,\n}", "3:1");
		rejects("01", "leading zero");
		rejects("", "end of input");
	}
}
