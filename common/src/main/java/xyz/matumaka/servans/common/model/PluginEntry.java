package xyz.matumaka.servans.common.model;

public record PluginEntry(String name, String version) {
    public PluginEntry {
        name = name == null || name.isBlank() ? "unknown" : name.trim();
        version = version == null || version.isBlank() ? "unknown" : version.trim();
    }
}
