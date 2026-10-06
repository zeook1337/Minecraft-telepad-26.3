package subaraki.telepads.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import subaraki.telepads.data.TelepadCatalog;
import subaraki.telepads.network.TelepadNetwork;

public final class FriendsService {
    private FriendsService() {}
    public static void handle(ServerPlayer player, TelepadNetwork.FriendRequest request) {
        var server = player.level().getServer();
        var catalog = TelepadCatalog.get(server);
        switch (request.action()) {
            case 0 -> {}
            case 1 -> {
                var friend = server.getPlayerList().getPlayerByName(request.name());
                if (friend == null || !catalog.addFriend(player.getUUID(), friend.getUUID(), friend.getGameProfile().name())) {
                    if (player.connection != null) player.sendSystemMessage(Component.translatable("message.telepads.friend_rejected"));
                }
            }
            case 2 -> catalog.removeFriend(player.getUUID(), request.friend());
            case 3 -> catalog.clearFriends(player.getUUID());
            default -> { return; }
        }
        if (player.connection != null) TelepadNetwork.send(player, new TelepadNetwork.FriendsView(catalog.preferences(player.getUUID()).friends()));
    }
}
