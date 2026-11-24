package xyz.matumaka.servans.common.config;

public record ThemeSettings(String title, String accentColor, String logoUrl) {
    private static final String DEFAULT_ACCENT = "#ededed";

    public ThemeSettings {
        title = title == null || title.isBlank() ? "ServAns" : title.trim();
        accentColor = accentColor != null && accentColor.matches("#[0-9a-fA-F]{6}") ? accentColor.toLowerCase() : DEFAULT_ACCENT;
        logoUrl = logoUrl == null ? "" : logoUrl.trim();
    }

    public static ThemeSettings defaults() {
        return new ThemeSettings("ServAns", DEFAULT_ACCENT, "");
    }
}
