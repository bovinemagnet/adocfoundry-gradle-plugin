package io.github.bovinemagnet.adocfoundry.tasks;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.gradle.api.GradleException;

/**
 * A small JSON reader for the CLI's reports, so the plugin needs no JSON library. Objects are
 * {@code Map}s, arrays {@code List}s, integers {@code Long}s and other numbers {@code Double}s.
 */
final class Json {

    private final String text;
    private int at;

    private Json(String text) {
        this.text = text;
    }

    /**
     * Parses one JSON value.
     *
     * @param text the document
     * @return the value
     * @throws GradleException when the text is not JSON
     */
    static Object parse(String text) {
        Json json = new Json(text);
        Object value = json.value();
        json.space();
        if (json.at != text.length()) {
            throw json.error("unexpected text after the value");
        }
        return value;
    }

    private Object value() {
        space();
        if (at >= text.length()) {
            throw error("unexpected end");
        }
        char c = text.charAt(at);
        switch (c) {
            case '{':
                return object();
            case '[':
                return array();
            case '"':
                return string();
            case 't':
                return literal("true", Boolean.TRUE);
            case 'f':
                return literal("false", Boolean.FALSE);
            case 'n':
                return literal("null", null);
            default:
                if (c == '-' || (c >= '0' && c <= '9')) {
                    return number();
                }
                throw error("unexpected '" + c + "'");
        }
    }

    private Map<String, Object> object() {
        Map<String, Object> object = new LinkedHashMap<>();
        at++;
        space();
        if (peek('}')) {
            at++;
            return object;
        }
        while (true) {
            space();
            if (!peek('"')) {
                throw error("expected a key");
            }
            String key = string();
            space();
            expect(':');
            object.put(key, value());
            space();
            if (peek(',')) {
                at++;
            } else {
                expect('}');
                return object;
            }
        }
    }

    private List<Object> array() {
        List<Object> array = new ArrayList<>();
        at++;
        space();
        if (peek(']')) {
            at++;
            return array;
        }
        while (true) {
            array.add(value());
            space();
            if (peek(',')) {
                at++;
            } else {
                expect(']');
                return array;
            }
        }
    }

    private String string() {
        StringBuilder out = new StringBuilder();
        at++;
        while (at < text.length()) {
            char c = text.charAt(at++);
            if (c == '"') {
                return out.toString();
            }
            if (c != '\\') {
                out.append(c);
                continue;
            }
            if (at >= text.length()) {
                break;
            }
            char escape = text.charAt(at++);
            switch (escape) {
                case '"', '\\', '/' -> out.append(escape);
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'u' -> {
                    if (at + 4 > text.length()) {
                        throw error("short \\u escape");
                    }
                    out.append((char) Integer.parseInt(text.substring(at, at + 4), 16));
                    at += 4;
                }
                default -> throw error("unknown escape \\" + escape);
            }
        }
        throw error("unterminated string");
    }

    private Object number() {
        int start = at;
        if (peek('-')) {
            at++;
        }
        boolean decimal = false;
        while (at < text.length()) {
            char c = text.charAt(at);
            if (c >= '0' && c <= '9') {
                at++;
            } else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                decimal = true;
                at++;
            } else {
                break;
            }
        }
        String number = text.substring(start, at);
        try {
            return decimal ? (Object) Double.parseDouble(number) : (Object) Long.parseLong(number);
        } catch (NumberFormatException e) {
            throw error("bad number " + number);
        }
    }

    private Object literal(String word, Object value) {
        if (!text.startsWith(word, at)) {
            throw error("unexpected text");
        }
        at += word.length();
        return value;
    }

    private void space() {
        while (at < text.length() && Character.isWhitespace(text.charAt(at))) {
            at++;
        }
    }

    private boolean peek(char c) {
        return at < text.length() && text.charAt(at) == c;
    }

    private void expect(char c) {
        if (!peek(c)) {
            throw error("expected '" + c + "'");
        }
        at++;
    }

    private GradleException error(String problem) {
        return new GradleException("Could not read the CLI's JSON output: " + problem + " at character " + at + ".");
    }
}
