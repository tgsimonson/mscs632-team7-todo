package todo.store;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON reader and writer.
 *
 * JavaScript has JSON.parse and JSON.stringify in the language. Java has
 * neither in the standard library, so the same capability costs a few hundred
 * lines here or an external dependency. This class is deliberately written by
 * hand so the project stays dependency free and so the serializer can emit the
 * exact field order, two space indentation and trailing newline that the spec
 * fixes. An off the shelf library would order fields by its own rules and the
 * two implementations would no longer produce identical files.
 */
public final class JsonCodec {

    private JsonCodec() {
    }

    // ---------------------------------------------------------------- parse

    private static final class Cursor {
        final String src;
        int pos;

        Cursor(String src) {
            this.src = src;
        }

        void skipWhitespace() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
                pos++;
            }
        }

        char peek() {
            if (pos >= src.length()) {
                throw new IllegalArgumentException("unexpected end of JSON input");
            }
            return src.charAt(pos);
        }

        char next() {
            char c = peek();
            pos++;
            return c;
        }

        void expect(char expected) {
            char c = next();
            if (c != expected) {
                throw new IllegalArgumentException(
                        "expected " + expected + " at offset " + (pos - 1) + ", found " + c);
            }
        }
    }

    /** Parses a JSON document into nested maps, lists, strings, numbers and nulls. */
    public static Object parse(String text) {
        Cursor cur = new Cursor(text);
        cur.skipWhitespace();
        Object value = parseValue(cur);
        cur.skipWhitespace();
        if (cur.pos < text.length()) {
            throw new IllegalArgumentException("trailing content at offset " + cur.pos);
        }
        return value;
    }

    private static Object parseValue(Cursor cur) {
        cur.skipWhitespace();
        char c = cur.peek();
        switch (c) {
            case '{':
                return parseObject(cur);
            case '[':
                return parseArray(cur);
            case '"':
                return parseString(cur);
            case 't':
            case 'f':
                return parseBoolean(cur);
            case 'n':
                return parseNull(cur);
            default:
                return parseNumber(cur);
        }
    }

    private static Map<String, Object> parseObject(Cursor cur) {
        Map<String, Object> map = new LinkedHashMap<>();
        cur.expect('{');
        cur.skipWhitespace();

        if (cur.peek() == '}') {
            cur.next();
            return map;
        }

        while (true) {
            cur.skipWhitespace();
            String key = parseString(cur);
            cur.skipWhitespace();
            cur.expect(':');
            map.put(key, parseValue(cur));
            cur.skipWhitespace();

            char c = cur.next();
            if (c == '}') {
                return map;
            }
            if (c != ',') {
                throw new IllegalArgumentException("expected , or } at offset " + (cur.pos - 1));
            }
        }
    }

    private static List<Object> parseArray(Cursor cur) {
        List<Object> list = new ArrayList<>();
        cur.expect('[');
        cur.skipWhitespace();

        if (cur.peek() == ']') {
            cur.next();
            return list;
        }

        while (true) {
            list.add(parseValue(cur));
            cur.skipWhitespace();

            char c = cur.next();
            if (c == ']') {
                return list;
            }
            if (c != ',') {
                throw new IllegalArgumentException("expected , or ] at offset " + (cur.pos - 1));
            }
        }
    }

    private static String parseString(Cursor cur) {
        cur.expect('"');
        StringBuilder sb = new StringBuilder();

        while (true) {
            char c = cur.next();
            if (c == '"') {
                return sb.toString();
            }
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            char esc = cur.next();
            switch (esc) {
                case '"':  sb.append('"');  break;
                case '\\': sb.append('\\'); break;
                case '/':  sb.append('/');  break;
                case 'b':  sb.append('\b'); break;
                case 'f':  sb.append('\f'); break;
                case 'n':  sb.append('\n'); break;
                case 'r':  sb.append('\r'); break;
                case 't':  sb.append('\t'); break;
                case 'u':
                    String hex = cur.src.substring(cur.pos, cur.pos + 4);
                    cur.pos += 4;
                    sb.append((char) Integer.parseInt(hex, 16));
                    break;
                default:
                    throw new IllegalArgumentException("invalid escape \\" + esc);
            }
        }
    }

    private static Object parseNumber(Cursor cur) {
        int start = cur.pos;
        while (cur.pos < cur.src.length() && "-+.eE0123456789".indexOf(cur.src.charAt(cur.pos)) >= 0) {
            cur.pos++;
        }
        String token = cur.src.substring(start, cur.pos);
        if (token.isEmpty()) {
            throw new IllegalArgumentException("expected a value at offset " + start);
        }
        if (token.contains(".") || token.contains("e") || token.contains("E")) {
            return Double.parseDouble(token);
        }
        return Long.parseLong(token);
    }

    private static Object parseBoolean(Cursor cur) {
        if (cur.src.startsWith("true", cur.pos)) {
            cur.pos += 4;
            return Boolean.TRUE;
        }
        if (cur.src.startsWith("false", cur.pos)) {
            cur.pos += 5;
            return Boolean.FALSE;
        }
        throw new IllegalArgumentException("invalid literal at offset " + cur.pos);
    }

    private static Object parseNull(Cursor cur) {
        if (cur.src.startsWith("null", cur.pos)) {
            cur.pos += 4;
            return null;
        }
        throw new IllegalArgumentException("invalid literal at offset " + cur.pos);
    }

    // ------------------------------------------------------------ serialize

    /** Escapes a string for JSON output. */
    public static String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b");  break;
                case '\f': sb.append("\\f");  break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}
