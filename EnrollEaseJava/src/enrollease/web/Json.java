package enrollease.web;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A tiny JSON writer (no libraries needed). It turns Maps, Lists, arrays, Strings,
 * Numbers and Booleans into JSON text that the browser's JavaScript can read.
 */
public class Json {

    /** Builds an ordered map from key, value, key, value, ... */
    public static Map<String, Object> map(Object... keysAndValues) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (int i = 0; i + 1 < keysAndValues.length; i += 2) {
            result.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return result;
    }

    public static String toJson(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String) {
            return quote((String) value);
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        StringBuilder sb = new StringBuilder();
        if (value instanceof Map) {
            sb.append("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (!first) {
                    sb.append(",");
                }
                first = false;
                sb.append(quote(String.valueOf(entry.getKey()))).append(":").append(toJson(entry.getValue()));
            }
            return sb.append("}").toString();
        }
        if (value instanceof Iterable) {
            sb.append("[");
            boolean first = true;
            for (Object item : (Iterable<?>) value) {
                if (!first) {
                    sb.append(",");
                }
                first = false;
                sb.append(toJson(item));
            }
            return sb.append("]").toString();
        }
        if (value instanceof int[]) {
            int[] numbers = (int[]) value;
            sb.append("[");
            for (int i = 0; i < numbers.length; i++) {
                sb.append(i == 0 ? "" : ",").append(numbers[i]);
            }
            return sb.append("]").toString();
        }
        if (value instanceof Object[]) { // String[] and int[][] end up here
            Object[] items = (Object[]) value;
            sb.append("[");
            for (int i = 0; i < items.length; i++) {
                sb.append(i == 0 ? "" : ",").append(toJson(items[i]));
            }
            return sb.append("]").toString();
        }
        return quote(value.toString());
    }

    // Wraps text in quotes and escapes characters that JSON does not allow.
    private static String quote(String text) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.append("\"").toString();
    }
}
