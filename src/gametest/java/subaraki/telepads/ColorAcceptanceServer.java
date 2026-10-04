package subaraki.telepads;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import subaraki.telepads.block.TelepadBlockEntity;
import subaraki.telepads.data.PadColors;
import subaraki.telepads.data.TelepadCatalog;
import java.util.*;

@Mod.EventBusSubscriber(modid = "telepads")
public final class ColorAcceptanceServer {
    private static boolean initialized;
    private static final Map<UUID, Session> sessions = new HashMap<>();
    private static class Session { final ServerPlayer player; int phase, ticks; Session(ServerPlayer player) { this.player = player; } }
    private static final BlockPos TABLE = new BlockPos(0, 100, -5);
    private static void require(boolean valid, String message) { if (!valid) throw new IllegalStateException("Color acceptance: " + message); }
    private static ItemStack pad(int phase) {
        if (phase == 2) return ColorGameTests.mixedPad();
        var result = new ItemStack(Telepads.TELEPAD.get());
        if (phase == 1) result.set(Telepads.COLORS.get(), PadColors.crafted(0xb02e26, Collections.nCopies(8, 14)));
        return result;
    }
    private static void preview(ServerPlayer player, int phase) {
        player.openMenu(new SimpleMenuProvider((id, inventory, owner) -> new CraftingMenu(id, inventory, ContainerLevelAccess.create(player.level(), TABLE)), Component.literal("Telepad " + phase)));
        var menu = (CraftingMenu)player.containerMenu;
        var items = phase == 0 ? List.of(new ItemStack(Items.GLASS), new ItemStack(Items.GLASS), new ItemStack(Items.GLASS), new ItemStack(Items.ENDER_PEARL), new ItemStack(Items.ENDER_PEARL), new ItemStack(Items.ENDER_PEARL), new ItemStack(Items.IRON_BLOCK), new ItemStack(Items.COMPASS), new ItemStack(Items.IRON_BLOCK)) : ColorGameTests.ring(new ItemStack(Telepads.TELEPAD.get()), phase == 2);
        for (int i = 0; i < 9; i++) menu.getSlot(i + 1).set(items.get(i));
        require(menu.getSlot(0).getItem().is(Telepads.TELEPAD.get()), "recipe preview missing");
        player.getInventory().setItem(8, new ItemStack(Items.STICK));
        player.getInventory().getItem(8).set(DataComponents.CUSTOM_NAME, Component.literal("color-phase-" + phase));
        menu.broadcastChanges(); player.inventoryMenu.broadcastChanges();
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent.Post event) {
        if (!Boolean.getBoolean("telepads.colorAcceptance")) return;
        var server = event.server(); var level = server.overworld();
        if (!initialized && !server.getPlayerList().getPlayers().isEmpty()) {
            var owner = server.getPlayerList().getPlayers().getFirst().getUUID();
            if (!Boolean.getBoolean("telepads.colorRestart")) {
                for (int x = -8; x <= 8; x++) for (int z = -8; z <= 5; z++) { level.setBlockAndUpdate(new BlockPos(x, 99, z), Blocks.STONE.defaultBlockState()); for (int y = 100; y <= 105; y++) level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState()); }
                for (int i = 0; i < 4; i++) {
                    var pos = new BlockPos((i - 1) * 2, 100, 0); level.setBlockAndUpdate(pos, Telepads.TELEPAD_BLOCK.get().defaultBlockState());
                    var entry = TelepadCatalog.get(server).place(subaraki.telepads.block.TelepadBlock.location(level, pos), owner);
                    var entity = (TelepadBlockEntity)level.getBlockEntity(pos); entity.setIdentity(entry.id(), "ColorFixture" + i);
                    entity.setColors(i == 3 ? new PadColors(14, 11) : pad(i).getOrDefault(Telepads.COLORS.get(), PadColors.DEFAULT));
                }
                level.setBlockAndUpdate(new BlockPos(6, 100, 0), Telepads.TELEPAD_BLOCK.get().defaultBlockState());
                ((TelepadBlockEntity)level.getBlockEntity(new BlockPos(6, 100, 0))).setColors(pad(2).get(Telepads.COLORS.get()).dyed(5));
            }
            for (int i = 0; i < 5; i++) {
                var pos = new BlockPos((i - 1) * 2, 100, 0); var entity = (TelepadBlockEntity)level.getBlockEntity(pos);
                var expected = i == 4 ? pad(2).get(Telepads.COLORS.get()).dyed(5) : i == 3 ? new PadColors(14, 11) : pad(i).getOrDefault(Telepads.COLORS.get(), PadColors.DEFAULT);
                require(entity != null && entity.colors().equals(expected), "persistent fixture " + i);
            }
            level.setBlockAndUpdate(TABLE, Blocks.CRAFTING_TABLE.defaultBlockState());
            TelepadConfig.WAIT_SECONDS.set(60);
            initialized = true; Telepads.LOGGER.info("COLOR_FIXTURE_PASS restart={}: default, uniform, mixed, legacy, partial", Boolean.getBoolean("telepads.colorRestart"));
        }
        for (var player : List.copyOf(server.getPlayerList().getPlayers())) {
            if (!player.getGameProfile().name().startsWith("Telepad")) continue;
            var session = sessions.get(player.getUUID());
            if (session == null || session.player != player) {
                session = new Session(player); sessions.put(player.getUUID(), session);
                player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL); player.getInventory().clearContent();
                for (int phase = 0; phase < 3; phase++) player.getInventory().setItem(phase, pad(phase));
                double x = player.getGameProfile().name().equals("TelepadBob") ? 3.5 : .5;
                player.teleportTo(level, x, 100, -3.5, Set.of(), 0, 25, true); preview(player, 0);
            }
            session.ticks++;
            if (session.ticks == 200 && session.phase < 2) { session.phase++; session.ticks = 0; preview(player, session.phase); }
            if (session.phase == 2 && session.ticks == 200) { player.closeContainer(); Telepads.LOGGER.info("COLOR_CLIENT_SERVER_PASS {}", player.getGameProfile().name()); }
        }
    }
}
