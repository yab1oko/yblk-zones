package yblk.zones.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/** Sliding on/off knob. {@code anim} is 0 (off) .. 1 (on). */
public final class ToggleSwitch {
    private ToggleSwitch() {
    }

    public static void paint(GuiGraphics graphics, int x, int y, int w, int h, float anim) {
        anim = Mth.clamp(anim, 0f, 1f);
        int pad = 2;
        int knob = h - pad * 2;
        float mid = 4f * anim * (1f - anim);
        int knobW = knob + Math.round(mid * 3f);
        int travel = Math.max(0, w - pad * 2 - knobW);
        int knobX = x + pad + Math.round(travel * anim);
        int knobY = y + pad;
        int off = 0xFF3A3A44;
        int on = 0xFF2F6F4E;
        int track = 0xFF000000 | lerp(off, on, anim);
        graphics.fill(x, y, x + w, y + h, track);
        graphics.fill(x + 1, y + 1, x + w - 1, y + 2, 0x22000000);
        graphics.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, 0x33FFFFFF);
        graphics.fill(knobX, knobY, knobX + knobW, knobY + knob, 0xFFE8E8F0);
        graphics.fill(knobX + 1, knobY + 1, knobX + knobW - 1, knobY + 2, 0x33FFFFFF);
    }

    private static int lerp(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF;
        int ag = (a >> 8) & 0xFF;
        int ab = a & 0xFF;
        int br = (b >> 16) & 0xFF;
        int bg = (b >> 8) & 0xFF;
        int bb = b & 0xFF;
        int r = (int) Mth.lerp(t, ar, br);
        int g = (int) Mth.lerp(t, ag, bg);
        int bl = (int) Mth.lerp(t, ab, bb);
        return (r << 16) | (g << 8) | bl;
    }
}
