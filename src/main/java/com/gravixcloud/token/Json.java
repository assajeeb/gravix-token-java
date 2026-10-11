package com.gravixcloud.token;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The small JSON writer behind the token payload: objects, strings, integers, booleans and string
 * arrays, written in the order they are added.
 *
 * <p>Strings are escaped exactly as Go's {@code encoding/json} escapes them (the Go library and
 * the Gravix server are Go): quote, backslash, backspace, form feed, newline, carriage return and
 * tab get their two-character escapes; other control characters, {@code < > &} and U+2028/U+2029
 * get six-character Unicode escapes (lower-case hex); an unpaired surrogate becomes U+FFFD.
 * Everything else, including non-ASCII text and {@code /}, is written as is (UTF-8). Same input,
 * same bytes as the Go library.
 */
final class Json {

  /**
   * Orders map keys by Unicode code point, which is the UTF-8 byte order Go sorts map keys by
   * ({@link String#compareTo} compares UTF-16 units and would put U+1F600 before U+FF01).
   */
  static final Comparator<String> CODE_POINT_ORDER =
      new Comparator<String>() {
        @Override
        public int compare(String a, String b) {
          int i = 0;
          int j = 0;
          while (i < a.length() && j < b.length()) {
            int ca = a.codePointAt(i);
            int cb = b.codePointAt(j);
            if (ca != cb) {
              return ca < cb ? -1 : 1;
            }
            i += Character.charCount(ca);
            j += Character.charCount(cb);
          }
          return Integer.compare(a.length() - i, b.length() - j);
        }
      };

  private static final char[] HEX = "0123456789abcdef".toCharArray();

  private final StringBuilder out = new StringBuilder(256);
  private boolean needsComma;

  Json beginObject() {
    out.append('{');
    needsComma = false;
    return this;
  }

  Json beginObject(String key) {
    key(key);
    return beginObject();
  }

  Json endObject() {
    out.append('}');
    needsComma = true;
    return this;
  }

  Json string(String key, String value) {
    key(key);
    quote(out, value);
    needsComma = true;
    return this;
  }

  Json number(String key, long value) {
    key(key);
    out.append(value);
    needsComma = true;
    return this;
  }

  Json bool(String key, boolean value) {
    key(key);
    out.append(value);
    needsComma = true;
    return this;
  }

  Json stringArray(String key, List<String> values) {
    key(key);
    out.append('[');
    for (int i = 0; i < values.size(); i++) {
      if (i > 0) {
        out.append(',');
      }
      quote(out, values.get(i));
    }
    out.append(']');
    needsComma = true;
    return this;
  }

  /** Writes the map in its iteration order; pass a map sorted by {@link #CODE_POINT_ORDER}. */
  Json stringMap(String key, Map<String, String> values) {
    beginObject(key);
    for (Map.Entry<String, String> e : values.entrySet()) {
      string(e.getKey(), e.getValue());
    }
    return endObject();
  }

  @Override
  public String toString() {
    return out.toString();
  }

  private void key(String key) {
    if (needsComma) {
      out.append(',');
    }
    quote(out, key);
    out.append(':');
  }

  static String quote(String s) {
    StringBuilder sb = new StringBuilder(s.length() + 2);
    quote(sb, s);
    return sb.toString();
  }

  static void quote(StringBuilder out, String s) {
    out.append('"');
    int n = s.length();
    for (int i = 0; i < n; i++) {
      char c = s.charAt(i);
      switch (c) {
        case '"':
          out.append("\\\"");
          break;
        case '\\':
          out.append("\\\\");
          break;
        case '\b':
          out.append("\\b");
          break;
        case '\f':
          out.append("\\f");
          break;
        case '\n':
          out.append("\\n");
          break;
        case '\r':
          out.append("\\r");
          break;
        case '\t':
          out.append("\\t");
          break;
        case '<':
        case '>':
        case '&':
        case ' ':
        case ' ':
          unicodeEscape(out, c);
          break;
        default:
          if (c < 0x20) {
            unicodeEscape(out, c);
          } else if (Character.isHighSurrogate(c)
              && i + 1 < n
              && Character.isLowSurrogate(s.charAt(i + 1))) {
            out.append(c).append(s.charAt(++i));
          } else if (Character.isSurrogate(c)) {
            // Unpaired surrogate: not encodable as UTF-8; Go writes invalid UTF-8 as U+FFFD.
            out.append("\\ufffd");
          } else {
            out.append(c);
          }
      }
    }
    out.append('"');
  }

  private static void unicodeEscape(StringBuilder out, char c) {
    out.append("\\u")
        .append(HEX[(c >> 12) & 0xF])
        .append(HEX[(c >> 8) & 0xF])
        .append(HEX[(c >> 4) & 0xF])
        .append(HEX[c & 0xF]);
  }
}
