package yblk.zones.network;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import yblk.zones.ZonesConfig;
import yblk.zones.script.ZoneAction;
import yblk.zones.script.ZoneActionType;
import yblk.zones.zone.PaymentDestination;
import yblk.zones.zone.SaleCost;
import yblk.zones.zone.Zone;
import yblk.zones.zone.ZoneFlag;
import yblk.zones.zone.ZoneManager;
import yblk.zones.zone.ZoneSettings;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public record CreateZonePacket(
        List<BlockPos> corners,
        int floorY,
        int height,
        String name,
        int flagBits,
        ZoneSettings settings,
        List<SaleCost> buyCosts,
        boolean creatorOwns,
        PaymentDestination paymentDestination,
        int priority,
        boolean allowCommands,
        List<ZoneAction> actions,
        boolean eventEnabled,
        boolean eventNeedsConfirm,
        boolean denyExitWhileEvent) {

    public CreateZonePacket(
            List<BlockPos> corners,
            int floorY,
            int height,
            String name,
            int flagBits,
            ZoneSettings settings,
            List<SaleCost> buyCosts,
            boolean creatorOwns,
            PaymentDestination paymentDestination) {
        this(corners, floorY, height, name, flagBits, settings, buyCosts, creatorOwns, paymentDestination,
                0, false, List.of(), false, true, false);
    }

    public CreateZonePacket(
            List<BlockPos> corners,
            int floorY,
            int height,
            String name,
            int flagBits,
            ZoneSettings settings,
            List<SaleCost> buyCosts,
            boolean creatorOwns,
            PaymentDestination paymentDestination,
            int priority,
            boolean allowCommands,
            List<ZoneAction> actions) {
        this(corners, floorY, height, name, flagBits, settings, buyCosts, creatorOwns, paymentDestination,
                priority, allowCommands, actions, false, true, false);
    }

    public static void encode(CreateZonePacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.corners.size());
        for (BlockPos c : packet.corners) {
            buf.writeBlockPos(c);
        }
        buf.writeVarInt(packet.floorY);
        buf.writeVarInt(packet.height);
        buf.writeUtf(packet.name, 64);
        buf.writeVarInt(packet.flagBits);
        buf.writeNbt(packet.settings.save());
        writeCosts(buf, packet.buyCosts);
        buf.writeBoolean(packet.creatorOwns);
        buf.writeEnum(packet.paymentDestination);
        buf.writeVarInt(packet.priority);
        buf.writeBoolean(packet.allowCommands);
        writeActions(buf, packet.actions);
        buf.writeBoolean(packet.eventEnabled);
        buf.writeBoolean(packet.eventNeedsConfirm);
        buf.writeBoolean(packet.denyExitWhileEvent);
    }

    public static CreateZonePacket decode(FriendlyByteBuf buf) {
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
        List<SaleCost> costs = readCosts(buf);
        boolean creatorOwns = buf.readBoolean();
        PaymentDestination paymentDestination = buf.readEnum(PaymentDestination.class);
        int priority = 0;
        boolean allowCommands = false;
        List<ZoneAction> actions = List.of();
        boolean eventEnabled = false;
        boolean eventNeedsConfirm = true;
        boolean denyExitWhileEvent = false;
        if (buf.readableBytes() > 0) {
            priority = buf.readVarInt();
            allowCommands = buf.readBoolean();
            actions = readActions(buf);
            if (buf.readableBytes() >= 3) {
                eventEnabled = buf.readBoolean();
                eventNeedsConfirm = buf.readBoolean();
                denyExitWhileEvent = buf.readBoolean();
            }
        }
        return new CreateZonePacket(corners, floorY, height, name, flags, settings, costs,
                creatorOwns, paymentDestination, priority, allowCommands, actions,
                eventEnabled, eventNeedsConfirm, denyExitWhileEvent);
    }

    static void writeCosts(FriendlyByteBuf buf, List<SaleCost> costs) {
        List<SaleCost> list = costs == null ? List.of() : costs;
        buf.writeVarInt(list.size());
        for (SaleCost c : list) {
            c.write(buf);
        }
    }

    static List<SaleCost> readCosts(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<SaleCost> costs = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            costs.add(SaleCost.read(buf));
        }
        return costs;
    }

    static void writeActions(FriendlyByteBuf buf, List<ZoneAction> actions) {
        List<ZoneAction> list = actions == null ? List.of() : actions;
        buf.writeVarInt(list.size());
        for (ZoneAction a : list) {
            buf.writeNbt(a.save());
        }
    }

    static List<ZoneAction> readActions(FriendlyByteBuf buf) {
        if (!buf.isReadable()) {
            return List.of();
        }
        int n = buf.readVarInt();
        List<ZoneAction> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            CompoundTag tag = buf.readNbt();
            if (tag != null) {
                out.add(ZoneAction.load(tag));
            }
        }
        return out;
    }

    public static void handle(CreateZonePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || packet.corners.size() < 2) {
                return;
            }
            if (!ZoneManager.mayEditZones(player)) {
                player.displayClientMessage(Component.translatable("message.yblk_zones.need_edit_perm"), true);
                return;
            }
            int height = Math.max(1, Math.min(packet.height, ZoneManager.edgeCap(player)));
            if (!ZoneManager.isWithinSizeFor(player, packet.corners, height)) {
                player.displayClientMessage(Component.translatable("message.yblk_zones.too_big",
                        ZonesConfig.maxZoneSize), true);
                return;
            }
            // UI toggle is authoritative; config only seeds the create-screen default.
            boolean owns = packet.creatorOwns();
            if (!player.hasPermissions(2) && owns && !ZoneManager.canOwnMore(player.serverLevel(), player.getUUID())) {
                player.displayClientMessage(Component.translatable("message.yblk_zones.too_many",
                        ZonesConfig.maxZonesPerPlayer), true);
                return;
            }

            EnumSet<ZoneFlag> flags = unpackFlags(packet.flagBits);
            if (flags.isEmpty()) {
                flags.add(ZoneFlag.VISIBLE);
                flags.add(ZoneFlag.DISPLAY);
            }

            String name = packet.name == null || packet.name.isBlank()
                    ? player.getGameProfile().getName() + "'s Zone"
                    : packet.name.trim();
            UUID owner = owns ? player.getUUID() : null;
            Zone zone = ZoneManager.create(
                    player.serverLevel(), name, packet.corners, packet.floorY, height,
                    owner, flags, packet.settings);
            zone.setBuyCosts(packet.buyCosts);
            zone.setPaymentDestination(packet.paymentDestination());
            zone.setPriority(Math.max(0, Math.min(10, packet.priority())));
            zone.setAllowCommands(player.hasPermissions(2) && packet.allowCommands());
            zone.setEventEnabled(packet.eventEnabled());
            zone.setEventNeedsConfirm(packet.eventNeedsConfirm());
            zone.setDenyExitWhileEvent(packet.denyExitWhileEvent());
            zone.setActions(acceptedActions(player, packet.actions(), List.of()));
            if (flags.contains(ZoneFlag.MOB_BUFF) || packet.settings.harvestLevel() > 0) {
                int mob = flags.contains(ZoneFlag.MOB_BUFF) ? packet.settings.buffAmplifier() + 1 : 0;
                int harv = packet.settings.harvestLevel();
                zone.setLevel(Math.max(1, Math.max(mob, harv)));
            }
            ZoneManager.data(player.serverLevel()).markDirty();
            ZoneManager.sync(player.serverLevel());
            player.displayClientMessage(Component.translatable("message.yblk_zones.created",
                    zone.getName(), zone.sizeX(), zone.sizeY(), zone.sizeZ()), false);
        });
        ctx.get().setPacketHandled(true);
    }

    public static int packFlags(EnumSet<ZoneFlag> flags) {
        int bits = 0;
        ZoneFlag[] all = ZoneFlag.values();
        for (int i = 0; i < all.length; i++) {
            if (flags.contains(all[i])) {
                bits |= 1 << i;
            }
        }
        return bits;
    }

    /** Operators may attach command scripts. Everyone else keeps existing command actions and cannot add new ones. */
    public static List<ZoneAction> acceptedActions(ServerPlayer player, List<ZoneAction> incoming, List<ZoneAction> existing) {
        if (player != null && player.hasPermissions(2)) {
            return incoming == null ? List.of() : incoming;
        }
        List<ZoneAction> out = new ArrayList<>();
        if (existing != null) {
            for (ZoneAction action : existing) {
                if (action.type() == ZoneActionType.COMMAND) {
                    out.add(action);
                }
            }
        }
        if (incoming != null) {
            for (ZoneAction action : incoming) {
                if (action.type() != ZoneActionType.COMMAND) {
                    out.add(action);
                }
            }
        }
        return out;
    }

    public static EnumSet<ZoneFlag> unpackFlags(int bits) {
        EnumSet<ZoneFlag> flags = EnumSet.noneOf(ZoneFlag.class);
        ZoneFlag[] all = ZoneFlag.values();
        for (int i = 0; i < all.length; i++) {
            if ((bits & (1 << i)) != 0) {
                flags.add(all[i]);
            }
        }
        return flags;
    }
}
