package subaraki.telepads.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import subaraki.telepads.network.TelepadNetwork;

public final class NameTelepadScreen extends Screen {
    private final TelepadNetwork.NamePrompt prompt;
    private EditBox name;
    private Checkbox share;
    private boolean sent;
    public NameTelepadScreen(TelepadNetwork.NamePrompt prompt) {
        super(Component.translatable("screen.telepads.name"));
        this.prompt = prompt;
    }

    @Override protected void init() {
        int x = width / 2 - 100;
        int y = height / 2 - 50;
        name = addRenderableWidget(new EditBox(font, x, y, 200, 20, title));
        name.setMaxLength(16);
        name.setValue(prompt.defaultName());
        share = addRenderableWidget(Checkbox.builder(Component.translatable("screen.telepads.share"), font).pos(x, y + 30).build());
        var confirm = addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> {
            sent = true;
            TelepadNetwork.sendToServer(new TelepadNetwork.NameRequest(prompt.activation(), name.getValue(), share.selected()));
            minecraft.gui.setScreen(null);
        }).bounds(x, y + 65, 98, 20).build());
        name.setResponder(value -> confirm.active = !value.isBlank());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose()).bounds(x + 102, y + 65, 98, 20).build());
        setInitialFocus(name);
    }

    @Override public void removed() {
        if (!sent && minecraft.getConnection() != null) TelepadNetwork.sendToServer(new TelepadNetwork.CancelName(prompt.activation()));
        sent = true;
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, height / 2 - 80, 0xffffffff);
    }
}
