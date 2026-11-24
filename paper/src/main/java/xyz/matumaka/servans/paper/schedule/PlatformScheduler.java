package xyz.matumaka.servans.paper.schedule;

public interface PlatformScheduler {
    void runGlobal(Runnable task);

    void runAsync(Runnable task);

    Cancellable repeatGlobal(Runnable task, long delayMillis, long periodMillis);

    Cancellable repeatAsync(Runnable task, long delayMillis, long periodMillis);

    boolean primaryThread();

    interface Cancellable {
        void cancel();
    }
}
