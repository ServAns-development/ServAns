package xyz.matumaka.servans.paper.collect;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import xyz.matumaka.servans.common.ServAnsLogger;
import xyz.matumaka.servans.common.config.CollectionSettings;
import xyz.matumaka.servans.common.model.OnlinePlayer;
import xyz.matumaka.servans.common.model.PluginEntry;
import xyz.matumaka.servans.common.model.SampleBundle;
import xyz.matumaka.servans.common.model.ServerDescriptor;
import xyz.matumaka.servans.common.model.WorldSample;
import xyz.matumaka.servans.common.store.DataStore;
import xyz.matumaka.servans.paper.AsyncWorker;
import xyz.matumaka.servans.paper.schedule.PaperScheduler;
import xyz.matumaka.servans.paper.schedule.PlatformScheduler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class MetricsCollector {
    private final Plugin plugin;
    private final DataStore store;
    private final PlatformScheduler scheduler;
    private final AsyncWorker worker;
    private final ServerDescriptor descriptor;
    private final ActivityCounters counters;
    private final CollectionSettings settings;
    private final ServAnsLogger logger;
    private final boolean folia;
    private volatile List<PluginEntry> plugins;
    private volatile long pluginsScannedAt;
    private volatile PlatformScheduler.Cancellable task;

    public MetricsCollector(Plugin plugin, DataStore store, PlatformScheduler scheduler, AsyncWorker worker, ServerDescriptor descriptor, ActivityCounters counters, CollectionSettings settings, ServAnsLogger logger, boolean folia) {
        this.plugin = plugin;
        this.store = store;
        this.scheduler = scheduler;
        this.worker = worker;
        this.descriptor = descriptor;
        this.counters = counters;
        this.settings = settings;
        this.logger = logger;
        this.folia = folia;
    }

    public void start() {
        if (settings.plugins()) {
            plugins = scanPlugins();
            pluginsScannedAt = System.currentTimeMillis();
        }
        task = scheduler.repeatGlobal(this::collect, 5_000L, settings.intervalSeconds() * 1000L);
    }

    public void stop() {
        PlatformScheduler.Cancellable current = task;
        if (current != null) {
            current.cancel();
            task = null;
        }
    }

    public void sampleNow() {
        collect();
    }

    private void collect() {
        try {
            long now = System.currentTimeMillis();
            List<OnlinePlayer> online = new ArrayList<>();
            if (settings.onlinePlayers()) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    online.add(new OnlinePlayer(player.getUniqueId().toString(), player.getName(), player.getWorld().getName(), player.getPing()));
                }
            }
            List<WorldSample> worlds = settings.worldStats() && !folia ? scanWorlds() : List.of();
            List<PluginEntry> pluginSnapshot = null;
            if (settings.plugins()) {
                if (plugins == null || now - pluginsScannedAt > settings.pluginScanIntervalMillis()) {
                    plugins = scanPlugins();
                    pluginsScannedAt = now;
                }
                pluginSnapshot = plugins;
            }
            SampleBundle bundle = new SampleBundle(
                now,
                descriptor,
                Bukkit.getOnlinePlayers().size(),
                tps(0),
                tps(1),
                tps(2),
                mspt(),
                Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory(),
                Runtime.getRuntime().maxMemory(),
                SystemMetrics.cpuPercent(),
                SystemMetrics.uptimeMillis(),
                worlds,
                pluginSnapshot,
                online,
                counters.takeJoins(),
                counters.takeLeaves()
            );
            worker.execute(() -> store.recordSample(bundle));
        } catch (Throwable error) {
            logger.error("Sample collection failed", error);
        }
    }

    private List<WorldSample> scanWorlds() {
        List<WorldSample> worlds = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            worlds.add(new WorldSample(world.getName(), world.getLoadedChunks().length, world.getEntities().size(), world.getPlayers().size()));
        }
        return worlds;
    }

    private double tps(int index) {
        try {
            double[] values = Bukkit.getTPS();
            if (index < values.length) {
                return values[index];
            }
        } catch (Throwable ignored) {
        }
        return 20.0;
    }

    private double mspt() {
        try {
            return Bukkit.getAverageTickTime();
        } catch (Throwable ignored) {
            return 0.0;
        }
    }

    private List<PluginEntry> scanPlugins() {
        return Arrays.stream(Bukkit.getPluginManager().getPlugins())
            .filter(plugin -> !plugin.getName().equals("ServAns"))
            .map(plugin -> new PluginEntry(plugin.getName(), plugin.getDescription().getVersion()))
            .toList();
    }
}
