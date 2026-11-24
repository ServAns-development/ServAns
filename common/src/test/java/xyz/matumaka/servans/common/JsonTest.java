package xyz.matumaka.servans.common;

import xyz.matumaka.servans.common.json.Json;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonTest {
    @Test
    void roundTripsNestedValues() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("name", "Survival \"SMP\"");
        root.put("players", 12);
        root.put("ratio", 0.75);
        root.put("online", true);
        root.put("empty", null);
        root.put("worlds", List.of(Map.of("name", "world", "chunks", 81), Map.of("name", "nether", "chunks", 12)));
        String encoded = Json.write(root);
        Map<String, Object> decoded = Json.asObject(Json.parse(encoded));
        assertEquals("Survival \"SMP\"", decoded.get("name"));
        assertEquals(12L, decoded.get("players"));
        assertEquals(0.75, decoded.get("ratio"));
        assertEquals(true, decoded.get("online"));
        assertTrue(decoded.containsKey("empty"));
        assertEquals(2, Json.asArray(decoded.get("worlds")).size());
    }

    @Test
    void escapesControlCharacters() {
        assertEquals("\"a\\nb\\t\\\\\\\"\"", Json.write("a\nb\t\\\""));
        assertEquals("\u0001", Json.parse("\"\\u0001\""));
    }

    @Test
    void rejectsMalformedInput() {
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\":}"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\":1} trailing"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("[1,2"));
        assertThrows(IllegalArgumentException.class, () -> Json.write(Double.NaN));
    }
}
