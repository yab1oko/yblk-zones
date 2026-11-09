package yblk.zones.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import yblk.zones.ZonesMod;

public final class ZonesNetwork {
    private static final String PROTOCOL = "7";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ZonesMod.MODID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);

    private static int id;

    private ZonesNetwork() {
    }

    public static void register() {
        CHANNEL.messageBuilder(SyncZonesPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SyncZonesPacket::decode)
                .encoder(SyncZonesPacket::encode)
                .consumerMainThread(SyncZonesPacket::handle)
                .add();
        CHANNEL.messageBuilder(CreateZonePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(CreateZonePacket::decode)
                .encoder(CreateZonePacket::encode)
                .consumerMainThread(CreateZonePacket::handle)
                .add();
        CHANNEL.messageBuilder(UpdateZonePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(UpdateZonePacket::decode)
                .encoder(UpdateZonePacket::encode)
                .consumerMainThread(UpdateZonePacket::handle)
                .add();
        CHANNEL.messageBuilder(DeleteZonePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(DeleteZonePacket::decode)
                .encoder(DeleteZonePacket::encode)
                .consumerMainThread(DeleteZonePacket::handle)
                .add();
        CHANNEL.messageBuilder(BuyZonePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(BuyZonePacket::decode)
                .encoder(BuyZonePacket::encode)
                .consumerMainThread(BuyZonePacket::handle)
                .add();
        CHANNEL.messageBuilder(OwnerUpdatePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(OwnerUpdatePacket::decode)
                .encoder(OwnerUpdatePacket::encode)
                .consumerMainThread(OwnerUpdatePacket::handle)
                .add();
        CHANNEL.messageBuilder(OpenOwnerMenuPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(OpenOwnerMenuPacket::decode)
                .encoder(OpenOwnerMenuPacket::encode)
                .consumerMainThread(OpenOwnerMenuPacket::handle)
                .add();
        CHANNEL.messageBuilder(ManageZonePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(ManageZonePacket::decode)
                .encoder(ManageZonePacket::encode)
                .consumerMainThread(ManageZonePacket::handle)
                .add();
    }

    public static void sendToPlayer(Object packet, ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}
