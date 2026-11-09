package yblk.zones.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import yblk.zones.script.ZoneActionType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Pick script action type (Do:) from a full list — no cycling. */
public class ScriptActionTypePickerScreen extends Screen {
    private static final int PANEL = 0xFF16161C;
    private static final int ACCENT = 0xFF4EC9B0;
    private static final int ROW = 0xFF1E1E26;
    private static final int ON = 0xFF3D8B6E;
    private static final int TEXT = 0xFFF0F0F5;

    private final Screen parent;
    private final ZoneActionType current;
    private final Consumer<ZoneActionType> onPick;
    private final List<AbstractWidget> rows = new ArrayList<>();
    private int scroll;
    private int panelX, panelY, panelW, panelH;

    public ScriptActionTypePickerScreen(Screen parent, ZoneActionType current, Consumer<ZoneActionType> onPick) {
        super(Component.translatable("screen.yblk_zones.script_do_pick"));
        this.parent = parent;
        this.current = current;
        this.onPick = onPick;
    }

    @Override
    protected void init() {
        clearWidgets();
        rows.clear();
        panelW = Math.min(320, width - 20);
        panelH = Math.min(280, height - 20);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        rebuildList();
        addRenderableWidget(new Btn(panelX + panelW / 2 - 40, panelY + panelH - 22, 80, 18,
                Component.translatable("gui.cancel"), () -> minecraft.setScreen(parent)));
    }

    private void rebuildList() {
        for (AbstractWidget w : rows) {
            removeWidget(w);
        }
        rows.clear();
        int top = panelY + 28;
        int bottom = panelY + panelH - 30;
        int y = top - scroll;
        for (ZoneActionType type : ZoneActionType.values()) {
            boolean selected = type == current;
            Component label = Component.translatable("screen.yblk_zones.script_do." + type.name().toLowerCase());
            Row row = new Row(panelX + 10, y, panelW - 20, 22, type, label, selected);
            boolean show = y + 22 > top && y < bottom;
            row.visible = show;
            row.active = show;
            rows.add(row);
            addRenderableWidget(row);
            y += 26;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int content = ZoneActionType.values().length * 26;
        int view = Math.max(1, panelH - 60);
        scroll = Mth.clamp(scroll - (int) (delta * 20), 0, Math.max(0, content - view));
        rebuildList();
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL);
        g.fill(panelX, panelY, panelX + panelW, panelY + 1, ACCENT);
        g.drawString(font, title, panelX + 10, panelY + 10, ACCENT, false);
        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private class Row extends AbstractWidget {
        private final ZoneActionType type;
        private final boolean selected;

        Row(int x, int y, int w, int h, ZoneActionType type, Component label, boolean selected) {
            super(x, y, w, h, label);
            this.type = type;
            this.selected = selected;
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            onPick.accept(type);
            minecraft.setScreen(parent);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
            int bg = selected ? ON : (isHoveredOrFocused() ? 0xFF2A4A44 : ROW);
            g.fill(getX(), getY(), getX() + width, getY() + height, bg);
            g.drawString(font, getMessage(), getX() + 8, getY() + 7, TEXT, false);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            defaultButtonNarrationText(out);
        }
    }

    private static class Btn extends AbstractWidget {
        private final Runnable action;

        Btn(int x, int y, int w, int h, Component label, Runnable action) {
            super(x, y, w, h, label);
            this.action = action;
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            action.run();
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
            g.fill(getX(), getY(), getX() + width, getY() + height, ROW);
            g.drawCenteredString(Minecraft.getInstance().font, getMessage(),
                    getX() + width / 2, getY() + 5, TEXT);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            defaultButtonNarrationText(out);
        }
    }
}
