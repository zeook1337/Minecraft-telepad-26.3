package subaraki.telepads.server;

import org.junit.jupiter.api.Test;
import subaraki.telepads.data.TelepadLocation;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TravelRulesTest {
    @Test void activationWaitOriginCancelExpiryAndReplay() {
        var sessions = new ActivationSessions(); var actor = UUID.randomUUID(); var origin = UUID.randomUUID(); var target = UUID.randomUUID();
        var location = new TelepadLocation("minecraft:overworld", 0, 64, 0);
        assertFalse(sessions.ready(actor, origin, 0, 60));
        assertFalse(sessions.ready(actor, origin, 59, 60));
        assertTrue(sessions.ready(actor, origin, 60, 60));
        assertFalse(sessions.ready(actor, origin, 61, 60));
        var session = sessions.open(actor, origin, location, 60, List.of(target));
        assertNull(sessions.find(UUID.randomUUID(), session.token(), 61));
        assertNotNull(sessions.find(actor, session.token(), 61));
        sessions.consume(actor); assertNull(sessions.find(actor, session.token(), 62));
        sessions.clear(actor); assertFalse(sessions.ready(actor, origin, 63, 60));
        assertFalse(sessions.ready(actor, UUID.randomUUID(), 123, 60));
        sessions.clear(actor); assertTrue(sessions.ready(actor, origin, 124, 0));
        var next = sessions.open(actor, origin, location, 124, List.of(target));
        assertNull(sessions.find(actor, next.token(), 1324));
        sessions.cancel(actor, next.token()); assertFalse(sessions.ready(actor, origin, 1325, 60));
    }
    @Test void experienceAcrossLevelsAndLevelPriority() {
        assertEquals(352, ExperienceCost.pointsAtLevel(16)); assertEquals(1507, ExperienceCost.pointsAtLevel(31));
        assertEquals(1395, ExperienceCost.pointsAtLevel(30)); assertEquals(1628, ExperienceCost.pointsAtLevel(32));
        assertEquals(17, ExperienceCost.balance(2, 1f / 11));
        assertTrue(new ExperienceCost(0, 17).affordable(2, 1f / 11));
        assertFalse(new ExperienceCost(0, 18).affordable(2, 1f / 11));
        assertTrue(new ExperienceCost(2, 100000).affordable(2, 0));
        assertFalse(new ExperienceCost(3, 0).affordable(2, .99f));
    }
    @Test void configuredDestinationSyntaxAndNegativeInclusiveIntervals() {
        var destination = ConfiguredDestination.parse("-9#-3/-64/0/minecraft:overworld/Negative");
        var random = net.minecraft.util.RandomSource.create(1);
        var chosen = new HashSet<Integer>();
        for (int i = 0; i < 1000; i++) chosen.add(destination.x().choose(-20, 20, random));
        assertEquals(Set.of(-9, -8, -7, -6, -5, -4, -3), chosen);
        assertEquals(-64, destination.y().choose(-64, 319, random));
        assertThrows(IllegalArgumentException.class, () -> destination.y().choose(0, 319, random));
        for (String invalid : List.of("0/0/0/overworld", "0/0/0/minecraft:overworld/", "2#1/0/0/random/a", "x/0/0/random/a", "0/0/0/Bad:ID/a", "0/0/0/random/abcdefghijklmnopq"))
            assertThrows(IllegalArgumentException.class, () -> ConfiguredDestination.parse(invalid));
        assertNotNull(ConfiguredDestination.parse("random/random/random/random/Random"));
    }
}
