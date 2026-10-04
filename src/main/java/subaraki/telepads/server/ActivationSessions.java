package subaraki.telepads.server;

import subaraki.telepads.data.TelepadLocation;
import java.util.*;

/** Server-thread-only transient state. A token can authorize one attempt, never a client coordinate. */
public final class ActivationSessions {
    public record Session(UUID token, UUID origin, TelepadLocation location, long expires, Set<UUID> offered) {}
    private static final class Presence {
        final UUID origin;
        final long started;
        boolean opened;
        Presence(UUID origin, long started) { this.origin = origin; this.started = started; }
    }
    private final Map<UUID, Presence> presence = new HashMap<>();
    private final Map<UUID, Session> sessions = new HashMap<>();
    public boolean ready(UUID player, UUID origin, long now, int delayTicks) {
        var state = presence.get(player);
        if (state == null || !state.origin.equals(origin)) {
            clear(player);
            state = new Presence(origin, now);
            presence.put(player, state);
        }
        if (state.opened || now - state.started < delayTicks) return false;
        state.opened = true;
        return true;
    }
    public Session open(UUID player, UUID origin, TelepadLocation location, long now, Collection<UUID> offered) {
        var session = new Session(UUID.randomUUID(), origin, location, now + 1200, Set.copyOf(offered));
        sessions.put(player, session);
        return session;
    }
    public Session find(UUID player, UUID token, long now) {
        var session = sessions.get(player);
        return session != null && session.token.equals(token) && now < session.expires ? session : null;
    }
    public void consume(UUID player) { sessions.remove(player); }
    public void cancel(UUID player, UUID token) {
        var session = sessions.get(player);
        if (session != null && session.token.equals(token)) clear(player);
    }
    public void clear(UUID player) { presence.remove(player); sessions.remove(player); }
    public void expire(long now) {
        sessions.entrySet().removeIf(entry -> entry.getValue().expires <= now);
    }
}
