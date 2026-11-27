package xyz.matumaka.servans.paper.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import xyz.matumaka.servans.common.ServAnsLogger;
import xyz.matumaka.servans.common.config.CloudSettings;
import xyz.matumaka.servans.common.config.DatabaseSettings;
import xyz.matumaka.servans.common.config.DatabaseType;
import xyz.matumaka.servans.common.config.ServerSettings;
import xyz.matumaka.servans.common.config.Settings;
import xyz.matumaka.servans.common.util.Ids;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class PaperConfig {
    private PaperConfig() {
    }

    /**
     * Loads only the bootstrap settings that must exist before the plugin can reach the dashboard.
     * Collection, retention, maintenance, privacy, logging and theme settings come from the website.
     */
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
        CloudSettings cloud = new CloudSettings(
            config.getBoolean("cloud.enabled", true),
            config.getString("cloud.url", CloudSettings.DEFAULT_URL),
            config.getString("cloud.key", ""),
            config.getInt("cloud.timeout-seconds", 10)
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
        return new Settings(new ServerSettings(id, name), cloud, null, database, null, null, null, null, null);
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
            for (String key : new String[]{
                "theme", "retention", "collection", "maintenance", "privacy", "logging"
            }) {
                if (config.isSet(key)) {
                    config.set(key, null);
                    changed = true;
                }
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
