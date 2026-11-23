package yblk.zones.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import yblk.zones.init.ModItems;

/** Loaded by JEI only. Absent JEI never references this class. */
@JeiPlugin
public class ZonesJeiPlugin implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return new ResourceLocation("yblk_zones", "jei");
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addItemStackInfo(new ItemStack(ModItems.ZONE_WAND.get()),
                Component.translatable("jei.yblk_zones.wand_draw"),
                Component.translatable("jei.yblk_zones.wand_shape"),
                Component.translatable("jei.yblk_zones.wand_menus"));
    }
}
