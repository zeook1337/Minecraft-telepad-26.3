package subaraki.telepads.server;

import subaraki.telepads.data.TelepadLocation;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** A name request is valid only for the actor's recent physical placement. */
public final class PlacementSessions {
    public record Placement(UUID token, UUID pad, TelepadLocation location, long expiresAt) {}
    private final Map<UUID, Placement> pending = new HashMap<>();

    public Placement begin(UUID actor, UUID pad, TelepadLocation location, long tick) {
        var placement = new Placement(UUID.randomUUID(), pad, location, tick + 600);
        pending.put(actor, placement);
        return placement;
    }

    public Placement find(UUID actor, UUID token, long tick) {
        var placement = pending.get(actor);
        if (placement == null || !placement.token.equals(token)) return null;
        if (tick >= placement.expiresAt) { pending.remove(actor); return null; }
        return placement;
    }

    public void cancel(UUID actor, UUID token) {
        var placement = pending.get(actor);
        if (placement != null && placement.token.equals(token)) pending.remove(actor);
    }

    public void clear(UUID actor) { pending.remove(actor); }
    public void expire(long tick) { pending.values().removeIf(value -> tick >= value.expiresAt); }
}
