package yblk.zones.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import yblk.zones.client.ClientZones;
import yblk.zones.zone.Zone;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public record SyncZonesPacket(String dimension, List<Zone> zones) {
    public static SyncZonesPacket of(String dimension, List<Zone> zones) {
        return new SyncZonesPacket(dimension, List.copyOf(zones));
    }

    public static void encode(SyncZonesPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.dimension);
        buf.writeVarInt(packet.zones.size());
        for (Zone zone : packet.zones) {
            buf.writeNbt(zone.save());
        }
    }

    public static SyncZonesPacket decode(FriendlyByteBuf buf) {
        String dim = buf.readUtf();
        int n = buf.readVarInt();
        List<Zone> zones = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            var tag = buf.readNbt();
            if (tag != null) {
                zones.add(Zone.load(tag));
            }
        }
        return new SyncZonesPacket(dim, zones);
    }

    public static void handle(SyncZonesPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientZones.accept(packet.dimension(), packet.zones())));
        ctx.get().setPacketHandled(true);
    }
}
