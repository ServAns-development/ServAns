package xyz.matumaka.servans.common.util;

import java.security.SecureRandom;
import java.util.Locale;

public final class Ids {
    public static final String ALPHABET = "23456789abcdefghjkmnpqrstuvwxyz";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String[] RESERVED = {"servers", "server", "players", "player", "query", "settings", "login", "api", "badge", "assets", "static", "favicon.ico", "index.html"};

    private Ids() {
    }

    public static String serverId() {
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    public static boolean validSlug(String value) {
        if (value == null || value.length() < 2 || value.length() > 64) {
            return false;
        }
        String slug = value.toLowerCase(Locale.ROOT);
        if (!slug.matches("[a-z0-9][a-z0-9_-]*")) {
            return false;
        }
        for (String reserved : RESERVED) {
            if (reserved.equals(slug)) {
                return false;
            }
        }
        return true;
    }

    public static String normalizeSlug(String value) {
        if (value == null) {
            return "";
        }
        String slug = value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "-").replaceAll("-{2,}", "-").replaceAll("^[-_]+|[-_]+$", "");
        if (slug.length() > 64) {
            slug = slug.substring(0, 64);
        }
        return validSlug(slug) ? slug : "";
    }

    public static String shortId(String value, int length) {
        if (value == null) {
            return "";
        }
        return value.length() <= length ? value : value.substring(0, length);
    }
}
