package subaraki.telepads;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import subaraki.telepads.network.TelepadNetwork;
import java.util.UUID;

/** Real TCP clients exercising the normal mod packets; included only in acceptance builds. */
@Mod.EventBusSubscriber(modid = "telepads", value = Dist.CLIENT)
public final class IntegrationClient {
    private static int ticks, stage, travel;
    private static int portable;
    private static boolean alice;
    private static UUID lastToken, lastDestination;
    private static TelepadNetwork.TravelView pending;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("telepads.integration")) return;
        var client = Minecraft.getInstance(); ticks++;
        if (stage == 0 && ticks > 100 && client.gui.overlay() == null && client.gui.screen() != null) {
            alice = client.getUser().getName().equals("TelepadAlice");
            client.options.pauseOnLostFocus = false;
            TelepadNetwork.nameHandler = prompt -> {
                var screen = new subaraki.telepads.client.NameTelepadScreen(prompt); client.gui.setScreen(screen);
                screen.children().stream().filter(child -> child instanceof net.minecraft.client.gui.components.EditBox).map(child -> (net.minecraft.client.gui.components.EditBox)child).findFirst().orElseThrow().setValue("SharedHome");
                screen.children().stream().filter(child -> child instanceof net.minecraft.client.gui.components.Checkbox).map(child -> (net.minecraft.client.gui.components.Checkbox)child).findFirst().orElseThrow().onPress(null);
                screen.children().stream().filter(child -> child instanceof net.minecraft.client.gui.components.Button).map(child -> (net.minecraft.client.gui.components.Button)child).findFirst().orElseThrow().onPress(null);
            };
            TelepadNetwork.friendsHandler = view -> {
                if (!alice && !view.friends().isEmpty()) throw new IllegalStateException("Bob must have an independent empty friend list");
                Telepads.LOGGER.info("INTEGRATION_FRIENDS {} {}", client.getUser().getName(), view.friends().size());
            };
            TelepadNetwork.travelHandler = view -> {
                if (Boolean.getBoolean("telepads.integrationRestart") || travel >= (alice ? 4 : 1)) { TelepadNetwork.sendToServer(new TelepadNetwork.TravelRequest(view.activation(), 0, new UUID(0, 0), 0, 0)); return; }
                if (client.gui.screen() instanceof subaraki.telepads.client.TravelScreen old && old.sameActivation(view.activation())) old.replacing();
                pending = view; ticks = 0; client.gui.setScreen(new subaraki.telepads.client.TravelScreen(view));
            };
            net.minecraft.client.gui.screens.ConnectScreen.startConnecting(new net.minecraft.client.gui.screens.TitleScreen(), client,
                net.minecraft.client.multiplayer.resolver.ServerAddress.parseString("127.0.0.1:25577"),
                new net.minecraft.client.multiplayer.ServerData("Telepads acceptance", "127.0.0.1:25577", net.minecraft.client.multiplayer.ServerData.Type.OTHER), false, null);
            stage = 1; ticks = 0;
        } else if (stage == 1 && client.player != null && client.level != null && client.gui.overlay() == null) {
            if (Boolean.getBoolean("telepads.integrationRestart")) { stage = 5; ticks = 0; client.gui.setScreen(null); }
            else {
            TelepadNetwork.sendToServer(new TelepadNetwork.FriendRequest(alice ? 1 : 0, new UUID(0, 0), alice ? "TelepadBob" : ""));
            stage = 2; ticks = 0;
            }
        } else if (stage == 2 && pending != null && ticks > 20) {
            var view = pending; pending = null;
            if (!alice && view.rows().stream().anyMatch(row -> row.name().equals("AliceSecret"))) throw new IllegalStateException("Private unknown destination leaked to Bob");
            String target = !alice || travel == 0 ? "SharedHome" : travel == 1 ? "NetherHome" : travel == 2 ? "FarHome" : "MissingHome";
            var row = view.rows().stream().filter(value -> value.name().equals(target)).findFirst();
            if (row.isEmpty()) {
                var direction = view.dimensionIndex() + 1 < view.dimensions() ? ">" : "<";
                client.gui.screen().children().stream().filter(child -> child instanceof net.minecraft.client.gui.components.Button button && button.getMessage().getString().equals(direction)).map(child -> (net.minecraft.client.gui.components.Button)child).findFirst().orElseThrow().onPress(null);
            } else {
                if (alice && travel == 0) TelepadNetwork.sendToServer(new TelepadNetwork.TravelRequest(view.activation(), 2, UUID.randomUUID(), 0, 0));
                lastToken = view.activation(); lastDestination = row.get().id();
                client.gui.screen().children().stream().filter(child -> child instanceof net.minecraft.client.gui.components.Button button && button.getMessage().getString().startsWith(target + " ·")).map(child -> (net.minecraft.client.gui.components.Button)child).findFirst().orElseThrow().onPress(null);
                if (row.get().state() == 2) ((net.minecraft.client.gui.components.Button)client.gui.screen().children().getFirst()).onPress(null);
                TelepadNetwork.sendToServer(new TelepadNetwork.TravelRequest(lastToken, 2, lastDestination, view.dimensionIndex(), view.page()));
                Screenshot.grab(client.gameDirectory, "integration-" + travel + ".png", client.gameRenderer.mainRenderTarget(), 1, message -> {});
                client.gui.setScreen(null); travel++; ticks = 0;
            }
        }
        if (stage == 2 && client.player == null && ticks > 100 && client.gui.screen() instanceof net.minecraft.client.gui.screens.DisconnectedScreen) { Telepads.LOGGER.info("INTEGRATION_CLIENT_EXIT {}", client.getUser().getName()); client.stop(); stage = 4; }
        if (stage == 5 && client.player != null && ticks == 40) { client.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK); client.player.setXRot(60); client.gui.setScreen(null); }
        if (stage == 5 && client.player != null && ticks == 60) Screenshot.grab(client.gameDirectory, "restart-platform.png", client.gameRenderer.mainRenderTarget(), 1, message -> {});
        if (stage == 5 && client.player == null && ticks > 100 && client.gui.screen() instanceof net.minecraft.client.gui.screens.DisconnectedScreen) { client.stop(); stage = 4; }
        if (!alice && stage == 2 && travel == 1 && ticks == 40 && client.player != null) { client.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK); client.player.setXRot(60); }
        if (!alice && stage == 2 && travel == 1 && ticks == 60 && client.player != null) Screenshot.grab(client.gameDirectory, "integration-platform.png", client.gameRenderer.mainRenderTarget(), 1, message -> {});
        if (alice && stage == 2 && travel == 4 && client.player != null && ticks > 40) {
            if (portable == 0 && client.player.getX() > 2056) {
                for (int slot = 0; slot < 9; slot++) if (client.player.getInventory().getItem(slot).is(Telepads.NECKLACE.get()) && client.player.getInventory().getItem(slot).getCount() == 2) {
                    client.player.getInventory().setSelectedSlot(slot); client.gameMode.useItem(client.player, net.minecraft.world.InteractionHand.MAIN_HAND); portable = 1; break;
                }
            } else if (portable == 1 && Math.abs(client.player.getX() - 2048.5) < 2) {
                for (int slot = 0; slot < 9; slot++) if (client.player.getInventory().getItem(slot).is(Telepads.BEAD.get()) && client.player.getInventory().getItem(slot).getCount() == 2) {
                    client.player.getInventory().setSelectedSlot(slot); client.gameMode.useItem(client.player, net.minecraft.world.InteractionHand.MAIN_HAND); portable = 2; break;
                }
            }
        }
        if (client.player != null && travel >= (alice ? 4 : 1) && (!alice || portable == 2) && ticks > (alice ? 100 : 1000)) {
            Screenshot.grab(client.gameDirectory, "integration-arrival.png", client.gameRenderer.mainRenderTarget(), 1, message -> {});
            Telepads.LOGGER.info("INTEGRATION_CLIENT_PASS {}", client.getUser().getName()); stage = 3;
        }
        if (stage == 3) { client.stop(); stage = 4; }
        if (ticks > 6000 && stage < 3) throw new IllegalStateException("Integration client timed out at stage " + stage + " travel " + travel);
    }
}
