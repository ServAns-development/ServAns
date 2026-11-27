package xyz.matumaka.servans.paper;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import xyz.matumaka.servans.common.ServAnsLogger;
import xyz.matumaka.servans.common.config.Settings;
import xyz.matumaka.servans.common.db.Database;
import xyz.matumaka.servans.common.db.Migrations;
import xyz.matumaka.servans.common.model.ServerDescriptor;
import xyz.matumaka.servans.common.rollup.MaintenanceService;
import xyz.matumaka.servans.common.store.DataStore;
import xyz.matumaka.servans.paper.cloud.CloudUploader;
import xyz.matumaka.servans.paper.collect.ActivityCounters;
import xyz.matumaka.servans.paper.collect.MetricsCollector;
import xyz.matumaka.servans.paper.config.PaperConfig;
import xyz.matumaka.servans.paper.schedule.PaperScheduler;
import xyz.matumaka.servans.paper.schedule.PlatformScheduler;
import xyz.matumaka.servans.paper.session.SessionTracker;

public final class ServAnsPlugin extends JavaPlugin {
    private ServAnsLogger logger;
    private volatile Settings settings;
    private Database database;
    private DataStore store;
    private AsyncWorker worker;
    private PlatformScheduler scheduler;
    private CloudUploader uploader;
    private MetricsCollector collector;
    private SessionTracker sessions;
    private MaintenanceService maintenance;
    private PlatformScheduler.Cancellable maintenanceTask;
    private PlatformScheduler.Cancellable settingsTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        logger = new BukkitLogger(getLogger(), false);
        try {
            settings = PaperConfig.load(this, logger);
            scheduler = PaperScheduler.create(this);
            database = Database.open(settings.database(), getDataFolder().toPath(), logger);
            Migrations.migrate(database, logger);
            store = new DataStore(database);
            ServerDescriptor descriptor = descriptor();
            long now = System.currentTimeMillis();
            store.ensureServer(descriptor, now);
            store.closeStaleSessions(descriptor.id(), now);
            store.clearOnline(descriptor.id());
            worker = new AsyncWorker(logger);
            ActivityCounters counters = new ActivityCounters();
            sessions = new SessionTracker(store, worker, descriptor.id(), counters);
            getServer().getPluginManager().registerEvents(sessions, this);
            uploader = new CloudUploader(settings.cloud(), getDescription().getVersion(), logger);
            collector = new MetricsCollector(this, store, scheduler, worker, descriptor, counters, uploader, settings.collection(), logger, PaperScheduler.isFolia());
            collector.start();
            maintenance = new MaintenanceService(database, settings.retention(), logger);
            scheduleMaintenance(settings.maintenance().intervalSeconds());
            settingsTask = scheduler.repeatAsync(this::refreshRemoteSettings, 5_000L, 300_000L);
            logger.info("ServAns enabled for " + descriptor.name() + " (" + descriptor.id() + ") using " + database.dialect().name().toLowerCase());
            if (uploader.configured()) {
                logger.info("Sending analytics to " + settings.cloud().url() + " and loading settings from the dashboard");
            } else {
                logger.warn("No dashboard key configured. Create a server at " + settings.cloud().url() + ", then paste its key into cloud.key in config.yml.");
            }
        } catch (Throwable error) {
            logger.error("Could not enable ServAns", error);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (settingsTask != null) {
            settingsTask.cancel();
            settingsTask = null;
        }
        if (maintenanceTask != null) {
            maintenanceTask.cancel();
            maintenanceTask = null;
        }
        if (collector != null) {
            collector.stop();
            collector.sampleNow();
        }
        if (sessions != null) {
            sessions.closeAll();
        }
        if (uploader != null) {
            uploader.close();
            uploader = null;
        }
        if (worker != null) {
            worker.close();
            worker = null;
        }
        if (database != null) {
            database.close();
            database = null;
        }
    }

    public Settings settings() {
        return settings;
    }

    public DataStore store() {
        return store;
    }

    public PlatformScheduler scheduler() {
        return scheduler;
    }

    private void refreshRemoteSettings() {
        CloudUploader current = uploader;
        if (current == null || !current.configured()) {
            return;
        }
        Settings remote = current.fetchSettings(settings);
        if (remote != null) {
            applySettings(remote);
        }
    }

    private synchronized void applySettings(Settings next) {
        if (next == null) {
            return;
        }
        Settings previous = settings;
        settings = next;
        if (logger instanceof BukkitLogger bukkit) {
            bukkit.setDebug(next.logging().debug());
        }
        if (collector != null) {
            collector.applySettings(next.collection());
        }
        if (maintenance != null) {
            maintenance.updateRetention(next.retention());
        }
        if (previous == null || previous.maintenance().intervalSeconds() != next.maintenance().intervalSeconds()) {
            scheduleMaintenance(next.maintenance().intervalSeconds());
        }
        logger.debug("Loaded dashboard settings: sample every " + next.collection().intervalSeconds()
            + "s, maintenance every " + next.maintenance().intervalSeconds() + "s");
    }

    private void scheduleMaintenance(int intervalSeconds) {
        if (maintenanceTask != null) {
            maintenanceTask.cancel();
        }
        maintenanceTask = scheduler.repeatAsync(
            () -> {
                MaintenanceService current = maintenance;
                if (current != null) {
                    current.runOnce(System.currentTimeMillis());
                }
            },
            30_000L,
            Math.max(30, intervalSeconds) * 1000L
        );
    }

    private ServerDescriptor descriptor() {
        String software = "Paper";
        try {
            software = Bukkit.getName();
        } catch (Throwable ignored) {
        }
        String version = "1.21";
        try {
            version = Bukkit.getMinecraftVersion();
        } catch (Throwable ignored) {
            try {
                version = Bukkit.getBukkitVersion().split("-")[0];
            } catch (Throwable ignoredAgain) {
            }
        }
        return new ServerDescriptor(settings.server().id(), settings.server().name(), software, version, Bukkit.getMaxPlayers());
    }
}
