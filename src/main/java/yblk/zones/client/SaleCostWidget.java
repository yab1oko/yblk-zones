package yblk.zones.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import yblk.zones.zone.SaleCost;

import java.util.function.IntConsumer;

/** Item icon slot + name + count (− / field / +) for one sale price line. */
public class SaleCostWidget extends AbstractWidget {
    private static final int SLOT = 20;
    private static final int BTN = 14;
    private static final int COUNT_W = 36;

    private final Screen parent;
    private final int index;
    private final IntConsumer onCountChange;
    private final Runnable onRemove;
    private final EditBox countBox;
    private String itemId;
    private int count;
    private final boolean canRemove;

    public SaleCostWidget(int x, int y, int w, Screen parent, int index, SaleCost cost,
            IntConsumer onCountChange, Runnable onRemove) {
        super(x, y, w, 36, Component.empty());
        this.parent = parent;
        this.index = index;
        this.onCountChange = onCountChange;
        this.onRemove = onRemove;
        this.itemId = cost == null ? "" : cost.itemId();
        this.count = cost == null ? 1 : Math.max(1, cost.count());
        this.canRemove = onRemove != null;

        int boxX = countBoxX(x, w);
        this.countBox = new EditBox(Minecraft.getInstance().font, boxX, y + 18, COUNT_W, 14,
                Component.translatable("screen.yblk_zones.sale_count_hint"));
        this.countBox.setMaxLength(4);
        this.countBox.setValue(String.valueOf(this.count));
        this.countBox.setFilter(s -> s.isEmpty() || s.chars().allMatch(Character::isDigit));
        this.countBox.setResponder(this::onCountTyped);
        this.countBox.setTextColor(0xFFE8E8F0);
        this.countBox.setBordered(true);
    }

    private static int countBoxX(int x, int w) {
        // layout from right: [×][+][box][−]…  (remove always reserved for alignment)
        int right = x + w - 4 - BTN; // remove slot
        right -= BTN; // +
        right -= COUNT_W;
        return right;
    }

    public EditBox countBox() {
        return countBox;
    }

    public int index() {
        return index;
    }

    public void setItemId(String id) {
        this.itemId = id == null ? "" : id;
    }

    public void setPosition(int x, int y) {
        this.setX(x);
        this.setY(y);
        countBox.setX(countBoxX(x, width));
        countBox.setY(y + 18);
    }

    private void onCountTyped(String s) {
        if (s == null || s.isBlank()) {
            return;
        }
        try {
            int v = Mth.clamp(Integer.parseInt(s), 1, 9999);
            if (v != count) {
                count = v;
                onCountChange.accept(v);
            }
        } catch (NumberFormatException ignored) {
        }
    }

    private void setCount(int v) {
        count = Mth.clamp(v, 1, 9999);
        String s = String.valueOf(count);
        if (!s.equals(countBox.getValue())) {
            countBox.setValue(s);
        }
        onCountChange.accept(count);
    }

    private ItemStack preview() {
        if (itemId.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) {
            return ItemStack.EMPTY;
        }
        Item item = ForgeRegistries.ITEMS.getValue(id);
        if (item == null || item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item);
    }

    private void openPicker() {
        Minecraft.getInstance().setScreen(new ItemPickerScreen(parent, id -> {
            itemId = id;
            if (parent instanceof ZoneCreateScreen create) {
                create.setSaleItemId(index, id);
            }
        }));
    }

    private int minusX() {
        return countBox.getX() - BTN - 1;
    }

    private int plusX() {
        return countBox.getX() + COUNT_W + 1;
    }

    private int removeX() {
        return getX() + width - 4 - BTN;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.active || !this.visible || button != 0) {
            return false;
        }
        // Let the separate EditBox child handle its own clicks
        if (mouseX >= countBox.getX() && mouseX < countBox.getX() + COUNT_W
                && mouseY >= countBox.getY() && mouseY < countBox.getY() + 14) {
            return false;
        }
        if (mouseX >= minusX() && mouseX < minusX() + BTN
                && mouseY >= getY() + 18 && mouseY < getY() + 18 + BTN) {
            setCount(count - 1);
            playDownSound(Minecraft.getInstance().getSoundManager());
            return true;
        }
        if (mouseX >= plusX() && mouseX < plusX() + BTN
                && mouseY >= getY() + 18 && mouseY < getY() + 18 + BTN) {
            setCount(count + 1);
            playDownSound(Minecraft.getInstance().getSoundManager());
            return true;
        }
        if (canRemove && mouseX >= removeX() && mouseX < removeX() + BTN
                && mouseY >= getY() + 2 && mouseY < getY() + 2 + BTN) {
            onRemove.run();
            playDownSound(Minecraft.getInstance().getSoundManager());
            return true;
        }
        if (clicked(mouseX, mouseY)) {
            openPicker();
            playDownSound(Minecraft.getInstance().getSoundManager());
            return true;
        }
        return false;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
        int x = getX();
        int y = getY();
        boolean inCount = mouseX >= countBox.getX() - BTN && mouseX < plusX() + BTN
                && mouseY >= y + 16;
        boolean hov = isHoveredOrFocused() && !inCount;

        g.fill(x, y, x + width, y + height, hov ? 0xFF242430 : 0xFF1A1A22);

        g.fill(x + 2, y + 2, x + 2 + SLOT, y + 2 + SLOT, 0xFF3A3A48);
        g.fill(x + 3, y + 3, x + 1 + SLOT, y + 1 + SLOT, 0xFF121218);
        ItemStack stack = preview();
        if (!stack.isEmpty()) {
            g.renderItem(stack, x + 4, y + 4);
            g.renderItemDecorations(Minecraft.getInstance().font, stack, x + 4, y + 4);
        } else {
            g.drawCenteredString(Minecraft.getInstance().font, "?", x + 2 + SLOT / 2, y + 8, 0xFF9A9AA8);
        }

        var font = Minecraft.getInstance().font;
        String name = stack.isEmpty()
                ? Component.translatable("screen.yblk_zones.pick_item").getString()
                : stack.getHoverName().getString();
        int nameMax = width - SLOT - (canRemove ? BTN + 10 : 12);
        if (font.width(name) > nameMax) {
            name = font.plainSubstrByWidth(name, nameMax - font.width("…")) + "…";
        }
        g.drawString(font, name, x + SLOT + 6, y + 6, stack.isEmpty() ? 0xFF9A9AA8 : 0xFFE8E8F0, false);

        drawBtn(g, minusX(), y + 18, "−", mouseX, mouseY);
        drawBtn(g, plusX(), y + 18, "+", mouseX, mouseY);
        if (canRemove) {
            drawBtn(g, removeX(), y + 2, "×", mouseX, mouseY);
        }
    }

    private void drawBtn(GuiGraphics g, int bx, int by, String label, int mouseX, int mouseY) {
        boolean hot = mouseX >= bx && mouseX < bx + BTN && mouseY >= by && mouseY < by + BTN;
        g.fill(bx, by, bx + BTN, by + BTN, hot ? 0xFF4EC9B0 : 0xFF3A3A48);
        g.fill(bx + 1, by + 1, bx + BTN - 1, by + BTN - 1, 0xFF1A1A22);
        g.drawCenteredString(Minecraft.getInstance().font, label, bx + BTN / 2, by + 3, 0xFFE8E8F0);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }
}
