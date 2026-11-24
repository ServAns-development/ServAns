package xyz.matumaka.servans.common.json;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Json {
    private Json() {
    }

    public static String write(Object value) {
        StringBuilder sb = new StringBuilder(256);
        writeValue(sb, value);
        return sb.toString();
    }

    public static Object parse(String input) {
        if (input == null) {
            throw new IllegalArgumentException("json input is null");
        }
        Parser parser = new Parser(input);
        Object value = parser.value();
        parser.whitespace();
        if (!parser.end()) {
            throw new IllegalArgumentException("unexpected trailing json content at " + parser.position());
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asObject(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new IllegalArgumentException("expected json object");
    }

    @SuppressWarnings("unchecked")
    public static List<Object> asArray(Object value) {
        if (value instanceof List<?> list) {
            return (List<Object>) list;
        }
        throw new IllegalArgumentException("expected json array");
    }

    private static void writeValue(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
            return;
        }
        if (value instanceof CharSequence text) {
            writeString(sb, text.toString());
            return;
        }
        if (value instanceof Boolean flag) {
            sb.append(flag);
            return;
        }
        if (value instanceof Number number) {
            writeNumber(sb, number);
            return;
        }
        if (value instanceof Map<?, ?> map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                writeString(sb, String.valueOf(entry.getKey()));
                sb.append(':');
                writeValue(sb, entry.getValue());
            }
            sb.append('}');
            return;
        }
        if (value instanceof Collection<?> collection) {
            sb.append('[');
            boolean first = true;
            for (Object element : collection) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                writeValue(sb, element);
            }
            sb.append(']');
            return;
        }
        if (value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            sb.append('[');
            for (int i = 0; i < length; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                writeValue(sb, java.lang.reflect.Array.get(value, i));
            }
            sb.append(']');
            return;
        }
        throw new IllegalArgumentException("unsupported json value: " + value.getClass().getName());
    }

    private static void writeNumber(StringBuilder sb, Number number) {
        if (number instanceof Double value && !Double.isFinite(value)) {
            throw new IllegalArgumentException("non-finite number");
        }
        if (number instanceof Float value && !Float.isFinite(value)) {
            throw new IllegalArgumentException("non-finite number");
        }
        sb.append(number);
    }

    private static void writeString(StringBuilder sb, String text) {
        sb.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    private static final class Parser {
        private final String input;
        private int index;
        private int depth;

        private Parser(String input) {
            this.input = input;
        }

        private Object value() {
            whitespace();
            if (end()) {
                throw new IllegalArgumentException("unexpected end of json");
            }
            char c = input.charAt(index);
            return switch (c) {
                case '{' -> objectValue();
                case '[' -> arrayValue();
                case '"' -> stringValue();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> numberValue();
            };
        }

        private Map<String, Object> objectValue() {
            enter();
            index++;
            Map<String, Object> result = new LinkedHashMap<>();
            whitespace();
            if (peek('}')) {
                index++;
                exit();
                return result;
            }
            while (true) {
                whitespace();
                if (!peek('"')) {
                    throw new IllegalArgumentException("expected json object key at " + index);
                }
                String key = stringValue();
                whitespace();
                expect(':');
                result.put(key, value());
                whitespace();
                if (peek('}')) {
                    index++;
                    exit();
                    return result;
                }
                expect(',');
            }
        }

        private List<Object> arrayValue() {
            enter();
            index++;
            List<Object> result = new ArrayList<>();
            whitespace();
            if (peek(']')) {
                index++;
                exit();
                return result;
            }
            while (true) {
                result.add(value());
                whitespace();
                if (peek(']')) {
                    index++;
                    exit();
                    return result;
                }
                expect(',');
            }
        }

        private String stringValue() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (!end()) {
                char c = input.charAt(index++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                if (end()) {
                    throw new IllegalArgumentException("unterminated json escape");
                }
                char escape = input.charAt(index++);
                switch (escape) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> sb.append(unicodeEscape());
                    default -> throw new IllegalArgumentException("invalid json escape at " + index);
                }
            }
            throw new IllegalArgumentException("unterminated json string");
        }

        private char unicodeEscape() {
            if (index + 4 > input.length()) {
                throw new IllegalArgumentException("truncated unicode escape");
            }
            String hex = input.substring(index, index + 4);
            index += 4;
            try {
                return (char) Integer.parseInt(hex, 16);
            } catch (NumberFormatException error) {
                throw new IllegalArgumentException("invalid unicode escape " + hex);
            }
        }

        private Object numberValue() {
            int start = index;
            if (peek('-')) {
                index++;
            }
            while (!end() && Character.isDigit(input.charAt(index))) {
                index++;
            }
            boolean decimal = false;
            if (peek('.')) {
                decimal = true;
                index++;
                while (!end() && Character.isDigit(input.charAt(index))) {
                    index++;
                }
            }
            if (peek('e') || peek('E')) {
                decimal = true;
                index++;
                if (peek('+') || peek('-')) {
                    index++;
                }
                while (!end() && Character.isDigit(input.charAt(index))) {
                    index++;
                }
            }
            if (start == index) {
                throw new IllegalArgumentException("invalid json value at " + index);
            }
            String number = input.substring(start, index);
            try {
                if (!decimal) {
                    return Long.parseLong(number);
                }
                return Double.parseDouble(number);
            } catch (NumberFormatException error) {
                throw new IllegalArgumentException("invalid json number " + number);
            }
        }

        private Object literal(String word, Object value) {
            if (!input.startsWith(word, index)) {
                throw new IllegalArgumentException("invalid json literal at " + index);
            }
            index += word.length();
            return value;
        }

        private void enter() {
            if (++depth > 64) {
                throw new IllegalArgumentException("json nesting too deep");
            }
        }

        private void exit() {
            depth--;
        }

        private void whitespace() {
            while (!end()) {
                char c = input.charAt(index);
                if (c != ' ' && c != '\n' && c != '\r' && c != '\t') {
                    return;
                }
                index++;
            }
        }

        private void expect(char expected) {
            if (end() || input.charAt(index) != expected) {
                throw new IllegalArgumentException("expected '" + expected + "' at " + index);
            }
            index++;
        }

        private boolean peek(char expected) {
            return !end() && input.charAt(index) == expected;
        }

        private boolean end() {
            return index >= input.length();
        }

        private int position() {
            return index;
        }
    }
}
