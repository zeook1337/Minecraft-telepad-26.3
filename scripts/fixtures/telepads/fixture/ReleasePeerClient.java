package telepads.fixture;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/** Independent UI automation usable with untouched old and new Telepads packages. */
@Mod.EventBusSubscriber(modid = "telepads_fixture", value = Dist.CLIENT)
public final class ReleasePeerClient {
    private static final Logger LOG = LogUtils.getLogger();
    private static int ticks, stage;
    private static long lastStep;
    @SubscribeEvent public static void tick(TickEvent.RenderTickEvent.Post event) {
        if (!Boolean.getBoolean("telepads.releasePeer")) return;
        long now = System.nanoTime();
        if (now-lastStep < 50_000_000L) return;
        lastStep=now;
        var client = Minecraft.getInstance(); ticks++;
        client.options.pauseOnLostFocus = false;
        if (stage==0 && (ticks==1 || ticks%200==0)) {
            LOG.info("RELEASE_PEER_WAIT ticks={} screen={} overlay={}",ticks,client.gui.screen(),client.gui.overlay());
            if (ticks==200) net.minecraft.client.Screenshot.grab(client.gameDirectory,"release-peer-wait.png",client.gameRenderer.mainRenderTarget(),1,ignored->{});
        }
        boolean reject = Boolean.getBoolean("telepads.peerReject");
        if (reject && (client.player != null || client.level != null)) throw new IllegalStateException("Incompatible peer entered world");
        if (stage == 0 && ticks > 100 && client.gui.overlay() == null && client.gui.screen() != null) {
            client.options.pauseOnLostFocus = false;
            String address = System.getProperty("telepads.peerAddress");
            ConnectScreen.startConnecting(new TitleScreen(), client,
                net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(address),
                new net.minecraft.client.multiplayer.ServerData("Local release compatibility", address, net.minecraft.client.multiplayer.ServerData.Type.OTHER), false, null);
            stage = 1; ticks = 0;
        }
        if (stage == 1 && client.gui.screen() instanceof DisconnectedScreen screen) {
            String reason = screen.getNarrationMessage().getString();
            if (!reject || (!reason.toLowerCase(java.util.Locale.ROOT).contains("mismatch") && !reason.toLowerCase(java.util.Locale.ROOT).contains("incompatib")))
                throw new IllegalStateException("Unexpected disconnect: " + reason);
            LOG.info("RELEASE_PEER_REJECTION_PASS before world entry: {}",reason); stage = 2; ticks = 0;
        }
        if (stage == 1 && !reject && client.player != null && client.level != null && client.gui.overlay() == null) {
            String sharing = net.minecraft.network.chat.Component.translatable("screen.telepads.share").getString();
            if (!sharing.equals("Share with server")) throw new IllegalStateException("Incorrect candidate sharing label: "+sharing);
            LOG.info("RELEASE_PEER_MATCH_PASS: real Forge client entered world; Share with server"); stage = 2; ticks = 0;
        }
        if (stage == 2 && ticks > 40) { client.stop(); stage = 3; }
        if (stage < 2 && ticks > 1800) throw new IllegalStateException("Release peer timed out");
    }
}
