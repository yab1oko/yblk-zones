package yblk.zones.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import yblk.zones.network.ManageZonePacket;
import yblk.zones.network.OwnerUpdatePacket;
import yblk.zones.network.ZonesNetwork;
import yblk.zones.zone.Zone;
import yblk.zones.zone.ZoneSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Owner menu: rename, pick trusted players from online list, transfer ownership to a chosen player.
 */
public class ZoneOwnerScreen extends Screen {
    private static final int PANEL = 0xFF16161C;
    private static final int ACCENT = 0xFF4EC9B0;
    private static final int ACCENT_BOTTOM = 0xFF0A0A0E;
    private static final int TEXT = 0xFFF0F0F5;
    private static final int MUTED = 0xFF9A9AA8;
    private static final int ROW = 0xFF1E1E26;
    private static final int ROW_HOV = 0xFF2A4A44;
    private static final int ON = 0xFF3D8B6E;
    private static final int OFF = 0xFF3A3A44;

    private final UUID zoneId;
    private final ZoneSettings settings = new ZoneSettings();
    private EditBox nameBox;
    private UUID transferTarget;
    private String transferTargetName = "";
    private int panelX, panelY, panelW, panelH;
    private int listScroll;
    private int listTop;
    private int listBottom;
    private final List<PlayerRow> playerRows = new ArrayList<>();

    public ZoneOwnerScreen(Zone zone) {
        super(Component.translatable("screen.yblk_zones.owner"));
        this.zoneId = zone.getId();
        settings.load(zone.settings().save());
    }

    @Override
    protected void init() {
        clearWidgets();
        playerRows.clear();
        panelW = Math.min(300, width - 24);
        panelH = Math.min(280, height - 24);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;

        Zone z = ClientZones.byId(zoneId);
        String def = z != null ? z.getName() : "base";
        nameBox = new EditBox(font, panelX + 10, panelY + 22, panelW - 20, 14,
                Component.translatable("screen.yblk_zones.name"));
        nameBox.setMaxLength(48);
        nameBox.setValue(def);
        nameBox.setTextColor(TEXT);
        addRenderableWidget(nameBox);

        listTop = panelY + 52;
        listBottom = panelY + panelH - 88;
        rebuildPlayerList();

        int y = panelY + panelH - 78;
        addRenderableWidget(new Btn(panelX + 10, y, panelW - 20, 14,
                Component.translatable("screen.yblk_zones.transfer_confirm"), this::confirmTransfer));
        y += 16;
        int half = (panelW - 28) / 2;
        addRenderableWidget(new Btn(panelX + 10, y, half, 14,
                Component.translatable("screen.yblk_zones.leave"), this::onLeave));
        addRenderableWidget(new Btn(panelX + 18 + half, y, half, 14,
                Component.translatable("screen.yblk_zones.sell"), this::onSell));
        y += 16;
        addRenderableWidget(new Btn(panelX + 10, y, half, 14,
                Component.translatable("screen.yblk_zones.event_start"), this::onEventStart));
        addRenderableWidget(new Btn(panelX + 18 + half, y, half, 14,
                Component.translatable("screen.yblk_zones.event_stop"), this::onEventStop));
        y += 16;
        addRenderableWidget(new Btn(panelX + 10, y, half, 14,
                Component.translatable("gui.cancel"), this::onClose));
        addRenderableWidget(new Btn(panelX + 18 + half, y, half, 14,
                Component.translatable("screen.yblk_zones.save"), this::save));
    }

