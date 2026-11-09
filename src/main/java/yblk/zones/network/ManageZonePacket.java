package yblk.zones.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import yblk.zones.ZonesConfig;
import yblk.zones.zone.PaymentDestination;
import yblk.zones.zone.Zone;
import yblk.zones.zone.ZoneManager;

import java.util.UUID;
import java.util.function.Supplier;

public record ManageZonePacket(
        Action action,
        UUID zoneId,
        UUID target,
        String payload) {

    public enum Action {
        LEAVE,
        TRANSFER,
        LIST_SALE,
        SET_PAYMENT,
        START_EVENT,
        STOP_EVENT,
        PAY_LEASE
    }

    public static void encode(ManageZonePacket packet, FriendlyByteBuf buf) {
        buf.writeEnum(packet.action);
        buf.writeUUID(packet.zoneId);
        buf.writeBoolean(packet.target != null);
        if (packet.target != null) {
            buf.writeUUID(packet.target);
        }
        buf.writeUtf(packet.payload == null ? "" : packet.payload, 256);
    }

    public static ManageZonePacket decode(FriendlyByteBuf buf) {
        Action action = buf.readEnum(Action.class);
        UUID zoneId = buf.readUUID();
        UUID target = buf.readBoolean() ? buf.readUUID() : null;
        String payload = buf.readUtf(256);
        if (payload.isEmpty()) {
            payload = null;
        }
        return new ManageZonePacket(action, zoneId, target, payload);
    }

    public static void handle(ManageZonePacket packet, Supplier<NetworkEvent.Context> ctx) {
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

            String err = switch (packet.action) {
                case LEAVE -> ZoneManager.tryLeave(player, zone);
                case TRANSFER -> {
                    if (packet.target == null) {
                        yield "message.yblk_zones.transfer_no_target";
                    }
                    ServerPlayer to = player.serverLevel().getServer().getPlayerList().getPlayer(packet.target);
                    if (to == null) {
                        yield "message.yblk_zones.transfer_offline";
                    }
                    yield ZoneManager.tryTransfer(player, zone, to);
                }
                case LIST_SALE -> {
                    if (!ZoneManager.canSellTransfer(player, zone)) {
                        yield "message.yblk_zones.not_owner";
                    }
                    if ("off".equalsIgnoreCase(packet.payload)) {
                        zone.clearBuyCosts();
                        ZoneManager.data(player.serverLevel()).markDirty();
                        ZoneManager.sync(player.serverLevel());
                        player.displayClientMessage(
                                Component.translatable("message.yblk_zones.listing_removed"), false);
                        yield null;
                    }
                    yield "message.yblk_zones.sale_use_edit";
                }
                case SET_PAYMENT -> {
                    if (!ZoneManager.canSellTransfer(player, zone)) {
                        yield "message.yblk_zones.not_owner";
                    }
                    PaymentDestination dest = PaymentDestination.VOID;
                    if (packet.payload != null) {
                        try {
                            dest = PaymentDestination.valueOf(packet.payload);
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                    zone.setPaymentDestination(dest);
                    ZoneManager.data(player.serverLevel()).markDirty();
                    ZoneManager.sync(player.serverLevel());
                    yield null;
                }
                case START_EVENT -> ZoneManager.startEvent(player, zone);
                case STOP_EVENT -> ZoneManager.stopEvent(player, zone);
                case PAY_LEASE -> ZoneManager.tryPayLease(player, zone);
            };

            if (err != null) {
                if ("message.yblk_zones.too_many".equals(err)) {
                    player.displayClientMessage(Component.translatable(err, ZonesConfig.maxZonesPerPlayer), true);
                } else {
                    player.displayClientMessage(Component.translatable(err), true);
                }
            } else if (packet.action == Action.LEAVE) {
                player.displayClientMessage(Component.translatable("message.yblk_zones.left"), false);
            } else if (packet.action == Action.TRANSFER && packet.target != null) {
                ServerPlayer to = player.serverLevel().getServer().getPlayerList().getPlayer(packet.target);
                String name = to != null ? to.getGameProfile().getName() : "?";
                player.displayClientMessage(Component.translatable("message.yblk_zones.transferred", name), false);
            }
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
}
