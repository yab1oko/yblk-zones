package yblk.zones.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** Grid of emoji for HUD text prefix. */
public class EmojiPickerScreen extends Screen {
    private static final String[] EMOJIS = {
            "★", "☆", "✦", "✧", "♠", "♣", "♥", "♦",
            "⚔", "🛡", "⛏", "🏹", "🔥", "❄", "⚡", "☢",
            "☠", "✦", "◎", "●", "◆", "◇", "▲", "▼",
            "☀", "☁", "☂", "☃", "☄", "☾", "☽", "✦",
            "①", "②", "③", "④", "⑤", "⑥", "⑦", "⑧",
            "✓", "✗", "⚠", "⚑", "⚐", "⚜", "⚙", "♿",
            "🏠", "🏰", "🏜", "🌲", "💎", "🪙", "📦", "🎁"
    };

    private static final int PANEL = 0xFF16161C;
    private static final int ACCENT = 0xFF4EC9B0;
    private static final int ROW = 0xFF1E1E26;
    private static final int TEXT = 0xFFF0F0F5;

    private final Screen parent;
    private final Consumer<String> onPick;
    private int panelX, panelY, panelW, panelH;

    public EmojiPickerScreen(Screen parent, Consumer<String> onPick) {
        super(Component.translatable("screen.yblk_zones.emoji_pick"));
        this.parent = parent;
        this.onPick = onPick;
    }

    @Override
    protected void init() {
        panelW = Math.min(280, width - 20);
        panelH = Math.min(220, height - 20);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        int cols = 8;
        int cell = 24;
        int gap = 4;
        int startX = panelX + 12;
        int startY = panelY + 24;
        for (int i = 0; i < EMOJIS.length; i++) {
            int c = i % cols;
            int r = i / cols;
            String emoji = EMOJIS[i];
            addRenderableWidget(new Cell(startX + c * (cell + gap), startY + r * (cell + gap), cell, cell, emoji));
        }
        addRenderableWidget(new DoneBtn(panelX + panelW / 2 - 40, panelY + panelH - 22, 80, 14,
                Component.translatable("gui.cancel"), () -> minecraft.setScreen(parent)));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL);
        g.fill(panelX, panelY, panelX + panelW, panelY + 1, ACCENT);
        g.drawString(font, title, panelX + 10, panelY + 8, ACCENT, false);
        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private class Cell extends AbstractWidget {
        private final String emoji;

        Cell(int x, int y, int w, int h, String emoji) {
            super(x, y, w, h, Component.literal(emoji));
            this.emoji = emoji;
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            onPick.accept(emoji);
            minecraft.setScreen(parent);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
            g.fill(getX(), getY(), getX() + width, getY() + height, isHoveredOrFocused() ? 0xFF2A4A44 : ROW);
            g.drawCenteredString(font, emoji, getX() + width / 2, getY() + 8, TEXT);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            defaultButtonNarrationText(out);
        }
    }

    private static class DoneBtn extends AbstractWidget {
        private final Runnable action;

        DoneBtn(int x, int y, int w, int h, Component label, Runnable action) {
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
            g.drawCenteredString(net.minecraft.client.Minecraft.getInstance().font, getMessage(),
                    getX() + width / 2, getY() + 3, TEXT);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            defaultButtonNarrationText(out);
        }
    }
}
