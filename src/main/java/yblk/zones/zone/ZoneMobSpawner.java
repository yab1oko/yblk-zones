package yblk.zones.zone;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Spawns / tracks event & script mobs. Bypasses MOB_DENY_SPAWN / MOB_DENY_ENTRY
 * while placing (via {@link #allowForcedSpawn()}).
 */
public final class ZoneMobSpawner {
    public static final String EVENT_TAG = "yblk_zones_event";
    public static final String NO_BURN_TAG = "yblk_zones_no_burn";
    private static final ThreadLocal<Boolean> FORCE = ThreadLocal.withInitial(() -> false);

    private ZoneMobSpawner() {
    }

    public static boolean allowForcedSpawn() {
        return Boolean.TRUE.equals(FORCE.get());
    }

    public static boolean isEventMob(Entity entity, Zone zone) {
        if (entity == null || zone == null) {
            return false;
        }
        String tag = entity.getPersistentData().getString(EVENT_TAG);
        return !tag.isEmpty() && tag.equals(zone.getId().toString());
    }

    public static boolean isAnyEventMob(Entity entity) {
        return entity != null && entity.getPersistentData().contains(EVENT_TAG);
    }

    /** Living event mobs for this zone (wide search so wanderers still count). */
    public static int countAliveEventMobs(ServerLevel level, Zone zone) {
        int n = 0;
        for (LivingEntity e : eventMobs(level, zone, 48.0)) {
            if (e.isAlive()) {
                n++;
            }
        }
        return n;
    }

    public static Iterable<LivingEntity> eventMobs(ServerLevel level, Zone zone, double inflate) {
        AABB box = zone.aabb().inflate(inflate);
        return level.getEntitiesOfClass(LivingEntity.class, box, e -> isEventMob(e, zone));
    }

    /**
     * Spawn across the full zone footprint (not clustered on the player).
     */
    public static int spawn(ServerLevel level, Zone zone, String mobId, int count, ServerPlayer nearPlayer) {
        ResourceLocation id = ResourceLocation.tryParse(mobId == null ? "" : mobId.trim());
        if (id == null) {
            ZoneLog.warn("Spawn: bad mob id '" + mobId + "' in zone " + zone.getName());
            return 0;
        }
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(id);
        if (type == null) {
            ZoneLog.warn("Spawn: unknown mob '" + id + "' in zone " + zone.getName());
            return 0;
        }
        count = Math.max(1, Math.min(32, count));
        AABB box = zone.aabb();
        boolean protectFromSun = shouldProtectFromSun(zone);
        double preferY = nearPlayer != null && zone.contains(nearPlayer.getX(), nearPlayer.getY() + 0.1, nearPlayer.getZ())
                ? nearPlayer.getY()
                : zone.getFloorY() + 1.0;

        int spawned = 0;
        FORCE.set(true);
        try {
            for (int i = 0; i < count; i++) {
                double x = Mth.lerp(level.random.nextDouble(), box.minX + 0.4, box.maxX - 0.4);
                double z = Mth.lerp(level.random.nextDouble(), box.minZ + 0.4, box.maxZ - 0.4);
                // Retry a few times for a point inside the polygon
                for (int attempt = 0; attempt < 8 && !zone.containsXZ(x, z); attempt++) {
                    x = Mth.lerp(level.random.nextDouble(), box.minX + 0.4, box.maxX - 0.4);
                    z = Mth.lerp(level.random.nextDouble(), box.minZ + 0.4, box.maxZ - 0.4);
                }
                if (!zone.containsXZ(x, z)) {
                    x = (box.minX + box.maxX) * 0.5;
                    z = (box.minZ + box.maxZ) * 0.5;
                }
                double y = findSpawnY(level, zone, x, preferY, z);
                y = Mth.clamp(y, zone.getFloorY(), zone.getFloorY() + Math.max(0, zone.getHeight() - 1));

                Entity entity = type.create(level);
                if (entity == null) {
                    continue;
                }
                entity.moveTo(x, y, z, level.random.nextFloat() * 360F, 0);
                entity.getPersistentData().putString(EVENT_TAG, zone.getId().toString());
                if (protectFromSun) {
                    entity.getPersistentData().putBoolean(NO_BURN_TAG, true);
                }
                if (entity instanceof Mob mob) {
                    try {
                        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()),
                                MobSpawnType.EVENT, null, null);
                    } catch (Exception ignored) {
                    }
                    mob.setPersistenceRequired();
                    if (protectFromSun) {
                        mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 60 * 20, 0, false, false, true));
                    }
                }
                if (level.addFreshEntity(entity)) {
                    spawned++;
                }
            }
        } finally {
            FORCE.set(false);
        }
        if (spawned == 0) {
            ZoneLog.warn("Spawn: placed 0× " + id + " in '" + zone.getName() + "'");
        } else {
            ZoneLog.info("Spawn: " + spawned + "× " + id + " across '" + zone.getName() + "'");
        }
        return spawned;
    }

    public static boolean shouldProtectFromSun(Zone zone) {
        if (zone == null || !zone.isEventActive()) {
            return false;
        }
        ZoneSettings.SkyMode sky = zone.settings().eventSkyMode();
        if (sky == ZoneSettings.SkyMode.DEFAULT) {
            sky = zone.settings().skyMode();
        }
        return sky == ZoneSettings.SkyMode.NIGHT
                || sky == ZoneSettings.SkyMode.DAWN
                || sky == ZoneSettings.SkyMode.DUSK;
    }

    /** Keep event mobs inside the zone; put out sun fire when local night is on. */
    public static void tickContainment(ServerLevel level, Zone zone) {
        if (zone == null || !zone.isEventActive()) {
            return;
        }
        boolean protect = shouldProtectFromSun(zone);
        BlockPos min = zone.getMin();
        BlockPos max = zone.getMax();
        double cx = (min.getX() + max.getX() + 1) * 0.5;
        double cz = (min.getZ() + max.getZ() + 1) * 0.5;
        double cy = zone.getFloorY() + Math.min(2, Math.max(1, zone.getHeight() / 2));

        for (LivingEntity e : eventMobs(level, zone, 64.0)) {
            if (!e.isAlive()) {
                continue;
            }
            if (protect || e.getPersistentData().getBoolean(NO_BURN_TAG)) {
                if (e.isOnFire()) {
                    e.clearFire();
                }
                e.setRemainingFireTicks(0);
            }
            if (!zone.contains(e.getX(), e.getY() + 0.1, e.getZ())) {
                // Soft pull: last inside approx center at floor+1
                double x = Mth.clamp(e.getX(), min.getX() + 0.5, max.getX() + 0.5);
                double z = Mth.clamp(e.getZ(), min.getZ() + 0.5, max.getZ() + 0.5);
                if (!zone.containsXZ(x, z)) {
                    x = cx;
                    z = cz;
                }
                double y = findSpawnY(level, zone, x, cy, z);
                e.teleportTo(x, y, z);
                e.setDeltaMovement(Vec3.ZERO);
                if (e instanceof Mob mob) {
                    mob.getNavigation().stop();
                }
            }
        }
    }

    private static double findSpawnY(ServerLevel level, Zone zone, double x, double preferY, double z) {
        int minY = zone.getFloorY();
        int maxY = zone.getFloorY() + Math.max(1, zone.getHeight()) - 1;
        int start = Mth.clamp(Mth.floor(preferY), minY, maxY);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(Mth.floor(x), start, Mth.floor(z));
        for (int dy = 0; dy <= Math.max(maxY - minY, 1); dy++) {
            for (int sign = 0; sign <= 1; sign++) {
                int y = sign == 0 ? start + dy : start - dy;
                if (y < minY || y > maxY) {
                    continue;
                }
                pos.setY(y);
                if (level.getBlockState(pos).isAir()
                        && (y >= maxY || level.getBlockState(pos.above()).isAir()
                        || !level.getBlockState(pos.above()).isSolid())) {
                    return y;
                }
            }
        }
        return start;
    }
}
