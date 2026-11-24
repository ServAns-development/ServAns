package xyz.matumaka.servans.paper.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import xyz.matumaka.servans.common.ServAnsLogger;
import xyz.matumaka.servans.common.config.CollectionSettings;
import xyz.matumaka.servans.common.config.DatabaseSettings;
import xyz.matumaka.servans.common.config.DatabaseType;
import xyz.matumaka.servans.common.config.LoggingSettings;
import xyz.matumaka.servans.common.config.MaintenanceSettings;
import xyz.matumaka.servans.common.config.PrivacySettings;
import xyz.matumaka.servans.common.config.RetentionSettings;
import xyz.matumaka.servans.common.config.ServerSettings;
import xyz.matumaka.servans.common.config.Settings;
import xyz.matumaka.servans.common.config.ThemeSettings;
import xyz.matumaka.servans.common.config.WebSettings;
import xyz.matumaka.servans.common.util.Ids;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class PaperConfig {
    private PaperConfig() {
    }

    public static Settings load(JavaPlugin plugin, ServAnsLogger logger) {
        mergeDefaults(plugin, logger);
        FileConfiguration config = plugin.getConfig();
        String configured = config.getString("server.id", "");
        String id = Ids.normalizeSlug(configured);
        if (id.isEmpty()) {
            if (!configured.isBlank()) {
                logger.warn("Invalid server.id '" + configured + "', generating a new id");
            }
            id = Ids.serverId();
            config.set("server.id", id);
            plugin.saveConfig();
            logger.info("Generated server id " + id);
        } else if (!id.equals(configured)) {
            config.set("server.id", id);
            plugin.saveConfig();
        }
        String name = config.getString("server.name", "Minecraft Server");
        WebSettings web = new WebSettings(
            config.getBoolean("web.enabled", true),
            config.getString("web.bind-address", "0.0.0.0"),
            port(config, "web.port", 8804, logger),
            config.getString("web.public-base-url", ""),
            config.getBoolean("web.trust-proxy-headers", false),
            config.getStringList("web.trusted-proxies"),
            config.getStringList("web.cors-origins"),
            config.getInt("web.rate-limit.requests", 120),
            config.getInt("web.rate-limit.window-seconds", 60),
            config.getBoolean("web.https.enabled", false),
            config.getString("web.https.keystore", "keystore.p12"),
            config.getString("web.https.keystore-password", "")
        );
        ThemeSettings theme = new ThemeSettings(
            config.getString("theme.title", "ServAns"),
            config.getString("theme.accent-color", "#ededed"),
            config.getString("theme.logo-url", "")
        );
        DatabaseType type = DatabaseType.parse(config.getString("database.type", "sqlite"));
        DatabaseSettings database = new DatabaseSettings(
            type,
            config.getString("database.file", "servans.db"),
            config.getString("database.mysql.host", "127.0.0.1"),
            port(config, "database.mysql.port", 3306, logger),
            config.getString("database.mysql.database", "servans"),
            config.getString("database.mysql.username", "servans"),
            config.getString("database.mysql.password", ""),
            config.getInt("database.pool-size", config.getInt("database.mysql.pool-size", 6)),
            config.getBoolean("database.mysql.use-ssl", false)
        );
        RetentionSettings retention = new RetentionSettings(
            config.getInt("retention.raw-days", 7),
            config.getInt("retention.minute-days", 30),
            config.getInt("retention.hour-days", 365)
        );
        CollectionSettings collection = new CollectionSettings(
            config.getInt("collection.interval-seconds", 60),
            config.getBoolean("collection.world-stats", true),
            config.getBoolean("collection.online-players", true),
            config.getBoolean("collection.plugins", true),
            config.getInt("collection.plugin-scan-minutes", 30)
        );
        MaintenanceSettings maintenance = new MaintenanceSettings(config.getInt("maintenance.interval-seconds", 60));
        PrivacySettings privacy = new PrivacySettings(config.getBoolean("privacy.store-ips", false));
        LoggingSettings logging = new LoggingSettings(config.getBoolean("logging.debug", false));
        return new Settings(new ServerSettings(id, name), web, theme, database, retention, collection, maintenance, privacy, logging);
    }

    private static void mergeDefaults(JavaPlugin plugin, ServAnsLogger logger) {
        InputStream resource = plugin.getResource("config.yml");
        if (resource == null) {
            return;
        }
        try (InputStreamReader reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(reader);
            FileConfiguration config = plugin.getConfig();
            boolean changed = false;
            if (config.isSet("database.mysql.pool-size")) {
                if (!config.isSet("database.pool-size")) {
                    config.set("database.pool-size", config.getInt("database.mysql.pool-size"));
                }
                config.set("database.mysql.pool-size", null);
                changed = true;
            }
            for (String key : defaults.getKeys(true)) {
                if (defaults.isConfigurationSection(key) || config.isSet(key)) {
                    continue;
                }
                config.set(key, defaults.get(key));
                changed = true;
            }
            if (changed) {
                plugin.saveConfig();
            }
        } catch (IOException error) {
            logger.warn("Could not merge new config defaults: " + error.getMessage());
        }
    }

    private static int port(FileConfiguration config, String path, int fallback, ServAnsLogger logger) {
        int value = config.getInt(path, fallback);
        if (value < 1 || value > 65535) {
            logger.warn("Invalid " + path + " value " + value + ", using " + fallback);
            return fallback;
        }
        return value;
    }
}
