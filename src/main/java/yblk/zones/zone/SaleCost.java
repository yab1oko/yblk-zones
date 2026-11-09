package yblk.zones.zone;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

/** One item+count line in a zone sale price. */
public final class SaleCost {
    private String itemId;
    private int count;

    public SaleCost(String itemId, int count) {
        this.itemId = itemId == null ? "" : itemId;
        this.count = Math.max(1, count);
    }

    public String itemId() {
        return itemId;
    }

    public int count() {
        return count;
    }

    public void setItemId(String id) {
        this.itemId = id == null ? "" : id;
    }

    public void setCount(int count) {
        this.count = Math.max(1, count);
    }

    public boolean isValid() {
        if (itemId.isBlank() || count <= 0) {
            return false;
        }
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) {
            return false;
        }
        Item item = ForgeRegistries.ITEMS.getValue(id);
        return item != null && item != Items.AIR;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Item", itemId);
        tag.putInt("Count", count);
        return tag;
    }

    public static SaleCost load(CompoundTag tag) {
        return new SaleCost(tag.getString("Item"), tag.getInt("Count"));
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(itemId, 128);
        buf.writeVarInt(count);
    }

    public static SaleCost read(FriendlyByteBuf buf) {
        return new SaleCost(buf.readUtf(128), buf.readVarInt());
    }

    public SaleCost copy() {
        return new SaleCost(itemId, count);
    }
}
