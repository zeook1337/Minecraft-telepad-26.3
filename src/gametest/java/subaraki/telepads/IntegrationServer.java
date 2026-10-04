package subaraki.telepads;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import subaraki.telepads.block.*;
import subaraki.telepads.data.*;
import subaraki.telepads.server.*;
import java.nio.file.*;
import java.util.*;

@Mod.EventBusSubscriber(modid = "telepads")
public final class IntegrationServer {
    private static int stage, ticks;
    private static UUID origin, bobOrigin, shared, nether, far, missing;
    private static TelepadEntry create(ServerLevel level, BlockPos pos, UUID owner, String name) {
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
            level.setBlockAndUpdate(pos.offset(x, -1, z), Blocks.STONE.defaultBlockState());
            for (int y = 0; y < 4; y++) level.setBlockAndUpdate(pos.offset(x, y, z), Blocks.AIR.defaultBlockState());
        }
        level.setBlockAndUpdate(pos, Telepads.TELEPAD_BLOCK.get().defaultBlockState());
        var catalog = TelepadCatalog.get(level.getServer()); var entry = catalog.place(TelepadBlock.location(level, pos), owner);
        catalog.update(entry.id(), value -> value.rename(name));
        ((TelepadBlockEntity)level.getBlockEntity(pos)).setIdentity(entry.id(), name);
        return catalog.find(entry.id());
    }
    private static boolean near(ServerPlayer player, TelepadEntry entry) {
        return player.level().dimension().identifier().toString().equals(entry.location().dimension()) && player.distanceToSqr(entry.location().x() + .5, entry.location().y() + .2, entry.location().z() + .5) < 16;
    }
    private static void put(ServerPlayer player, TelepadEntry entry) {
        player.teleportTo(TravelService.dimension(player.level().getServer(), entry.location().dimension()), entry.location().x() + .5, entry.location().y() + .2, entry.location().z() + .5, Set.of(), 0, 0, true);
        TravelService.sessions(player.level().getServer()).clear(player.getUUID());
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException("Telepads integration: " + message); }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent.Post event) {
        if (!Boolean.getBoolean("telepads.integration") || stage == 10) return;
        ticks++;
        var server = event.server(); var catalog = TelepadCatalog.get(server);
        var alice = server.getPlayerList().getPlayerByName("TelepadAlice"); var bob = server.getPlayerList().getPlayerByName("TelepadBob");
        if (alice == null || bob == null) return;
        if (stage == 0) {
            if (Boolean.getBoolean("telepads.integrationRestart")) {
                require(catalog.preferences(alice.getUUID()).friends().size() == 1, "friends did not survive restart");
                require(catalog.visibleTo(bob.getUUID()).stream().anyMatch(entry -> entry.name().equals("SharedHome")), "shared access did not survive restart");
                require(catalog.entries().stream().anyMatch(entry -> entry.name().equals("MissingHome") && entry.missing()), "missing state did not survive restart");
                var savedOrigin = catalog.entries().stream().filter(entry -> entry.name().equals("AliceOrigin")).findFirst().orElseThrow();
                server.overworld().getChunk(savedOrigin.location().x() >> 4, savedOrigin.location().z() >> 4);
                require(server.overworld().getBlockEntity(TravelService.pos(savedOrigin.location())) instanceof TelepadBlockEntity pad && pad.transmitter() && savedOrigin.id().equals(pad.identity()), "physical upgrade and identity did not survive restart");
                var savedShared = catalog.entries().stream().filter(entry -> entry.name().equals("SharedHome")).findFirst().orElseThrow();
                server.overworld().getChunk(savedShared.location().x() >> 4, savedShared.location().z() >> 4);
                require(server.overworld().getBlockEntity(TravelService.pos(savedShared.location())) instanceof TelepadBlockEntity pad && pad.colors().equals(new PadColors(14, 11)) && pad.toggler(), "colors/toggler did not survive restart");
                put(bob, savedShared);
                Telepads.LOGGER.info("INTEGRATION_RESTART_PASS: catalog, friends, shared users, missing state survived dedicated-server restart"); stage = 10; return;
            }
            TelepadConfig.XP_LEVELS.set(2); TelepadConfig.XP_POINTS.set(99999);
            alice.setGameMode(net.minecraft.world.level.GameType.SURVIVAL); bob.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            alice.giveExperienceLevels(-alice.experienceLevel); bob.giveExperienceLevels(-bob.experienceLevel);
            alice.getInventory().clearContent(); bob.getInventory().clearContent();
            alice.giveExperienceLevels(10); bob.giveExperienceLevels(10);
            var items = Telepads.ITEMS.getEntries().stream().map(item -> new net.minecraft.world.item.ItemStack(item.get())).toList();
            for (int i = 0; i < items.size(); i++) { alice.getInventory().setItem(i, items.get(i).copy()); bob.getInventory().setItem(i, items.get(i).copy()); }
            origin = create(server.overworld(), new BlockPos(0, 100, 0), alice.getUUID(), "AliceOrigin").id();
            bobOrigin = create(server.overworld(), new BlockPos(24, 100, 0), bob.getUUID(), "BobOrigin").id();
            shared = create(server.overworld(), new BlockPos(12, 100, 0), alice.getUUID(), "Pending").id();
            create(server.overworld(), new BlockPos(40, 100, 0), alice.getUUID(), "AliceSecret");
            nether = create(server.getLevel(Level.NETHER), new BlockPos(0, 100, 0), alice.getUUID(), "NetherHome").id();
            far = create(server.overworld(), new BlockPos(2048, 100, 2048), alice.getUUID(), "FarHome").id();
            missing = create(server.overworld(), new BlockPos(2060, 100, 2048), alice.getUUID(), "MissingHome").id();
            server.overworld().removeBlock(TravelService.pos(catalog.find(missing).location()), false);
            // Keep saved login positions from holding the far destination loaded on repeat runs.
            alice.teleportTo(server.overworld(), 2.5, 100, 2.5, Set.of(), 0, 0, true);
            bob.teleportTo(server.overworld(), 2.5, 100, 2.5, Set.of(), 0, 0, true);
            stage = 1; ticks = 0;
        } else if (stage == 1 && catalog.preferences(alice.getUUID()).friends().size() == 1 && !server.overworld().hasChunkAt(TravelService.pos(catalog.find(far).location()))) {
            Telepads.LOGGER.info("INTEGRATION_UNLOADED_PASS: far destination chunk is unloaded before any trip");
            put(alice, catalog.find(shared)); NamingService.open(alice, catalog.find(shared)); stage = 2;
        } else if (stage == 2 && catalog.find(shared).name().equals("SharedHome") && catalog.find(shared).canUse(bob.getUUID())) {
            var sharedPad = (TelepadBlockEntity)server.overworld().getBlockEntity(TravelService.pos(catalog.find(shared).location()));
            sharedPad.install(true); sharedPad.install(false); sharedPad.setColors(new PadColors(14, 11));
            put(alice, catalog.find(origin)); put(bob, catalog.find(bobOrigin)); stage = 3; ticks = 0;
            Telepads.LOGGER.info("INTEGRATION_SHARED_PASS: friends and naming packets granted Bob access");
        } else if (stage == 3 && near(alice, catalog.find(shared)) && near(bob, catalog.find(shared))) {
            require(alice.experienceLevel == 8 && bob.experienceLevel == 8, "travel/replay must charge exactly two levels each");
            ((TelepadBlockEntity)server.overworld().getBlockEntity(TravelService.pos(catalog.find(origin).location()))).install(true);
            ((TelepadBlockEntity)server.getLevel(Level.NETHER).getBlockEntity(TravelService.pos(catalog.find(nether).location()))).install(true);
            put(alice, catalog.find(origin)); stage = 4; ticks = 0;
            Telepads.LOGGER.info("INTEGRATION_TRAVEL_PASS: two TCP clients traveled; private destinations hidden; forged/replayed requests caused no extra charge");
        } else if (stage == 4 && near(alice, catalog.find(nether))) {
            require(alice.experienceLevel == 6, "cross-dimension cost must be charged once"); stage = 5; ticks = 0;
            Telepads.LOGGER.info("INTEGRATION_DIMENSION_PASS: Alice arrived in the Nether");
        } else if (stage == 5 && near(alice, catalog.find(far))) {
            require(alice.experienceLevel == 4, "return cost must be charged once"); stage = 6; ticks = 0;
            Telepads.LOGGER.info("INTEGRATION_FAR_PASS: far destination arrived safely");
        } else if (stage == 6 && near(alice, catalog.find(missing))) {
            require(alice.experienceLevel == 2, "missing destination must charge exactly once");
            alice.getInventory().setItem(1, new net.minecraft.world.item.ItemStack(Telepads.BEAD.get(), 2));
            alice.getInventory().setItem(2, new net.minecraft.world.item.ItemStack(Telepads.NECKLACE.get(), 2));
            stage = 7; ticks = 0;
        } else if (stage == 7 && near(alice, catalog.find(far)) && alice.getInventory().getItem(2).getCount() == 1) {
            require(alice.experienceLevel == 2, "necklace must ignore configured XP cost");
            int string = 0; for (int slot = 0; slot < alice.getInventory().getContainerSize(); slot++) if (alice.getInventory().getItem(slot).is(net.minecraft.world.item.Items.STRING)) string += alice.getInventory().getItem(slot).getCount();
            require(string >= 1 && string <= 2, "necklace must return one or two string");
            Telepads.LOGGER.info("INTEGRATION_NECKLACE_PASS: real client used necklace, nearest valid pad, one consumed, string returned, no XP"); stage = 8;
        } else if (stage == 8 && alice.getInventory().getItem(1).getCount() == 1) {
            require(alice.experienceLevel == 2 && catalog.visibleTo(alice.getUUID()).stream().anyMatch(entry -> !entry.missing() && !entry.disabled() && near(alice, entry)), "bead must choose a valid accessible destination without XP");
            Telepads.LOGGER.info("INTEGRATION_BEAD_PASS: real client used bead, valid random destination, one consumed, no XP");
            try { Files.writeString(Path.of("integration-complete.txt"), "Two real Forge clients passed sharing, travel, cross-dimension, missing locations and portable items.\n"); } catch (java.io.IOException exception) { throw new RuntimeException(exception); }
            Telepads.LOGGER.info("INTEGRATION_SERVER_PASS: all real-client travel stages passed"); stage = 10;
        }
        if (ticks > 6000) throw new IllegalStateException("Integration server timed out at stage " + stage);
    }
}
