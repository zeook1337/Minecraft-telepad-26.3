package subaraki.telepads;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import subaraki.telepads.client.NameTelepadScreen;
import subaraki.telepads.client.FriendsScreen;
import subaraki.telepads.data.PlayerPreferences;
import subaraki.telepads.network.TelepadNetwork;
import java.io.File;
import java.util.ArrayList;
import java.util.UUID;

/** Opt-in graphical smoke checks, excluded from distribution builds. */
@Mod.EventBusSubscriber(modid = "telepads", value = Dist.CLIENT)
public final class ClientSmoke {
    private static final String PREFIX = "telepads-smoke-" + System.currentTimeMillis() + "-";
    private static int ticks;
    private static int stage;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("telepads.clientSmoke")) return;
        var client = Minecraft.getInstance();
        ticks++;
        if (stage == 0 && ticks > 100 && client.gui.screen() != null && client.gui.overlay() == null) {
            boolean keyPresent = java.util.Arrays.stream(client.options.keyMappings).anyMatch(key -> key.getName().equals("key.telepads.friends"));
            if (!keyPresent) throw new IllegalStateException("Friends key mapping was not registered");
            client.gui.setScreen(new NameTelepadScreen(new TelepadNetwork.NamePrompt(UUID.randomUUID(), "Telepad")));
            stage = 1;
            ticks = 0;
        } else if (stage == 1 && ticks > 40) {
            Screenshot.grab(client.gameDirectory, PREFIX + "name.png", client.gameRenderer.mainRenderTarget(), 1,
                message -> Telepads.LOGGER.info("Client smoke naming screenshot: {}", message.getString()));
            stage = 2;
        } else if (stage == 2 && new File(client.gameDirectory, "screenshots/" + PREFIX + "name.png").isFile()) {
            var friends = new ArrayList<PlayerPreferences.Friend>();
            for (int i = 0; i < 9; i++) friends.add(new PlayerPreferences.Friend(UUID.randomUUID(), "Friend" + i));
            client.gui.setScreen(new FriendsScreen(new TelepadNetwork.FriendsView(friends)));
            stage = 3;
            ticks = 0;
        } else if (stage == 3 && ticks > 40) {
            Screenshot.grab(client.gameDirectory, PREFIX + "friends.png", client.gameRenderer.mainRenderTarget(), 1,
                message -> Telepads.LOGGER.info("Client smoke friends screenshot: {}", message.getString()));
            stage = 4;
        } else if (stage == 4 && new File(client.gameDirectory, "screenshots/" + PREFIX + "friends.png").isFile()) {
            var rows = new ArrayList<TelepadNetwork.TravelRow>();
            for (int i = 0; i < 30; i++) rows.add(new TelepadNetwork.TravelRow(UUID.randomUUID(), "Destination" + i, i % 3));
            client.gui.setScreen(new subaraki.telepads.client.TravelScreen(new TelepadNetwork.TravelView(UUID.randomUUID(), "minecraft:overworld", 0, 2, 0, 2, rows)));
            stage = 5; ticks = 0;
        } else if (stage == 5 && ticks > 40) {
            Screenshot.grab(client.gameDirectory, PREFIX + "travel.png", client.gameRenderer.mainRenderTarget(), 1, message -> {});
            stage = 6;
        } else if (stage == 6 && new File(client.gameDirectory, "screenshots/" + PREFIX + "travel.png").isFile()) {
            for (int i = 0; i < 24; i++) client.gui.screen().mouseScrolled(0, 0, 0, -1);
            boolean scrolled = client.gui.screen().children().stream().anyMatch(child -> child instanceof net.minecraft.client.gui.components.Button button && button.getMessage().getString().startsWith("Destination24"));
            if (!scrolled) throw new IllegalStateException("Destination list did not scroll");
            stage = 7; ticks = 0;
        } else if (stage == 7 && ticks > 40) {
            Screenshot.grab(client.gameDirectory, PREFIX + "scroll.png", client.gameRenderer.mainRenderTarget(), 1, message -> {}); stage = 8;
        } else if (stage == 8 && new File(client.gameDirectory, "screenshots/" + PREFIX + "scroll.png").isFile()) {
            client.gui.setScreen(new subaraki.telepads.client.TravelScreen(new TelepadNetwork.TravelView(UUID.randomUUID(), "minecraft:overworld", 0, 1, 0, 1, java.util.List.of()))); stage = 9; ticks = 0;
        } else if (stage == 9 && ticks > 40) {
            Screenshot.grab(client.gameDirectory, PREFIX + "empty.png", client.gameRenderer.mainRenderTarget(), 1, message -> {}); stage = 10;
        } else if (stage == 10 && new File(client.gameDirectory, "screenshots/" + PREFIX + "empty.png").isFile()) {
            Telepads.LOGGER.info("TELEPADS_CLIENT_SMOKE_PASS: naming, nine friends, destination states, scrolling and empty screen; period key registered");
            stage = 11;
            client.stop();
        }
        if (ticks > 2400 && stage != 11) throw new IllegalStateException("Client smoke test timed out at stage " + stage);
    }
}
