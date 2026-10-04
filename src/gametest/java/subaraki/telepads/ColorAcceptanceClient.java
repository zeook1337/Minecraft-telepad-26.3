package subaraki.telepads;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import subaraki.telepads.block.TelepadBlockEntity;
import subaraki.telepads.data.PadColors;
import java.nio.file.*;
import java.util.*;

@Mod.EventBusSubscriber(modid = "telepads", value = Dist.CLIENT)
public final class ColorAcceptanceClient {
    private static int ticks, stage, lastPhase = -1;
    private static final Set<Integer> previews = new HashSet<>();
    private static String user;
    private static void require(boolean valid, String message) { if (!valid) throw new IllegalStateException("Color client: " + message); }
    private static void capture(Minecraft client, String name) { Screenshot.grab(client.gameDirectory, "colors-" + name + ".png", client.gameRenderer.mainRenderTarget(), 1, ignored -> {}); }
    private static void connect(Minecraft client) {
        net.minecraft.client.gui.screens.ConnectScreen.startConnecting(new TitleScreen(), client,
            net.minecraft.client.multiplayer.resolver.ServerAddress.parseString("127.0.0.1:25577"),
            new net.minecraft.client.multiplayer.ServerData("Telepad colors", "127.0.0.1:25577", net.minecraft.client.multiplayer.ServerData.Type.OTHER), false, null);
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("telepads.colorAcceptance")) return;
        var client = Minecraft.getInstance(); ticks++;
        if (stage == 0 && ticks > 100 && client.gui.overlay() == null && client.gui.screen() != null) {
            user = client.getUser().getName(); client.options.pauseOnLostFocus = false;
            TelepadConfig.PARTICLES.set(false);
            client.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            Telepads.LOGGER.info("COLOR_CLIENT_START {}", user); connect(client); stage = 1; ticks = 0;
        }
        if (Boolean.getBoolean("telepads.colorReject")) {
            require(client.player == null && client.level == null, "incompatible peer reached play/component synchronization");
            if (stage == 1 && client.gui.screen() instanceof DisconnectedScreen screen) {
                String reason = screen.getNarrationMessage().getString();
                require(reason.toLowerCase(Locale.ROOT).contains("mismatch") || reason.toLowerCase(Locale.ROOT).contains("incompatible"), "unexpected rejection: " + reason);
                Telepads.LOGGER.info("COLOR_PROTOCOL_REJECTION_PASS {}: {}", user, reason); capture(client, "protocol-rejection"); stage = 5; ticks = 0;
            }
            if (stage == 5 && ticks > 30) client.stop();
            if (ticks > 1800) throw new IllegalStateException("Protocol rejection timed out");
            return;
        }
        if (stage == 1 && client.player != null && client.level != null && client.gui.overlay() == null) {
            client.gui.toastManager().clear();
            var marker = client.player.getInventory().getItem(8).get(DataComponents.CUSTOM_NAME);
            if (marker != null && marker.getString().startsWith("color-phase-")) {
                int phase = Integer.parseInt(marker.getString().substring(12));
                if (phase != lastPhase) { lastPhase = phase; ticks = 0; }
                if (ticks > 30 && !previews.contains(phase) && client.gui.screen() instanceof CraftingScreen screen) {
                    var expected = phase == 0 ? PadColors.DEFAULT : phase == 1 ? PadColors.crafted(0xb02e26, Collections.nCopies(8, 14)) : ColorGameTests.mixedPad().get(Telepads.COLORS.get());
                    var output = screen.getMenu().getSlot(0).getItem();
                    require(output.is(Telepads.TELEPAD.get()) && output.getOrDefault(Telepads.COLORS.get(), PadColors.DEFAULT).equals(expected), "crafting preview sync phase " + phase);
                    require(client.player.getInventory().getItem(phase).getOrDefault(Telepads.COLORS.get(), PadColors.DEFAULT).equals(expected), "inventory sync phase " + phase);
                    capture(client, "preview-" + phase); previews.add(phase);
                    Telepads.LOGGER.info("COLOR_PREVIEW_PASS {} phase={} frame={} rim={}", user, phase, Integer.toHexString(expected.color(0)), Integer.toHexString(expected.color(1)));
                }
            }
            if (previews.size() == 3 && client.gui.screen() == null) {
                for (int i = 0; i < 5; i++) {
                    var entity = client.level.getBlockEntity(new BlockPos((i - 1) * 2, 100, 0));
                    var expected = i == 4 ? ColorGameTests.mixedPad().get(Telepads.COLORS.get()).dyed(5) : i == 3 ? new PadColors(14, 11) : i == 0 ? PadColors.DEFAULT : i == 1 ? PadColors.crafted(0xb02e26, Collections.nCopies(8, 14)) : ColorGameTests.mixedPad().get(Telepads.COLORS.get());
                    require(entity instanceof TelepadBlockEntity pad && pad.colors().equals(expected), "block sync fixture " + i);
                }
                client.player.setYRot(0); client.player.setXRot(25);
                stage = 2; ticks = 0;
            }
        }
        if (stage == 2 && ticks == 20) capture(client, "placed");
        if (stage == 2 && ticks == 40) client.gui.setScreen(new InventoryScreen(client.player));
        if (stage == 2 && ticks == 60) capture(client, "inventory");
        if (stage == 2 && ticks > 90) {
            try { var path = Path.of(System.getProperty("telepads.colorEvidence")); Files.createDirectories(path); Files.writeString(path.resolve(user + ".pass"), "All previews, inventory palettes and five placed fixtures match.\n"); }
            catch (java.io.IOException exception) { throw new RuntimeException(exception); }
            Telepads.LOGGER.info("COLOR_CLIENT_PASS {}", user); client.stop(); stage = 3;
        }
        if (ticks > 2400 && stage < 3) throw new IllegalStateException("Color client timed out stage=" + stage + " phase=" + lastPhase);
    }
}
