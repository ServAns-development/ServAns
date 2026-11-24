package xyz.matumaka.servans.common.config;

public record RetentionSettings(int rawDays, int minuteDays, int hourDays) {
    public RetentionSettings {
        rawDays = Math.max(1, rawDays);
        minuteDays = Math.max(1, minuteDays);
        hourDays = Math.max(1, hourDays);
    }

    public static RetentionSettings defaults() {
        return new RetentionSettings(7, 30, 365);
    }
}
