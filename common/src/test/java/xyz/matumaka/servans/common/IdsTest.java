package xyz.matumaka.servans.common;

import xyz.matumaka.servans.common.util.Ids;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdsTest {
    @Test
    void generatesUrlSafeIds() {
        for (int i = 0; i < 500; i++) {
            String id = Ids.serverId();
            assertEquals(8, id.length());
            assertTrue(id.matches("[23456789abcdefghjkmnpqrstuvwxyz]{8}"));
            assertTrue(Ids.validSlug(id));
        }
    }

    @Test
    void normalizesCustomSlugs() {
        assertEquals("my-server", Ids.normalizeSlug("  My Server  "));
        assertEquals("survival_1", Ids.normalizeSlug("Survival_1"));
        assertEquals("", Ids.normalizeSlug("servers"));
        assertEquals("", Ids.normalizeSlug("a"));
        assertEquals("", Ids.normalizeSlug("---"));
        assertFalse(Ids.validSlug("bad slug"));
        assertFalse(Ids.validSlug("query"));
        assertTrue(Ids.validSlug("atm10"));
    }
}
