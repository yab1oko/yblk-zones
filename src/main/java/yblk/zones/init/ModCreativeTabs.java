package yblk.zones.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import yblk.zones.ZonesMod;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ZonesMod.MODID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.yblk_zones"))
                    .icon(() -> new ItemStack(ModItems.ZONE_WAND.get()))
                    .displayItems((params, out) -> out.accept(ModItems.ZONE_WAND.get()))
                    .build());

    private ModCreativeTabs() {
    }
}
