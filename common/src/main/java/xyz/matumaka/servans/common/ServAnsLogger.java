package xyz.matumaka.servans.common;

public interface ServAnsLogger {
    void info(String message);

    void warn(String message);

    void error(String message, Throwable error);

    default void debug(String message) {
    }
}
