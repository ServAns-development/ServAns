package xyz.matumaka.servans.common;

import org.junit.jupiter.api.Test;
import xyz.matumaka.servans.common.config.CloudSettings;
import xyz.matumaka.servans.common.config.CollectionSettings;
import xyz.matumaka.servans.common.config.DatabaseSettings;
import xyz.matumaka.servans.common.config.DatabaseType;
import xyz.matumaka.servans.common.config.MaintenanceSettings;
import xyz.matumaka.servans.common.config.ServerSettings;
import xyz.matumaka.servans.common.config.Settings;
import xyz.matumaka.servans.common.config.SettingsJson;
import xyz.matumaka.servans.common.config.ThemeSettings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigTest {
    @Test
    void validatesCloudSettings() {
        CloudSettings settings = new CloudSettings(true, "https://servans.example.com///", "  key-123  ", 0);
        assertEquals("https://servans.example.com", settings.url());
        assertEquals("key-123", settings.key());
        assertEquals(1, settings.timeoutSeconds());
        assertEquals("https://servans.example.com/api/ingest", settings.ingestUrl());
        assertTrue(settings.configured());

        CloudSettings defaults = new CloudSettings(true, "", null, 999);
        assertEquals(CloudSettings.DEFAULT_URL, defaults.url());
        assertEquals("", defaults.key());
        assertEquals(60, defaults.timeoutSeconds());
        assertFalse(defaults.configured());

        assertFalse(new CloudSettings(false, "https://example.com", "key", 10).configured());
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

    @Test
    void parsesDashboardSettings() {
        Settings base = new Settings(
            new ServerSettings("my-server", "My Server"),
            new CloudSettings(true, "https://example.com", "key", 10),
            null,
            new DatabaseSettings(DatabaseType.SQLITE, "servans.db", "127.0.0.1", 3306, "servans", "", "", 6, false),
            null, null, null, null, null
        );
        Settings parsed = SettingsJson.parseJson("""
            {"settings":{
              "collection":{"intervalSeconds":120,"worldStats":false,"onlinePlayers":true,"plugins":true,"pluginScanMinutes":15},
              "retention":{"rawDays":3,"minuteDays":10,"hourDays":100},
              "maintenance":{"intervalSeconds":90},
              "privacy":{"storeIps":true},
              "logging":{"debug":true},
              "theme":{"title":"My SMP","accentColor":"#7c3aed","logoUrl":"https://example.com/logo.png"}
            }}
            """, base);

        assertEquals(120, parsed.collection().intervalSeconds());
        assertFalse(parsed.collection().worldStats());
        assertEquals(15, parsed.collection().pluginScanMinutes());
        assertEquals(3, parsed.retention().rawDays());
        assertEquals(90, parsed.maintenance().intervalSeconds());
        assertTrue(parsed.privacy().storeIps());
        assertTrue(parsed.logging().debug());
        assertEquals("My SMP", parsed.theme().title());
        assertEquals("#7c3aed", parsed.theme().accentColor());
        assertEquals("my-server", parsed.server().id());
    }
}
