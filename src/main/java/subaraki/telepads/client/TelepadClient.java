package subaraki.telepads.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import subaraki.telepads.Telepads;
import subaraki.telepads.network.TelepadNetwork;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.resources.Identifier;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = Telepads.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class TelepadClient {
    private static final KeyMapping FRIENDS = new KeyMapping("key.telepads.friends", InputConstants.KEY_PERIOD,
        KeyMapping.Category.register(Identifier.fromNamespaceAndPath("telepads", "telepads")));

    @Mod.EventBusSubscriber(modid = Telepads.MOD_ID, value = Dist.CLIENT)
    public static final class InputEvents {
        @SubscribeEvent public static void tooltip(net.minecraftforge.event.entity.player.ItemTooltipEvent event) {
            if (event.getItemStack().is(Telepads.TELEPAD.get())) return;
            var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem());
            if (id.getNamespace().equals("telepads")) event.getToolTip().add(net.minecraft.network.chat.Component.translatable("tooltip.telepads." + id.getPath()));
        }
        @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event) { event.register(FRIENDS); }
        @SubscribeEvent public static void colors(net.minecraftforge.client.event.RegisterColorHandlersEvent.Block event) {
            net.minecraft.client.color.item.ItemTintSources.ID_MAPPER.put(Identifier.fromNamespaceAndPath("telepads", "colors"), PadTint.CODEC);
            var sources = new java.util.ArrayList<net.minecraft.client.color.block.BlockTintSource>();
            for (int i = 0; i < 2; i++) {
                final int part = i;
                sources.add(new net.minecraft.client.color.block.BlockTintSource() {
                    public int color(net.minecraft.world.level.block.state.BlockState state) { return subaraki.telepads.data.PadColors.DEFAULT.color(part); }
                    public int colorInWorld(net.minecraft.world.level.block.state.BlockState state, net.minecraft.client.renderer.block.BlockAndTintGetter level, net.minecraft.core.BlockPos pos) {
                        return level.getBlockEntity(pos) instanceof subaraki.telepads.block.TelepadBlockEntity pad ? pad.colors().color(part) : color(state);
                    }
                });
            }
            event.register(sources, Telepads.TELEPAD_BLOCK.get());
        }
    }
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        TelepadNetwork.nameHandler = prompt -> Minecraft.getInstance().gui.setScreen(new NameTelepadScreen(prompt));
        TelepadNetwork.friendsHandler = view -> Minecraft.getInstance().gui.setScreen(new FriendsScreen(view));
        TelepadNetwork.travelHandler = view -> {
            if (TravelScreen.closed(view.activation())) return;
            var client = Minecraft.getInstance();
            if (client.gui.screen() instanceof TravelScreen old && old.sameActivation(view.activation())) old.replacing();
            client.gui.setScreen(new TravelScreen(view));
        };
        TickEvent.ClientTickEvent.Post.BUS.addListener(tick -> {
            var client = Minecraft.getInstance();
            while (FRIENDS.consumeClick()) {
                if (client.player != null && client.gui.screen() == null)
                    TelepadNetwork.sendToServer(new TelepadNetwork.FriendRequest(0, new UUID(0, 0), ""));
            }
        });
    }
}
