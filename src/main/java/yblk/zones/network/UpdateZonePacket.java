package yblk.zones.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import yblk.zones.ZonesConfig;
import yblk.zones.script.ZoneAction;
import yblk.zones.zone.PaymentDestination;
import yblk.zones.zone.SaleCost;
import yblk.zones.zone.Zone;
import yblk.zones.zone.ZoneFlag;
import yblk.zones.zone.ZoneManager;
import yblk.zones.zone.ZoneSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Edit an existing zone (resize / flags / settings / meta / scripts). */
public record UpdateZonePacket(
        UUID zoneId,
        List<BlockPos> corners,
        int floorY,
        int height,
        String name,
        int flagBits,
        ZoneSettings settings,
        List<SaleCost> buyCosts,
        PaymentDestination paymentDestination,
        int priority,
        boolean allowCommands,
        boolean eventEnabled,
        boolean eventNeedsConfirm,
        boolean denyExitWhileEvent,
        List<ZoneAction> actions) {

    public UpdateZonePacket(
            UUID zoneId,
            List<BlockPos> corners,
            int floorY,
            int height,
            String name,
            int flagBits,
            ZoneSettings settings,
            List<SaleCost> buyCosts) {
        this(zoneId, corners, floorY, height, name, flagBits, settings, buyCosts,
                PaymentDestination.VOID, 0, false, false, true, false, List.of());
    }

    public static void encode(UpdateZonePacket packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.zoneId);
        buf.writeVarInt(packet.corners.size());
        for (BlockPos c : packet.corners) {
            buf.writeBlockPos(c);
        }
        buf.writeVarInt(packet.floorY);
        buf.writeVarInt(packet.height);
        buf.writeUtf(packet.name, 64);
        buf.writeVarInt(packet.flagBits);
        buf.writeNbt(packet.settings.save());
        CreateZonePacket.writeCosts(buf, packet.buyCosts);
        buf.writeEnum(packet.paymentDestination);
        buf.writeVarInt(packet.priority);
        buf.writeBoolean(packet.allowCommands);
        buf.writeBoolean(packet.eventEnabled);
        buf.writeBoolean(packet.eventNeedsConfirm);
        buf.writeBoolean(packet.denyExitWhileEvent);
        CreateZonePacket.writeActions(buf, packet.actions);
    }

    public static UpdateZonePacket decode(FriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        int n = buf.readVarInt();
        List<BlockPos> corners = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            corners.add(buf.readBlockPos());
        }
        int floorY = buf.readVarInt();
        int height = buf.readVarInt();
        String name = buf.readUtf(64);
        int flags = buf.readVarInt();
        ZoneSettings settings = new ZoneSettings();
        var tag = buf.readNbt();
        if (tag != null) {
            settings.load(tag);
        }
        List<SaleCost> costs = CreateZonePacket.readCosts(buf);
        PaymentDestination pay = PaymentDestination.VOID;
        int priority = 0;
        boolean allowCommands = false;
        boolean eventEnabled = false;
        boolean eventNeedsConfirm = true;
        boolean denyExitWhileEvent = false;
        List<ZoneAction> actions = List.of();
        if (buf.readableBytes() > 0) {
            pay = buf.readEnum(PaymentDestination.class);
            priority = buf.readVarInt();
            allowCommands = buf.readBoolean();
            eventEnabled = buf.readBoolean();
            eventNeedsConfirm = buf.readBoolean();
            denyExitWhileEvent = buf.readBoolean();
            if (buf.readableBytes() > 0) {
                actions = CreateZonePacket.readActions(buf);
            }
        }
        return new UpdateZonePacket(id, corners, floorY, height, name, flags, settings, costs,
                pay, priority, allowCommands, eventEnabled, eventNeedsConfirm, denyExitWhileEvent, actions);
    }

    public static void handle(UpdateZonePacket packet, Supplier<NetworkEvent.Context> ctx) {
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
            if (!ZoneManager.canModify(player, zone)) {
                player.displayClientMessage(Component.translatable(
                        zone.has(ZoneFlag.CLAIM) ? "message.yblk_zones.not_owner" : "message.yblk_zones.need_edit_perm"), true);
                return;
            }
            int height = Math.max(1, Math.min(packet.height, ZoneManager.edgeCap(player)));
            if (!ZoneManager.isWithinSizeFor(player, packet.corners, height)) {
                player.displayClientMessage(Component.translatable("message.yblk_zones.too_big",
                        ZonesConfig.maxZoneSize), true);
                return;
            }
            zone.setName(packet.name);
            zone.setFloorY(packet.floorY);
            zone.setHeight(height);
            zone.setCorners(packet.corners);
            zone.getFlags().clear();
            zone.getFlags().addAll(CreateZonePacket.unpackFlags(packet.flagBits));
            zone.settings().load(packet.settings.save());
            if (packet.buyCosts == null || packet.buyCosts.isEmpty()) {
                zone.clearBuyCosts();
            } else {
                zone.setBuyCosts(packet.buyCosts);
            }
            zone.setPaymentDestination(packet.paymentDestination());
            zone.setPriority(Math.max(0, Math.min(10, packet.priority())));
            if (player.hasPermissions(2)) {
                zone.setAllowCommands(packet.allowCommands());
            }
            zone.setEventEnabled(packet.eventEnabled());
            zone.setEventNeedsConfirm(packet.eventNeedsConfirm());
            zone.setDenyExitWhileEvent(packet.denyExitWhileEvent());
            zone.setActions(CreateZonePacket.acceptedActions(player, packet.actions(), zone.getActions()));
            if (zone.has(ZoneFlag.MOB_BUFF) || zone.settings().harvestLevel() > 0) {
                int mob = zone.has(ZoneFlag.MOB_BUFF) ? zone.settings().buffAmplifier() + 1 : 0;
                int harv = zone.settings().harvestLevel();
                zone.setLevel(Math.max(1, Math.max(mob, harv)));
            }
            ZoneManager.data(player.serverLevel()).markDirty();
            ZoneManager.sync(player.serverLevel());
            player.displayClientMessage(Component.translatable("message.yblk_zones.updated", zone.getName()), false);
        });
        ctx.get().setPacketHandled(true);
    }
}
