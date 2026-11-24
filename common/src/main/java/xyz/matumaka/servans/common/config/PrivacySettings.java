package xyz.matumaka.servans.common.config;

public record PrivacySettings(boolean storeIps) {
    public static PrivacySettings defaults() {
        return new PrivacySettings(false);
    }
}
