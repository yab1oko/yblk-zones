package yblk.zones.zone;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ZoneSavedData extends SavedData {
    private static final String DATA_NAME = "yblk_zones";

    private final Map<UUID, Zone> zones = new LinkedHashMap<>();
    /** Chunk key → zones whose bounding box touches that chunk. */
    private final Map<Long, List<Zone>> chunkIndex = new HashMap<>();

    private static int zonePriority(Zone zone) {
        return zone.getPriority();
    }

    public static ZoneSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(ZoneSavedData::load, ZoneSavedData::new, DATA_NAME);
    }

    public Collection<Zone> all() {
        return zones.values();
    }

    public Zone get(UUID id) {
        return zones.get(id);
    }

    public void add(Zone zone) {
        zones.put(zone.getId(), zone);
        setDirty();
    }

    public void remove(UUID id) {
        if (zones.remove(id) != null) {
            setDirty();
        }
    }

    public void markDirty() {
        setDirty();
    }

    public Zone findAt(double x, double y, double z) {
        Zone best = null;
        int bestPriority = Integer.MIN_VALUE;
        int bestVolume = Integer.MAX_VALUE;
        for (Zone zone : zones.values()) {
            if (zone.contains(x, y, z)) {
                int pri = zonePriority(zone);
                int vol = zone.sizeX() * zone.sizeY() * zone.sizeZ();
                if (best == null || pri > bestPriority || (pri == bestPriority && vol < bestVolume)) {
                    bestPriority = pri;
                    bestVolume = vol;
                    best = zone;
                }
            }
        }
        if (best != null) {
            return best;
        }
        bestPriority = Integer.MIN_VALUE;
        bestVolume = Integer.MAX_VALUE;
        for (Zone zone : zones.values()) {
            if (zone.containsAabb(x, y, z)) {
                int pri = zonePriority(zone);
                int vol = zone.sizeX() * zone.sizeY() * zone.sizeZ();
                if (best == null || pri > bestPriority || (pri == bestPriority && vol < bestVolume)) {
                    bestPriority = pri;
                    bestVolume = vol;
                    best = zone;
                }
            }
        }
        return best;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Zone zone : zones.values()) {
            list.add(zone.save());
        }
        tag.put("Zones", list);
        return tag;
    }

    public static ZoneSavedData load(CompoundTag tag) {
        ZoneSavedData data = new ZoneSavedData();
        ListTag list = tag.getList("Zones", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            Zone zone = Zone.load(list.getCompound(i));
            data.zones.put(zone.getId(), zone);
        }
        return data;
    }
}
