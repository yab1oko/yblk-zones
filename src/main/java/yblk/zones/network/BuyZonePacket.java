package yblk.zones.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;
import yblk.zones.ZonesConfig;
import yblk.zones.zone.SaleCost;
import yblk.zones.zone.Zone;
import yblk.zones.zone.ZoneManager;

import java.util.UUID;
import java.util.function.Supplier;

/** Shift+RMB buy (wand optional): consume materials, claim zone, open owner menu. */
public record BuyZonePacket(UUID zoneId) {
    public static void encode(BuyZonePacket packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.zoneId);
    }

    public static BuyZonePacket decode(FriendlyByteBuf buf) {
        return new BuyZonePacket(buf.readUUID());
    }

    public static void handle(BuyZonePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                return;
            }
            Zone zone = ZoneManager.data(player.serverLevel()).get(packet.zoneId);
            if (zone == null) {
                player.displayClientMessage(Component.translatable("message.yblk_zones.not_in_zone"), true);
                return;
            }
            if (!nearZone(player, zone)) {
                player.displayClientMessage(Component.translatable("message.yblk_zones.not_in_zone"), true);
                return;
            }
            String err = ZoneManager.tryBuy(player, zone);
            if (err != null) {
                if ("message.yblk_zones.too_many".equals(err)) {
                    player.displayClientMessage(Component.translatable(err, ZonesConfig.maxZonesPerPlayer), true);
                } else if ("message.yblk_zones.cannot_afford".equals(err)) {
                    player.displayClientMessage(
                            Component.translatable("message.yblk_zones.cannot_afford_list", affordHint(zone)), true);
                } else {
                    player.displayClientMessage(Component.translatable(err), true);
                }
                return;
            }
            player.displayClientMessage(Component.translatable("message.yblk_zones.bought", zone.getName()), false);
            ZonesNetwork.sendToPlayer(new OpenOwnerMenuPacket(zone.getId()), player);
        });
        ctx.get().setPacketHandled(true);
    }

    private static boolean nearZone(ServerPlayer player, Zone zone) {
        if (zone.contains(player.getX(), player.getY() + 0.1, player.getZ())
                || zone.containsAabb(player.getX(), player.getY() + 0.1, player.getZ())) {
            return true;
        }
        var hit = player.pick(6.0, 0.0F, false);
        if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            var p = ((net.minecraft.world.phys.BlockHitResult) hit).getBlockPos();
            double x = p.getX() + 0.5;
            double y = p.getY() + 0.5;
            double z = p.getZ() + 0.5;
            return zone.contains(x, y, z) || zone.containsAabb(x, y, z);
        }
        return false;
    }

    private static String affordHint(Zone zone) {
        StringBuilder sb = new StringBuilder();
        for (SaleCost cost : zone.getBuyCosts()) {
            if (!cost.isValid()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(cost.count()).append("×").append(itemLabel(cost.itemId()));
        }
        return sb.isEmpty() ? "?" : sb.toString();
    }

    private static String itemLabel(String id) {
        if (id == null || id.isBlank()) {
            return "?";
        }
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) {
            return id;
        }
        Item item = ForgeRegistries.ITEMS.getValue(rl);
        if (item == null) {
            return id;
        }
        return new ItemStack(item).getHoverName().getString();
    }
}
