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
import xyz.matumaka.servans.paper.collect.ActivityCounters;
import xyz.matumaka.servans.paper.collect.MetricsCollector;
import xyz.matumaka.servans.paper.config.PaperConfig;
import xyz.matumaka.servans.paper.schedule.PaperScheduler;
import xyz.matumaka.servans.paper.schedule.PlatformScheduler;
import xyz.matumaka.servans.paper.session.SessionTracker;

public final class ServAnsPlugin extends JavaPlugin {
    private ServAnsLogger logger;
    private Settings settings;
    private Database database;
    private DataStore store;
    private AsyncWorker worker;
    private PlatformScheduler scheduler;
    private MetricsCollector collector;
    private SessionTracker sessions;
    private MaintenanceService maintenance;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        logger = new BukkitLogger(getLogger(), getConfig().getBoolean("logging.debug", false));
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
            collector = new MetricsCollector(this, store, scheduler, worker, descriptor, counters, settings.collection(), logger, PaperScheduler.isFolia());
            collector.start();
            maintenance = new MaintenanceService(database, settings.retention(), logger);
            worker.scheduleAtFixedRate(() -> maintenance.runOnce(System.currentTimeMillis()), 30, settings.maintenance().intervalSeconds());
            logger.info("ServAns enabled for " + descriptor.name() + " (" + descriptor.id() + ") using " + database.dialect().name().toLowerCase());
            logger.debug("Web server configured for " + settings.web().listenAddress() + " with public base " + (settings.web().publicBaseUrl().isBlank() ? "(auto)" : settings.web().publicBaseUrl()));
        } catch (Throwable error) {
            logger.error("Could not enable ServAns", error);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (collector != null) {
            collector.stop();
            collector.sampleNow();
        }
        if (sessions != null) {
            sessions.closeAll();
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
