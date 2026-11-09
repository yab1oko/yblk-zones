package yblk.zones.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/** Browse all simple particle types with live preview in front of the camera. */
public class ParticlePickerScreen extends Screen {
    private static final int PANEL = 0xFF16161C;
    private static final int ACCENT = 0xFF4EC9B0;
    private static final int ROW = 0xFF1E1E26;
    private static final int TEXT = 0xFFF0F0F5;
    private static final int MUTED = 0xFF9A9AA8;

    private final Screen parent;
    private final Consumer<String> onPick;
    private final List<ResourceLocation> all = new ArrayList<>();
    private final List<ResourceLocation> filtered = new ArrayList<>();
    private final List<AbstractWidget> rows = new ArrayList<>();
    private final Map<SoundSource, Double> mutedVolumes = new EnumMap<>(SoundSource.class);
    private EditBox search;
    private int scroll;
    private int panelX, panelY, panelW, panelH;
    private ResourceLocation previewId;
    private int previewTicks;
    private boolean soundsMuted;

    public ParticlePickerScreen(Screen parent, Consumer<String> onPick) {
        super(Component.translatable("screen.yblk_zones.particle_pick"));
        this.parent = parent;
        this.onPick = onPick;
        for (ParticleType<?> type : ForgeRegistries.PARTICLE_TYPES) {
            if (type instanceof SimpleParticleType) {
                ResourceLocation id = ForgeRegistries.PARTICLE_TYPES.getKey(type);
                if (id != null) {
                    all.add(id);
                }
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
        panelH = Math.min(280, height - 20);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        search = new EditBox(font, panelX + 10, panelY + 22, panelW - 20, 14,
                Component.translatable("screen.yblk_zones.search"));
        search.setResponder(this::filter);
        search.setTextColor(TEXT);
        addRenderableWidget(search);
        rebuildList();
        addRenderableWidget(new Btn(panelX + 10, panelY + panelH - 22, 80, 14,
                Component.translatable("gui.cancel"), this::closeToParent));
        addRenderableWidget(new Btn(panelX + panelW - 90, panelY + panelH - 22, 80, 14,
                Component.translatable("screen.yblk_zones.clear"), () -> {
            onPick.accept("");
            closeToParent();
        }));
        muteParticleSounds();
    }

    private void closeToParent() {
        restoreSounds();
        minecraft.setScreen(parent);
    }

    private void muteParticleSounds() {
        if (soundsMuted || minecraft == null) {
            return;
        }
        mutedVolumes.clear();
        for (SoundSource src : new SoundSource[]{
                SoundSource.AMBIENT, SoundSource.BLOCKS, SoundSource.HOSTILE,
                SoundSource.NEUTRAL, SoundSource.PLAYERS, SoundSource.RECORDS, SoundSource.WEATHER
        }) {
            mutedVolumes.put(src, (double) minecraft.options.getSoundSourceVolume(src));
            minecraft.options.getSoundSourceOptionInstance(src).set(0.0);
        }
        soundsMuted = true;
    }

    private void restoreSounds() {
        if (!soundsMuted || minecraft == null) {
            return;
        }
        for (Map.Entry<SoundSource, Double> e : mutedVolumes.entrySet()) {
            minecraft.options.getSoundSourceOptionInstance(e.getKey()).set(e.getValue());
        }
        mutedVolumes.clear();
        soundsMuted = false;
    }

    private void filter(String q) {
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        filtered.clear();
        for (ResourceLocation id : all) {
            if (needle.isEmpty() || id.toString().contains(needle) || id.getPath().contains(needle)) {
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
        int bottom = panelY + panelH - 48;
        int y = top - scroll;
        for (ResourceLocation id : filtered) {
            Row row = new Row(panelX + 10, y, panelW - 20, 14, id);
            boolean show = y + 14 > top && y < bottom;
            row.visible = show;
            row.active = show;
            rows.add(row);
            addRenderableWidget(row);
            y += 16;
        }
    }

    @Override
    public void tick() {
        super.tick();
        search.tick();
        previewTicks++;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || previewId == null) {
            return;
        }
        if (previewTicks % 2 != 0) {
            return;
        }
        ParticleType<?> type = ForgeRegistries.PARTICLE_TYPES.getValue(previewId);
        if (!(type instanceof SimpleParticleType simple)) {
            return;
        }
        Vec3 eye = mc.player.getEyePosition(1.0F);
        Vec3 look = mc.player.getViewVector(1.0F);
        Vec3 base = eye.add(look.scale(1.35));
        var rand = mc.level.random;
        for (int i = 0; i < 6; i++) {
            double ox = (rand.nextDouble() - 0.5) * 0.55;
            double oy = (rand.nextDouble() - 0.5) * 0.55;
            double oz = (rand.nextDouble() - 0.5) * 0.55;
            mc.level.addParticle(simple, true,
                    base.x + ox, base.y + oy, base.z + oz,
                    (rand.nextDouble() - 0.5) * 0.02,
                    0.02 + rand.nextDouble() * 0.04,
                    (rand.nextDouble() - 0.5) * 0.02);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int content = filtered.size() * 16;
        int view = Math.max(1, panelH - 90);
        scroll = Mth.clamp(scroll - (int) (delta * 16), 0, Math.max(0, content - view));
        rebuildList();
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL);
        g.fill(panelX, panelY, panelX + panelW, panelY + 1, ACCENT);
        g.drawString(font, title, panelX + 10, panelY + 8, ACCENT, false);
        String preview = previewId == null
                ? Component.translatable("screen.yblk_zones.particle_preview_hint").getString()
                : Component.translatable("screen.yblk_zones.particle_preview", previewId.getPath()).getString();
        g.drawString(font, font.plainSubstrByWidth(preview, panelW - 20),
                panelX + 10, panelY + panelH - 38, MUTED, false);
        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public void removed() {
        restoreSounds();
        super.removed();
    }

    @Override
    public void onClose() {
        restoreSounds();
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
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
            previewId = id;
            onPick.accept(id.toString());
            closeToParent();
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
            boolean hov = isHoveredOrFocused();
            if (hov) {
                previewId = id;
            }
            g.fill(getX(), getY(), getX() + width, getY() + height, hov ? 0xFF2A4A44 : ROW);
            g.drawString(font, font.plainSubstrByWidth(id.toString(), width - 8), getX() + 4, getY() + 3, TEXT, false);
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
