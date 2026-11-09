package yblk.zones.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import yblk.zones.zone.Zone;
import yblk.zones.zone.ZoneManager;

import java.util.UUID;
import java.util.function.Supplier;

public record DeleteZonePacket(UUID zoneId) {
    public static void encode(DeleteZonePacket packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.zoneId);
    }

    public static DeleteZonePacket decode(FriendlyByteBuf buf) {
        return new DeleteZonePacket(buf.readUUID());
    }

    public static void handle(DeleteZonePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                return;
            }
            Zone zone = ZoneManager.data(player.serverLevel()).get(packet.zoneId);
            if (zone == null) {
                // Force resync so client drops a zombie outline
                ZoneManager.syncTo(player);
                player.displayClientMessage(Component.translatable("message.yblk_zones.not_in_zone"), true);
                return;
            }
            if (!ZoneManager.canDelete(player, zone)) {
                player.displayClientMessage(Component.translatable("message.yblk_zones.not_owner"), true);
                return;
            }
            ZoneManager.data(player.serverLevel()).remove(packet.zoneId);
            ZoneManager.sync(player.serverLevel());
            player.displayClientMessage(Component.translatable("message.yblk_zones.removed"), false);
        });
        ctx.get().setPacketHandled(true);
    }
}
