package yblk.zones.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Client: open compact owner menu after buying a zone. */
public record OpenOwnerMenuPacket(UUID zoneId) {
    public static void encode(OpenOwnerMenuPacket packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.zoneId);
    }

    public static OpenOwnerMenuPacket decode(FriendlyByteBuf buf) {
        return new OpenOwnerMenuPacket(buf.readUUID());
    }

    public static void handle(OpenOwnerMenuPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> yblk.zones.client.ClientWand.openOwnerMenu(packet.zoneId())));
        ctx.get().setPacketHandled(true);
    }
}
