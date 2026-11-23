package yblk.zones.event;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.Event;
import yblk.zones.zone.Zone;

import java.util.UUID;

/** Enter or leave. KubeJS can listen to these Forge events without a plugin. */
public abstract class ZonePlayerEvent extends Event {
    private final ServerPlayer player;
    private final UUID zoneId;
    private final String zoneName;
    private final ResourceKey<Level> dimension;

    protected ZonePlayerEvent(ServerPlayer player, Zone zone) {
        this.player = player;
        this.zoneId = zone.getId();
        this.zoneName = zone.getName();
        this.dimension = player.serverLevel().dimension();
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
