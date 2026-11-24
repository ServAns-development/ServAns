package xyz.matumaka.servans.common.config;

public record CollectionSettings(
    int intervalSeconds,
    boolean worldStats,
    boolean onlinePlayers,
    boolean plugins,
    int pluginScanMinutes
) {
    public CollectionSettings {
        intervalSeconds = Math.max(10, Math.min(3600, intervalSeconds));
        pluginScanMinutes = Math.max(1, Math.min(1440, pluginScanMinutes));
    }

    public long pluginScanIntervalMillis() {
        return pluginScanMinutes * 60_000L;
    }

    public static CollectionSettings defaults() {
        return new CollectionSettings(60, true, true, true, 30);
    }
}
