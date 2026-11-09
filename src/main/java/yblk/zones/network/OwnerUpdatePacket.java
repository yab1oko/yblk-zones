package yblk.zones.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import yblk.zones.zone.Zone;
import yblk.zones.zone.ZoneManager;
import yblk.zones.zone.ZoneMember;
import yblk.zones.zone.ZoneRole;
import yblk.zones.zone.ZoneSettings;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/** Buyer/owner menu: rename + trust list only. */
public record OwnerUpdatePacket(UUID zoneId, String name, ZoneSettings settings) {
    public static void encode(OwnerUpdatePacket packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.zoneId);
        buf.writeUtf(packet.name == null ? "" : packet.name, 64);
        buf.writeNbt(packet.settings.save());
    }

    public static OwnerUpdatePacket decode(FriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        String name = buf.readUtf(64);
        ZoneSettings settings = new ZoneSettings();
        var tag = buf.readNbt();
        if (tag != null) {
            settings.load(tag);
        }
        return new OwnerUpdatePacket(id, name, settings);
    }

    public static void handle(OwnerUpdatePacket packet, Supplier<NetworkEvent.Context> ctx) {
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
            if (!player.hasPermissions(2)
                    && (zone.getOwner() == null || !zone.getOwner().equals(player.getUUID()))) {
                player.displayClientMessage(Component.translatable("message.yblk_zones.not_owner"), true);
                return;
            }
            String name = packet.name == null || packet.name.isBlank()
                    ? player.getGameProfile().getName() + "'s base"
                    : packet.name.trim();
            String oldName = zone.getName();
            zone.setName(name);
            // HUD prefers hudText / enterTitle over zone name — sync so rename is visible
            zone.settings().setHudText("");
            if (zone.settings().enterTitle().isBlank()
                    || zone.settings().enterTitle().equals(oldName)
                    || zone.settings().enterTitle().equalsIgnoreCase(oldName)) {
                zone.settings().setEnterTitle("");
            }
            // Keep non-trust settings; only overlay trust from menu
            zone.settings().trustedNames().clear();
            zone.settings().trustedIds().clear();
            zone.settings().trustedNames().addAll(packet.settings.trustedNames());
            zone.settings().trustedIds().addAll(packet.settings.trustedIds());
            zone.settings().setTrustedTeam(packet.settings.trustedTeam());
            // Mirror trust list into MEMBER roles (keep OWNER/MANAGER)
            Set<UUID> keep = new HashSet<>(packet.settings.trustedIds());
            for (ZoneMember m : new java.util.ArrayList<>(zone.getMembers())) {
                if (m.role() == ZoneRole.OWNER || m.role() == ZoneRole.MANAGER) {
                    continue;
                }
                if (m.id() != null && !keep.contains(m.id())) {
                    zone.removeMember(m.id());
                }
            }
            for (UUID tid : packet.settings.trustedIds()) {
                ZoneRole existing = zone.roleOf(tid);
                if (existing != ZoneRole.OWNER && existing != ZoneRole.MANAGER) {
                    zone.setMember(tid, "", ZoneRole.MEMBER);
                }
            }
            for (String tn : packet.settings.trustedNames()) {
                boolean found = false;
                for (ZoneMember m : zone.getMembers()) {
                    if (m.name().equalsIgnoreCase(tn)
                            || (m.id() != null && packet.settings.trustedIds().contains(m.id()))) {
                        if (m.name().isBlank()) {
                            m.setName(tn);
                        }
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    zone.setMember(null, tn, ZoneRole.MEMBER);
                }
            }
            ZoneManager.data(player.serverLevel()).markDirty();
            ZoneManager.sync(player.serverLevel());
            player.displayClientMessage(Component.translatable("message.yblk_zones.updated", zone.getName()), false);
        });
        ctx.get().setPacketHandled(true);
    }
}
