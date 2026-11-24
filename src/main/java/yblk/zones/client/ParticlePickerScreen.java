package yblk.zones.client;

import com.google.gson.JsonElement;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.NativeImage;
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
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.HashMap;
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
    private int previewX, previewY, previewW, previewH;
    private int listW;
    private ResourceLocation previewId;
    private final Map<ResourceLocation, List<Sheet>> frameCache = new HashMap<>();
    private ResourceLocation bitsFor;
    private final List<GuiBit> bits = new ArrayList<>();
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
        panelW = Math.min(460, width - 16);
        panelH = Math.min(280, height - 16);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        previewW = Math.min(148, panelW / 3);
        previewH = panelH - 28;
        previewX = panelX + panelW - previewW - 8;
        previewY = panelY + 14;
        listW = previewX - panelX - 12;
        search = new EditBox(font, panelX + 10, panelY + 22, listW - 16, 14,
                Component.translatable("screen.yblk_zones.search"));
        search.setResponder(this::filter);
        search.setTextColor(TEXT);
        addRenderableWidget(search);
        rebuildList();
        addRenderableWidget(new Btn(panelX + 10, panelY + panelH - 22, 80, 14,
                Component.translatable("gui.cancel"), this::closeToParent));
        addRenderableWidget(new Btn(panelX + listW - 90, panelY + panelH - 22, 80, 14,
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
            Row row = new Row(panelX + 10, y, listW - 16, 14, id);
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
        if (!previewId.equals(bitsFor)) {
            bits.clear();
            bitsFor = previewId;
        }
        Iterator<GuiBit> it = bits.iterator();
        while (it.hasNext()) {
            GuiBit bit = it.next();
            bit.x += bit.vx;
            bit.y += bit.vy;
            bit.vy += 0.04F;
            if (--bit.life <= 0 || bit.y > previewH) {
                it.remove();
            }
        }
        if (bits.size() < 3 && previewTicks % 5 == 0 && previewW > 8) {
            var rand = mc.level.random;
            bits.add(new GuiBit(
                    previewW / 2F + (rand.nextFloat() - 0.5F) * 16F,
                    previewH * 0.55F,
                    (rand.nextFloat() - 0.5F) * 0.25F,
                    -0.22F - rand.nextFloat() * 0.15F,
                    36 + rand.nextInt(18)));
        }
    }

    private List<Sheet> framesFor(ResourceLocation id) {
        List<Sheet> cached = frameCache.get(id);
        if (cached != null) {
            return cached;
        }
        List<Sheet> found = new ArrayList<>();
        ResourceLocation json = new ResourceLocation(id.getNamespace(), "particles/" + id.getPath() + ".json");
        Minecraft mc = Minecraft.getInstance();
        try {
            var resource = mc.getResourceManager().getResource(json);
            if (resource.isPresent()) {
                try (var reader = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
                    var obj = GsonHelper.parse(reader);
                    for (JsonElement el : GsonHelper.getAsJsonArray(obj, "textures")) {
                        Sheet sheet = sheetFor(new ResourceLocation(el.getAsString()));
                        if (sheet != null) {
                            found.add(sheet);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            found.clear();
        }
        frameCache.put(id, found);
        return found;
    }

    private static Sheet sheetFor(ResourceLocation tex) {
        String path = tex.getPath().startsWith("textures/")
                ? tex.getPath()
                : "textures/particle/" + tex.getPath() + ".png";
        ResourceLocation file = new ResourceLocation(tex.getNamespace(), path);
        Minecraft mc = Minecraft.getInstance();
        try {
            var resource = mc.getResourceManager().getResource(file);
            if (resource.isEmpty()) {
                return null;
            }
            try (var in = resource.get().open()) {
                NativeImage image = NativeImage.read(in);
                int w = image.getWidth();
                int h = image.getHeight();
                image.close();
                int frame = w > 0 && h > w && h % w == 0 ? w : Math.min(w, h);
                return new Sheet(file, w, h, Math.max(1, frame));
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private static final class Sheet {
        final ResourceLocation file;
        final int width;
        final int height;
        final int frame;

        Sheet(ResourceLocation file, int width, int height, int frame) {
            this.file = file;
            this.width = width;
            this.height = height;
            this.frame = frame;
        }
    }

    private static final class GuiBit {
        float x;
        float y;
        float vx;
        float vy;
        int life;

        GuiBit(float x, float y, float vx, float vy, int life) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.life = life;
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
        int dim = 0xFF101014;
        int x0 = previewX;
        int y0 = previewY;
        int x1 = previewX + previewW;
        int y1 = previewY + previewH;
        g.fill(0, 0, this.width, y0, dim);
        g.fill(0, y0, x0, y1, dim);
        g.fill(x1, y0, this.width, y1, dim);
        g.fill(0, y1, this.width, this.height, dim);
        g.fill(x0 - 1, y0 - 1, x1 + 1, y0, ACCENT);
        g.fill(x0 - 1, y1, x1 + 1, y1 + 1, ACCENT);
        g.fill(x0 - 1, y0, x0, y1, ACCENT);
        g.fill(x1, y0, x1 + 1, y1, ACCENT);
        g.fill(x0, y0, x1, y1, 0xFF12121A);
        g.fill(panelX, panelY, previewX - 4, panelY + panelH, PANEL);
        g.fill(panelX, panelY, panelX + panelW, panelY + 1, ACCENT);
        g.drawString(font, title, panelX + 10, panelY + 8, ACCENT, false);
        String preview = previewId == null
                ? Component.translatable("screen.yblk_zones.particle_preview_hint").getString()
                : Component.translatable("screen.yblk_zones.particle_preview", previewId.getPath()).getString();
        g.drawString(font, font.plainSubstrByWidth(preview, listW - 20),
                panelX + 10, panelY + panelH - 38, MUTED, false);
        super.render(g, mouseX, mouseY, partial);
        g.fill(x0, y0, x1, y1, 0xFF12121A);
        g.fill(x0 - 1, y0 - 1, x1 + 1, y0, ACCENT);
        g.fill(x0 - 1, y1, x1 + 1, y1 + 1, ACCENT);
        g.fill(x0 - 1, y0, x0, y1, ACCENT);
        g.fill(x1, y0, x1 + 1, y1, ACCENT);
        List<Sheet> frames = previewId == null ? List.of() : framesFor(previewId);
        Sheet sheet = frames.isEmpty() ? null : frames.get((previewTicks / 4) % frames.size());
        int size = 48;
        for (GuiBit bit : bits) {
            int px = x0 + Math.round(bit.x) - size / 2;
            int py = y0 + Math.round(bit.y) - size / 2;
            px = Mth.clamp(px, x0 + 2, Math.max(x0 + 2, x1 - size - 2));
            py = Mth.clamp(py, y0 + 2, Math.max(y0 + 2, y1 - size - 2));
            if (sheet != null) {
                int count = Math.max(1, sheet.height / sheet.frame);
                int index = (previewTicks / 4) % count;
                float v = index * sheet.frame;
                g.blit(sheet.file, px, py, size, size, 0, v, sheet.frame, sheet.frame, sheet.width, sheet.height);
            } else {
                g.fill(px + 8, py + 8, px + 20, py + 20, ACCENT);
            }
        }
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
