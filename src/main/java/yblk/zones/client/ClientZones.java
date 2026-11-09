package yblk.zones.client;

import yblk.zones.zone.Zone;

import java.util.Collections;
import java.util.List;

/** Client cache of zones for the current dimension (synced from server). */
public final class ClientZones {
    private static String dimension = "";
    private static List<Zone> zones = List.of();

    private ClientZones() {
    }

    public static void accept(String dim, List<Zone> list) {
        dimension = dim;
        zones = List.copyOf(list);
    }

    public static List<Zone> all() {
        return zones;
    }

    public static String dimension() {
        return dimension;
    }

    /** Same rule as server {@code ZoneSavedData.findAt}: higher priority, then smaller volume. */
    public static Zone at(double x, double y, double z) {
        Zone best = null;
        int bestPriority = Integer.MIN_VALUE;
        int bestVolume = Integer.MAX_VALUE;
        for (Zone zone : zones) {
            if (zone.contains(x, y, z)) {
                int pri = zone.getPriority();
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
        // Fallback: outline uses AABB; broken polygons still selectable/deletable
        bestPriority = Integer.MIN_VALUE;
        bestVolume = Integer.MAX_VALUE;
        for (Zone zone : zones) {
            if (zone.containsAabb(x, y, z)) {
                int pri = zone.getPriority();
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

    public static Zone byId(java.util.UUID id) {
        if (id == null) {
            return null;
        }
        for (Zone zone : zones) {
            if (id.equals(zone.getId())) {
                return zone;
            }
        }
        return null;
    }

    public static void clear() {
        dimension = "";
        zones = Collections.emptyList();
    }
}
