package xyz.matumaka.servans.common;

import org.junit.jupiter.api.Test;
import xyz.matumaka.servans.common.config.CollectionSettings;
import xyz.matumaka.servans.common.config.MaintenanceSettings;
import xyz.matumaka.servans.common.config.ThemeSettings;
import xyz.matumaka.servans.common.config.WebSettings;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigTest {
    @Test
    void validatesWebSettings() {
        WebSettings settings = new WebSettings(false, "", 0, "https://servans.matumaka.com/", true,
            List.of(" 10.0.0.1 ", ""), List.of(), 0, 0, false, "", null);
        assertEquals("0.0.0.0", settings.bindAddress());
        assertEquals(8804, settings.port());
        assertEquals("https://servans.matumaka.com", settings.publicBaseUrl());
        assertEquals(List.of("10.0.0.1"), settings.trustedProxies());
        assertEquals(1, settings.rateLimitRequests());
        assertEquals(1, settings.rateLimitWindowSeconds());
        assertEquals("keystore.p12", settings.keystore());
        assertEquals("", settings.keystorePassword());
        assertEquals("0.0.0.0:8804", settings.listenAddress());
    }

    @Test
    void acceptsCustomPortsAndOrigins() {
        WebSettings settings = new WebSettings(true, "127.0.0.1", 8899, "", false,
            List.of(), List.of("https://Example.com"), 500, 30, true, "custom.p12", "secret");
        assertEquals(8899, settings.port());
        assertEquals(500, settings.rateLimitRequests());
        assertEquals(List.of("https://example.com"), settings.corsOrigins());
        assertTrue(settings.httpsEnabled());
        assertEquals("127.0.0.1:8899", settings.listenAddress());
    }

    @Test
    void validatesThemeAndCollection() {
        ThemeSettings theme = new ThemeSettings("", "red", "");
        assertEquals("ServAns", theme.title());
        assertEquals("#ededed", theme.accentColor());
        ThemeSettings custom = new ThemeSettings("My SMP", "#7C3AED", "https://example.com/logo.png");
        assertEquals("#7c3aed", custom.accentColor());
        CollectionSettings collection = new CollectionSettings(2, true, false, true, 0);
        assertEquals(10, collection.intervalSeconds());
        assertEquals(1, collection.pluginScanMinutes());
        assertEquals(60_000L, collection.pluginScanIntervalMillis());
        assertEquals(30, new MaintenanceSettings(1).intervalSeconds());
        assertEquals(3600, new MaintenanceSettings(99_999).intervalSeconds());
    }
}
