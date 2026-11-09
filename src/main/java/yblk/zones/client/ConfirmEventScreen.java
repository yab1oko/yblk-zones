package yblk.zones.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import yblk.zones.network.ManageZonePacket;
import yblk.zones.network.ZonesNetwork;
import yblk.zones.zone.Zone;

import java.util.UUID;

/** Confirm starting a zone event when eventNeedsConfirm is set. */
public class ConfirmEventScreen extends Screen {
    private static final int PANEL = 0xFF16161C;
    private static final int EDGE = 0xFF2A2A32;
    private static final int ACCENT = 0xFF4EC9B0;
    private static final int TEXT = 0xFFF0F0F5;
    private static final int MUTED = 0xFF9A9AA8;
    private static final int ROW = 0xFF1E1E26;

    private final UUID zoneId;
    private final String zoneName;
    private final boolean denyExit;
    private final Screen parent;
    private int panelX, panelY, panelW, panelH;

    public ConfirmEventScreen(Zone zone, Screen parent) {
        super(Component.translatable("screen.yblk_zones.event_confirm"));
        this.zoneId = zone.getId();
        this.zoneName = zone.getName();
        this.denyExit = zone.isDenyExitWhileEvent();
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelW = Math.min(240, width - 24);
        panelH = 100;
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        int bw = (panelW - 28) / 2;
        addRenderableWidget(new Btn(panelX + 10, panelY + panelH - 24, bw, 16,
                Component.translatable("gui.cancel"), () -> minecraft.setScreen(parent)));
        addRenderableWidget(new Btn(panelX + 18 + bw, panelY + panelH - 24, bw, 16,
                Component.translatable("screen.yblk_zones.event_start"), () -> {
                    ZonesNetwork.CHANNEL.sendToServer(new ManageZonePacket(
                            ManageZonePacket.Action.START_EVENT, zoneId, null, null));
                    onClose();
                }));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        g.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, EDGE);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL);
        g.fill(panelX, panelY, panelX + panelW, panelY + 1, ACCENT);
        g.drawString(font, title, panelX + 10, panelY + 8, ACCENT, false);
        g.drawString(font, zoneName, panelX + 10, panelY + 24, TEXT, false);
        if (denyExit) {
            g.drawString(font, Component.translatable("screen.yblk_zones.event_deny_exit"),
                    panelX + 10, panelY + 40, MUTED, false);
        }
        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
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
            g.fill(getX(), getY(), getX() + width, getY() + height, isHoveredOrFocused() ? ACCENT : EDGE);
            g.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, ROW);
            g.drawCenteredString(net.minecraft.client.Minecraft.getInstance().font, getMessage(),
                    getX() + width / 2, getY() + (height - 8) / 2, TEXT);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            defaultButtonNarrationText(out);
        }
    }
}
