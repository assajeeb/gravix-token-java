package com.gravixcloud.token;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/** The JSON writer: Go's encoding/json string escaping, code-point key order, valid JSON. */
class JsonTest {

  @Test
  void shortEscapesMatchGo() {
    assertEquals("\"\\\"\\\\\\b\\f\\n\\r\\t\"", Json.quote("\"\\\b\f\n\r\t"));
  }

  @Test
  void otherControlCharactersUseLowerCaseUnicodeEscapes() {
    for (char c = 0; c < 0x20; c++) {
      if ("\b\f\n\r\t".indexOf(c) >= 0) {
        continue;
      }
      assertEquals(String.format("\"\\u%04x\"", (int) c), Json.quote(String.valueOf(c)));
    }
  }

  @Test
  void htmlCharactersAndLineSeparatorsAreEscapedLikeGo() {
    assertEquals("\"\\u003c\\u003e\\u0026\\u2028\\u2029\"", Json.quote("<>&\u2028\u2029"));
  }

  @Test
  void slashDelAndNonAsciiAreWrittenAsIs() {
    String s = "/\u007f\u00e9\u4e2d\ud83d\ude00\uffff";
    assertEquals("\"" + s + "\"", Json.quote(s));
  }

  @Test
  void unpairedSurrogatesBecomeTheReplacementCharacter() {
    assertEquals("\"a\\ufffdb\"", Json.quote("a\ud83db"));
    assertEquals("\"a\\ufffdb\"", Json.quote("a\ude00b"));
    assertEquals("\"\\ufffd\"", Json.quote("\ud83d"));
    assertEquals("\"\\ufffd\\ufffd\"", Json.quote("\ude00\ud83d"));
  }

  @Test
  void codePointOrderIsUtf8ByteOrderNotUtf16Order() {
    // U+FF01 sorts before U+1F600 in UTF-8 (and Go); String.compareTo says the opposite.
    assertTrue("\uff01".compareTo("\ud83d\ude00") > 0);
    assertTrue(Json.CODE_POINT_ORDER.compare("\uff01", "\ud83d\ude00") < 0);
    List<String> keys = new ArrayList<>(Arrays.asList("z", "\ud83d\ude00", "", "a", "ab", "\u00e9", "\uff01", "A"));
    Collections.sort(keys, Json.CODE_POINT_ORDER);
    assertEquals(Arrays.asList("", "A", "a", "ab", "z", "\u00e9", "\uff01", "\ud83d\ude00"), keys);
    assertEquals(0, Json.CODE_POINT_ORDER.compare("same", "same"));
  }

  @Test
  void objectsArraysNumbersAndBooleansNestCorrectly() {
    Map<String, String> m = new TreeMap<>(Json.CODE_POINT_ORDER);
    m.put("b", "2");
    m.put("a", "1");
    String json =
        new Json()
            .beginObject()
            .string("s", "x")
            .number("n", -42L)
            .bool("t", true)
            .stringMap("m", m)
            .stringMap("e", Collections.<String, String>emptyMap())
            .beginObject("o")
            .bool("f", false)
            .stringArray("arr", Arrays.asList("p", "q"))
            .stringArray("none", Collections.<String>emptyList())
            .endObject()
            .endObject()
            .toString();
    assertEquals(
        "{\"s\":\"x\",\"n\":-42,\"t\":true,\"m\":{\"a\":\"1\",\"b\":\"2\"},\"e\":{},"
            + "\"o\":{\"f\":false,\"arr\":[\"p\",\"q\"],\"none\":[]}}",
        json);
  }

  @Test
  void randomStringsRoundTripThroughAStrictParser() {
    Random random = new Random(20261011L);
    for (int i = 0; i < 2000; i++) {
      StringBuilder sb = new StringBuilder();
      int len = random.nextInt(24);
      while (sb.length() < len) {
        int cp;
        switch (random.nextInt(4)) {
          case 0:
            cp = random.nextInt(0x80); // ASCII incl. controls
            break;
          case 1:
            cp = random.nextInt(0xD800); // BMP below surrogates
            break;
          case 2:
            cp = 0xE000 + random.nextInt(0x2000); // BMP above surrogates
            break;
          default:
            cp = 0x10000 + random.nextInt(0x100000); // supplementary
        }
        sb.appendCodePoint(cp);
      }
      String s = sb.toString();
      String json = new Json().beginObject().string(s, s).endObject().toString();
      assertEquals(Collections.singletonMap(s, s), TestTokens.parse(json), "round trip #" + i);
    }
  }
}
