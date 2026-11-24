package xyz.matumaka.servans.common.config;

public record LoggingSettings(boolean debug) {
    public static LoggingSettings defaults() {
        return new LoggingSettings(false);
    }
}
