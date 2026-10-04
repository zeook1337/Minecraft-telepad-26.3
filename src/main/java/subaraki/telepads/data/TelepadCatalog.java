package subaraki.telepads.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;

/** One catalog in the Overworld; no player entity capability or process-wide world state. */
public final class TelepadCatalog extends SavedData {
    public static final int FORMAT_VERSION = 1;
    private record Snapshot(int version, List<TelepadEntry> entries, List<PlayerPreferences> players) {
        private static final Codec<Snapshot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("version").forGetter(Snapshot::version),
            TelepadEntry.CODEC.listOf().fieldOf("entries").forGetter(Snapshot::entries),
            PlayerPreferences.CODEC.listOf().fieldOf("players").forGetter(Snapshot::players)
        ).apply(instance, Snapshot::new));
    }

    public static final Codec<TelepadCatalog> CODEC = Snapshot.CODEC.flatXmap(TelepadCatalog::read,
        catalog -> DataResult.success(catalog.snapshot()));
    public static final SavedDataType<TelepadCatalog> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath("telepads", "catalog"), TelepadCatalog::new, CODEC, null);

    private final Map<UUID, TelepadEntry> entries = new LinkedHashMap<>();
    private final Map<TelepadLocation, UUID> locations = new HashMap<>();
    private final Map<UUID, PlayerPreferences> players = new LinkedHashMap<>();

    public static TelepadCatalog get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    private Snapshot snapshot() {
        return new Snapshot(FORMAT_VERSION, List.copyOf(entries.values()), List.copyOf(players.values()));
    }

    private static DataResult<TelepadCatalog> read(Snapshot snapshot) {
        if (snapshot.version != FORMAT_VERSION) return DataResult.error(() -> "Unsupported Telepads catalog version " + snapshot.version);
        var catalog = new TelepadCatalog();
        for (var entry : snapshot.entries) {
            if (catalog.entries.putIfAbsent(entry.id(), entry) != null || catalog.locations.putIfAbsent(entry.location(), entry.id()) != null)
                return DataResult.error(() -> "Duplicate Telepads identity or location");
        }
        for (var player : snapshot.players) {
            if (catalog.players.putIfAbsent(player.player(), player) != null)
                return DataResult.error(() -> "Duplicate Telepads player preferences");
        }
        return DataResult.success(catalog);
    }

    public TelepadEntry find(UUID id) { return entries.get(id); }
    public TelepadEntry find(TelepadLocation location) { return find(locations.get(location)); }
    public List<TelepadEntry> entries() { return List.copyOf(entries.values()); }
    public PlayerPreferences preferences(UUID player) { return players.getOrDefault(player, PlayerPreferences.empty(player)); }

    public TelepadEntry place(TelepadLocation location, UUID owner) {
        UUID previous = locations.get(location);
        if (previous != null) entries.remove(previous);
        var entry = new TelepadEntry(UUID.randomUUID(), "Telepad", location, Set.of(owner), false, false, false, false);
        entries.put(entry.id(), entry);
        locations.put(location, entry.id());
        setDirty();
        return entry;
    }

    public boolean update(UUID id, UnaryOperator<TelepadEntry> action) {
        var previous = entries.get(id);
        if (previous == null) return false;
        var changed = action.apply(previous);
        if (!changed.id().equals(id) || !changed.location().equals(previous.location()))
            throw new IllegalArgumentException("A catalog update cannot replace identity or location");
        if (!changed.equals(previous)) {
            entries.put(id, changed);
            setDirty();
        }
        return true;
    }

    public void markMissing(TelepadLocation location) {
        var entry = find(location);
        if (entry != null) update(entry.id(), value -> value.withState(true, value.disabled(), value.transmitter()));
    }

    public List<TelepadEntry> visibleTo(UUID player) {
        var forgotten = preferences(player).forgotten();
        return entries.values().stream().filter(entry -> entry.canUse(player) && !forgotten.contains(entry.id())).toList();
    }

    public boolean toggleRegistration(UUID id, UUID player) {
        var entry = find(id);
        if (entry == null || entry.publicAccess() || entry.missing()) return false;
        var prefs = preferences(player);
        var forgotten = new HashSet<>(prefs.forgotten());
        if (forgotten.remove(id)) {
            players.put(player, new PlayerPreferences(player, prefs.friends(), forgotten));
            setDirty();
        }
        return update(id, value -> value.register(player, !value.users().contains(player)));
    }

    public boolean addFriend(UUID player, UUID friend, String name) {
        var prefs = preferences(player);
        if (player.equals(friend) || prefs.friends().size() >= 9 || prefs.friends().stream().anyMatch(value -> value.id().equals(friend))) return false;
        var friends = new ArrayList<>(prefs.friends());
        friends.add(new PlayerPreferences.Friend(friend, name));
        players.put(player, new PlayerPreferences(player, friends, prefs.forgotten()));
        setDirty();
        return true;
    }

    public void removeFriend(UUID player, UUID friend) {
        var prefs = preferences(player);
        var friends = prefs.friends().stream().filter(value -> !value.id().equals(friend)).toList();
        if (!friends.equals(prefs.friends())) {
            players.put(player, new PlayerPreferences(player, friends, prefs.forgotten()));
            setDirty();
        }
    }

    public void clearFriends(UUID player) {
        var prefs = preferences(player);
        if (!prefs.friends().isEmpty()) {
            players.put(player, new PlayerPreferences(player, List.of(), prefs.forgotten()));
            setDirty();
        }
    }

    public boolean share(UUID id, UUID actor) {
        var entry = find(id);
        if (entry == null || !entry.users().contains(actor)) return false;
        var users = new HashSet<>(entry.users());
        preferences(actor).friends().forEach(friend -> users.add(friend.id()));
        return update(id, value -> value.withUsers(users));
    }

    public boolean forget(UUID id, UUID player) {
        var entry = find(id);
        if (entry == null || !entry.missing() || !entry.canUse(player)) return false;
        var prefs = preferences(player);
        var forgotten = new HashSet<>(prefs.forgotten());
        if (forgotten.add(id)) {
            players.put(player, new PlayerPreferences(player, prefs.friends(), forgotten));
            setDirty();
        }
        return true;
    }
}
