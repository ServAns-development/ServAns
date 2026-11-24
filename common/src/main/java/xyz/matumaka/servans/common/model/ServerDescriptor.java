package xyz.matumaka.servans.common.model;

public record ServerDescriptor(String id, String name, String software, String version, int maxPlayers) {
    public ServerDescriptor {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("server id is required");
        }
        name = name == null || name.isBlank() ? "Minecraft Server" : name.trim();
        software = software == null || software.isBlank() ? "Paper" : software.trim();
        version = version == null || version.isBlank() ? "1.21" : version.trim();
        maxPlayers = Math.max(0, maxPlayers);
    }
}
