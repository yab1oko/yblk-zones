package yblk.zones.init;

import com.mojang.serialization.Codec;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import yblk.zones.ZonesMod;
import yblk.zones.loot.ZoneChestLootModifier;

public final class ModLootModifiers {
    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, ZonesMod.MODID);

    public static final RegistryObject<Codec<? extends IGlobalLootModifier>> ZONE_CHEST_LOOT =
            SERIALIZERS.register("zone_chest_loot", () -> ZoneChestLootModifier.CODEC);

    private ModLootModifiers() {
    }
}
