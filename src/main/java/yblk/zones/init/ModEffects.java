package yblk.zones.init;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import yblk.zones.ZonesMod;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, ZonesMod.MODID);

    public static final RegistryObject<MobEffect> RADIATION = EFFECTS.register("radiation",
            () -> new MobEffect(MobEffectCategory.HARMFUL, 0x66CC55) {
                @Override
                public boolean isDurationEffectTick(int duration, int amplifier) {
                    return duration % 20 == 0;
                }
            });

    private ModEffects() {
    }
}
