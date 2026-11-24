package xyz.matumaka.servans.common.config;

public record ServerSettings(String id, String name) {
    public ServerSettings {
        id = id == null ? "" : id.trim();
        name = name == null || name.isBlank() ? "Minecraft Server" : name.trim();
    }
}
