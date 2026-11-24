package xyz.matumaka.servans.common.config;

import java.util.List;
import java.util.Locale;

public record WebSettings(
    boolean enabled,
    String bindAddress,
    int port,
    String publicBaseUrl,
    boolean trustProxyHeaders,
    List<String> trustedProxies,
    List<String> corsOrigins,
    int rateLimitRequests,
    int rateLimitWindowSeconds,
    boolean httpsEnabled,
    String keystore,
    String keystorePassword
) {
    public WebSettings {
        bindAddress = bindAddress == null || bindAddress.isBlank() ? "0.0.0.0" : bindAddress.trim();
        port = port < 1 || port > 65535 ? 8804 : port;
        publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl.trim().replaceAll("/+$", "");
        trustedProxies = clean(trustedProxies);
        corsOrigins = clean(corsOrigins);
        rateLimitRequests = Math.max(1, Math.min(100_000, rateLimitRequests));
        rateLimitWindowSeconds = Math.max(1, Math.min(3600, rateLimitWindowSeconds));
        keystore = keystore == null || keystore.isBlank() ? "keystore.p12" : keystore.trim();
        keystorePassword = keystorePassword == null ? "" : keystorePassword;
    }

    public String listenAddress() {
        return bindAddress + ":" + port;
    }

    public static WebSettings defaults() {
        return new WebSettings(true, "0.0.0.0", 8804, "", false, List.of(), List.of(), 120, 60, false, "keystore.p12", "");
    }

    private static List<String> clean(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
            .filter(value -> value != null && !value.isBlank())
            .map(value -> value.trim().toLowerCase(Locale.ROOT))
            .toList();
    }
}
