package yblk.zones.event;

import net.minecraft.server.level.ServerPlayer;
import yblk.zones.zone.Zone;

public class ZoneEnterEvent extends ZonePlayerEvent {
    public ZoneEnterEvent(ServerPlayer player, Zone zone) {
        super(player, zone);
    }
}
