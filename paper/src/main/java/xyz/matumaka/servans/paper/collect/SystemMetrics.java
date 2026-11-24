package xyz.matumaka.servans.paper.collect;

import java.lang.management.ManagementFactory;

public final class SystemMetrics {
    private static final com.sun.management.OperatingSystemMXBean OS =
        (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

    private SystemMetrics() {
    }

    public static double cpuPercent() {
        double load = OS.getProcessCpuLoad();
        if (!Double.isFinite(load) || load < 0.0) {
            return 0.0;
        }
        return Math.round(load * 1000.0) / 10.0;
    }

    public static long uptimeMillis() {
        return ManagementFactory.getRuntimeMXBean().getUptime();
    }
}
