package yblk.zones.event;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import yblk.zones.zone.Zone;

public class ZoneRaidStartEvent extends ZoneRaidEvent {
    public ZoneRaidStartEvent(ServerLevel level, Zone zone, ServerPlayer player) {
        super(level, zone, player);
    }
}
