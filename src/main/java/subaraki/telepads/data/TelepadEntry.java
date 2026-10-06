package subaraki.telepads.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Immutable records prevent persistent changes from bypassing SavedData.setDirty(). */
public record TelepadEntry(UUID id, String name, TelepadLocation location, Set<UUID> users,
                           boolean publicAccess, boolean missing, boolean disabled, boolean transmitter) {
    public static final Codec<TelepadEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        UUIDUtil.CODEC.fieldOf("id").forGetter(TelepadEntry::id),
        Codec.string(1, 16).fieldOf("name").forGetter(TelepadEntry::name),
        TelepadLocation.CODEC.fieldOf("location").forGetter(TelepadEntry::location),
        UUIDUtil.CODEC.listOf().fieldOf("users").forGetter(entry -> List.copyOf(entry.users)),
        Codec.BOOL.fieldOf("public").forGetter(TelepadEntry::publicAccess),
        Codec.BOOL.fieldOf("missing").forGetter(TelepadEntry::missing),
        Codec.BOOL.fieldOf("disabled").forGetter(TelepadEntry::disabled),
        Codec.BOOL.fieldOf("transmitter").forGetter(TelepadEntry::transmitter)
    ).apply(instance, (id, name, location, users, pub, missing, disabled, transmitter) ->
        new TelepadEntry(id, name, location, Set.copyOf(users), pub, missing, disabled, transmitter)));

    public TelepadEntry {
        users = Set.copyOf(users);
        if (name.isBlank() || name.length() > 16) throw new IllegalArgumentException("Telepad name must contain 1–16 characters");
    }

    public boolean canUse(UUID player) { return publicAccess || users.contains(player); }

    public TelepadEntry rename(String value) {
        return new TelepadEntry(id, value, location, users, publicAccess, missing, disabled, transmitter);
    }

    public TelepadEntry withUsers(Set<UUID> value) {
        return new TelepadEntry(id, name, location, value, publicAccess, missing, disabled, transmitter);
    }

    public TelepadEntry register(UUID player, boolean registered) {
        var changed = new HashSet<>(users);
        if (registered) changed.add(player); else changed.remove(player);
        return withUsers(changed);
    }

    public TelepadEntry withState(boolean absent, boolean powered, boolean upgraded) {
        return new TelepadEntry(id, name, location, users, publicAccess, absent, powered, upgraded);
    }

    public TelepadEntry withPublic(boolean value) {
        return new TelepadEntry(id, name, location, users, value, missing, disabled, transmitter);
    }
}
