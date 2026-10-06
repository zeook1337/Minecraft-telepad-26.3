package telepads.fixture;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import subaraki.telepads.block.*;
import subaraki.telepads.data.*;
import subaraki.telepads.Telepads;
import subaraki.telepads.TelepadConfig;
import java.nio.file.*;
import java.util.*;

/** Packaged separately as a fixture mod: the original Telepads binary stays untouched. */
@Mod("telepads_fixture")
@Mod.EventBusSubscriber(modid = "telepads_fixture")
public final class ReleaseBaselineFixture {
    private static boolean done;
    private static final UUID OWNER = UUID.nameUUIDFromBytes("OfflinePlayer:TelepadAlice".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    private static final UUID FRIEND = UUID.nameUUIDFromBytes("OfflinePlayer:TelepadBob".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    private static final UUID STRANGER = UUID.nameUUIDFromBytes("OfflinePlayer:TelepadCarol".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    static JsonElement normalize(JsonElement value) {
        if (value.isJsonObject()) for (var entry : value.getAsJsonObject().entrySet()) {
            normalize(entry.getValue());
            if (entry.getKey().equals("users") || entry.getKey().equals("forgotten")) {
                var sorted = new JsonArray();
                java.util.stream.StreamSupport.stream(entry.getValue().getAsJsonArray().spliterator(), false)
                    .sorted(Comparator.comparing(JsonElement::toString)).forEach(sorted::add);
                entry.setValue(sorted);
            }
        } else if (value.isJsonArray()) value.getAsJsonArray().forEach(ReleaseBaselineFixture::normalize);
        return value;
    }

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent.Post event) throws Exception {
        String mode = System.getProperty("telepads.fixtureMode", "");
        if (done || mode.isEmpty() || event.server().getTickCount() < 40) return;
        done = true;
        var server = event.server(); var catalog = TelepadCatalog.get(server);
        if (mode.equals("empty")) {
            if (!catalog.entries().isEmpty() || !catalog.visibleTo(OWNER).isEmpty()
                || !catalog.preferences(OWNER).friends().isEmpty() || !catalog.preferences(OWNER).forgotten().isEmpty())
                throw new IllegalStateException("New world imported baseline data");
            Telepads.LOGGER.info("RELEASE_NEW_WORLD_PASS: catalog and preferences empty");
            return;
        }
        boolean alpha = Boolean.getBoolean("telepads.fixturePublic");
        if (mode.equals("create")) {
            if (!catalog.entries().isEmpty()) throw new IllegalStateException("Fixture world must start empty");
            TelepadConfig.DESTINATIONS.set(List.of("32/100/0/minecraft:overworld/Fixed"));
            TelepadConfig.DESTINATIONS.save();
            catalog.addFriend(OWNER, FRIEND, "TelepadBob");
            for (int i = 0; i < 8; i++) {
                var level = server.getLevel(i == 6 ? Level.NETHER : i == 7 ? Level.END : Level.OVERWORLD);
                var pos = new BlockPos(i * 16, 100, 0);
                level.getChunkAt(pos);
                for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
                    level.setBlockAndUpdate(pos.offset(x, -1, z), Blocks.STONE.defaultBlockState());
                    for (int y = 0; y < 4; y++) level.setBlockAndUpdate(pos.offset(x, y, z), Blocks.AIR.defaultBlockState());
                }
                level.setBlockAndUpdate(pos, Telepads.TELEPAD_BLOCK.get().defaultBlockState());
                var entry = catalog.place(TelepadBlock.location(level, pos), OWNER);
                String name = "Baseline" + i;
                catalog.update(entry.id(), v -> v.rename(name));
                var pad = (TelepadBlockEntity)level.getBlockEntity(pos);
                pad.setIdentity(entry.id(), name);
                if (i == 0) catalog.update(entry.id(), v -> v.register(FRIEND, true));
                if (i == 1 && alpha) catalog.update(entry.id(), v -> v.withPublic(true));
                if (i == 2) pad.setColors(new PadColors(14, 11));
                if (i == 3) pad.setColors(PadColors.crafted(0x804080, List.of(14,11,14,11,14,11,14,11)));
                if (i == 4) {
                    pad.setColors(PadColors.crafted(0x804080, List.of(14,11,14,11,14,11,14,11)).dyed(5));
                    pad.install(true); pad.install(false); pad.setConfigured("32/100/0/minecraft:overworld/Fixed");
                }
                if (i == 5) { level.removeBlock(pos, false); catalog.forget(entry.id(), OWNER); }
                if (i >= 6) pad.install(true);
            }
        }
        if (catalog.entries().size() != 8) throw new IllegalStateException("Fixture entries lost");
        var legacy = catalog.entries().stream().filter(v -> v.name().equals("Baseline0")).findFirst().orElseThrow();
        if (legacy.publicAccess() || !legacy.users().equals(Set.of(OWNER, FRIEND)) || legacy.canUse(STRANGER)) throw new IllegalStateException("Private registrations changed");
        var visible = catalog.visibleTo(STRANGER);
        if (visible.size() != (alpha ? 1 : 0) || visible.stream().anyMatch(v -> !v.name().equals("Baseline1"))) throw new IllegalStateException("Public permissions changed");
        var futureVisible = catalog.visibleTo(UUID.fromString("b493effb-740a-429a-89db-dba482d8c548"));
        if (!futureVisible.equals(visible)) throw new IllegalStateException("Future-user authorization changed");
        JsonObject state = new JsonObject();
        state.add("catalog", TelepadCatalog.CODEC.encodeStart(JsonOps.INSTANCE, catalog).getOrThrow());
        JsonArray blocks = new JsonArray();
        for (var entry : catalog.entries().stream().sorted(Comparator.comparing(TelepadEntry::name)).toList()) {
            var location = entry.location();
            var level = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, net.minecraft.resources.Identifier.parse(location.dimension())));
            var pos = new BlockPos(location.x(), location.y(), location.z()); level.getChunkAt(pos);
            if (entry.missing()) {
                if (level.getBlockEntity(pos) instanceof TelepadBlockEntity) throw new IllegalStateException("Missing pad reappeared");
                continue;
            }
            var pad = (TelepadBlockEntity)level.getBlockEntity(pos);
            if (pad == null || !entry.id().equals(pad.identity())) throw new IllegalStateException("Physical identity changed");
            JsonObject block = new JsonObject();
            block.addProperty("id", pad.identity().toString()); block.addProperty("name", pad.padName());
            block.addProperty("toggler", pad.toggler()); block.addProperty("transmitter", pad.transmitter());
            block.addProperty("configured", pad.configured());
            block.add("colors", PadColors.CODEC.encodeStart(JsonOps.INSTANCE, pad.colors()).getOrThrow());
            block.add("washReceipt", JSON.toJsonTree(pad.colors().recoveredDyes()));
            blocks.add(block);
        }
        state.add("blocks", blocks);
        state.add("configuredDestinations", JSON.toJsonTree(TelepadConfig.DESTINATIONS.get()));
        Path expected = Path.of(System.getProperty("telepads.fixtureManifest"));
        normalize(state);
        if (mode.equals("create")) Files.writeString(expected, JSON.toJson(state));
        else if (!normalize(JsonParser.parseString(Files.readString(expected))).equals(state)) {
            Files.writeString(expected.resolveSibling("observed-state.json"), JSON.toJson(state));
            throw new IllegalStateException("Fixture differs from original manifest");
        }
        Telepads.LOGGER.info("RELEASE_BASELINE_FIXTURE_PASS mode={} public={}: catalog, identities, friends, missing/forgotten, palettes, wash receipts, upgrades, configured destinations", mode, alpha);
    }
}
