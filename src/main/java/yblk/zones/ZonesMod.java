package yblk.zones;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import yblk.zones.command.ZoneCommands;
import yblk.zones.init.ModCreativeTabs;
import yblk.zones.init.ModItems;
import yblk.zones.init.ModLootModifiers;
import yblk.zones.network.ZonesNetwork;
import yblk.zones.zone.ZoneLog;
import yblk.zones.zone.ZoneTemplates;

@Mod(ZonesMod.MODID)
public class ZonesMod {
    public static final String MODID = "yblk_zones";
    private static final Logger LOGGER = LogUtils.getLogger();

    public ZonesMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.ITEMS.register(modBus);
        ModCreativeTabs.TABS.register(modBus);
        ModLootModifiers.SERIALIZERS.register(modBus);
        modBus.addListener(this::commonSetup);

        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarting);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ZonesConfig.SPEC);
        LOGGER.info("YBLK Zones loading");
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ZonesNetwork.register();
            ZoneLog.init(FMLPaths.GAMEDIR.get());
            ZoneTemplates.init(FMLPaths.CONFIGDIR.get());
        });
    }

    private void onServerStarting(ServerStartingEvent event) {
        ZoneLog.init(FMLPaths.GAMEDIR.get());
        ZoneTemplates.init(FMLPaths.CONFIGDIR.get());
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        ZoneCommands.register(event.getDispatcher());
    }
}
