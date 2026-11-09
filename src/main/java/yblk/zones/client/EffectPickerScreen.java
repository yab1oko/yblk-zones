package yblk.zones.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Pick a mob effect from the registry (list + search + potion icons). */
public class EffectPickerScreen extends Screen {
    private static final int PANEL = 0xFF16161C;
    private static final int ACCENT = 0xFF4EC9B0;
    private static final int ROW = 0xFF1E1E26;
    private static final int TEXT = 0xFFF0F0F5;

    private final Screen parent;
    private final Consumer<String> onPick;
    private final List<ResourceLocation> all = new ArrayList<>();
    private final List<ResourceLocation> filtered = new ArrayList<>();
    private final List<AbstractWidget> rows = new ArrayList<>();
    private EditBox search;
    private int scroll;
    private int panelX, panelY, panelW, panelH;

    public EffectPickerScreen(Screen parent, Consumer<String> onPick) {
        super(Component.translatable("screen.yblk_zones.effect_pick"));
        this.parent = parent;
        this.onPick = onPick;
        for (MobEffect effect : ForgeRegistries.MOB_EFFECTS) {
            ResourceLocation id = ForgeRegistries.MOB_EFFECTS.getKey(effect);
            if (id != null) {
                all.add(id);
            }
        }
        all.sort(Comparator.comparing(ResourceLocation::toString));
        filtered.addAll(all);
    }

    @Override
    protected void init() {
        clearWidgets();
        rows.clear();
        panelW = Math.min(360, width - 20);
        panelH = Math.min(300, height - 20);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        search = new EditBox(font, panelX + 10, panelY + 22, panelW - 20, 14,
                Component.translatable("screen.yblk_zones.search"));
        search.setResponder(this::filter);
        search.setTextColor(TEXT);
        addRenderableWidget(search);
        rebuildList();
        addRenderableWidget(new Btn(panelX + panelW / 2 - 40, panelY + panelH - 22, 80, 14,
                Component.translatable("gui.cancel"), () -> minecraft.setScreen(parent)));
    }

    private void filter(String q) {
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        filtered.clear();
        for (ResourceLocation id : all) {
            MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(id);
            String name = effect != null
                    ? Component.translatable(effect.getDescriptionId()).getString().toLowerCase(Locale.ROOT) : "";
            if (needle.isEmpty() || id.toString().contains(needle) || id.getPath().contains(needle) || name.contains(needle)) {
                filtered.add(id);
            }
        }
        scroll = 0;
        rebuildList();
    }

    private void rebuildList() {
        for (AbstractWidget w : rows) {
            removeWidget(w);
        }
        rows.clear();
        int top = panelY + 42;
        int bottom = panelY + panelH - 28;
        int y = top - scroll;
        for (ResourceLocation id : filtered) {
            Row row = new Row(panelX + 10, y, panelW - 20, 20, id);
            boolean show = y + 20 > top && y < bottom;
            row.visible = show;
            row.active = show;
            rows.add(row);
            addRenderableWidget(row);
            y += 22;
        }
    }

    @Override
    public void tick() {
        super.tick();
        search.tick();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int content = filtered.size() * 22;
        int view = Math.max(1, panelH - 70);
        scroll = Mth.clamp(scroll - (int) (delta * 18), 0, Math.max(0, content - view));
        rebuildList();
        return true;
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

    private class Row extends AbstractWidget {
        private final ResourceLocation id;

        Row(int x, int y, int w, int h, ResourceLocation id) {
            super(x, y, w, h, Component.literal(id.toString()));
            this.id = id;
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            onPick.accept(id.toString());
            minecraft.setScreen(parent);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
            MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(id);
            g.fill(getX(), getY(), getX() + width, getY() + height, isHoveredOrFocused() ? 0xFF2A4A44 : ROW);
            int ix = getX() + 3;
            int iy = getY() + 2;
            if (effect != null) {
                TextureAtlasSprite sprite = Minecraft.getInstance().getMobEffectTextures().get(effect);
                g.blit(ix, iy, 0, 16, 16, sprite);
            } else {
                g.fill(ix, iy, ix + 16, iy + 16, 0xFF888888);
            }
            String label = effect != null
                    ? Component.translatable(effect.getDescriptionId()).getString() + " (" + id.getPath() + ")"
                    : id.toString();
            g.drawString(font, font.plainSubstrByWidth(label, width - 28), getX() + 24, getY() + 6, TEXT, false);
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
                    getX() + width / 2, getY() + 3, TEXT);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            defaultButtonNarrationText(out);
        }
    }
}
