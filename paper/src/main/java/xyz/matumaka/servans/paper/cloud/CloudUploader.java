package xyz.matumaka.servans.paper.cloud;

import xyz.matumaka.servans.common.ServAnsLogger;
import xyz.matumaka.servans.common.config.CloudSettings;
import xyz.matumaka.servans.common.config.Settings;
import xyz.matumaka.servans.common.config.SettingsJson;
import xyz.matumaka.servans.common.json.Json;
import xyz.matumaka.servans.common.model.OnlinePlayer;
import xyz.matumaka.servans.common.model.PluginEntry;
import xyz.matumaka.servans.common.model.SampleBundle;
import xyz.matumaka.servans.common.model.WorldSample;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pushes collected samples to the central ServAns dashboard.
 *
 * <p>The dashboard owns the website, storage and history. The plugin only needs an
 * outbound HTTPS connection and the server key copied from the dashboard.</p>
 */
public final class CloudUploader implements AutoCloseable {
    private static final int MAX_QUEUED_SAMPLES = 20;

    private final CloudSettings settings;
    private final ServAnsLogger logger;
    private final String userAgent;
    private final HttpClient client;
    private final ExecutorService executor;
    private final AtomicInteger queued = new AtomicInteger();
    private volatile long lastFailureLog;
    private volatile String lastFailure;

    public CloudUploader(CloudSettings settings, String pluginVersion, ServAnsLogger logger) {
        this.settings = settings;
        this.logger = logger;
        this.userAgent = "ServAns/" + (pluginVersion == null || pluginVersion.isBlank() ? "dev" : pluginVersion);
        this.client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(Math.min(settings.timeoutSeconds(), 15)))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ServAns-Dashboard");
            thread.setDaemon(true);
            return thread;
        });
    }

    public boolean configured() {
        return settings.configured();
    }

    public CloudSettings settings() {
        return settings;
    }

    /**
     * Fetches the per-server settings configured on the dashboard. Returns {@code null}
     * when the request fails, so the caller can keep the current settings.
     */
    public Settings fetchSettings(Settings base) {
        if (!settings.configured()) {
            return null;
        }
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(settings.settingsUrl()))
                .timeout(Duration.ofSeconds(settings.timeoutSeconds()))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + settings.key())
                .header("User-Agent", userAgent)
                .GET()
                .build();
        } catch (IllegalArgumentException error) {
            warn("Invalid cloud.url '" + settings.url() + "': " + error.getMessage());
            return null;
        }

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            String body = response.body();
            if (status >= 200 && status < 300 && body != null && !body.isBlank()) {
                return SettingsJson.parseJson(body, base);
            }
            if (status == 401 || status == 403) {
                warn("Dashboard rejected the server key while loading settings. Rotate the key and update cloud.key.");
            } else {
                warn("Could not load dashboard settings (HTTP " + status + ")");
            }
        } catch (IOException error) {
            warn("Could not load dashboard settings from " + settings.url() + ": " + error.getMessage());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException error) {
            warn("Dashboard returned invalid settings: " + error.getMessage());
        }
        return null;
    }

    public void upload(SampleBundle bundle) {
        if (!settings.configured()) {
            return;
        }
        if (queued.get() >= MAX_QUEUED_SAMPLES) {
            logger.debug("Dashboard upload queue is full, skipping sample " + bundle.ts());
            return;
        }
        queued.incrementAndGet();
        executor.execute(() -> {
            try {
                send(bundle);
            } finally {
                queued.decrementAndGet();
            }
        });
    }

    private void send(SampleBundle bundle) {
        String body;
        try {
            body = Json.write(payload(bundle));
        } catch (RuntimeException error) {
            logger.warn("Could not encode dashboard sample: " + error.getMessage());
            return;
        }

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(settings.ingestUrl()))
                .timeout(Duration.ofSeconds(settings.timeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + settings.key())
                .header("User-Agent", userAgent)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        } catch (IllegalArgumentException error) {
            warn("Invalid cloud.url '" + settings.url() + "': " + error.getMessage());
            return;
        }

        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status >= 200 && status < 300) {
                    if (lastFailure != null) {
                        logger.info("Dashboard connection restored");
                        lastFailure = null;
                    }
                    logger.debug("Uploaded sample " + bundle.ts() + " to dashboard");
                    return;
                }
                String message = response.body() == null || response.body().isBlank()
                    ? "HTTP " + status
                    : "HTTP " + status + ": " + truncate(response.body());
                if (status == 401 || status == 403) {
                    warn("Dashboard rejected the server key. Create a new server on the website or rotate its key, then update cloud.key. (" + message + ")");
                    return;
                }
                if (status >= 500 && attempt == 1) {
                    sleep(750L);
                    continue;
                }
                warn("Dashboard upload failed (" + message + ")");
                return;
            } catch (IOException error) {
                if (attempt == 1) {
                    sleep(750L);
                    continue;
                }
                warn("Could not reach the dashboard at " + settings.url() + ": " + error.getMessage());
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private Map<String, Object> payload(SampleBundle bundle) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("timestamp", bundle.ts());
        root.put("players", bundle.players());
        root.put("tps1", bundle.tps1());
        root.put("tps5", bundle.tps5());
        root.put("tps15", bundle.tps15());
        root.put("mspt", bundle.mspt());
        root.put("ramUsed", bundle.ramUsed());
        root.put("ramMax", bundle.ramMax());
        root.put("cpu", bundle.cpu());
        root.put("uptime", bundle.uptime());
        root.put("joins", bundle.joins());
        root.put("leaves", bundle.leaves());

        Map<String, Object> server = new LinkedHashMap<>();
        server.put("localId", bundle.server().id());
        server.put("name", bundle.server().name());
        server.put("software", bundle.server().software());
        server.put("version", bundle.server().version());
        server.put("maxPlayers", bundle.server().maxPlayers());
        root.put("server", server);

        List<Map<String, Object>> worlds = new ArrayList<>();
        for (WorldSample world : bundle.worlds()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("world", world.world());
            entry.put("chunks", world.chunks());
            entry.put("entities", world.entities());
            entry.put("players", world.players());
            worlds.add(entry);
        }
        root.put("worlds", worlds);

        if (bundle.plugins() != null) {
            List<Map<String, Object>> plugins = new ArrayList<>();
            for (PluginEntry plugin : bundle.plugins()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("name", plugin.name());
                entry.put("version", plugin.version());
                plugins.add(entry);
            }
            root.put("plugins", plugins);
        }

        List<Map<String, Object>> online = new ArrayList<>();
        for (OnlinePlayer player : bundle.online()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("uuid", player.uuid());
            entry.put("name", player.name());
            entry.put("world", player.world());
            entry.put("ping", player.ping());
            online.add(entry);
        }
        root.put("online", online);
        return root;
    }

    private void warn(String message) {
        long now = System.currentTimeMillis();
        if (message.equals(lastFailure) && now - lastFailureLog < 60_000L) {
            return;
        }
        lastFailure = message;
        lastFailureLog = now;
        logger.warn(message);
    }

    private static String truncate(String value) {
        String clean = value.replaceAll("\\s+", " ").trim();
        return clean.length() <= 240 ? clean : clean.substring(0, 237) + "...";
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }
}
