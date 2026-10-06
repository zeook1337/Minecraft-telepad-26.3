package subaraki.telepads.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record PlayerPreferences(UUID player, List<Friend> friends, Set<UUID> forgotten) {
    public record Friend(UUID id, String name) {
        public static final Codec<Friend> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(Friend::id),
            Codec.string(1, 16).fieldOf("name").forGetter(Friend::name)
        ).apply(instance, Friend::new));
    }

    public static final Codec<PlayerPreferences> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        UUIDUtil.CODEC.fieldOf("player").forGetter(PlayerPreferences::player),
        Friend.CODEC.listOf(0, 9).fieldOf("friends").forGetter(PlayerPreferences::friends),
        UUIDUtil.CODEC.listOf().fieldOf("forgotten").forGetter(value -> List.copyOf(value.forgotten))
    ).apply(instance, (player, friends, forgotten) -> new PlayerPreferences(player, friends, Set.copyOf(forgotten))));

    public PlayerPreferences {
        friends = List.copyOf(friends);
        forgotten = Set.copyOf(forgotten);
        if (friends.size() > 9 || friends.stream().map(Friend::id).distinct().count() != friends.size())
            throw new IllegalArgumentException("Friends must be distinct and limited to nine");
    }

    public static PlayerPreferences empty(UUID player) { return new PlayerPreferences(player, List.of(), Set.of()); }
}
