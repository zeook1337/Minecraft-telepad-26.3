package subaraki.telepads.server;

import org.junit.jupiter.api.Test;
import subaraki.telepads.data.TelepadLocation;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PlacementSessionsTest {
    @Test void contextsAreActorBoundExpiringAndReplacedPerPlacement() {
        var sessions = new PlacementSessions();
        var actor = UUID.randomUUID();
        var first = sessions.begin(actor, UUID.randomUUID(), new TelepadLocation("minecraft:overworld", 1, 2, 3), 100);
        assertNull(sessions.find(UUID.randomUUID(), first.token(), 101));
        assertNotNull(sessions.find(actor, first.token(), 699));
        assertNull(sessions.find(actor, first.token(), 700));
        var second = sessions.begin(actor, UUID.randomUUID(), first.location(), 701);
        sessions.cancel(actor, first.token());
        assertNotNull(sessions.find(actor, second.token(), 702));
        sessions.clear(actor);
        assertNull(sessions.find(actor, second.token(), 702));
    }
}
