package xyz.matumaka.servans.paper;

import xyz.matumaka.servans.common.ServAnsLogger;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class AsyncWorker implements AutoCloseable {
    private final ServAnsLogger logger;
    private final ScheduledExecutorService executor;

    public AsyncWorker(ServAnsLogger logger) {
        this.logger = logger;
        this.executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ServAns-Database");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void execute(Runnable task) {
        executor.execute(wrap(task));
    }

    public void scheduleAtFixedRate(Runnable task, long initialDelaySeconds, long periodSeconds) {
        executor.scheduleAtFixedRate(wrap(task), initialDelaySeconds, periodSeconds, TimeUnit.SECONDS);
    }

    public void schedule(Runnable task, long delaySeconds) {
        executor.schedule(wrap(task), delaySeconds, TimeUnit.SECONDS);
    }

    private Runnable wrap(Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (Throwable error) {
                logger.error("Background task failed", error);
            }
        };
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(20, TimeUnit.SECONDS)) {
                logger.warn("Database queue did not drain within 20 seconds");
                executor.shutdownNow();
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }
}
