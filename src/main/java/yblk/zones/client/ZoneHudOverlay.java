package yblk.zones.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import yblk.zones.ZonesMod;
import yblk.zones.zone.SaleCost;
import yblk.zones.zone.Zone;
import yblk.zones.zone.ZoneFlag;
import yblk.zones.zone.ZoneSettings;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = ZonesMod.MODID, value = Dist.CLIENT)
public final class ZoneHudOverlay {
    public static final String ITEM_PREFIX = "item:";

    private static UUID fadeZoneId;
    private static long fadeEnteredAtMs;

    private ZoneHudOverlay() {
    }

    @SubscribeEvent
    public static void onHud(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }
        Zone zone = ClientZones.at(mc.player.getX(), mc.player.getY() + 0.1, mc.player.getZ());
        if (zone == null || !zone.has(ZoneFlag.DISPLAY)) {
            fadeZoneId = null;
            return;
        }

        ZoneSettings settings = zone.settings();
        long now = System.currentTimeMillis();
        UUID id = zone.getId();
        if (fadeZoneId == null || !fadeZoneId.equals(id)) {
            fadeZoneId = id;
            fadeEnteredAtMs = now;
        }
        int fadeSec = settings.hudFadeSeconds();
        boolean faded = fadeSec > 0 && (now - fadeEnteredAtMs) >= fadeSec * 1000L;

        GuiGraphics g = event.getGuiGraphics();
        int w = mc.getWindow().getGuiScaledWidth();
        int y = 8;
        int rgb = settings.hudColor();
        if (rgb == 0) {
            rgb = settings.outlineColor();
        }
        if (rgb == 0) {
            rgb = zone.primaryColor();
        }
        int color = 0xFF000000 | rgb;

        float scale = settings.hudScale();
        boolean showLevel = zone.has(ZoneFlag.MOB_BUFF) || settings.harvestLevel() > 0;
        String name = settings.hudText().isBlank() ? zone.getName() : settings.hudText();
        Component title;
        if (showLevel) {
            int shownLevel = 1;
            if (zone.has(ZoneFlag.MOB_BUFF)) {
                shownLevel = Math.max(shownLevel, settings.buffAmplifier() + 1);
            }
            if (settings.harvestLevel() > 0) {
                shownLevel = Math.max(shownLevel, settings.harvestLevel());
            }
            title = Component.translatable("hud.yblk_zones.zone", name, shownLevel);
        } else {
            title = Component.literal(name);
        }

        ZoneSettings.HudMode mode = settings.hudMode();
        boolean showText = !faded && (mode == ZoneSettings.HudMode.TEXT || mode == ZoneSettings.HudMode.BOTH);
        ItemStack iconStack = ItemStack.EMPTY;
        ResourceLocation image = null;
        boolean showImage = !faded && (mode == ZoneSettings.HudMode.IMAGE || mode == ZoneSettings.HudMode.BOTH);
        if (showImage && !settings.hudImage().isBlank()) {
            String stored = settings.hudImage();
            if (stored.startsWith(ITEM_PREFIX)) {
                iconStack = stackOf(stored.substring(ITEM_PREFIX.length()));
                showImage = !iconStack.isEmpty();
            } else if (stored.startsWith(ClientHudImages.FILE_PREFIX)) {
                image = ClientHudImages.resolve(stored);
                showImage = image != null;
            } else {
                showImage = false;
            }
        } else {
            showImage = false;
        }

        int drawW = 0;
        int drawH = 0;
        int texW = 16;
        int texH = 16;
        if (showImage) {
            if (image != null) {
                int[] wh = ClientHudImages.sizeOf(image);
                texW = Math.max(1, wh[0]);
                texH = Math.max(1, wh[1]);
            } else {
                texW = 16;
                texH = 16;
            }
            int[] sized = computeImageSize(texW, texH, settings.hudImageSize(), settings.hudImageFit());
            drawW = sized[0];
            drawH = sized[1];
        }

        int textW = showText ? mc.font.width(title) : 0;
        int textH = showText ? mc.font.lineHeight : 0;
        int gap = showImage && showText ? 4 : 0;
        int blockW = drawW + gap + textW;
        int blockH = Math.max(drawH, textH);
        if (blockW <= 0 && blockH <= 0) {
            // Still show enter title / sale / owner tip below
            blockH = 0;
        }
        float cx = w * 0.5F;
        float cy = y + Math.max(blockH, 1) * 0.5F;
        int left = Math.round(cx - Math.max(blockW, 0) * 0.5F);

        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(cx, cy, 0);
        pose.scale(scale, scale, 1);
        pose.translate(-cx, -cy, 0);

