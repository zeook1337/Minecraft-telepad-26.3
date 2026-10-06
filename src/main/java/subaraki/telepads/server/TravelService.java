package subaraki.telepads.server;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import subaraki.telepads.TelepadConfig;
import subaraki.telepads.block.TelepadBlockEntity;
import subaraki.telepads.data.*;
import subaraki.telepads.network.TelepadNetwork;
import java.util.*;

public final class TravelService {
    private static final Map<MinecraftServer, ActivationSessions> SESSIONS = new WeakHashMap<>();
    public static ActivationSessions sessions(MinecraftServer server) { return SESSIONS.computeIfAbsent(server, ignored -> new ActivationSessions()); }
    public static void initialize() {
        ServerStoppedEvent.BUS.addListener(event -> SESSIONS.remove(event.getServer()));
        PlayerEvent.PlayerLoggedOutEvent.BUS.addListener(event -> clear(event.getEntity()));
        PlayerEvent.PlayerChangedDimensionEvent.BUS.addListener(event -> clear(event.getEntity()));
        LivingDeathEvent.BUS.addListener(event -> { clear(event.getEntity()); });
        TickEvent.ServerTickEvent.Post.BUS.addListener(event -> {
            sessions(event.server()).expire(event.server().overworld().getGameTime());
            for (var player : event.server().getPlayerList().getPlayers()) tick(player);
        });
    }
    private static void clear(net.minecraft.world.entity.Entity entity) {
        if (entity instanceof ServerPlayer player) sessions(player.level().getServer()).clear(player.getUUID());
    }
    public static TelepadBlockEntity standingPad(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator() || player.isPassenger()) return null;
        var pos = BlockPos.containing(player.getX(), player.getY() - 0.01, player.getZ());
        if (!(player.level().getBlockEntity(pos) instanceof TelepadBlockEntity pad) || pad.identity() == null) return null;
        return Math.abs(player.getY() - (pos.getY() + 0.2)) <= 0.07 ? pad : null;
    }
    public static boolean dragonBlocked(ServerPlayer player) {
        if (!TelepadConfig.DRAGON_BLOCK.get() || !player.level().dimension().equals(Level.END)) return false;
        if (player.level().getDragons().stream().anyMatch(dragon -> dragon.isAlive())) return true;
        var fight = player.level().getDragonFight();
        // The fight persists the live dragon even while its entity's chunk is unloaded.
        // 26.3 exposes its codec and UUID, but no dragonKilled getter.
        return fight != null && fight.dragonUUID() != null && net.minecraft.world.level.dimension.end.EnderDragonFight.CODEC
            .encodeStart(com.mojang.serialization.JsonOps.INSTANCE, fight).result()
            .map(json -> !json.getAsJsonObject().get("dragon_killed").getAsBoolean()).orElse(false);
    }
    public static void tick(ServerPlayer player) {
        var server = player.level().getServer();
        var sessions = sessions(server);
        var pad = standingPad(player);
        if (pad == null || pad.refreshDisabled()) { sessions.clear(player.getUUID()); return; }
        long now = server.overworld().getGameTime();
        if (!sessions.ready(player.getUUID(), pad.identity(), now, TelepadConfig.WAIT_SECONDS.get() * 20)) return;
        if (dragonBlocked(player)) { message(player, "dragon_blocked"); return; }
        if (!pad.configured().isEmpty() && !TelepadConfig.DESTINATIONS.get().contains(pad.configured())) { pad.setConfigured(""); message(player, "invalid_configured"); }
        if (!pad.configured().isEmpty()) {
            try {
                var destination = ConfiguredDestination.parse(pad.configured()).resolve(server, player.getRandom());
                move(player, destination.level(), destination.pos(), new ExperienceCost(0, 0));
            } catch (IllegalArgumentException exception) {
                subaraki.telepads.Telepads.LOGGER.warn("Invalid configured Telepads destination '{}': {}", pad.configured(), exception.getMessage());
                message(player, "invalid_configured");
            }
            return;
        }
        var catalog = TelepadCatalog.get(server);
        var origin = catalog.find(pad.identity());
        if (origin == null || origin.missing()) return;
        var entries = catalog.visibleTo(player.getUUID()).stream()
            .filter(entry -> !entry.id().equals(origin.id()))
            .filter(entry -> origin.transmitter() || entry.location().dimension().equals(origin.location().dimension())).toList();
        var session = sessions.open(player.getUUID(), origin.id(), origin.location(), now, entries.stream().map(TelepadEntry::id).toList());
        sendView(player, session, 0, 0);
    }
    public static void sendView(ServerPlayer player, ActivationSessions.Session session, int dimensionIndex, int page) {
        var catalog = TelepadCatalog.get(player.level().getServer());
        var entries = catalog.visibleTo(player.getUUID()).stream().filter(entry -> session.offered().contains(entry.id()))
            .sorted(Comparator.comparing(TelepadEntry::name).thenComparing(entry -> entry.id().toString())).toList();
        var dimensions = entries.stream().map(entry -> entry.location().dimension()).distinct().sorted().toList();
        int dim = Math.clamp(dimensionIndex, 0, Math.max(0, dimensions.size() - 1));
        String dimension = dimensions.isEmpty() ? session.location().dimension() : dimensions.get(dim);
        var rows = entries.stream().filter(entry -> entry.location().dimension().equals(dimension)).toList();
        int pages = Math.max(1, (rows.size() + 63) / 64);
        int selectedPage = Math.clamp(page, 0, pages - 1);
        var slice = rows.stream().skip(selectedPage * 64L).limit(64).map(entry -> new TelepadNetwork.TravelRow(entry.id(), entry.name(), entry.missing() ? 2 : entry.disabled() ? 1 : 0)).toList();
        if (player.connection != null) TelepadNetwork.send(player, new TelepadNetwork.TravelView(session.token(), dimension, dim, Math.max(1, dimensions.size()), selectedPage, pages, slice));
    }
    public static boolean handle(ServerPlayer player, TelepadNetwork.TravelRequest request) {
        var server = player.level().getServer();
        var sessions = sessions(server);
        if (request.action() == 0) { sessions.cancel(player.getUUID(), request.activation()); return true; }
        var session = sessions.find(player.getUUID(), request.activation(), server.overworld().getGameTime());
        var originPad = standingPad(player);
        if (session == null || originPad == null || !session.origin().equals(originPad.identity())
            || !session.location().equals(subaraki.telepads.block.TelepadBlock.location(player.level(), originPad.getBlockPos()))) return fail(player, "invalid_activation");
        var catalog = TelepadCatalog.get(server);
        var origin = catalog.find(session.origin());
        if (origin == null || origin.missing() || !originPad.configured().isEmpty() || originPad.refreshDisabled() || dragonBlocked(player)) return fail(player, "origin_blocked");
        if (request.action() == 1) { sendView(player, session, request.dimension(), request.page()); return true; }
        if (request.action() < 2 || request.action() > 4 || !session.offered().contains(request.destination())) return fail(player, "invalid_destination");
        var destination = catalog.find(request.destination());
        if (destination == null || !destination.canUse(player.getUUID()) || catalog.preferences(player.getUUID()).forgotten().contains(destination.id())) return fail(player, "access_changed");
        if (!origin.transmitter() && !destination.location().dimension().equals(origin.location().dimension())) return fail(player, "transmitter_required");
        if (request.action() == 4) {
            if (!destination.missing()) return fail(player, "invalid_destination");
            catalog.forget(destination.id(), player.getUUID());
            sendView(player, session, request.dimension(), request.page());
            return true;
        }
        if (destination.missing() && request.action() != 3) return fail(player, "confirm_missing");
        sessions.consume(player.getUUID());
        return travel(player, destination, true, destination.missing());
    }
    public static ServerLevel dimension(MinecraftServer server, String id) {
        var identifier = Identifier.tryParse(id);
        return identifier == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, identifier));
    }
    public static BlockPos pos(TelepadLocation location) { return new BlockPos(location.x(), location.y(), location.z()); }
    public static boolean travel(ServerPlayer player, TelepadEntry destination, boolean chargeXp, boolean allowMissing) {
        var server = player.level().getServer();
        var current = TelepadCatalog.get(server).find(destination.id());
        if (current == null || !current.canUse(player.getUUID())) return fail(player, "access_changed");
        var level = dimension(server, current.location().dimension());
        if (level == null) return fail(player, "dimension_missing");
        var pos = pos(current.location());
        if (!level.isInsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)) return fail(player, "no_arrival");
        level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (!current.missing()) {
            if (!(level.getBlockEntity(pos) instanceof TelepadBlockEntity pad) || !current.id().equals(pad.identity())) return fail(player, "destination_missing");
            if (pad.refreshDisabled()) return fail(player, "destination_disabled");
        } else if (!allowMissing) return fail(player, "destination_missing");
        var cost = chargeXp ? new ExperienceCost(TelepadConfig.XP_LEVELS.get(), TelepadConfig.XP_POINTS.get()) : new ExperienceCost(0, 0);
        return move(player, level, pos, cost);
    }
    public static boolean move(ServerPlayer player, ServerLevel level, BlockPos pos, ExperienceCost cost) {
        if (!cost.affordable(player)) return fail(player, "insufficient_xp");
        var arrival = SafeArrival.find(level, player, pos);
        if (arrival.isEmpty()) return fail(player, "no_arrival");
        var target = arrival.get();
        // ServerPlayer.teleportTo invokes Forge's cancellable travel event in this MDK, including same-dimension travel.
        if (!player.teleportTo(level, target.x, target.y, target.z, Set.of(), player.getYRot(), player.getXRot(), true)) return fail(player, "travel_canceled");
        player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        player.fallDistance = 0;
        cost.charge(player);
        return true;
    }
    public static boolean fail(ServerPlayer player, String key) { message(player, key); return false; }
    public static void message(ServerPlayer player, String key) { if (player.connection != null) player.sendSystemMessage(Component.translatable("message.telepads." + key), true); }
}
