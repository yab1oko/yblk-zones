package yblk.zones.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import yblk.zones.script.ZoneAction;
import yblk.zones.script.ZoneScriptText;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Full-screen multiline editor for zone scripts. */
public class ScriptTextEditorScreen extends Screen {
    private static final int PANEL = 0xFF16161C;
    private static final int ACCENT = 0xFF4EC9B0;
    private static final int TEXT = 0xFFF0F0F5;
    private static final int MUTED = 0xFF9A9AA8;
    private static final int ERR = 0xFFE57373;

    private final Screen parent;
    private final List<ZoneAction> source;
    private final Consumer<List<ZoneAction>> onApply;
    private MultiLineEditBox editor;
    private String status = "";
    private boolean statusError;

    public ScriptTextEditorScreen(Screen parent, List<ZoneAction> actions, Consumer<List<ZoneAction>> onApply) {
        super(Component.translatable("screen.yblk_zones.script_editor"));
        this.parent = parent;
        this.source = actions;
        this.onApply = onApply;
    }

    @Override
    protected void init() {
        int pad = 10;
        int top = 28;
        int bottomBar = 48;
        int boxH = Math.max(80, height - top - bottomBar - pad);
        editor = new MultiLineEditBox(font, pad, top, width - pad * 2, boxH,
                Component.translatable("screen.yblk_zones.script_editor_hint"),
                Component.translatable("screen.yblk_zones.script_editor"));
        editor.setValue(ZoneScriptText.encode(source));
        addRenderableWidget(editor);

        int bw = 90;
        int by = height - 28;
        addRenderableWidget(Button.builder(Component.translatable("screen.yblk_zones.script_editor_apply"), b -> apply())
                .bounds(pad, by, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.yblk_zones.script_editor_reload"), b -> {
            editor.setValue(ZoneScriptText.encode(source));
            status = Component.translatable("screen.yblk_zones.script_editor_reloaded").getString();
            statusError = false;
        }).bounds(pad + bw + 6, by, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> minecraft.setScreen(parent))
                .bounds(width - pad - bw, by, bw, 20).build());
    }

    private void apply() {
        try {
            List<ZoneAction> parsed = ZoneScriptText.decode(editor.getValue());
            onApply.accept(new ArrayList<>(parsed));
            status = Component.translatable("screen.yblk_zones.script_editor_ok", parsed.size()).getString();
            statusError = false;
            minecraft.setScreen(parent);
        } catch (IllegalArgumentException e) {
            status = e.getMessage();
            statusError = true;
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        g.fill(0, 0, width, 24, PANEL);
        g.fill(0, 23, width, 24, ACCENT);
        g.drawString(font, title, 10, 8, ACCENT, false);
        if (!status.isEmpty()) {
            g.drawString(font, font.plainSubstrByWidth(status, width - 20), 10, height - 42,
                    statusError ? ERR : MUTED, false);
        }
        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