        int drawX = left;
        if (showImage) {
            int iconY = y + (blockH - drawH) / 2;
            if (!iconStack.isEmpty()) {
                pose.pushPose();
                pose.translate(drawX, iconY, 0);
                float itemScale = drawW / 16.0F;
                pose.scale(itemScale, itemScale, 1);
                g.renderItem(iconStack, 0, 0);
                pose.popPose();
            } else if (image != null) {
                g.blit(image, drawX, iconY, drawW, drawH, 0f, 0f, texW, texH, texW, texH);
            }
            drawX += drawW + gap;
        }
        if (showText) {
            int textY = y + (Math.max(blockH, textH) - textH) / 2;
            g.drawString(mc.font, title, drawX, textY, color, true);
        }

        pose.popPose();

        int lineY = y + Math.round(Math.max(blockH, 1) * scale) + 4;
        if (!faded) {
            String enterTitle = settings.enterTitle();
            if (!enterTitle.isBlank()) {
                int tw = mc.font.width(enterTitle);
                g.drawString(mc.font, enterTitle, (w - tw) / 2, lineY, 0xFF9A9AA8, true);
                lineY += 12;
            }
        }
        if (zone.isForSale() && !zone.getBuyCosts().isEmpty()) {
            renderSaleLine(g, mc, zone, w, lineY);
            lineY += 18;
        }
        boolean own = mc.player != null && zone.getOwner() != null
                && zone.getOwner().equals(mc.player.getUUID());
        if (own) {
            Component tip = Component.translatable("hud.yblk_zones.owner_hint");
            g.drawString(mc.font, tip, (w - mc.font.width(tip)) / 2, lineY, 0xFF9A9AA8, true);
        }
    }

    /** Returns [drawW, drawH] for the chosen fit mode. */
    static int[] computeImageSize(int texW, int texH, int sizeSetting, ZoneSettings.HudImageFit fit) {
        int size = Mth.clamp(sizeSetting, 8, 128);
        return switch (fit == null ? ZoneSettings.HudImageFit.FIT : fit) {
            case STRETCH -> new int[]{size, size};
            case NATIVE -> {
                float s = size / 32.0F;
                yield new int[]{
                        Math.max(1, Math.round(texW * s)),
                        Math.max(1, Math.round(texH * s))
                };
            }
            case FIT -> {
                float s = size / (float) Math.max(texW, texH);
                yield new int[]{
                        Math.max(1, Math.round(texW * s)),
                        Math.max(1, Math.round(texH * s))
                };
            }
        };
    }

    private static void renderSaleLine(GuiGraphics g, Minecraft mc, Zone zone, int screenW, int y) {
        String prefix = Component.translatable("hud.yblk_zones.for_sale_prefix").getString();
        int gap = 4;
        int icon = 16;
        int totalW = mc.font.width(prefix);
        for (SaleCost cost : zone.getBuyCosts()) {
            if (!cost.isValid()) {
                continue;
            }
            String cnt = "×" + cost.count();
            totalW += gap + icon + 2 + mc.font.width(cnt);
        }
        int x = (screenW - totalW) / 2;
        g.drawString(mc.font, prefix, x, y + 4, 0xFFFFAA00, true);
        x += mc.font.width(prefix);

        for (SaleCost cost : zone.getBuyCosts()) {
            ItemStack stack = stackOf(cost.itemId());
            if (stack.isEmpty()) {
                continue;
            }
            x += gap;
            g.renderItem(stack, x, y);
            g.renderItemDecorations(mc.font, stack, x, y);
            x += icon + 2;
            String cnt = "×" + cost.count();
            g.drawString(mc.font, cnt, x, y + 4, 0xFFFFAA00, true);
            x += mc.font.width(cnt);
        }
    }

    private static ItemStack stackOf(String id) {
        if (id == null || id.isBlank()) {
            return ItemStack.EMPTY;
        }
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) {
            return ItemStack.EMPTY;
        }
        Item item = ForgeRegistries.ITEMS.getValue(rl);
        if (item == null) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item);
    }
}
