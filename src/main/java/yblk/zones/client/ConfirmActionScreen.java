package yblk.zones.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Yes / no dialog drawn over another zones screen, in the same button style. */
public class ConfirmActionScreen extends Screen {
    private static final int ACCENT = 0xFF4EC9B0;
    private static final int ACCENT_BOTTOM = 0xFF2A8F7A;
    private static final int TEXT = 0xFFF0F0F5;

    private final Screen parent;
    private final Component body;
    private final Runnable onYes;

    public ConfirmActionScreen(Screen parent, Component title, Component body, Runnable onYes) {
        super(title);
        this.parent = parent;
        this.body = body;
        this.onYes = onYes;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;
        addRenderableWidget(new Choice(cx - 82, cy + 16, 76, 16,
                Component.translatable("gui.yes"), onYes));
        addRenderableWidget(new Choice(cx + 6, cy + 16, 76, 16,
                Component.translatable("gui.no"), this::onClose));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The editor is not drawn under this dialog. Its text is flushed after gui quads
        // and would show through any overlay painted in the same frame.
        graphics.fill(0, 0, this.width, this.height, 0xFF101014);
        int w = 220;
        int h = 86;
        int x = this.width / 2 - w / 2;
        int y = this.height / 2 - 32;
        graphics.fill(x, y, x + w, y + h, 0xFF16161C);
        drawGradientBorder(graphics, x, y, w, h, ACCENT, ACCENT_BOTTOM);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, y + 12, TEXT);
        graphics.drawCenteredString(this.font, this.body, this.width / 2, y + 28, 0xFFB0B0BA);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private static void drawGradientBorder(GuiGraphics graphics, int x, int y, int w, int h,
                                           int topRgb, int bottomRgb) {
        for (int i = 0; i < h; i++) {
            float t = h <= 1 ? 0f : i / (float) (h - 1);
            int color = lerpColor(topRgb, bottomRgb, t) | 0xFF000000;
            graphics.fill(x, y + i, x + 1, y + i + 1, color);
            graphics.fill(x + w - 1, y + i, x + w, y + i + 1, color);
        }
        graphics.fill(x, y, x + w, y + 1, topRgb | 0xFF000000);
        graphics.fill(x, y + h - 1, x + w, y + h, bottomRgb | 0xFF000000);
    }

    private static int lerpColor(int a, int b, float t) {
        t = Mth.clamp(t, 0f, 1f);
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = Mth.floor(Mth.lerp(t, ar, br));
        int g = Mth.floor(Mth.lerp(t, ag, bg));
        int bl = Mth.floor(Mth.lerp(t, ab, bb));
        return (r << 16) | (g << 8) | bl;
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private final class Choice extends AbstractWidget {
        private final Runnable action;

        Choice(int x, int y, int w, int h, Component label, Runnable action) {
            super(x, y, w, h, label);
            this.action = action;
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            action.run();
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
            boolean hov = isHoveredOrFocused();
            g.fill(getX(), getY(), getX() + width, getY() + height, hov ? 0xFF2A2A32 : 0xFF1A1A22);
            drawGradientBorder(g, getX(), getY(), width, height, hov ? ACCENT : 0xFF9A9AA8, ACCENT_BOTTOM);
            g.drawCenteredString(font, getMessage(), getX() + width / 2, getY() + (height - 8) / 2,
                    hov ? TEXT : 0xFFC0C0C8);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            defaultButtonNarrationText(out);
        }
    }
}
