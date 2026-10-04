package subaraki.telepads.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import subaraki.telepads.network.TelepadNetwork;
import java.util.UUID;

public final class FriendsScreen extends Screen {
    private final TelepadNetwork.FriendsView view;
    public FriendsScreen(TelepadNetwork.FriendsView view) { super(Component.translatable("screen.telepads.friends")); this.view = view; }
    @Override protected void init() {
        int x = width / 2 - 110;
        int row = Math.min(20, Math.max(12, (height - 110) / 9));
        int top = Math.max(30, (height - row * 9 - 60) / 2);
        int inputs = top + 9 * row + 6;
        for (int i = 0; i < view.friends().size(); i++) {
            var friend = view.friends().get(i);
            addRenderableWidget(Button.builder(Component.translatable("screen.telepads.remove_friend", friend.name()), button ->
                TelepadNetwork.sendToServer(new TelepadNetwork.FriendRequest(2, friend.id(), ""))).bounds(x, top + i * row, 220, row - 2).build());
        }
        var name = addRenderableWidget(new EditBox(font, x, inputs, 140, 20, Component.translatable("screen.telepads.online_name")));
        name.setMaxLength(16);
        name.setHint(Component.translatable("screen.telepads.online_name"));
        var add = addRenderableWidget(Button.builder(Component.translatable("screen.telepads.add"), button ->
            TelepadNetwork.sendToServer(new TelepadNetwork.FriendRequest(1, new UUID(0, 0), name.getValue()))).bounds(x + 145, inputs, 75, 20).build());
        add.active = false;
        name.setResponder(value -> add.active = !value.isBlank() && view.friends().size() < 9);
        addRenderableWidget(Button.builder(Component.translatable("screen.telepads.clear"), button ->
            TelepadNetwork.sendToServer(new TelepadNetwork.FriendRequest(3, new UUID(0, 0), ""))).bounds(x, inputs + 26, 108, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose()).bounds(x + 112, inputs + 26, 108, 20).build());
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, Math.max(10, height / 2 - 155), 0xffffffff);
    }
}
