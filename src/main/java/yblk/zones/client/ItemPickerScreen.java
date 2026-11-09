package yblk.zones.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/** Visual searchable grid to pick a cost item (inventory first, then all items). */
public class ItemPickerScreen extends Screen {
    private static final int COLS = 9;
    private static final int CELL = 20;
    private static final int PAD = 8;

    private final Screen parent;
    private final Consumer<String> onPick;
    private EditBox search;
    private final List<ItemStack> invStacks = new ArrayList<>();
    private final List<ItemStack> allStacks = new ArrayList<>();
    private final List<ItemStack> filtered = new ArrayList<>();
    private int scrollRows;
    private int panelX, panelY, panelW, panelH;
    private int gridTop;
    private boolean inventoryOnly = true;

    public ItemPickerScreen(Screen parent, Consumer<String> onPick) {
        super(Component.translatable("screen.yblk_zones.pick_item"));
        this.parent = parent;
        this.onPick = onPick;
    }

    @Override
    protected void init() {
        panelW = Math.min(220, width - 16);
        panelH = Math.min(240, height - 16);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        gridTop = panelY + 52;

        search = new EditBox(font, panelX + PAD, panelY + 28, panelW - PAD * 2, 16,
                Component.translatable("screen.yblk_zones.search"));
        search.setMaxLength(64);
        search.setResponder(s -> {
            scrollRows = 0;
            // Typing → search all items, not only inventory
            if (s != null && !s.isBlank()) {
                inventoryOnly = false;
            }
            refilter();
        });
        addRenderableWidget(search);
        setInitialFocus(search);

        rebuildCatalog();
        refilter();
    }

    private void rebuildCatalog() {
        invStacks.clear();
        allStacks.clear();
        Map<Item, Integer> invCounts = new LinkedHashMap<>();
        if (minecraft != null && minecraft.player != null) {
            for (ItemStack stack : minecraft.player.getInventory().items) {
                if (!stack.isEmpty() && stack.getItem() != Items.AIR) {
                    invCounts.merge(stack.getItem(), stack.getCount(), Integer::sum);
                }
            }
            for (ItemStack stack : minecraft.player.getInventory().offhand) {
                if (!stack.isEmpty() && stack.getItem() != Items.AIR) {
                    invCounts.merge(stack.getItem(), stack.getCount(), Integer::sum);
                }
            }
        }
        for (Map.Entry<Item, Integer> e : invCounts.entrySet()) {
            ItemStack s = new ItemStack(e.getKey());
            s.setCount(Math.min(e.getValue(), s.getMaxStackSize()));
            invStacks.add(s);
        }
        invStacks.sort(Comparator.comparing(s -> s.getHoverName().getString().toLowerCase(Locale.ROOT)));

        List<Item> sorted = new ArrayList<>();
        for (Item item : ForgeRegistries.ITEMS) {
            if (item != null && item != Items.AIR) {
                sorted.add(item);
            }
        }
        sorted.sort(Comparator.comparing(i -> {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(i);
            return id == null ? "" : id.toString();
        }));
        for (Item item : sorted) {
            allStacks.add(new ItemStack(item));
        }
    }

    private void refilter() {
        filtered.clear();
        String q = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        List<ItemStack> src = inventoryOnly && q.isEmpty() ? invStacks : allStacks;
        if (inventoryOnly && q.isEmpty() && invStacks.isEmpty()) {
            src = allStacks;
            inventoryOnly = false;
        }
        for (ItemStack stack : src) {
            if (q.isEmpty() || matches(stack, q)) {
                filtered.add(stack);
            }
        }
        scrollRows = Mth.clamp(scrollRows, 0, maxScroll());
    }

    private boolean matches(ItemStack stack, String q) {
        String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        String path = id == null ? "" : id.toString().toLowerCase(Locale.ROOT);
        return name.contains(q) || path.contains(q);
    }

    private int visibleRows() {
        return Math.max(1, (panelY + panelH - PAD - gridTop) / CELL);
    }

    private int maxScroll() {
        int rows = (filtered.size() + COLS - 1) / COLS;
        return Math.max(0, rows - visibleRows());
    }

    private void pick(ItemStack stack) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id != null) {
            onPick.accept(id.toString());
        }
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        g.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, 0xFF3A3A48);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xF0121218);
        g.drawString(font, title, panelX + PAD, panelY + 6, 0xFF4EC9B0, false);

        String tab = inventoryOnly
                ? Component.translatable("screen.yblk_zones.pick_inv").getString()
                : Component.translatable("screen.yblk_zones.pick_all").getString();
        int tabW = font.width(tab) + 8;
        int tabX = panelX + panelW - PAD - tabW;
        boolean tabHov = mouseX >= tabX && mouseX < tabX + tabW && mouseY >= panelY + 4 && mouseY < panelY + 16;
        g.fill(tabX, panelY + 4, tabX + tabW, panelY + 16, tabHov ? 0xFF2A6B5E : 0xFF1A1A22);
        g.drawCenteredString(font, tab, tabX + tabW / 2, panelY + 6, 0xFFE8E8F0);

        int rows = visibleRows();
        int start = scrollRows * COLS;
        ItemStack hovered = ItemStack.EMPTY;
        for (int i = 0; i < rows * COLS; i++) {
            int idx = start + i;
            int col = i % COLS;
            int row = i / COLS;
            int x = panelX + PAD + col * CELL;
            int y = gridTop + row * CELL;
            g.fill(x, y, x + CELL - 2, y + CELL - 2, 0xFF1A1A22);
            if (idx >= filtered.size()) {
                continue;
            }
            ItemStack stack = filtered.get(idx);
            g.renderItem(stack, x + 2, y + 2);
            if (stack.getCount() > 1) {
                g.renderItemDecorations(font, stack, x + 2, y + 2);
            }
            boolean hot = mouseX >= x && mouseX < x + CELL - 2 && mouseY >= y && mouseY < y + CELL - 2;
            if (hot) {
                g.fill(x, y, x + CELL - 2, y + CELL - 2, 0x66FFFFFF);
                hovered = stack;
            }
        }

        if (filtered.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("screen.yblk_zones.pick_empty"),
                    panelX + panelW / 2, gridTop + 24, 0xFF9A9AA8);
        }

        super.render(g, mouseX, mouseY, partial);
        if (!hovered.isEmpty()) {
            g.renderTooltip(font, hovered, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            String tab = inventoryOnly
                    ? Component.translatable("screen.yblk_zones.pick_inv").getString()
                    : Component.translatable("screen.yblk_zones.pick_all").getString();
            int tabW = font.width(tab) + 8;
            int tabX = panelX + panelW - PAD - tabW;
            if (mouseX >= tabX && mouseX < tabX + tabW && mouseY >= panelY + 4 && mouseY < panelY + 16) {
                inventoryOnly = !inventoryOnly;
                scrollRows = 0;
                refilter();
                return true;
            }

            int rows = visibleRows();
            int start = scrollRows * COLS;
            for (int i = 0; i < rows * COLS; i++) {
                int idx = start + i;
                if (idx >= filtered.size()) {
                    break;
                }
                int col = i % COLS;
                int row = i / COLS;
                int x = panelX + PAD + col * CELL;
                int y = gridTop + row * CELL;
                if (mouseX >= x && mouseX < x + CELL - 2 && mouseY >= y && mouseY < y + CELL - 2) {
                    pick(filtered.get(idx));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta > 0) {
            scrollRows = Math.max(0, scrollRows - 1);
        } else if (delta < 0) {
            scrollRows = Math.min(maxScroll(), scrollRows + 1);
        }
        return true;
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
}
