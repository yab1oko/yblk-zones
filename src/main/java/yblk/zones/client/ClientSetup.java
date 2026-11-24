package yblk.zones.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import yblk.zones.ZonesMod;

@Mod.EventBusSubscriber(modid = ZonesMod.MODID, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientZones.clear();
        ZoneSelection.clear();
    }

    /** Create-like face resize. Cancels hotbar scroll when Alt is held. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) {
            return;
        }
        if (!ClientWand.holdingWand() || !ZoneSelection.hasPreview()) {
            return;
        }
        if (!ZoneKeybinds.RESIZE.isDown()) {
            return;
        }
        // Always consume scroll so hotbar does not move
        event.setCanceled(true);
        ZoneSelection.altScroll(event.getScrollDelta());
    }

}
