package xyz.matumaka.servans.paper.schedule;

import io.papermc.paper.threadedregions.scheduler.AsyncScheduler;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.TimeUnit;

public final class PaperScheduler implements PlatformScheduler {
    private final Plugin plugin;
    private final boolean folia;
    private final GlobalRegionScheduler global;
    private final AsyncScheduler async;

    private PaperScheduler(Plugin plugin) {
        this.plugin = plugin;
        this.folia = isFolia();
        this.global = folia ? Bukkit.getGlobalRegionScheduler() : null;
        this.async = folia ? Bukkit.getAsyncScheduler() : null;
    }

    public static PaperScheduler create(Plugin plugin) {
        return new PaperScheduler(plugin);
    }

    public static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException ignored) {
            return Bukkit.getVersion().toLowerCase().contains("folia");
        }
    }

    @Override
    public void runGlobal(Runnable task) {
        if (folia) {
            global.execute(plugin, task);
            return;
        }
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    @Override
    public void runAsync(Runnable task) {
        if (folia) {
            async.runNow(plugin, ignored -> task.run());
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    @Override
    public Cancellable repeatGlobal(Runnable task, long delayMillis, long periodMillis) {
        if (folia) {
            ScheduledTask handle = global.runAtFixedRate(plugin, ignored -> task.run(),
                Math.max(1L, delayMillis / 50L), Math.max(1L, periodMillis / 50L));
            return () -> handle.cancel();
        }
        int id = Bukkit.getScheduler().runTaskTimer(plugin, task, Math.max(1L, delayMillis / 50L), Math.max(1L, periodMillis / 50L)).getTaskId();
        return () -> Bukkit.getScheduler().cancelTask(id);
    }

    @Override
    public Cancellable repeatAsync(Runnable task, long delayMillis, long periodMillis) {
        if (folia) {
            ScheduledTask handle = async.runAtFixedRate(plugin, ignored -> task.run(),
                Math.max(0L, delayMillis), Math.max(1L, periodMillis), TimeUnit.MILLISECONDS);
            return () -> handle.cancel();
        }
        int id = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, Math.max(1L, delayMillis / 50L), Math.max(1L, periodMillis / 50L)).getTaskId();
        return () -> Bukkit.getScheduler().cancelTask(id);
    }

    @Override
    public boolean primaryThread() {
        return !folia && Bukkit.isPrimaryThread();
    }
}
