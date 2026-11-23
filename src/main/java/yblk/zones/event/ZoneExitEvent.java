package yblk.zones.event;

import net.minecraft.server.level.ServerPlayer;
import yblk.zones.zone.Zone;

public class ZoneExitEvent extends ZonePlayerEvent {
    public ZoneExitEvent(ServerPlayer player, Zone zone) {
        super(player, zone);
    }
}
