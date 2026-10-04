package subaraki.telepads.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;
import subaraki.telepads.server.NamingService;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.List;
import java.util.ArrayList;
import subaraki.telepads.data.PlayerPreferences;
import subaraki.telepads.server.FriendsService;

public final class TelepadNetwork {
    public record NameRequest(UUID activation, String name, boolean share) {}
    public record CancelName(UUID activation) {}
    public record NamePrompt(UUID activation, String defaultName) {}
    public record FriendRequest(int action, UUID friend, String name) {}
    public record FriendsView(List<PlayerPreferences.Friend> friends) {}
    public record TravelRequest(UUID activation, int action, UUID destination, int dimension, int page) {}
    public record TravelRow(UUID id, String name, int state) {}
    public record TravelView(UUID activation, String dimension, int dimensionIndex, int dimensions, int page, int pages, List<TravelRow> rows) {}
    public static Consumer<NamePrompt> nameHandler = ignored -> {};
    public static Consumer<FriendsView> friendsHandler = ignored -> {};
    public static Consumer<TravelView> travelHandler = ignored -> {};
    private static SimpleChannel channel;

    public static final StreamCodec<RegistryFriendlyByteBuf, NameRequest> NAME_REQUEST = StreamCodec.of(
        (buf, value) -> { buf.writeUUID(value.activation); buf.writeUtf(value.name, 16); buf.writeBoolean(value.share); },
        buf -> new NameRequest(buf.readUUID(), buf.readUtf(16), buf.readBoolean()));
    public static final StreamCodec<RegistryFriendlyByteBuf, CancelName> CANCEL_NAME = StreamCodec.of(
        (buf, value) -> buf.writeUUID(value.activation), buf -> new CancelName(buf.readUUID()));
    public static final StreamCodec<RegistryFriendlyByteBuf, NamePrompt> NAME_PROMPT = StreamCodec.of(
        (buf, value) -> { buf.writeUUID(value.activation); buf.writeUtf(value.defaultName, 16); },
        buf -> new NamePrompt(buf.readUUID(), buf.readUtf(16)));
    public static final StreamCodec<RegistryFriendlyByteBuf, FriendRequest> FRIEND_REQUEST = StreamCodec.of(
        (buf, value) -> { buf.writeByte(value.action); buf.writeUUID(value.friend); buf.writeUtf(value.name, 16); },
        buf -> new FriendRequest(buf.readUnsignedByte(), buf.readUUID(), buf.readUtf(16)));
    public static final StreamCodec<RegistryFriendlyByteBuf, FriendsView> FRIENDS_VIEW = StreamCodec.of(
        (buf, value) -> {
            if (value.friends.size() > 9) throw new IllegalArgumentException("Too many friends");
            buf.writeVarInt(value.friends.size());
            value.friends.forEach(friend -> { buf.writeUUID(friend.id()); buf.writeUtf(friend.name(), 16); });
        }, buf -> {
            int size = buf.readVarInt();
            if (size < 0 || size > 9) throw new IllegalArgumentException("Invalid friend count");
            var friends = new ArrayList<PlayerPreferences.Friend>();
            for (int i = 0; i < size; i++) friends.add(new PlayerPreferences.Friend(buf.readUUID(), buf.readUtf(16)));
            return new FriendsView(List.copyOf(friends));
        });

    public static final StreamCodec<RegistryFriendlyByteBuf, TravelRequest> TRAVEL_REQUEST = StreamCodec.of(
        (buf, value) -> { buf.writeUUID(value.activation); buf.writeByte(value.action); buf.writeUUID(value.destination); buf.writeVarInt(value.dimension); buf.writeVarInt(value.page); },
        buf -> new TravelRequest(buf.readUUID(), buf.readUnsignedByte(), buf.readUUID(), buf.readVarInt(), buf.readVarInt()));
    public static final StreamCodec<RegistryFriendlyByteBuf, TravelView> TRAVEL_VIEW = StreamCodec.of(
        (buf, value) -> {
            if (value.rows.size() > 64) throw new IllegalArgumentException("Too many destinations in a page");
            buf.writeUUID(value.activation); buf.writeUtf(value.dimension, 256); buf.writeVarInt(value.dimensionIndex); buf.writeVarInt(value.dimensions);
            buf.writeVarInt(value.page); buf.writeVarInt(value.pages); buf.writeVarInt(value.rows.size());
            for (var row : value.rows) { buf.writeUUID(row.id); buf.writeUtf(row.name, 16); buf.writeByte(row.state); }
        }, buf -> {
            UUID activation = buf.readUUID(); String dimension = buf.readUtf(256);
            int index = buf.readVarInt(), dimensions = buf.readVarInt(), page = buf.readVarInt(), pages = buf.readVarInt(), count = buf.readVarInt();
            if (dimensions < 1 || pages < 1 || index < 0 || index >= dimensions || page < 0 || page >= pages || count < 0 || count > 64) throw new IllegalArgumentException("Invalid destination page");
            var rows = new ArrayList<TravelRow>();
            for (int i = 0; i < count; i++) { UUID id = buf.readUUID(); String name = buf.readUtf(16); int state = buf.readUnsignedByte(); if (state > 2) throw new IllegalArgumentException("Invalid destination state"); rows.add(new TravelRow(id, name, state)); }
            return new TravelView(activation, dimension, index, dimensions, page, pages, List.copyOf(rows));
        });

    public static void initialize() {
        channel = ChannelBuilder.named("telepads:main").networkProtocolVersion(2)
            .acceptedVersions(net.minecraftforge.network.Channel.VersionTest.exact(2)).simpleChannel();
        channel.play().serverbound()
            .addMain(NameRequest.class, NAME_REQUEST, (packet, context) -> {
                if (context.getSender() != null) NamingService.confirm(context.getSender(), packet);
            })
            .addMain(CancelName.class, CANCEL_NAME, (packet, context) -> {
                var player = context.getSender();
                if (player != null) NamingService.sessions(player.level().getServer()).cancel(player.getUUID(), packet.activation);
            })
            .addMain(FriendRequest.class, FRIEND_REQUEST, (packet, context) -> {
                if (context.getSender() != null) FriendsService.handle(context.getSender(), packet);
            })
            .addMain(TravelRequest.class, TRAVEL_REQUEST, (packet, context) -> {
                if (context.getSender() != null) subaraki.telepads.server.TravelService.handle(context.getSender(), packet);
            })
            .clientbound().addMain(NamePrompt.class, NAME_PROMPT, (packet, context) -> nameHandler.accept(packet))
            .addMain(FriendsView.class, FRIENDS_VIEW, (packet, context) -> friendsHandler.accept(packet))
            .addMain(TravelView.class, TRAVEL_VIEW, (packet, context) -> travelHandler.accept(packet))
            .build();
    }

    public static void send(ServerPlayer player, Object packet) { channel.send(packet, PacketDistributor.PLAYER.with(player)); }
    public static void sendToServer(Object packet) { channel.send(packet, PacketDistributor.SERVER.noArg()); }
}
