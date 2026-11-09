package yblk.zones.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import yblk.zones.network.BuyZonePacket;
import yblk.zones.network.ZonesNetwork;
import yblk.zones.zone.SaleCost;
import yblk.zones.zone.Zone;

import java.util.ArrayList;
import java.util.List;

/** Confirm before sending {@link BuyZonePacket}. */
public class ConfirmBuyScreen extends Screen {
    private static final int PANEL = 0xFF16161C;
    private static final int EDGE = 0xFF2A2A32;
    private static final int ACCENT = 0xFF4EC9B0;
    private static final int TEXT = 0xFFF0F0F5;
    private static final int MUTED = 0xFF9A9AA8;
    private static final int ROW = 0xFF1E1E26;

    private final Zone zone;
    private int panelX, panelY, panelW, panelH;
    private final List<String> costLines = new ArrayList<>();

    public ConfirmBuyScreen(Zone zone) {
        super(Component.translatable("screen.yblk_zones.confirm_buy"));
        this.zone = zone;
    }

    @Override
    protected void init() {
        clearWidgets();
        costLines.clear();
        for (SaleCost cost : zone.getBuyCosts()) {
            if (!cost.isValid()) {
                continue;
            }
            costLines.add(cost.count() + " × " + itemLabel(cost.itemId()));
        }
        panelW = Math.min(240, width - 32);
        panelH = Math.min(120 + costLines.size() * 10, height - 32);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;

        int btnY = panelY + panelH - 24;
        int btnW = (panelW - 28) / 2;
        addRenderableWidget(new Btn(panelX + 10, btnY, btnW, 16,
                Component.translatable("gui.cancel"), this::onClose));
        addRenderableWidget(new Btn(panelX + 18 + btnW, btnY, btnW, 16,
                Component.translatable("screen.yblk_zones.confirm_buy_yes"), this::confirm));
    }

    private void confirm() {
        ZonesNetwork.CHANNEL.sendToServer(new BuyZonePacket(zone.getId()));
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        g.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, EDGE);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL);
        g.fill(panelX, panelY, panelX + panelW, panelY + 1, ACCENT);
        g.drawString(font, title, panelX + 10, panelY + 8, ACCENT, false);
        g.drawString(font, zone.getName(), panelX + 10, panelY + 22, TEXT, false);
        g.drawString(font, Component.translatable("screen.yblk_zones.confirm_buy_costs"),
                panelX + 10, panelY + 36, MUTED, false);
        int y = panelY + 48;
        for (String line : costLines) {
            g.drawString(font, line, panelX + 14, y, TEXT, false);
            y += 10;
        }
        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String itemLabel(String id) {
        if (id == null || id.isBlank()) {
            return "?";
        }
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) {
            return id;
        }
        Item item = ForgeRegistries.ITEMS.getValue(rl);
        if (item == null) {
            return id;
        }
        return new ItemStack(item).getHoverName().getString();
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
            g.drawCenteredString(Minecraft.getInstance().font, getMessage(),
                    getX() + width / 2, getY() + (height - 8) / 2, TEXT);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            defaultButtonNarrationText(out);
        }
    }
}
