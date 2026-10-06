package subaraki.telepads.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import subaraki.telepads.network.TelepadNetwork;
import java.util.UUID;

public final class TravelScreen extends Screen {
    private final TelepadNetwork.TravelView view;
    private static UUID closedActivation;
    private int scroll;
    private boolean sent;
    private TelepadNetwork.TravelRow missing;
    public TravelScreen(TelepadNetwork.TravelView view) { super(Component.translatable("screen.telepads.travel")); this.view = view; }
    private void send(int action, UUID id, int dimension, int page) { TelepadNetwork.sendToServer(new TelepadNetwork.TravelRequest(view.activation(), action, id, dimension, page)); }
    public static boolean closed(UUID token) { return token.equals(closedActivation); }
    public boolean sameActivation(UUID token) { return view.activation().equals(token); }
    public void replacing() { sent = true; }
    private void complete() { sent = true; closedActivation = view.activation(); }
    private void page(int dimension, int page) { send(1, new UUID(0, 0), dimension, page); }
    @Override protected void init() {
        int x = width / 2 - 140;
        int visible = Math.max(1, (height - 130) / 22);
        if (missing != null) {
            addRenderableWidget(Button.builder(Component.translatable("screen.telepads.confirm_missing"), b -> {
                complete(); send(3, missing.id(), view.dimensionIndex(), view.page()); minecraft.gui.setScreen(null);
            }).bounds(x, 90, 280, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("screen.telepads.forget"), b -> send(4, missing.id(), view.dimensionIndex(), view.page())).bounds(x, 116, 280, 20).build());
            addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, b -> { missing = null; rebuildWidgets(); }).bounds(x, 142, 280, 20).build());
        } else {
            addRenderableWidget(Button.builder(Component.literal("<"), b -> page(view.dimensionIndex() - 1, 0)).bounds(x, 35, 20, 20).build()).active = view.dimensionIndex() > 0;
            addRenderableWidget(Button.builder(Component.literal(">"), b -> page(view.dimensionIndex() + 1, 0)).bounds(x + 260, 35, 20, 20).build()).active = view.dimensionIndex() + 1 < view.dimensions();
            for (int i = 0; i < visible && scroll + i < view.rows().size(); i++) {
                var row = view.rows().get(scroll + i);
                var label = Component.literal(row.name()).append(" · ").append(Component.translatable("screen.telepads.state." + row.state()));
                var button = addRenderableWidget(Button.builder(label, b -> {
                    if (row.state() == 2) { missing = row; rebuildWidgets(); }
                    else { complete(); send(2, row.id(), view.dimensionIndex(), view.page()); minecraft.gui.setScreen(null); }
                }).bounds(x, 65 + i * 22, 280, 20).build());
                button.active = row.state() != 1;
            }
            addRenderableWidget(Button.builder(Component.translatable("screen.telepads.previous"), b -> page(view.dimensionIndex(), view.page() - 1)).bounds(x, height - 55, 88, 20).build()).active = view.page() > 0;
            addRenderableWidget(Button.builder(Component.translatable("screen.telepads.next"), b -> page(view.dimensionIndex(), view.page() + 1)).bounds(x + 192, height - 55, 88, 20).build()).active = view.page() + 1 < view.pages();
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose()).bounds(x + 90, height - 30, 100, 20).build());
    }
    @Override public boolean mouseScrolled(double x, double y, double sx, double sy) {
        int visible = Math.max(1, (height - 130) / 22);
        scroll = Math.clamp(scroll - (int)Math.signum(sy), 0, Math.max(0, view.rows().size() - visible));
        rebuildWidgets(); return true;
    }
    @Override public void removed() { if (!sent) { closedActivation = view.activation(); if (minecraft.getConnection() != null) send(0, new UUID(0, 0), 0, 0); } sent = true; }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int x, int y, float tick) {
        super.extractRenderState(graphics, x, y, tick);
        graphics.centeredText(font, title, width / 2, 15, 0xffffffff);
        graphics.centeredText(font, missing == null ? Component.literal(view.dimension()) : Component.literal(missing.name()), width / 2, missing == null ? 40 : 60, 0xffffffff);
        if (view.rows().isEmpty()) graphics.centeredText(font, Component.translatable("screen.telepads.empty"), width / 2, 85, 0xffffffff);
    }
}
