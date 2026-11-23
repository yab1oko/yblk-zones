package yblk.zones.event;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.Event;
import yblk.zones.zone.Zone;

import java.util.UUID;

/** Raid start or end. The player is the one who started or stopped it, or null when the raid ends on its own. */
public abstract class ZoneRaidEvent extends Event {
    private final ServerPlayer player;
    private final UUID zoneId;
    private final String zoneName;
    private final ResourceKey<Level> dimension;

    protected ZoneRaidEvent(ServerLevel level, Zone zone, ServerPlayer player) {
        this.player = player;
        this.zoneId = zone.getId();
        this.zoneName = zone.getName();
        this.dimension = level.dimension();
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public UUID getZoneId() {
        return zoneId;
    }

    public String getZoneName() {
        return zoneName;
    }

    public ResourceKey<Level> getDimension() {
        return dimension;
    }
}
