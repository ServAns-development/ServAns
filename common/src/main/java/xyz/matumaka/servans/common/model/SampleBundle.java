package xyz.matumaka.servans.common.model;

import java.util.List;

public record SampleBundle(
    long ts,
    ServerDescriptor server,
    int players,
    double tps1,
    double tps5,
    double tps15,
    double mspt,
    long ramUsed,
    long ramMax,
    double cpu,
    long uptime,
    List<WorldSample> worlds,
    List<PluginEntry> plugins,
    List<OnlinePlayer> online,
    int joins,
    int leaves
) {
    public SampleBundle {
        if (server == null) {
            throw new IllegalArgumentException("server descriptor is required");
        }
        players = Math.max(0, players);
        tps1 = clampTps(tps1);
        tps5 = clampTps(tps5);
        tps15 = clampTps(tps15);
        mspt = Math.max(0.0, mspt);
        ramUsed = Math.max(0L, ramUsed);
        ramMax = Math.max(0L, ramMax);
        cpu = Math.max(0.0, Math.min(100.0, cpu));
        uptime = Math.max(0L, uptime);
        worlds = worlds == null ? List.of() : List.copyOf(worlds);
        plugins = plugins == null ? null : List.copyOf(plugins);
        online = online == null ? List.of() : List.copyOf(online);
        joins = Math.max(0, joins);
        leaves = Math.max(0, leaves);
    }

    private static double clampTps(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(20.0, value));
    }
}
