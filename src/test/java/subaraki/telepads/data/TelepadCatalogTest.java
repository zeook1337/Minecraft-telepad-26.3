package subaraki.telepads.data;

import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class TelepadCatalogTest {
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final TelepadLocation home = new TelepadLocation("minecraft:overworld", -12, -40, 90);

    private TelepadCatalog reload(TelepadCatalog catalog) {
        var encoded = TelepadCatalog.CODEC.encodeStart(JsonOps.INSTANCE, catalog).getOrThrow();
        return TelepadCatalog.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
    }

    @Test void sameNamesAreDistinctAndOneLocationHasOneIdentityAfterReload() {
        var catalog = new TelepadCatalog();
        var first = catalog.place(home, alice);
        var other = catalog.place(new TelepadLocation("minecraft:the_nether", -12, -40, 90), bob);
        var third = catalog.place(new TelepadLocation("minecraft:overworld", -13, -40, 90), alice);
        var loaded = reload(catalog);
        assertEquals(3, loaded.entries().size());
        assertEquals(first, loaded.find(home));
        assertEquals(other, loaded.find(other.location()));
        assertEquals(third, loaded.find(third.id()));
        assertEquals(2, loaded.visibleTo(alice).size());
        assertEquals(1, loaded.visibleTo(bob).size());
    }

    @Test void replacingMissingPadInvalidatesOldIdentityAndOldForgetPreference() {
        var catalog = new TelepadCatalog();
        var original = catalog.place(home, alice);
        catalog.markMissing(home);
        assertTrue(catalog.find(original.id()).missing());
        assertTrue(catalog.forget(original.id(), alice));
        var replacement = catalog.place(home, alice);
        var loaded = reload(catalog);
        assertNotEquals(original.id(), replacement.id());
        assertNull(loaded.find(original.id()));
        assertEquals(1, loaded.entries().size());
        assertEquals(replacement, loaded.visibleTo(alice).getFirst());
    }

    @Test void publicAccessAndIndividualDiscoveryPreserveOtherUsers() {
        var catalog = new TelepadCatalog();
        var entry = catalog.place(home, alice);
        assertTrue(catalog.visibleTo(bob).isEmpty());
        assertTrue(catalog.toggleRegistration(entry.id(), bob));
        assertTrue(catalog.find(entry.id()).canUse(alice));
        assertTrue(catalog.find(entry.id()).canUse(bob));
        catalog.update(entry.id(), value -> value.withPublic(true));
        assertFalse(catalog.toggleRegistration(entry.id(), bob));
        catalog.update(entry.id(), value -> value.withPublic(false));
        assertTrue(catalog.find(entry.id()).canUse(bob));
        assertTrue(catalog.toggleRegistration(entry.id(), bob));
        assertFalse(catalog.find(entry.id()).canUse(bob));
        assertTrue(catalog.find(entry.id()).canUse(alice));
    }

    @Test void sharingIsSnapshotAndForgettingIsIndividualAndWorldLocal() {
        var catalog = new TelepadCatalog();
        var entry = catalog.place(home, alice);
        assertTrue(catalog.addFriend(alice, bob, "Bob"));
        assertFalse(catalog.addFriend(alice, bob, "Bob"));
        assertTrue(catalog.share(entry.id(), alice));
        catalog.removeFriend(alice, bob);
        catalog.markMissing(home);
        assertTrue(catalog.forget(entry.id(), alice));
        var loaded = reload(catalog);
        assertTrue(loaded.visibleTo(alice).isEmpty());
        assertEquals(1, loaded.visibleTo(bob).size());
        assertTrue(loaded.find(entry.id()).canUse(bob));
        assertTrue(new TelepadCatalog().visibleTo(bob).isEmpty());
        assertTrue(new TelepadCatalog().preferences(alice).friends().isEmpty());
    }

    @Test void friendLimitAndClearArePerPlayerAndPersistent() {
        var catalog = new TelepadCatalog();
        assertFalse(catalog.addFriend(alice, alice, "Alice"));
        for (int i = 0; i < 9; i++) assertTrue(catalog.addFriend(alice, UUID.randomUUID(), "Friend" + i));
        assertFalse(catalog.addFriend(alice, bob, "Bob"));
        assertTrue(catalog.addFriend(bob, alice, "Alice"));
        var loaded = reload(catalog);
        assertEquals(9, loaded.preferences(alice).friends().size());
        loaded.clearFriends(alice);
        assertEquals(1, loaded.preferences(bob).friends().size());
        assertEquals(0, reload(loaded).preferences(alice).friends().size());
    }

    @Test void mutationMarksDirtyAndFutureFormatsAndDuplicateLocationsAreRejected() {
        var catalog = new TelepadCatalog();
        var entry = catalog.place(home, alice);
        assertTrue(catalog.isDirty());
        catalog.setDirty(false);
        catalog.update(entry.id(), value -> value.rename("Home"));
        assertTrue(catalog.isDirty());
        var json = TelepadCatalog.CODEC.encodeStart(JsonOps.INSTANCE, catalog).getOrThrow().getAsJsonObject();
        json.addProperty("version", 2);
        assertTrue(TelepadCatalog.CODEC.parse(JsonOps.INSTANCE, json).error().isPresent());
        json.addProperty("version", 1);
        var entries = json.getAsJsonArray("entries");
        entries.add(entries.get(0).deepCopy());
        assertTrue(TelepadCatalog.CODEC.parse(JsonOps.INSTANCE, json).error().isPresent());
    }
}
