package xyz.matumaka.servans.common.model;

public record OnlinePlayer(String uuid, String name, String world, int ping) {
    public OnlinePlayer {
        uuid = uuid == null ? "" : uuid;
        name = name == null || name.isBlank() ? "unknown" : name.trim();
        world = world == null || world.isBlank() ? "world" : world.trim();
        ping = Math.max(0, ping);
    }
}
