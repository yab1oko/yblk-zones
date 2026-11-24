package yblk.zones.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import yblk.zones.ZonesMod;
import yblk.zones.item.ZoneWandItem;
import yblk.zones.zone.Zone;
import yblk.zones.zone.ZoneFlag;

/** Client-side adventure mayBuild + Shift+RMB buy without wand. */
@Mod.EventBusSubscriber(modid = ZonesMod.MODID, value = Dist.CLIENT)
public final class ZoneClientEvents {
    private static long lastBuyMs;

    private ZoneClientEvents() {
    }

    /** Shift+RMB in a for-sale zone — works without the wand (wand still works via ClientWand). */
    @SubscribeEvent
    public static void onRightClickBlockBuy(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide() || !event.getEntity().isShiftKeyDown()) {
            return;
        }
        if (event.getEntity().getItemInHand(event.getHand()).getItem() instanceof ZoneWandItem) {
            return; // wand handles this
        }
        tryBuyNearby(event.getPos().getX() + 0.5, event.getPos().getY() + 0.5, event.getPos().getZ() + 0.5);
    }

    @SubscribeEvent
    public static void onRightClickEmptyBuy(PlayerInteractEvent.RightClickEmpty event) {
        if (!event.getLevel().isClientSide() || !event.getEntity().isShiftKeyDown()) {
            return;
        }
        if (event.getEntity().getItemInHand(event.getHand()).getItem() instanceof ZoneWandItem) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        tryBuyNearby(mc.player.getX(), mc.player.getY() + 0.1, mc.player.getZ());
    }

    private static void tryBuyNearby(double x, double y, double z) {
        long now = System.currentTimeMillis();
        if (now - lastBuyMs < 400) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        Zone zone = ClientZones.at(x, y, z);
        if (zone == null) {
            zone = ClientZones.at(mc.player.getX(), mc.player.getY() + 0.1, mc.player.getZ());
        }
        if (zone == null || !zone.isForSale()) {
            return;
        }
        if (zone.getOwner() != null && zone.getOwner().equals(mc.player.getUUID())) {
            mc.player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("message.yblk_zones.already_owner"), true);
            lastBuyMs = now;
            return;
        }
        lastBuyMs = now;
        ClientWand.tryBuy(zone);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null) {
            return;
        }
        if (mc.gameMode.getPlayerMode() != GameType.ADVENTURE) {
            return;
        }
        boolean allow = false;
        var look = player.pick(player.getBlockReach(), 0.0F, false);
        if (look instanceof net.minecraft.world.phys.BlockHitResult block
                && block.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            Zone broken = ClientZones.at(block.getBlockPos().getX() + 0.5,
                    block.getBlockPos().getY() + 0.5, block.getBlockPos().getZ() + 0.5);
            var place = block.getBlockPos().relative(block.getDirection());
            Zone placed = ClientZones.at(place.getX() + 0.5, place.getY() + 0.5, place.getZ() + 0.5);
            allow = (broken != null && broken.has(ZoneFlag.ADVENTURE_BREAK))
                    || (placed != null && placed.has(ZoneFlag.ADVENTURE_BUILD));
        }
        if (player.getAbilities().mayBuild != allow) {
            player.getAbilities().mayBuild = allow;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (!event.getLevel().isClientSide()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null || mc.gameMode.getPlayerMode() != GameType.ADVENTURE) {
            return;
        }
        Zone zone = ClientZones.at(
                event.getPos().getX() + 0.5,
                event.getPos().getY() + 0.5,
                event.getPos().getZ() + 0.5);
        if (zone != null && zone.has(ZoneFlag.ADVENTURE_BREAK)) {
            if (mc.player != null) {
                mc.player.getAbilities().mayBuild = true;
            }
            event.setCanceled(false);
            event.setUseBlock(Event.Result.ALLOW);
            event.setUseItem(Event.Result.ALLOW);
        }
    }
}