    private void rebuildPlayerList() {
        for (PlayerRow row : playerRows) {
            removeWidget(row);
        }
        playerRows.clear();

        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null || mc.player == null) {
            return;
        }
        UUID self = mc.player.getUUID();
        List<PlayerInfo> online = new ArrayList<>();
        for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
            if (!info.getProfile().getId().equals(self)) {
                online.add(info);
            }
        }
        online.sort((a, b) -> a.getProfile().getName().compareToIgnoreCase(b.getProfile().getName()));

        int rowH = 16;
        int y = listTop - listScroll;
        for (PlayerInfo info : online) {
            UUID id = info.getProfile().getId();
            String name = info.getProfile().getName();
            PlayerRow row = new PlayerRow(panelX + 10, y, panelW - 20, rowH, id, name);
            playerRows.add(row);
            addRenderableWidget(row);
            boolean show = y + rowH > listTop && y < listBottom;
            row.visible = show;
            row.active = show;
            y += rowH + 2;
        }
    }

    private void onEventStart() {
        Zone z = ClientZones.byId(zoneId);
        if (z != null && z.isEventNeedsConfirm()) {
            Minecraft.getInstance().setScreen(new ConfirmEventScreen(z, this));
            return;
        }
        ZonesNetwork.CHANNEL.sendToServer(new ManageZonePacket(
                ManageZonePacket.Action.START_EVENT, zoneId, null, null));
        onClose();
    }

    private void onEventStop() {
        ZonesNetwork.CHANNEL.sendToServer(new ManageZonePacket(
                ManageZonePacket.Action.STOP_EVENT, zoneId, null, null));
        onClose();
    }

    private void onLeave() {
        ZonesNetwork.CHANNEL.sendToServer(new ManageZonePacket(
                ManageZonePacket.Action.LEAVE, zoneId, null, null));
        onClose();
    }

    private void onSell() {
        Minecraft mc = Minecraft.getInstance();
        Zone z = ClientZones.byId(zoneId);
        if (z != null && z.isForSale()) {
            ZonesNetwork.CHANNEL.sendToServer(new ManageZonePacket(
                    ManageZonePacket.Action.LIST_SALE, zoneId, null, "off"));
        } else if (mc.player != null) {
            mc.player.displayClientMessage(Component.translatable("message.yblk_zones.sale_use_edit"), true);
        }
    }

    private void confirmTransfer() {
        if (transferTarget == null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.translatable("message.yblk_zones.transfer_no_target"), true);
            }
            return;
        }
        ZonesNetwork.CHANNEL.sendToServer(new ManageZonePacket(
                ManageZonePacket.Action.TRANSFER, zoneId, transferTarget, null));
        onClose();
    }

    private void toggleTrust(UUID id, String name) {
        if (settings.trustedNames().stream().anyMatch(n -> n.equalsIgnoreCase(name))
                || settings.trustedIds().contains(id)) {
            settings.removeTrustedName(name);
            settings.trustedIds().remove(id);
        } else {
            settings.addTrustedName(name);
            settings.trustedIds().add(id);
        }
    }

    private boolean isTrusted(UUID id, String name) {
        return settings.trustedIds().contains(id)
                || settings.trustedNames().stream().anyMatch(n -> n.equalsIgnoreCase(name));
    }

    private void save() {
        ZonesNetwork.CHANNEL.sendToServer(new OwnerUpdatePacket(zoneId, nameBox.getValue(), settings));
        onClose();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= panelX && mouseX <= panelX + panelW && mouseY >= listTop && mouseY <= listBottom) {
            int content = playerRows.size() * 18;
            int view = Math.max(1, listBottom - listTop);
            int max = Math.max(0, content - view);
            listScroll = Mth.clamp(listScroll - (int) (delta * 14), 0, max);
            rebuildPlayerList();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL);
        drawBorder(g, panelX, panelY, panelW, panelH);
        g.drawString(font, title, panelX + 10, panelY + 8, ACCENT, false);
        g.drawString(font, Component.translatable("screen.yblk_zones.trust_pick"),
                panelX + 10, panelY + 40, MUTED, false);

        g.enableScissor(panelX + 8, listTop, panelX + panelW - 8, listBottom);
        for (PlayerRow row : playerRows) {
            if (row.visible) {
                row.render(g, mouseX, mouseY, partial);
            }
        }
        g.disableScissor();

        if (playerRows.isEmpty()) {
            g.drawString(font, Component.translatable("screen.yblk_zones.trust_empty"),
                    panelX + 14, listTop + 8, MUTED, false);
        }

        String transfer = transferTargetName.isEmpty()
                ? Component.translatable("screen.yblk_zones.transfer_none").getString()
                : Component.translatable("screen.yblk_zones.transfer_to", transferTargetName).getString();
        g.drawString(font, font.plainSubstrByWidth(transfer, panelW - 20),
                panelX + 10, panelY + panelH - 86, TEXT, false);

        // Footer buttons / nameBox rendered by super after our labels
        nameBox.render(g, mouseX, mouseY, partial);
        for (var w : children()) {
            if (w instanceof AbstractWidget aw && aw != nameBox && !(aw instanceof PlayerRow)) {
                aw.render(g, mouseX, mouseY, partial);
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** One online player: left = trust toggle, right = pick as transfer target. */
    private class PlayerRow extends AbstractWidget {
        private final UUID playerId;
        private final String playerName;

        PlayerRow(int x, int y, int w, int h, UUID id, String name) {
            super(x, y, w, h, Component.literal(name));
            this.playerId = id;
            this.playerName = name;
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            if (mouseX >= getX() + width - 72) {
                transferTarget = playerId;
                transferTargetName = playerName;
            } else {
                toggleTrust(playerId, playerName);
            }
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
            boolean hov = isHoveredOrFocused();
            boolean trusted = isTrusted(playerId, playerName);
            boolean transfer = playerId.equals(transferTarget);
            g.fill(getX(), getY(), getX() + width, getY() + height, hov ? ROW_HOV : ROW);
            if (transfer) {
                g.fill(getX(), getY(), getX() + 2, getY() + height, ACCENT);
            }
            g.drawString(font, font.plainSubstrByWidth(playerName, width - 80),
                    getX() + 6, getY() + 4, TEXT, false);

            int tx = getX() + width - 68;
            g.fill(tx, getY() + 2, tx + 30, getY() + height - 2, trusted ? ON : OFF);
            g.drawCenteredString(font, trusted ? "✓" : "+", tx + 15, getY() + 4, TEXT);

            int gx = getX() + width - 34;
            g.fill(gx, getY() + 2, getX() + width - 2, getY() + height - 2, transfer ? ACCENT : OFF);
            g.drawCenteredString(font, "→", gx + 16, getY() + 4, TEXT);
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
            boolean hov = isHoveredOrFocused();
            var f = Minecraft.getInstance().font;
            g.fill(getX(), getY(), getX() + width, getY() + height, hov ? 0xFF2A2A32 : ROW);
            g.fill(getX(), getY(), getX() + width, getY() + 1, hov ? ACCENT : 0xFF9A9AA8);
            g.fill(getX(), getY() + height - 1, getX() + width, getY() + height, ACCENT_BOTTOM);
            g.drawCenteredString(f, f.plainSubstrByWidth(getMessage().getString(), width - 4),
                    getX() + width / 2, getY() + (height - 8) / 2, TEXT);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            defaultButtonNarrationText(out);
        }
    }

    private static void drawBorder(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + 1, ACCENT);
        g.fill(x, y + h - 1, x + w, y + h, ACCENT_BOTTOM);
        g.fill(x, y, x + 1, y + h, ACCENT);
        g.fill(x + w - 1, y, x + w, y + h, ACCENT_BOTTOM);
    }
}
