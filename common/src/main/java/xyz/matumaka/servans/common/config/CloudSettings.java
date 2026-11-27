package xyz.matumaka.servans.common.config;

public record CloudSettings(boolean enabled, String url, String key, int timeoutSeconds) {
    public static final String DEFAULT_URL = "https://servans.matumaka.com";

    public CloudSettings {
        url = url == null || url.isBlank() ? DEFAULT_URL : url.trim().replaceAll("/+$", "");
        key = key == null ? "" : key.trim();
        timeoutSeconds = Math.max(1, Math.min(60, timeoutSeconds));
    }

    public boolean configured() {
        return enabled && !key.isBlank();
    }

    public String ingestUrl() {
        return url + "/api/ingest";
    }

    public String settingsUrl() {
        return url + "/api/plugin/settings";
    }

    public static CloudSettings defaults() {
        return new CloudSettings(true, DEFAULT_URL, "", 10);
    }
}
