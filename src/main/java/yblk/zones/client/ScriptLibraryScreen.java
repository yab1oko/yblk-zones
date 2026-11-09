package yblk.zones.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import yblk.zones.script.ZoneScriptBundle;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Save / import zone scripts (files in config/yblk_zones/scripts). */
public class ScriptLibraryScreen extends Screen {
    private static final int PANEL = 0xFF16161C;
    private static final int ACCENT = 0xFF4EC9B0;
    private static final int ROW = 0xFF1E1E26;
    private static final int ON = 0xFF3D8B6E;
    private static final int TEXT = 0xFFF0F0F5;
    private static final int MUTED = 0xFF9A9AA8;
    private static final int ERR = 0xFFE57373;

    private final Screen parent;
    private final ZoneScriptBundle current;
    private final Consumer<ZoneScriptBundle> onImport;
    private EditBox nameBox;
    private final List<AbstractWidget> rows = new ArrayList<>();
    private int scroll;
    private int panelX, panelY, panelW, panelH;
    private String status = "";
    private boolean statusError;

    public ScriptLibraryScreen(Screen parent, ZoneScriptBundle current, Consumer<ZoneScriptBundle> onImport) {
        super(Component.translatable("screen.yblk_zones.script_library"));
        this.parent = parent;
        this.current = current;
        this.onImport = onImport;
    }

    @Override
    protected void init() {
        clearWidgets();
        rows.clear();
        panelW = Math.min(380, width - 16);
        panelH = Math.min(300, height - 16);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;

        nameBox = new EditBox(font, panelX + 10, panelY + 28, panelW - 100, 16,
                Component.translatable("screen.yblk_zones.script_library_name"));
        nameBox.setMaxLength(48);
        nameBox.setValue("raid");
        nameBox.setTextColor(TEXT);
        addRenderableWidget(nameBox);

        addRenderableWidget(new Btn(panelX + panelW - 84, panelY + 26, 74, 18,
                Component.translatable("screen.yblk_zones.script_library_save"), this::saveCurrent));

        addRenderableWidget(new Btn(panelX + 10, panelY + panelH - 44, 110, 18,
                Component.translatable("screen.yblk_zones.script_library_clip_copy"), this::copyClipboard));
        addRenderableWidget(new Btn(panelX + 126, panelY + panelH - 44, 110, 18,
                Component.translatable("screen.yblk_zones.script_library_clip_paste"), this::pasteClipboard));
        addRenderableWidget(new Btn(panelX + panelW - 90, panelY + panelH - 44, 80, 18,
                Component.translatable("gui.cancel"), () -> minecraft.setScreen(parent)));

        rebuildList();
    }

    private void rebuildList() {
        for (AbstractWidget w : rows) {
            removeWidget(w);
        }
        rows.clear();
        List<Path> files = ZoneScriptLibrary.list();
        int top = panelY + 52;
        int bottom = panelY + panelH - 52;
        int y = top - scroll;
        if (files.isEmpty()) {
            return;
        }
        for (Path file : files) {
            String name = file.getFileName().toString();
            Row row = new Row(panelX + 10, y, panelW - 56, 20, file, name);
            Btn del = new Btn(panelX + panelW - 42, y, 32, 20, Component.literal("✕"), () -> {
                try {
                    ZoneScriptLibrary.delete(file);
                    status = Component.translatable("screen.yblk_zones.script_library_deleted", name).getString();
                    statusError = false;
                } catch (Exception e) {
                    status = e.getMessage() == null ? "delete fail" : e.getMessage();
                    statusError = true;
                }
                rebuildList();
            });
            boolean show = y + 20 > top && y < bottom;
            row.visible = show;
            row.active = show;
            del.visible = show;
            del.active = show;
            rows.add(row);
            rows.add(del);
            addRenderableWidget(row);
            addRenderableWidget(del);
            y += 24;
        }
    }

    private void saveCurrent() {
        try {
            Path file = ZoneScriptLibrary.save(nameBox.getValue(), current);
            status = Component.translatable("screen.yblk_zones.script_library_saved",
                    file.getFileName().toString()).getString();
            statusError = false;
            rebuildList();
        } catch (Exception e) {
            status = e.getMessage() == null ? "save fail" : e.getMessage();
            statusError = true;
        }
    }

    private void importFile(Path file) {
        try {
            ZoneScriptBundle bundle = ZoneScriptLibrary.load(file);
            onImport.accept(bundle);
            status = Component.translatable("screen.yblk_zones.script_library_imported",
                    file.getFileName().toString(), bundle.actions().size()).getString();
            statusError = false;
            minecraft.setScreen(parent);
        } catch (Exception e) {
            status = e.getMessage() == null ? "import fail" : e.getMessage();
            statusError = true;
        }
    }

    private void copyClipboard() {
        Minecraft.getInstance().keyboardHandler.setClipboard(current.encode());
        status = Component.translatable("screen.yblk_zones.script_library_clip_copied").getString();
        statusError = false;
    }

    private void pasteClipboard() {
        String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
        try {
            ZoneScriptBundle bundle = ZoneScriptBundle.decode(clip);
            if (bundle.actions().isEmpty() && !bundle.hasEventMeta()) {
                throw new IllegalArgumentException("empty");
            }
            onImport.accept(bundle);
            status = Component.translatable("screen.yblk_zones.script_library_imported",
                    "clipboard", bundle.actions().size()).getString();
            statusError = false;
            minecraft.setScreen(parent);
        } catch (Exception e) {
            status = Component.translatable("screen.yblk_zones.script_library_clip_fail").getString();
            statusError = true;
        }
    }

    @Override
    public void tick() {
        super.tick();
        nameBox.tick();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int content = ZoneScriptLibrary.list().size() * 24;
        int view = Math.max(1, panelH - 110);
        scroll = Mth.clamp(scroll - (int) (delta * 18), 0, Math.max(0, content - view));
        rebuildList();
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL);
        g.fill(panelX, panelY, panelX + panelW, panelY + 1, ACCENT);
        g.drawString(font, title, panelX + 10, panelY + 10, ACCENT, false);
        if (!status.isEmpty()) {
            g.drawString(font, font.plainSubstrByWidth(status, panelW - 20),
                    panelX + 10, panelY + panelH - 22, statusError ? ERR : MUTED, false);
        }
        if (ZoneScriptLibrary.list().isEmpty()) {
            g.drawCenteredString(font, Component.translatable("screen.yblk_zones.script_library_empty"),
                    panelX + panelW / 2, panelY + panelH / 2 - 4, MUTED);
        }
        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private class Row extends AbstractWidget {
        private final Path file;

        Row(int x, int y, int w, int h, Path file, String label) {
            super(x, y, w, h, Component.literal(label));
            this.file = file;
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            importFile(file);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
            g.fill(getX(), getY(), getX() + width, getY() + height,
                    isHoveredOrFocused() ? ON : ROW);
            g.drawString(font, font.plainSubstrByWidth(getMessage().getString(), width - 8),
                    getX() + 6, getY() + 6, TEXT, false);
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
                    getX() + width / 2, getY() + (height - 8) / 2, TEXT);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            defaultButtonNarrationText(out);
        }
    }
}
