package yblk.zones.event;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import yblk.zones.zone.Zone;

public class ZoneRaidEndEvent extends ZoneRaidEvent {
    public ZoneRaidEndEvent(ServerLevel level, Zone zone, ServerPlayer player) {
        super(level, zone, player);
    }
}
