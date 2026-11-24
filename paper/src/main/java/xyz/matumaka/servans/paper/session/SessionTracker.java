package xyz.matumaka.servans.paper.session;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import xyz.matumaka.servans.common.store.DataStore;
import xyz.matumaka.servans.paper.AsyncWorker;
import xyz.matumaka.servans.paper.collect.ActivityCounters;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SessionTracker implements Listener {
    private final DataStore store;
    private final AsyncWorker worker;
    private final String serverId;
    private final ActivityCounters counters;
    private final Map<UUID, ActiveSession> active = new ConcurrentHashMap<>();

    public SessionTracker(DataStore store, AsyncWorker worker, String serverId, ActivityCounters counters) {
        this.store = store;
        this.worker = worker;
        this.serverId = serverId;
        this.counters = counters;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        ActiveSession session = new ActiveSession(UUID.randomUUID().toString(), player.getUniqueId(), player.getName(), player.getWorld().getName(), now);
        ActiveSession previous = active.put(player.getUniqueId(), session);
        if (previous != null) {
            close(previous, now, false);
        }
        counters.joined();
        worker.execute(() -> store.openSession(session.sessionId, serverId, session.uuid, session.name, session.world, session.joinedAt));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        ActiveSession session = active.remove(event.getPlayer().getUniqueId());
        if (session == null) {
            return;
        }
        close(session, System.currentTimeMillis(), true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        ActiveSession session = active.get(event.getPlayer().getUniqueId());
        if (session == null) {
            return;
        }
        String target = event.getPlayer().getWorld().getName();
        long now = System.currentTimeMillis();
        String from;
        long seconds;
        synchronized (session) {
            if (session.world.equals(target)) {
                return;
            }
            from = session.world;
            seconds = Math.max(0L, (now - session.worldSince) / 1000L);
            session.world = target;
            session.worldSince = now;
        }
        if (seconds > 0) {
            worker.execute(() -> store.addWorldTime(serverId, session.uuid, from, seconds, now));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        ActiveSession session = active.get(player.getUniqueId());
        long now = System.currentTimeMillis();
        String sessionId = session == null ? null : session.sessionId;
        worker.execute(() -> store.recordDeath(serverId, player.getUniqueId(), player.getName(), sessionId, now));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        ActiveSession session = active.get(killer.getUniqueId());
        if (session == null) {
            return;
        }
        long now = System.currentTimeMillis();
        worker.execute(() -> store.recordKill(serverId, killer.getUniqueId(), killer.getName(), session.sessionId, now));
    }

    public void closeAll() {
        long now = System.currentTimeMillis();
        for (UUID uuid : active.keySet()) {
            ActiveSession session = active.remove(uuid);
            if (session != null) {
                close(session, now, false);
            }
        }
    }

    private void close(ActiveSession session, long now, boolean countLeave) {
        long playSeconds = Math.max(0L, (now - session.joinedAt) / 1000L);
        long worldSeconds;
        String world;
        synchronized (session) {
            world = session.world;
            worldSeconds = Math.max(0L, (now - session.worldSince) / 1000L);
        }
        if (countLeave) {
            counters.left();
        }
        worker.execute(() -> store.closeSession(session.sessionId, serverId, session.uuid, session.name, world, now, playSeconds, worldSeconds));
    }

    private static final class ActiveSession {
        private final String sessionId;
        private final UUID uuid;
        private final String name;
        private final long joinedAt;
        private String world;
        private long worldSince;

        private ActiveSession(String sessionId, UUID uuid, String name, String world, long joinedAt) {
            this.sessionId = sessionId;
            this.uuid = uuid;
            this.name = name;
            this.world = world;
            this.worldSince = joinedAt;
            this.joinedAt = joinedAt;
        }
    }
}
