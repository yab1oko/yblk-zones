package yblk.zones.event;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.FireChargeItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityMobGriefingEvent;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.level.PistonEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import yblk.zones.ZonesMod;
import yblk.zones.script.ZoneScriptEngine;
import yblk.zones.script.ZoneTrigger;
import yblk.zones.zone.EffectEntry;
import yblk.zones.zone.Zone;
import yblk.zones.zone.ZoneFlag;
import yblk.zones.zone.ZoneManager;
import yblk.zones.zone.ZoneSettings;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = ZonesMod.MODID)
public final class ZoneEvents {
    private static final Map<UUID, UUID> LAST_ZONE = new ConcurrentHashMap<>();
    private static final Map<UUID, Vec3> LAST_INSIDE = new ConcurrentHashMap<>();
    private static final Map<UUID, Vec3> LAST_OUTSIDE = new ConcurrentHashMap<>();
    private static final Set<UUID> PULLING = ConcurrentHashMap.newKeySet();

    private ZoneEvents() {
    }

    @SubscribeEvent
    public static void onLogin(PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ZoneManager.syncTo(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        LAST_ZONE.remove(id);
        LAST_INSIDE.remove(id);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (server == null) {
            return;
        }
        int tick = server.getTickCount();
        for (ServerLevel level : server.getAllLevels()) {
            // Keep raid mobs inside + extinguish sun fire often
            if (tick % 5 == 0) {
                ZoneManager.tickEventMobs(level);
            }
            if (tick % 20 == 0) {
                ZoneManager.tickLeases(level);
                ZoneManager.tickEvents(level);
            }
        }
    }

    @SubscribeEvent
    public static void onDimChange(PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ZoneManager.syncTo(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Player player = event.getPlayer();
        Zone zone = ZoneManager.at(level, event.getPos());
        if (zone == null) {
            return;
        }
        if (isAdventure(player) && zone.has(ZoneFlag.ADVENTURE_BREAK)) {
            return; // allow
        }
        if (zone.has(ZoneFlag.CLAIM) && !ZoneManager.canModify(player, zone)) {
            event.setCanceled(true);
            return;
        }
        if (isAdventure(player) && !player.hasPermissions(2)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            Zone foreign = ZoneManager.at(level, event.getPos());
            if (foreign != null && foreign.has(ZoneFlag.CLAIM)) {
                event.setCanceled(true);
            }
            return;
        }
        Zone zone = ZoneManager.at(level, event.getPos());
        if (zone == null) {
            return;
        }
        if (isAdventure(player) && zone.has(ZoneFlag.ADVENTURE_BUILD)) {
            return;
        }
        if (zone.has(ZoneFlag.CLAIM) && !ZoneManager.canModify(player, zone)) {
            event.setCanceled(true);
            return;
        }
        if (isAdventure(player) && !player.hasPermissions(2)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Player player = event.getEntity();
        if (!isAdventure(player)) {
            return;
        }
        Zone zone = ZoneManager.at(level, event.getPos());
        if (zone != null && zone.has(ZoneFlag.ADVENTURE_BREAK)) {
            grantMayBuild(player);
            event.setCanceled(false);
            event.setUseBlock(Event.Result.ALLOW);
            event.setUseItem(Event.Result.ALLOW);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Player player = event.getEntity();
        if (!isAdventure(player)) {
            return;
        }
        BlockPos placeAt = event.getPos().relative(event.getFace());
        Zone zone = ZoneManager.at(level, placeAt);
        if (zone == null) {
            zone = ZoneManager.at(level, event.getPos());
        }
        if (zone != null && zone.has(ZoneFlag.ADVENTURE_BUILD)) {
            grantMayBuild(player);
            event.setUseBlock(Event.Result.ALLOW);
            event.setUseItem(Event.Result.ALLOW);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        if (!(player.level() instanceof ServerLevel level) || !isAdventure(player)) {
            return;
        }
        BlockPos pos = event.getPosition().orElse(null);
        if (pos == null) {
            return;
        }
        Zone zone = ZoneManager.at(level, pos);
        if (zone != null && zone.has(ZoneFlag.ADVENTURE_BREAK)) {
            grantMayBuild(player);
            if (event.getNewSpeed() <= 0.0F) {
                event.setNewSpeed(Math.max(1.0F, event.getOriginalSpeed()));
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHarvest(PlayerEvent.HarvestCheck event) {
        Player player = event.getEntity();
        if (!(player.level() instanceof ServerLevel level) || !isAdventure(player)) {
            return;
        }
        BlockPos harvested = player.blockPosition();
        net.minecraft.world.phys.HitResult look = player.pick(6.0D, 1.0F, false);
        if (look instanceof net.minecraft.world.phys.BlockHitResult blockHit
                && blockHit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            harvested = blockHit.getBlockPos();
        }
        Zone zone = ZoneManager.at(level, harvested);
        if (zone != null && zone.has(ZoneFlag.ADVENTURE_BREAK)) {
            event.setCanHarvest(true);
        }
    }

    @SubscribeEvent
    public static void onSpawnCheck(MobSpawnEvent.FinalizeSpawn event) {
        if (yblk.zones.zone.ZoneMobSpawner.allowForcedSpawn()) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = BlockPos.containing(event.getX(), event.getY(), event.getZ());
        Zone zone = ZoneManager.at(level, pos);
        if (zone != null && zone.has(ZoneFlag.MOB_DENY_SPAWN)) {
            event.setSpawnCancelled(true);
            event.setResult(Event.Result.DENY);
        }
    }

    @SubscribeEvent
    public static void onMobJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (yblk.zones.zone.ZoneMobSpawner.allowForcedSpawn()) {
            return;
        }
        if (!(event.getEntity() instanceof Mob mob) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Zone zone = ZoneManager.at(level, mob.blockPosition());
        if (zone != null && zone.has(ZoneFlag.MOB_DENY_ENTRY) && deniesMob(zone, mob)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide() || !(entity.level() instanceof ServerLevel level)) {
            return;
        }
        if (!(entity instanceof Mob mob)) {
            return;
        }
        Zone zone = ZoneManager.at(level, mob.blockPosition());
        if (zone != null && zone.has(ZoneFlag.MOB_BUFF) && buffsMob(zone, mob)) {
            event.setAmount(event.getAmount() * 0.85F);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlayerAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Entity source = event.getSource().getEntity();
        if (!(source instanceof Player attacker) || attacker == victim) {
            return;
        }
        if (inFlag(attacker, ZoneFlag.DENY_PVP) || inFlag(victim, ZoneFlag.DENY_PVP)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onProtectUse(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level) || event.getEntity() == null) {
            return;
        }
        Player player = event.getEntity();
        BlockPos pos = event.getPos();
        Zone zone = ZoneManager.at(level, pos);
        if (zone == null) {
            return;
        }
        ItemStack stack = event.getItemStack();
        boolean container = isContainer(level, pos);
        boolean fireItem = stack.getItem() instanceof FlintAndSteelItem || stack.getItem() instanceof FireChargeItem;
        boolean bucket = stack.getItem() instanceof BucketItem;
        boolean claimLocked = zone.has(ZoneFlag.CLAIM) && !ZoneManager.canModify(player, zone);
        if (claimLocked && (container || fireItem || bucket)) {
            denyUse(event);
            return;
        }
        if (zone.has(ZoneFlag.DENY_CONTAINERS) && container && !ZoneManager.canModify(player, zone) && !player.hasPermissions(2)) {
            denyUse(event);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onProtectEntity(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (lockedDecoration(event.getEntity(), event.getTarget())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttackDecoration(AttackEntityEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        if (lockedDecoration(event.getEntity(), event.getTarget())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        event.getAffectedBlocks().removeIf(pos -> explosionBlocked(level, pos));
        event.getAffectedEntities().removeIf(entity -> explosionBlocked(level, entity.blockPosition()));
    }

    @SubscribeEvent
    public static void onPiston(PistonEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (claimLocked(level, event.getPos()) || claimLocked(level, event.getFaceOffsetPos())) {
            event.setCanceled(true);
            return;
        }
        var helper = event.getStructureHelper();
        if (helper == null || !helper.resolve()) {
            return;
        }
        for (BlockPos pos : helper.getToPush()) {
            if (claimLocked(level, pos)) {
                event.setCanceled(true);
                return;
            }
        }
        for (BlockPos pos : helper.getToDestroy()) {
            if (claimLocked(level, pos)) {
                event.setCanceled(true);
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onMobGrief(EntityMobGriefingEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        if (claimLocked(level, event.getEntity().blockPosition())) {
            event.setResult(Event.Result.DENY);
        }
    }

    @SubscribeEvent
    public static void onFireNotify(BlockEvent.NeighborNotifyEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!(level.getBlockState(event.getPos()).getBlock() instanceof BaseFireBlock)) {
            return;
        }
        if (claimLocked(level, event.getPos())) {
            event.setCanceled(true);
            level.removeBlock(event.getPos(), false);
        }
    }

    @SubscribeEvent
    public static void onEnderPearl(EntityTeleportEvent.EnderPearl event) {
        denyPlayerTeleport(event.getPlayer(), event.getPrev(), event.getTarget(), event);
    }

    @SubscribeEvent
    public static void onChorus(EntityTeleportEvent.ChorusFruit event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            denyPlayerTeleport(player, event.getPrev(), event.getTarget(), event);
        }
    }

    @SubscribeEvent
    public static void onCommandTeleport(EntityTeleportEvent.TeleportCommand event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            denyPlayerTeleport(player, event.getPrev(), event.getTarget(), event);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide()) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        Zone zone = ZoneManager.at(level, player);
        if (zone != null && zone.has(ZoneFlag.DENY_ENTRY) && !mayPassEntry(player, zone)) {
            pushPlayerOutside(player, zone, player.getUUID());
            zone = ZoneManager.at(level, player);
        } else {
            LAST_OUTSIDE.put(player.getUUID(), player.position());
        }

        GameType mode = player.gameMode.getGameModeForPlayer();
        if (mode == GameType.ADVENTURE) {
            boolean allow = zone != null
                    && (zone.has(ZoneFlag.ADVENTURE_BUILD) || zone.has(ZoneFlag.ADVENTURE_BREAK));
            if (player.getAbilities().mayBuild != allow) {
                player.getAbilities().mayBuild = allow;
                player.onUpdateAbilities();
            }
        }

        handleZonePresence(player, level, zone);

        if (zone == null) {
            return;
        }

        if (zone.has(ZoneFlag.PLAYER_DAMAGE)
                && !player.isCreative()
                && !player.isSpectator()) {
            ZoneSettings s = zone.settings();
            int interval = s.damageInterval();
            if (player.tickCount % interval == 0) {
                player.hurt(damageSource(player, s.damageKind()), s.damageAmount());
                if (s.damageWither()) {
                    player.addEffect(new MobEffectInstance(MobEffects.WITHER, interval + 10, 0));
                }
            }
        }

        refreshWhileInsideEffects(player, zone);
        if (player.tickCount % Math.max(1, zone.settings().particleRate()) == 0) {
            spawnZoneParticles(level, zone, player);
        }
        if (zone.isEventActive() && player.tickCount % 40 == 0) {
            ZoneScriptEngine.run(level, zone, ZoneTrigger.ON_EVENT_TICK, player);
        }
    }

    private static void handleZonePresence(ServerPlayer player, ServerLevel level, Zone zone) {
        UUID playerId = player.getUUID();
        UUID currentId = zone != null ? zone.getId() : null;
        UUID prevId = LAST_ZONE.get(playerId);

        if (zone != null && zone.contains(player.getX(), player.getY() + 0.1, player.getZ())) {
            LAST_INSIDE.put(playerId, player.position());
        }

        if (!Objects.equals(currentId, prevId)) {
            if (prevId != null) {
                Zone prev = ZoneManager.data(level).get(prevId);
                if (prev != null) {
                    ZoneScriptEngine.run(level, prev, ZoneTrigger.ON_EXIT, player);
                }
            }
            if (zone != null) {
                ZoneScriptEngine.run(level, zone, ZoneTrigger.ON_ENTER, player);
                onZoneEnter(player, level, zone);
            }
            if (currentId == null) {
                LAST_ZONE.remove(playerId);
            } else {
                LAST_ZONE.put(playerId, currentId);
            }
        }

        Zone denyZone = zone != null ? zone : (prevId != null ? ZoneManager.data(level).get(prevId) : null);
        if (denyZone != null && shouldDenyExit(denyZone)) {
            boolean inside = denyZone.contains(player.getX(), player.getY() + 0.1, player.getZ());
            if (!inside) {
                pullPlayerInside(player, denyZone, playerId);
            }
        }
    }

    private static void pullPlayerInside(ServerPlayer player, Zone denyZone, UUID playerId) {
        Vec3 back = LAST_INSIDE.get(playerId);
        BlockPos min = denyZone.getMin();
        BlockPos max = denyZone.getMax();
        double cx = (min.getX() + max.getX() + 1) * 0.5;
        double cz = (min.getZ() + max.getZ() + 1) * 0.5;
        double cy = Mth.clamp(player.getY(), denyZone.getFloorY(),
                denyZone.getFloorY() + Math.max(0, denyZone.getHeight() - 1));
        PULLING.add(playerId);
        try {
            if (back != null && denyZone.contains(back.x, back.y + 0.1, back.z)) {
                player.teleportTo(back.x, back.y, back.z);
            } else {
                player.teleportTo(cx, cy, cz);
            }
        } finally {
            PULLING.remove(playerId);
        }
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
        LAST_INSIDE.put(playerId, player.position());
        LAST_ZONE.put(playerId, denyZone.getId());
        if (player.tickCount % 20 == 0) {
            player.displayClientMessage(Component.translatable("message.yblk_zones.deny_exit"), true);
        }
    }

    private static boolean shouldDenyExit(Zone zone) {
        return zone.isDenyExit() || (zone.isEventActive() && zone.isDenyExitWhileEvent());
    }

    private static void onZoneEnter(ServerPlayer player, ServerLevel level, Zone zone) {
        ZoneSettings s = zone.settings();
        // Enter title is shown under the zone HUD name (client overlay), not as actionbar spam.
        if (!s.enterSound().isBlank()) {
            ResourceLocation soundId = ResourceLocation.tryParse(s.enterSound());
            if (soundId != null) {
                SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(soundId);
                if (sound != null) {
                    level.playSound(null, player.blockPosition(), sound, SoundSource.AMBIENT,
                            s.enterSoundVolume(), 1.0F);
                }
            }
        }
        for (EffectEntry entry : s.effects()) {
            if (entry.onEnter()) {
                applyEffectEntry(player, entry);
            }
        }
    }

    private static void refreshWhileInsideEffects(ServerPlayer player, Zone zone) {
        for (EffectEntry entry : zone.settings().effects()) {
            if (entry.whileInside()) {
                applyEffectEntry(player, entry);
            }
        }
    }

    private static void applyEffectEntry(ServerPlayer player, EffectEntry entry) {
        if (!entry.isValid()) {
            return;
        }
        MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(entry.id());
        if (effect == null) {
            return;
        }
        boolean show = entry.showParticles();
        // ambient, visible particles, showIcon — Fx OFF hides both particles and HUD icon
        player.addEffect(new MobEffectInstance(effect, entry.durationTicks(), entry.amplifier(),
                true, show, show));
    }

    @SuppressWarnings("unchecked")
    private static void spawnZoneParticles(ServerLevel level, Zone zone, ServerPlayer player) {
        ZoneSettings s = zone.settings();
        int count = Math.max(1, s.particleCount());
        int floorY = zone.getFloorY();
        int height = zone.getHeight();
        double borderYSpan = Math.min(height, 3);
        if (!s.particleInterior().isBlank()) {
            ParticleOptions opts = parseParticle(s.particleInterior());
            if (opts != null) {
                AABB box = zone.aabb();
                for (int i = 0; i < count; i++) {
                    for (int attempt = 0; attempt < 16; attempt++) {
                        double x = box.minX + level.random.nextDouble() * (box.maxX - box.minX);
                        double y = floorY + level.random.nextDouble() * height;
                        double z = box.minZ + level.random.nextDouble() * (box.maxZ - box.minZ);
                        if (zone.contains(x, y, z)) {
                            level.sendParticles(opts, x, y, z, 1, 0, 0.05, 0, 0.01);
                            break;
                        }
                    }
                }
            }
        }
        if (!s.particleBorder().isBlank()) {
            ParticleOptions opts = parseParticle(s.particleBorder());
            if (opts != null) {
                int borderCount = count * 2;
                List<BlockPos> wire = zone.wireCorners();
                if (wire.size() >= 2) {
                    for (int i = 0; i < borderCount; i++) {
                        int edge = level.random.nextInt(wire.size());
                        BlockPos a = wire.get(edge);
                        BlockPos b = wire.get((edge + 1) % wire.size());
                        double t = level.random.nextDouble();
                        double x = Mth.lerp(t, (double) a.getX(), (double) b.getX());
                        double z = Mth.lerp(t, (double) a.getZ(), (double) b.getZ());
                        double y = floorY + level.random.nextDouble() * borderYSpan;
                        level.sendParticles(opts, x, y, z, 1, 0, 0.1, 0, 0.02);
                    }
                } else {
                    BlockPos min = zone.getMin();
                    BlockPos max = zone.getMax();
                    double x0 = min.getX();
                    double z0 = min.getZ();
                    double x1 = max.getX() + 1.0;
                    double z1 = max.getZ() + 1.0;
                    for (int i = 0; i < borderCount; i++) {
                        double x;
                        double z;
                        switch (level.random.nextInt(4)) {
                            case 0 -> {
                                x = Mth.lerp(level.random.nextDouble(), x0, x1);
                                z = z0;
                            }
                            case 1 -> {
                                x = Mth.lerp(level.random.nextDouble(), x0, x1);
                                z = z1;
                            }
                            case 2 -> {
                                x = x0;
                                z = Mth.lerp(level.random.nextDouble(), z0, z1);
                            }
                            default -> {
                                x = x1;
                                z = Mth.lerp(level.random.nextDouble(), z0, z1);
                            }
                        }
                        double y = floorY + level.random.nextDouble() * borderYSpan;
                        level.sendParticles(opts, x, y, z, 1, 0, 0.1, 0, 0.02);
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static ParticleOptions parseParticle(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) {
            return null;
        }
        ParticleType<?> type = ForgeRegistries.PARTICLE_TYPES.getValue(rl);
        if (type instanceof ParticleOptions options) {
            return options;
        }
        if (type instanceof SimpleParticleType simple) {
            return simple;
        }
        return null;
    }

    /**
     * Denied mobs treat the zone volume as a solid block (min-penetration resolve).
     * Buff applies independently for mobs that are inside.
     */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide() || !(entity.level() instanceof ServerLevel level)) {
            return;
        }
        if (!(entity instanceof Mob mob)) {
            return;
        }

        for (Zone zone : ZoneManager.data(level).all()) {
            // --- wall: solid collision, no teleport ---
            if (zone.has(ZoneFlag.MOB_DENY_ENTRY) && deniesMob(zone, mob)) {
                collideLikeBlock(mob, zone);
            }

            // --- buff: separate from deny ---
            if (zone.has(ZoneFlag.MOB_BUFF)
                    && buffsMob(zone, mob)
                    && zone.contains(mob.getX(), mob.getY() + 0.1, mob.getZ())
                    && mob.tickCount % 20 == 0) {
                int amp = zone.settings().buffAmplifier();
                mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 45, amp, true, true));
                mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 45,
                        Math.min(2, amp / 2), true, true));
            }
        }
    }

    /** Resolve mob AABB vs zone as a solid cube — same idea as walking into a wall. */
    private static void collideLikeBlock(Mob mob, Zone zone) {
        Vec3 sep = zone.collideSolid(mob.getBoundingBox());
        if (sep.equals(Vec3.ZERO)) {
            // Also block inward velocity before they overlap (edge stick)
            Vec3 motion = mob.getDeltaMovement();
            if (motion.x == 0 && motion.z == 0) {
                return;
            }
            var moved = mob.getBoundingBox().move(motion.x, 0, motion.z);
            Vec3 pred = zone.collideSolid(moved);
            if (pred.equals(Vec3.ZERO)) {
                return;
            }
            double vx = motion.x;
            double vz = motion.z;
            if (pred.x != 0) {
                vx = 0;
            }
            if (pred.z != 0) {
                vz = 0;
            }
            mob.setDeltaMovement(vx, motion.y, vz);
            return;
        }

        mob.setPos(mob.getX() + sep.x, mob.getY(), mob.getZ() + sep.z);
        Vec3 motion = mob.getDeltaMovement();
        double vx = motion.x;
        double vz = motion.z;
        if (sep.x != 0) {
            vx = 0;
        }
        if (sep.z != 0) {
            vz = 0;
        }
        mob.setDeltaMovement(vx, motion.y, vz);
        mob.horizontalCollision = true;
        // Stop pathfinding into the wall
        if (mob.getNavigation().isInProgress()) {
            var path = mob.getNavigation().getPath();
            if (path != null && !path.isDone()) {
                var next = path.getNextNodePos();
                if (zone.containsXZ(next.getX() + 0.5, next.getZ() + 0.5)) {
                    mob.getNavigation().stop();
                }
            }
        }
    }

    private static boolean deniesMob(Zone zone, Mob mob) {
        return matchesMobGroup(mob, zone.settings().denyMobGroup(), zone.settings().denyMobs());
    }

    private static boolean buffsMob(Zone zone, Mob mob) {
        return matchesMobGroup(mob, zone.settings().buffMobGroup(), zone.settings().buffMobs());
    }

    private static boolean matchesMobGroup(Mob mob, ZoneSettings.MobGroup group,
                                           java.util.Set<ResourceLocation> custom) {
        return switch (group) {
            case HOSTILE -> mob instanceof Enemy;
            case PASSIVE -> mob instanceof net.minecraft.world.entity.animal.Animal && !(mob instanceof Enemy);
            case NEUTRAL -> mob instanceof net.minecraft.world.entity.NeutralMob;
            case CUSTOM -> {
                ResourceLocation id = EntityType.getKey(mob.getType());
                yield id != null && custom.contains(id);
            }
        };
    }

    private static boolean isAdventure(Player player) {
        if (player instanceof ServerPlayer sp) {
            return sp.gameMode.getGameModeForPlayer() == GameType.ADVENTURE;
        }
        return false;
    }

    private static void grantMayBuild(Player player) {
        if (player.getAbilities().mayBuild) {
            return;
        }
        player.getAbilities().mayBuild = true;
        if (player instanceof ServerPlayer sp) {
            sp.onUpdateAbilities();
        }
    }

    private static DamageSource damageSource(ServerPlayer player, ZoneSettings.DamageKind kind) {
        return switch (kind) {
            case WITHER -> player.damageSources().wither();
            case GENERIC -> player.damageSources().generic();
            case FIRE -> player.damageSources().onFire();
            case SWEET_BERRY -> player.damageSources().sweetBerryBush();
            case THORNS -> player.damageSources().thorns(player);
            case MAGIC -> player.damageSources().magic();
        };
    }

    private static boolean mayPassEntry(Player player, Zone zone) {
        if (player.hasPermissions(2)) {
            return true;
        }
        if (zone.getOwner() != null && zone.getOwner().equals(player.getUUID())) {
            return true;
        }
        if (zone.roleOf(player).canBuild()) {
            return true;
        }
        return zone.settings().isTrusted(player);
    }

    private static void pushPlayerOutside(ServerPlayer player, Zone zone, UUID playerId) {
        Vec3 back = LAST_OUTSIDE.get(playerId);
        BlockPos min = zone.getMin();
        BlockPos max = zone.getMax();
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        if (back == null || zone.contains(back.x, back.y + 0.1, back.z)) {
            double cx = (min.getX() + max.getX() + 1) * 0.5;
            double cz = (min.getZ() + max.getZ() + 1) * 0.5;
            double dx = x - cx;
            double dz = z - cz;
            if (dx == 0 && dz == 0) {
                dx = 1;
            }
            double scale = Math.max(Math.abs(dx), Math.abs(dz));
            dx /= scale;
            dz /= scale;
            x = cx + dx * ((max.getX() - min.getX()) * 0.5 + 1.5);
            z = cz + dz * ((max.getZ() - min.getZ()) * 0.5 + 1.5);
        } else {
            x = back.x;
            y = back.y;
            z = back.z;
        }
        PULLING.add(playerId);
        try {
            player.teleportTo(x, y, z);
        } finally {
            PULLING.remove(playerId);
        }
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
        if (player.tickCount % 20 == 0) {
            player.displayClientMessage(Component.translatable("message.yblk_zones.deny_entry"), true);
        }
    }

    private static void denyPlayerTeleport(ServerPlayer player, Vec3 from, Vec3 to, EntityTeleportEvent event) {
        if (player.hasPermissions(2) || PULLING.contains(player.getUUID())) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (teleportBlocked(level, from) || teleportBlocked(level, to)) {
            event.setCanceled(true);
            player.displayClientMessage(Component.translatable("message.yblk_zones.deny_teleport"), true);
        }
    }

    private static boolean teleportBlocked(ServerLevel level, Vec3 pos) {
        Zone zone = ZoneManager.at(level, BlockPos.containing(pos));
        return zone != null && zone.has(ZoneFlag.DENY_TELEPORT);
    }

    private static boolean inFlag(Entity entity, ZoneFlag flag) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return false;
        }
        Zone zone = ZoneManager.at(level, entity.blockPosition());
        return zone != null && zone.has(flag);
    }

    private static boolean claimLocked(ServerLevel level, BlockPos pos) {
        Zone zone = ZoneManager.at(level, pos);
        return zone != null && zone.has(ZoneFlag.CLAIM);
    }

    private static boolean explosionBlocked(ServerLevel level, BlockPos pos) {
        Zone zone = ZoneManager.at(level, pos);
        return zone != null && (zone.has(ZoneFlag.CLAIM) || zone.has(ZoneFlag.DENY_EXPLOSIONS));
    }

    private static boolean isContainer(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof BaseContainerBlockEntity
                || level.getBlockState(pos).getMenuProvider(level, pos) != null;
    }

    private static void denyUse(PlayerInteractEvent.RightClickBlock event) {
        event.setCanceled(true);
        event.setUseBlock(Event.Result.DENY);
        event.setUseItem(Event.Result.DENY);
    }

    private static boolean lockedDecoration(Player player, Entity target) {
        if (!(target instanceof ItemFrame || target instanceof ArmorStand || target instanceof Painting)) {
            return false;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        Zone zone = ZoneManager.at(level, target.blockPosition());
        return zone != null && zone.has(ZoneFlag.CLAIM) && !ZoneManager.canModify(player, zone);
    }
}
