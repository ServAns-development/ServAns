package xyz.matumaka.servans.paper;

import xyz.matumaka.servans.common.ServAnsLogger;

import java.util.logging.Level;
import java.util.logging.Logger;

final class BukkitLogger implements ServAnsLogger {
    private final Logger logger;
    private final boolean debug;

    BukkitLogger(Logger logger, boolean debug) {
        this.logger = logger;
        this.debug = debug;
    }

    @Override
    public void info(String message) {
        logger.info(message);
    }

    @Override
    public void warn(String message) {
        logger.warning(message);
    }

    @Override
    public void error(String message, Throwable error) {
        logger.log(Level.SEVERE, message, error);
    }

    @Override
    public void debug(String message) {
        if (debug) {
            logger.info("[debug] " + message);
        }
    }
}
