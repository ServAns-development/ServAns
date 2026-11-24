package xyz.matumaka.servans.common.config;

public record Settings(
    ServerSettings server,
    WebSettings web,
    ThemeSettings theme,
    DatabaseSettings database,
    RetentionSettings retention,
    CollectionSettings collection,
    MaintenanceSettings maintenance,
    PrivacySettings privacy,
    LoggingSettings logging
) {
    public Settings {
        server = server == null ? new ServerSettings("", "Minecraft Server") : server;
        web = web == null ? WebSettings.defaults() : web;
        theme = theme == null ? ThemeSettings.defaults() : theme;
        database = database == null ? new DatabaseSettings(DatabaseType.SQLITE, "servans.db", "127.0.0.1", 3306, "servans", "", "", 6, false) : database;
        retention = retention == null ? RetentionSettings.defaults() : retention;
        collection = collection == null ? CollectionSettings.defaults() : collection;
        maintenance = maintenance == null ? MaintenanceSettings.defaults() : maintenance;
        privacy = privacy == null ? PrivacySettings.defaults() : privacy;
        logging = logging == null ? LoggingSettings.defaults() : logging;
    }
}
