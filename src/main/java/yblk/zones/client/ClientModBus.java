package yblk.zones.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import yblk.zones.ZonesMod;
import yblk.zones.zone.ZoneTemplates;

@Mod.EventBusSubscriber(modid = ZonesMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModBus {
    private ClientModBus() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ZoneTemplates.init(FMLPaths.CONFIGDIR.get()));
    }
}
