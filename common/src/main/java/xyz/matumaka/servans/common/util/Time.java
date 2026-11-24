package xyz.matumaka.servans.common.util;

public final class Time {
    public static final long SECOND = 1000L;
    public static final long MINUTE = 60_000L;
    public static final long HOUR = 3_600_000L;
    public static final long DAY = 86_400_000L;

    private Time() {
    }

    public static long minuteBucket(long timestamp) {
        return Math.floorDiv(timestamp, MINUTE) * MINUTE;
    }

    public static long hourBucket(long timestamp) {
        return Math.floorDiv(timestamp, HOUR) * HOUR;
    }

    public static long days(int days) {
        return Math.max(0, days) * DAY;
    }

    public static long hours(int hours) {
        return Math.max(0, hours) * HOUR;
    }

    public static long minutes(int minutes) {
        return Math.max(0, minutes) * MINUTE;
    }

    public static String duration(long seconds) {
        if (seconds < 60) {
            return seconds + "s";
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + "m " + (seconds % 60) + "s";
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return hours + "h " + (minutes % 60) + "m";
        }
        return (hours / 24) + "d " + (hours % 24) + "h";
    }
}
