package xyz.matumaka.servans.common.model;

public record WorldSample(String world, int chunks, int entities, int players) {
    public WorldSample {
        world = world == null || world.isBlank() ? "world" : world;
        chunks = Math.max(0, chunks);
        entities = Math.max(0, entities);
        players = Math.max(0, players);
    }
}
