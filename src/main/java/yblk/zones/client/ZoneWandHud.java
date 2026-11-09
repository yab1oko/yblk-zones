package yblk.zones.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import yblk.zones.ZonesMod;

/**
 * Create Schematic-style horizontal key strip — every binding visible, no H toggle.
 */
@Mod.EventBusSubscriber(modid = ZonesMod.MODID, value = Dist.CLIENT)
public final class ZoneWandHud {
    private static final int PANEL = 0xA016161C;
    private static final int EDGE = 0xFF2A2A32;
    private static final int ACCENT = 0xFF4EC9B0;
    private static final int ACCENT_BOTTOM = 0xFF0A0A0E;
    private static final int TEXT = 0xFFE8E8F0;
    private static final int MUTED = 0xFF9A9AA8;
    private static final int CHIP = 0xFF1E1E26;
    private static final int CHIP_KEY = 0xFF2A4A44;

    private static final String[] BASE_CHIPS = {"n", "g", "r", "o"};
    private static final String[] DRAFT_CHIPS = {"c", "x"};

    private ZoneWandHud() {
    }

    @SubscribeEvent
    public static void onHud(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen != null) {
            return;
        }
        if (!ClientWand.holdingWand()) {
            return;
        }

        GuiGraphics g = event.getGuiGraphics();
        var font = mc.font;
        boolean draft = ZoneSelection.hasPreview();

        String[] ids = draft
                ? concat(BASE_CHIPS, DRAFT_CHIPS)
                : BASE_CHIPS;

        int gap = 4;
        int chipH = 16;
        int pad = 6;
        int totalChipW = 0;
        int[] widths = new int[ids.length];
        for (int i = 0; i < ids.length; i++) {
            widths[i] = chipWidth(font, ids[i]);
            totalChipW += widths[i];
            if (i > 0) {
                totalChipW += gap;
            }
        }

        String alt = Component.translatable("hud.yblk_zones.chip_alt").getString();
        String size = draft
                ? ZoneSelection.sizeX() + "×" + ZoneSelection.height() + "×" + ZoneSelection.sizeZ()
                : "";
        int footerW = font.width(alt) + (size.isEmpty() ? 0 : 12 + font.width(size));
        int panelW = Math.max(totalChipW, footerW) + pad * 2;
        int panelH = pad + chipH + 4 + 10 + pad;
        int panelX = 6;
        int panelY = mc.getWindow().getGuiScaledHeight() - panelH - 26;

        g.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, EDGE);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL);
        g.fill(panelX, panelY, panelX + panelW, panelY + 1, ACCENT);
        g.fill(panelX, panelY + panelH - 1, panelX + panelW, panelY + panelH, ACCENT_BOTTOM);

        int cx = panelX + pad;
        int cy = panelY + pad;
        for (int i = 0; i < ids.length; i++) {
            drawChip(g, font, cx, cy, widths[i], chipH, ids[i]);
            cx += widths[i] + gap;
        }

        int tipY = cy + chipH + 4;
        g.drawString(font, alt, panelX + pad, tipY, MUTED, false);
        if (!size.isEmpty()) {
            g.drawString(font, size, panelX + panelW - pad - font.width(size), tipY, TEXT, false);
        }
    }

    private static int chipWidth(net.minecraft.client.gui.Font font, String id) {
        String label = Component.translatable("hud.yblk_zones.chip_" + id).getString();
        return font.width(label) + 10;
    }

    private static void drawChip(GuiGraphics g, net.minecraft.client.gui.Font font,
                                 int x, int y, int w, int h, String id) {
        String label = Component.translatable("hud.yblk_zones.chip_" + id).getString();
        g.fill(x, y, x + w, y + h, CHIP);
        g.fill(x, y, x + 2, y + h, ACCENT);
        // Key letter tint strip on the left of the text
        int keyEnd = label.indexOf(' ');
        if (keyEnd > 0) {
            String key = label.substring(0, keyEnd);
            int kw = font.width(key) + 6;
            g.fill(x + 2, y + 1, x + kw, y + h - 1, CHIP_KEY);
            g.drawString(font, key, x + 4, y + (h - 8) / 2, ACCENT, false);
            g.drawString(font, label.substring(keyEnd + 1), x + kw + 2, y + (h - 8) / 2, TEXT, false);
        } else {
            g.drawString(font, label, x + 5, y + (h - 8) / 2, TEXT, false);
        }
    }

    private static String[] concat(String[] a, String[] b) {
        String[] out = new String[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onKey(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) {
            return;
        }
        int key = event.getKey();
        if (key == GLFW.GLFW_KEY_O) {
            ClientWand.openOwnerMenuFromHud();
            return;
        }
        if (!ClientWand.holdingWand()) {
            return;
        }
        if (key == GLFW.GLFW_KEY_R) {
            ClientWand.openSettingsFromHud();
        } else if (key == GLFW.GLFW_KEY_G) {
            ClientWand.beginEditFromHud();
        } else if (key == GLFW.GLFW_KEY_N) {
            ClientWand.startNewZone();
        } else if (key == GLFW.GLFW_KEY_C) {
            ClientWand.addCornerFromHud();
        } else if (key == GLFW.GLFW_KEY_X) {
            ClientWand.deleteCornerFromHud();
        }
    }
}
