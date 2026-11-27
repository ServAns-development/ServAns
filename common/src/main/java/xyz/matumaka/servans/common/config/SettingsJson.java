package xyz.matumaka.servans.common.config;

import xyz.matumaka.servans.common.json.Json;

import java.util.Map;

/**
 * Builds a {@link Settings} record from the dashboard's plugin settings response.
 *
 * <p>The response is shaped as {@code {"settings": {...}}} or the settings object itself.
 * Unknown or missing values fall back to the last known settings, and every record
 * constructor clamps values to safe limits.</p>
 */
public final class SettingsJson {
    private SettingsJson() {
    }

    public static Settings parse(Object response, Settings base) {
        Map<String, Object> root = object(response);
        Object nested = root.get("settings");
        Map<String, Object> settings = nested instanceof Map<?, ?> ? object(nested) : root;

        Map<String, Object> collection = object(settings.get("collection"));
        Map<String, Object> retention = object(settings.get("retention"));
        Map<String, Object> maintenance = object(settings.get("maintenance"));
        Map<String, Object> privacy = object(settings.get("privacy"));
        Map<String, Object> logging = object(settings.get("logging"));
        Map<String, Object> theme = object(settings.get("theme"));

        CollectionSettings collectionSettings = new CollectionSettings(
            integer(collection.get("intervalSeconds"), base.collection().intervalSeconds()),
            bool(collection.get("worldStats"), base.collection().worldStats()),
            bool(collection.get("onlinePlayers"), base.collection().onlinePlayers()),
            bool(collection.get("plugins"), base.collection().plugins()),
            integer(collection.get("pluginScanMinutes"), base.collection().pluginScanMinutes())
        );
        RetentionSettings retentionSettings = new RetentionSettings(
            integer(retention.get("rawDays"), base.retention().rawDays()),
            integer(retention.get("minuteDays"), base.retention().minuteDays()),
            integer(retention.get("hourDays"), base.retention().hourDays())
        );
        MaintenanceSettings maintenanceSettings = new MaintenanceSettings(
            integer(maintenance.get("intervalSeconds"), base.maintenance().intervalSeconds())
        );
        PrivacySettings privacySettings = new PrivacySettings(
            bool(privacy.get("storeIps"), base.privacy().storeIps())
        );
        LoggingSettings loggingSettings = new LoggingSettings(
            bool(logging.get("debug"), base.logging().debug())
        );
        ThemeSettings themeSettings = new ThemeSettings(
            text(theme.get("title"), base.theme().title()),
            text(theme.get("accentColor"), base.theme().accentColor()),
            text(theme.get("logoUrl"), base.theme().logoUrl())
        );

        return new Settings(
            base.server(),
            base.cloud(),
            themeSettings,
            base.database(),
            retentionSettings,
            collectionSettings,
            maintenanceSettings,
            privacySettings,
            loggingSettings
        );
    }

    public static Settings parseJson(String body, Settings base) {
        return parse(Json.parse(body), base);
    }

    private static Map<String, Object> object(Object value) {
        if (value instanceof Map<?, ?> map) {
            return Json.asObject(map);
        }
        return Map.of();
    }

    private static int integer(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static boolean bool(Object value, boolean fallback) {
        return value instanceof Boolean flag ? flag : fallback;
    }

    private static String text(Object value, String fallback) {
        return value instanceof CharSequence text && !text.toString().isBlank()
            ? text.toString()
            : fallback;
    }
}
