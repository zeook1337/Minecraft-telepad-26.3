package subaraki.telepads.server;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import subaraki.telepads.block.TelepadBlockEntity;
import subaraki.telepads.data.TelepadCatalog;
import subaraki.telepads.data.TelepadEntry;
import subaraki.telepads.network.TelepadNetwork;
import java.util.Map;
import java.util.WeakHashMap;

public final class NamingService {
    private static final Map<MinecraftServer, PlacementSessions> SESSIONS = new WeakHashMap<>();
    private NamingService() {}
    public static PlacementSessions sessions(MinecraftServer server) { return SESSIONS.computeIfAbsent(server, ignored -> new PlacementSessions()); }

    public static void initialize() {
        ServerStoppedEvent.BUS.addListener(event -> SESSIONS.remove(event.getServer()));
        PlayerEvent.PlayerLoggedOutEvent.BUS.addListener(event -> clear(event.getEntity()));
        PlayerEvent.PlayerChangedDimensionEvent.BUS.addListener(event -> clear(event.getEntity()));
        LivingDeathEvent.BUS.addListener(event -> { clear(event.getEntity()); });
        TickEvent.ServerTickEvent.Post.BUS.addListener(event -> sessions(event.server()).expire(event.server().overworld().getGameTime()));
    }

    private static void clear(net.minecraft.world.entity.Entity entity) {
        if (entity instanceof ServerPlayer player) sessions(player.level().getServer()).clear(player.getUUID());
    }

    public static PlacementSessions.Placement open(ServerPlayer player, TelepadEntry entry) {
        var server = player.level().getServer();
        var placement = sessions(server).begin(player.getUUID(), entry.id(), entry.location(), server.overworld().getGameTime());
        if (player.connection != null) TelepadNetwork.send(player, new TelepadNetwork.NamePrompt(placement.token(), entry.name()));
        return placement;
    }

    public static boolean confirm(ServerPlayer player, TelepadNetwork.NameRequest request) {
        var server = player.level().getServer();
        var placement = sessions(server).find(player.getUUID(), request.activation(), server.overworld().getGameTime());
        String name = request.name().strip();
        if (placement == null || name.isEmpty() || name.length() > 16 || name.chars().anyMatch(Character::isISOControl)) return reject(player);
        var location = placement.location();
        var pos = new BlockPos(location.x(), location.y(), location.z());
        if (!player.level().dimension().identifier().toString().equals(location.dimension()) || player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64)
            return reject(player);
        if (!(player.level().getBlockEntity(pos) instanceof TelepadBlockEntity pad) || !placement.pad().equals(pad.identity())) return reject(player);
        var catalog = TelepadCatalog.get(server);
        var entry = catalog.find(placement.pad());
        if (entry == null || entry.missing() || !entry.users().contains(player.getUUID())) return reject(player);
        sessions(server).cancel(player.getUUID(), request.activation());
        catalog.update(entry.id(), value -> value.rename(name));
        if (request.share()) catalog.share(entry.id(), player.getUUID());
        pad.setIdentity(entry.id(), name);
        return true;
    }

    private static boolean reject(ServerPlayer player) {
        if (player.connection != null) player.sendSystemMessage(Component.translatable("message.telepads.invalid_name_context"));
        return false;
    }
}
