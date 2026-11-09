package yblk.zones.init;

import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import yblk.zones.ZonesMod;
import yblk.zones.item.ZoneWandItem;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, ZonesMod.MODID);

    public static final RegistryObject<Item> ZONE_WAND = ITEMS.register("zone_wand",
            () -> new ZoneWandItem(new Item.Properties().stacksTo(1)));

    private ModItems() {
    }
}
