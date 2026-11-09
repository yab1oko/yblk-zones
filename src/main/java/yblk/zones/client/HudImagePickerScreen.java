package yblk.zones.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * In-game image picker (no AWT). Lists PNGs from config/hud_images plus Desktop/Pictures/Downloads,
 * and accepts a pasted absolute path.
 */
public class HudImagePickerScreen extends Screen {
    private static final int PANEL = 0xFF16161C;
    private static final int ACCENT = 0xFF4EC9B0;
    private static final int ROW = 0xFF1E1E26;
    private static final int TEXT = 0xFFF0F0F5;
    private static final int MUTED = 0xFF9A9AA8;

    private final Screen parent;
    private final Consumer<String> onPick;
    private final List<Path> files = new ArrayList<>();
    private final List<AbstractWidget> rows = new ArrayList<>();
    private EditBox pathBox;
    private int scroll;
    private int panelX, panelY, panelW, panelH;
    private int listTop, listBottom;

    public HudImagePickerScreen(Screen parent, Consumer<String> onPick) {
        super(Component.translatable("screen.yblk_zones.hud_image_pick"));
        this.parent = parent;
        this.onPick = onPick;
    }

    @Override
    protected void init() {
        clearWidgets();
        rows.clear();
        panelW = Math.min(380, width - 16);
        panelH = Math.min(300, height - 16);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        listTop = panelY + 56;
        listBottom = panelY + panelH - 44;

        pathBox = new EditBox(font, panelX + 10, panelY + 34, panelW - 100, 14,
                Component.translatable("screen.yblk_zones.hud_image_path"));
        pathBox.setMaxLength(512);
        pathBox.setHint(Component.translatable("screen.yblk_zones.hud_image_path_hint"));
        pathBox.setTextColor(TEXT);
        addRenderableWidget(pathBox);

        addRenderableWidget(new Btn(panelX + panelW - 86, panelY + 34, 76, 14,
                Component.translatable("screen.yblk_zones.hud_image_use_path"), this::importFromPathBox));

        refreshFileList();
        rebuildRows();

        addRenderableWidget(new Btn(panelX + 10, panelY + panelH - 22, 90, 14,
                Component.translatable("screen.yblk_zones.refresh"), () -> {
                    refreshFileList();
                    scroll = 0;
                    rebuildRows();
                }));
        addRenderableWidget(new Btn(panelX + 106, panelY + panelH - 22, 110, 14,
                Component.translatable("screen.yblk_zones.open_hud_folder"), this::openHudFolder));
        addRenderableWidget(new Btn(panelX + panelW - 90, panelY + panelH - 22, 80, 14,
                Component.translatable("gui.cancel"), () -> minecraft.setScreen(parent)));
    }

    private void refreshFileList() {
        files.clear();
        addImagesFrom(ClientHudImages.imagesDir());
        String home = System.getProperty("user.home");
        if (home != null) {
            Path h = Path.of(home);
            addImagesFrom(h.resolve("Desktop"));
            addImagesFrom(h.resolve("Pictures"));
            addImagesFrom(h.resolve("Downloads"));
            // RU Windows localized names
            addImagesFrom(h.resolve("Рабочий стол"));
            addImagesFrom(h.resolve("Изображения"));
            addImagesFrom(h.resolve("Загрузки"));
        }
        files.sort(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)));
    }

    private void addImagesFrom(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path p : stream) {
                if (Files.isRegularFile(p) && isImageName(p.getFileName().toString()) && !files.contains(p)) {
                    files.add(p);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static boolean isImageName(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg")
                || n.endsWith(".gif") || n.endsWith(".bmp") || n.endsWith(".webp");
    }

    private void rebuildRows() {
        for (AbstractWidget w : rows) {
            removeWidget(w);
        }
        rows.clear();
        int y = listTop - scroll;
        for (Path file : files) {
            String label = file.getFileName().toString();
            Path parentDir = file.getParent();
            if (parentDir != null) {
                label = parentDir.getFileName() + "/" + label;
            }
            FileRow row = new FileRow(panelX + 10, y, panelW - 20, 16, file, label);
            boolean show = y + 16 > listTop && y < listBottom;
            row.visible = show;
            row.active = show;
            rows.add(row);
            addRenderableWidget(row);
            y += 18;
        }
    }

    private void importFromPathBox() {
        String raw = pathBox.getValue() == null ? "" : pathBox.getValue().trim().replace("\"", "");
        if (raw.isEmpty()) {
            return;
        }
        pickPath(Path.of(raw));
    }

    private void pickPath(Path path) {
        try {
            if (!Files.isRegularFile(path)) {
                fail(Component.translatable("message.yblk_zones.hud_image_missing"));
                return;
            }
            String stored = ClientHudImages.importFile(path);
            if (ClientHudImages.resolve(stored) == null) {
                fail(Component.translatable("message.yblk_zones.hud_image_fail"));
                return;
            }
            onPick.accept(stored);
            minecraft.setScreen(parent);
        } catch (Exception e) {
            fail(Component.translatable("message.yblk_zones.hud_image_fail"));
        }
    }

    private void fail(Component msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(msg, true);
        }
    }

    private void openHudFolder() {
        try {
            Path dir = ClientHudImages.imagesDir();
            java.awt.Desktop.getDesktop().open(dir.toFile());
        } catch (Exception e) {
            fail(Component.translatable("message.yblk_zones.hud_folder_fail",
                    ClientHudImages.imagesDir().toAbsolutePath().toString()));
        }
    }

    @Override
    public void tick() {
        super.tick();
        pathBox.tick();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int content = files.size() * 18;
        int view = Math.max(1, listBottom - listTop);
        scroll = Mth.clamp(scroll - (int) (delta * 18), 0, Math.max(0, content - view));
        rebuildRows();
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL);
        g.fill(panelX, panelY, panelX + panelW, panelY + 1, ACCENT);
        g.drawString(font, title, panelX + 10, panelY + 8, ACCENT, false);
        g.drawString(font, Component.translatable("screen.yblk_zones.hud_image_pick_hint"),
                panelX + 10, panelY + 22, MUTED, false);
        if (files.isEmpty()) {
            g.drawString(font, Component.translatable("screen.yblk_zones.hud_image_empty"),
                    panelX + 14, listTop + 8, MUTED, false);
        }
        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private class FileRow extends AbstractWidget {
        private final Path path;

        FileRow(int x, int y, int w, int h, Path path, String label) {
            super(x, y, w, h, Component.literal(label));
            this.path = path;
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            pickPath(path);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
            g.fill(getX(), getY(), getX() + width, getY() + height, isHoveredOrFocused() ? 0xFF2A4A44 : ROW);
            g.drawString(font, font.plainSubstrByWidth(getMessage().getString(), width - 8),
                    getX() + 4, getY() + 4, TEXT, false);
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
            g.drawCenteredString(Minecraft.getInstance().font,
                    Minecraft.getInstance().font.plainSubstrByWidth(getMessage().getString(), width - 4),
                    getX() + width / 2, getY() + 3, TEXT);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            defaultButtonNarrationText(out);
        }
    }
}
