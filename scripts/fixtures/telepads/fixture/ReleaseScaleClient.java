package telepads.fixture;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import subaraki.telepads.Telepads;
import subaraki.telepads.network.TelepadNetwork;
import java.util.*;

/** Walks real pages, sends ordinary packets, uses portable items and reconnects. */
@Mod.EventBusSubscriber(modid = "telepads_fixture", value = Dist.CLIENT)
public final class ReleaseScaleClient {
    private static TelepadNetwork.TravelView pending;
    private static UUID activation;
    private static int phase, clientIndex, reconnectTicks, travelPages;
    private static boolean installed, usedPortable, reconnecting, reconnectStarted;
    private static long lastAction;
    private static void connect(Minecraft client) {
        String address=System.getProperty("telepads.peerAddress","127.0.0.1:25582");
        ConnectScreen.startConnecting(new TitleScreen(),client,
            net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(address),
            new net.minecraft.client.multiplayer.ServerData("Local release scale",address,net.minecraft.client.multiplayer.ServerData.Type.OTHER),false,null);
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("telepads.releaseScale")) return;
        var client=Minecraft.getInstance();
        client.options.pauseOnLostFocus=false;
        if (!installed) {
            clientIndex=Arrays.asList(ReleaseScaleServer.NAMES).indexOf(client.getUser().getName());
            if (clientIndex<0) throw new IllegalStateException("Use a ScaleAlice/Bob/Carol/Dave identity");
            TelepadNetwork.travelHandler=view -> {
                if (!view.activation().equals(activation)) { activation=view.activation(); travelPages=0; }
                pending=view;
            };
            installed=true;
        }
        if (reconnecting) {
            reconnectTicks++;
            if (client.gui.screen() instanceof DisconnectedScreen && reconnectTicks>40 && !reconnectStarted) { connect(client); reconnectStarted=true; }
            if (reconnectStarted && client.player!=null && client.level!=null && client.gui.overlay()==null) {
                reconnecting=false; phase=0; usedPortable=false;
            }
            return;
        }
        if (client.player==null || client.level==null) {
            if (client.gui.overlay()==null && client.gui.screen() instanceof TitleScreen) connect(client);
            return;
        }
        if (System.nanoTime()-lastAction<150_000_000L) return;
        lastAction=System.nanoTime();
        if (phase==4 || phase==5) {
            var item=phase==4 ? Telepads.NECKLACE.get() : Telepads.BEAD.get();
            if (!usedPortable && client.player.getInventory().getItem(0).is(item) && client.player.getInventory().getItem(0).getCount()==2) {
                client.player.getInventory().setSelectedSlot(0); client.gameMode.useItem(client.player,InteractionHand.MAIN_HAND); usedPortable=true;
            }
            if (usedPortable && client.player.getInventory().getItem(0).is(item) && client.player.getInventory().getItem(0).getCount()==1) { phase++; usedPortable=false; }
            return;
        }
        if (phase==7) {
            // Wait until the server has placed us back at the origin for this scheduled step.
            if (client.player.getZ()<-60 && client.player.getZ()>-68 && Math.abs(client.player.getX()-(clientIndex*16+.5))<1) {
                client.getConnection().getConnection().disconnect(Component.literal("Scheduled Telepads acceptance reconnect"));
                reconnecting=true; reconnectStarted=false; reconnectTicks=0;
            }
            return;
        }
        if (pending==null) return;
        var view=pending; pending=null; travelPages++;
        if (view.rows().stream().anyMatch(row -> row.name().startsWith("Private"))) throw new IllegalStateException("Unauthorized private row exposed");
        String target=phase==0 ? "S0997" : phase==1 ? "S0998" : phase==2 ? "Hazard"+clientIndex : "S0996";
        if (phase==3) {
            // An unoffered identity must reject and leave position, XP and items intact.
            TelepadNetwork.sendToServer(new TelepadNetwork.TravelRequest(view.activation(),2,UUID.fromString("72cf9c37-c4a8-4455-83e0-34a16c29607c"),0,0));
            TelepadNetwork.sendToServer(new TelepadNetwork.TravelRequest(view.activation(),0,new UUID(0,0),0,0)); phase++;
            return;
        }
        var row=view.rows().stream().filter(r -> r.name().equals(target)).findFirst();
        if (row.isPresent()) {
            int action=row.get().state()==2 ? 3 : 2;
            TelepadNetwork.sendToServer(new TelepadNetwork.TravelRequest(view.activation(),action,row.get().id(),view.dimensionIndex(),view.page()));
            // Replay the same token: only the first success may charge/consume.
            TelepadNetwork.sendToServer(new TelepadNetwork.TravelRequest(view.activation(),action,row.get().id(),view.dimensionIndex(),view.page()));
            Telepads.LOGGER.info("RELEASE_SCALE_CLIENT_OPERATION phase={} pages={}",phase,travelPages); phase++;
        } else {
            int dimension=view.dimensionIndex(), page=view.page()+1;
            if (page>=view.pages()) { dimension++; page=0; }
            if (dimension>=view.dimensions()) throw new IllegalStateException("Missing workload target "+target);
            TelepadNetwork.sendToServer(new TelepadNetwork.TravelRequest(view.activation(),1,new UUID(0,0),dimension,page));
        }
    }
}
