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
        rebuildIndex();
        setDirty();
    }

    public void remove(UUID id) {
        if (zones.remove(id) != null) {
            rebuildIndex();
            setDirty();
        }
    }

    public void markDirty() {
        rebuildIndex();
        setDirty();
    }

    /** Zones whose box touches this chunk or a neighboring one. Used by the mob wall. */
    public List<Zone> near(BlockPos pos) {
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;
        List<Zone> out = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                List<Zone> list = chunkIndex.get(ChunkPos.asLong(cx + dx, cz + dz));
                if (list != null) {
                    for (Zone zone : list) {
                        if (!out.contains(zone)) {
                            out.add(zone);
                        }
                    }
                }
            }
        }
        return out;
    }

    public Zone findAt(double x, double y, double z) {
        Zone best = null;
        int bestPriority = Integer.MIN_VALUE;
        int bestVolume = Integer.MAX_VALUE;
        int cx = BlockPos.containing(x, 0, z).getX() >> 4;
        int cz = BlockPos.containing(x, 0, z).getZ() >> 4;
        List<Zone> candidates = chunkIndex.get(ChunkPos.asLong(cx, cz));
        if (candidates == null) {
            candidates = List.of();
        }
        for (Zone zone : candidates) {
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
        for (Zone zone : candidates) {
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
        data.rebuildIndex();
        return data;
    }

    private void rebuildIndex() {
        chunkIndex.clear();
        for (Zone zone : zones.values()) {
            BlockPos min = zone.getMin();
            BlockPos max = zone.getMax();
            int minCx = min.getX() >> 4;
            int maxCx = max.getX() >> 4;
            int minCz = min.getZ() >> 4;
            int maxCz = max.getZ() >> 4;
            for (int cx = minCx; cx <= maxCx; cx++) {
                for (int cz = minCz; cz <= maxCz; cz++) {
                    chunkIndex.computeIfAbsent(ChunkPos.asLong(cx, cz), key -> new ArrayList<>()).add(zone);
                }
            }
        }
    }
}
