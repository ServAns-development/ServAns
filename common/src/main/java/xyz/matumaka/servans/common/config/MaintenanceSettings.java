package xyz.matumaka.servans.common.config;

public record MaintenanceSettings(int intervalSeconds) {
    public MaintenanceSettings {
        intervalSeconds = Math.max(30, Math.min(3600, intervalSeconds));
    }

    public static MaintenanceSettings defaults() {
        return new MaintenanceSettings(60);
    }
}
