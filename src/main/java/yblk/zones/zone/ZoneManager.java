package yblk.zones.zone;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import yblk.zones.ZonesConfig;
import yblk.zones.network.SyncZonesPacket;
import yblk.zones.network.ZonesNetwork;
import yblk.zones.script.ZoneScriptEngine;
import yblk.zones.script.ZoneTrigger;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class ZoneManager {
    private ZoneManager() {
    }

    public static ZoneSavedData data(ServerLevel level) {
        return ZoneSavedData.get(level);
    }

    public static Zone create(
            ServerLevel level,
            String name,
            List<BlockPos> corners,
            int floorY,
            int height,
            UUID owner,
            Set<ZoneFlag> flags,
            ZoneSettings settings) {
        Zone zone = new Zone(UUID.randomUUID(), name, corners, floorY, height);
        zone.setOwner(owner);
        for (ZoneFlag flag : flags) {
            zone.setFlag(flag, true);
        }
        if (!zone.has(ZoneFlag.VISIBLE) && !zone.has(ZoneFlag.DISPLAY)) {
            zone.setFlag(ZoneFlag.VISIBLE, true);
            zone.setFlag(ZoneFlag.DISPLAY, true);
        }
        if (settings != null) {
            zone.settings().load(settings.save());
        }
        if (zone.getLeasePeriodTicks() <= 0) {
            zone.setLeasePeriodTicks(ZonesConfig.defaultLeasePeriodTicks);
        }
        data(level).add(zone);
        ZoneLog.info("Created zone '" + zone.getName() + "' id=" + zone.getId());
        sync(level);
        return zone;
    }

    public static int countOwned(ServerLevel level, UUID owner) {
        int n = 0;
        for (Zone zone : data(level).all()) {
            if (owner.equals(zone.getOwner())) {
                n++;
            }
        }
        return n;
    }

    public static boolean canOwnMore(ServerLevel level, UUID owner) {
        return countOwned(level, owner) < ZonesConfig.maxZonesPerPlayer;
    }

    public static int volumeOf(List<BlockPos> corners, int height) {
        if (corners == null || corners.isEmpty() || height < 1) {
            return 0;
        }
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos c : corners) {
            minX = Math.min(minX, c.getX());
            maxX = Math.max(maxX, c.getX());
            minZ = Math.min(minZ, c.getZ());
            maxZ = Math.max(maxZ, c.getZ());
        }
        return (maxX - minX + 1) * (maxZ - minZ + 1) * height;
    }

    public static boolean isWithinSize(List<BlockPos> corners, int height) {
        if (corners == null || corners.isEmpty()) {
            return false;
        }
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos c : corners) {
            minX = Math.min(minX, c.getX());
            maxX = Math.max(maxX, c.getX());
            minZ = Math.min(minZ, c.getZ());
            maxZ = Math.max(maxZ, c.getZ());
        }
        int max = ZonesConfig.maxZoneSize;
        if ((maxX - minX + 1) > max || (maxZ - minZ + 1) > max || height > max) {
            return false;
        }
        return volumeOf(corners, height) <= ZonesConfig.maxZoneVolume;
    }

    public static boolean isWithinSize(BlockPos a, BlockPos b) {
        return isWithinSize(List.of(a, b), Math.abs(a.getY() - b.getY()) + 1);
    }

    public static Zone at(ServerLevel level, BlockPos pos) {
        return data(level).findAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    public static Zone at(ServerLevel level, Player player) {
        return data(level).findAt(player.getX(), player.getY() + 0.1, player.getZ());
    }

    public static boolean canModify(Player player, Zone zone) {
        if (player == null || zone == null) {
            return true;
        }
        if (player.hasPermissions(2)) {
            return true;
        }
        if (!zone.has(ZoneFlag.CLAIM)) {
            return true;
        }
        ZoneRole role = zone.roleOf(player);
        if (role.canBuild()) {
            return true;
        }
        return zone.settings().isTrusted(player);
    }

    public static boolean canDelete(Player player, Zone zone) {
        if (player == null || zone == null) {
            return false;
        }
        if (player.hasPermissions(2)) {
            return true;
        }
        if (zone.getOwner() != null && zone.getOwner().equals(player.getUUID())) {
            return true;
        }
        return zone.getOwner() == null && canModify(player, zone);
    }

    public static boolean canSellTransfer(Player player, Zone zone) {
        if (player == null || zone == null) {
            return false;
        }
        if (player.hasPermissions(2)) {
            return true;
        }
        return zone.roleOf(player).canSellTransfer();
    }

    /**
     * @return null on success, or a translation key
     */
    public static String tryBuy(ServerPlayer player, Zone zone) {
        if (player == null || zone == null) {
            return "message.yblk_zones.not_in_zone";
        }
        if (!zone.isForSale() || zone.getBuyCosts().isEmpty()) {
            return "message.yblk_zones.not_for_sale";
        }
        if (zone.getOwner() != null && zone.getOwner().equals(player.getUUID())) {
            return "message.yblk_zones.already_owner";
        }
        if (!canOwnMore(player.serverLevel(), player.getUUID())) {
            return "message.yblk_zones.too_many";
        }
        List<SaleCost> costs = zone.getBuyCosts();
        for (SaleCost cost : costs) {
            if (!cost.isValid()) {
                return "message.yblk_zones.not_for_sale";
            }
        }
        ZoneScriptEngine.run(player.serverLevel(), zone, ZoneTrigger.BEFORE_BUY, player);

        List<ItemStack> paid = new ArrayList<>();
        if (!player.isCreative()) {
            for (SaleCost cost : costs) {
                Item item = itemOf(cost.itemId());
                if (item == null || countOf(player.getInventory(), item) < cost.count()) {
                    return "message.yblk_zones.cannot_afford";
                }
            }
            for (SaleCost cost : costs) {
                Item item = itemOf(cost.itemId());
                consume(player.getInventory(), item, cost.count());
                paid.add(new ItemStack(item, cost.count()));
            }
        }

        UUID previousOwner = zone.getOwner();
        if (zone.getPaymentDestination() == PaymentDestination.SELLER && previousOwner != null && !paid.isEmpty()) {
            deliverTo(player.serverLevel(), previousOwner, paid);
        }

        String buyerName = player.getGameProfile().getName();
        if (zone.getLandlord() == null && previousOwner != null) {
            zone.setLandlord(previousOwner);
        }
        zone.setOwner(player.getUUID());
        zone.setFlag(ZoneFlag.CLAIM, true);
        zone.setName(buyerName + "'s base");
        zone.settings().trustedNames().clear();
        zone.settings().trustedIds().clear();
        zone.settings().addTrustedName(buyerName);
        zone.settings().trustedIds().add(player.getUUID());
        zone.clearBuyCosts();
        if (!zone.getLeaseCosts().isEmpty() && zone.getLeasePeriodTicks() > 0) {
            zone.setLeaseExpiresAt(player.serverLevel().getGameTime() + zone.getLeasePeriodTicks());
        }
        ZoneScriptEngine.run(player.serverLevel(), zone, ZoneTrigger.AFTER_BUY, player);
        ZoneLog.info(buyerName + " bought zone '" + zone.getName() + "'");
        data(player.serverLevel()).markDirty();
        sync(player.serverLevel());
        return null;
    }

    public static String tryTransfer(ServerPlayer from, Zone zone, ServerPlayer to) {
        if (from == null || zone == null || to == null) {
            return "message.yblk_zones.not_in_zone";
        }
        if (!canSellTransfer(from, zone)) {
            return "message.yblk_zones.not_owner";
        }
        if (!canOwnMore(to.serverLevel(), to.getUUID())) {
            return "message.yblk_zones.too_many";
        }
        zone.setOwner(to.getUUID());
        zone.setMember(to.getUUID(), to.getGameProfile().getName(), ZoneRole.OWNER);
        zone.setMember(from.getUUID(), from.getGameProfile().getName(), ZoneRole.MEMBER);
        ZoneLog.info(from.getGameProfile().getName() + " transferred '" + zone.getName()
                + "' to " + to.getGameProfile().getName());
        data(from.serverLevel()).markDirty();
        sync(from.serverLevel());
        return null;
    }

    public static String tryLeave(ServerPlayer player, Zone zone) {
        if (player == null || zone == null) {
            return "message.yblk_zones.not_in_zone";
        }
        UUID id = player.getUUID();
        if (zone.getOwner() != null && zone.getOwner().equals(id)) {
            ZoneMember manager = null;
            for (ZoneMember m : zone.getMembers()) {
                if (m.role() == ZoneRole.MANAGER && (m.id() == null || !m.id().equals(id))) {
                    manager = m;
                    break;
                }
            }
            if (manager != null && manager.id() != null) {
                zone.setOwner(manager.id());
                zone.setMember(manager.id(), manager.name(), ZoneRole.OWNER);
            } else {
                zone.setOwner(null);
                zone.setFlag(ZoneFlag.CLAIM, false);
            }
            zone.removeMember(id);
            ZoneLog.info(player.getGameProfile().getName() + " left ownership of '" + zone.getName() + "'");
        } else {
            zone.removeMember(id);
            zone.settings().trustedIds().remove(id);
            zone.settings().removeTrustedName(player.getGameProfile().getName());
        }
        data(player.serverLevel()).markDirty();
        sync(player.serverLevel());
        return null;
    }

    public static String tryPayLease(ServerPlayer player, Zone zone) {
        if (player == null || zone == null) {
            return "message.yblk_zones.not_in_zone";
        }
        if (zone.getOwner() == null || !zone.getOwner().equals(player.getUUID())) {
            return "message.yblk_zones.not_owner";
        }
        List<SaleCost> costs = zone.getLeaseCosts();
        if (costs.isEmpty()) {
            return "message.yblk_zones.no_lease";
        }
        if (!player.isCreative()) {
            for (SaleCost cost : costs) {
                Item item = itemOf(cost.itemId());
                if (item == null || countOf(player.getInventory(), item) < cost.count()) {
                    return "message.yblk_zones.cannot_afford";
                }
            }
            List<ItemStack> paid = new ArrayList<>();
            for (SaleCost cost : costs) {
                Item item = itemOf(cost.itemId());
                consume(player.getInventory(), item, cost.count());
                paid.add(new ItemStack(item, cost.count()));
            }
            if (zone.getPaymentDestination() == PaymentDestination.SELLER && zone.getLandlord() != null) {
                deliverTo(player.serverLevel(), zone.getLandlord(), paid);
            }
        }
        long period = zone.getLeasePeriodTicks() > 0 ? zone.getLeasePeriodTicks() : ZonesConfig.defaultLeasePeriodTicks;
        long now = player.serverLevel().getGameTime();
        long base = Math.max(now, zone.getLeaseExpiresAt());
        zone.setLeaseExpiresAt(base + period);
        ZoneLog.info(player.getGameProfile().getName() + " paid lease for '" + zone.getName() + "'");
        data(player.serverLevel()).markDirty();
        sync(player.serverLevel());
        return null;
    }

    public static void tickLeases(ServerLevel level) {
        long now = level.getGameTime();
        boolean dirty = false;
        for (Zone zone : data(level).all()) {
            if (zone.getLeaseExpiresAt() <= 0 || zone.getLeaseCosts().isEmpty()) {
                continue;
            }
            if (now < zone.getLeaseExpiresAt()) {
                continue;
            }
            applyLeaseExpire(level, zone);
            dirty = true;
        }
        if (dirty) {
            data(level).markDirty();
            sync(level);
        }
    }

    private static void applyLeaseExpire(ServerLevel level, Zone zone) {
        ZoneLog.info("Lease expired for '" + zone.getName() + "' action=" + zone.getOnExpire());
        switch (zone.getOnExpire()) {
            case RETURN_LANDLORD -> {
                if (zone.getLandlord() != null) {
                    zone.setOwner(zone.getLandlord());
                } else {
                    zone.setOwner(null);
                    zone.setFlag(ZoneFlag.CLAIM, false);
                }
            }
            case LOCK -> {
                // keep owner, strip build access beyond owner
                List<ZoneMember> copy = new ArrayList<>(zone.getMembers());
                for (ZoneMember m : copy) {
                    if (m.role() != ZoneRole.OWNER) {
                        m.setRole(ZoneRole.VISITOR);
                    }
                }
            }
            case UNCLAIM -> {
                zone.setOwner(null);
                zone.setFlag(ZoneFlag.CLAIM, false);
            }
        }
        zone.setLeaseExpiresAt(0);
    }

    public static String startEvent(ServerPlayer player, Zone zone) {
        if (player == null || zone == null) {
            return "message.yblk_zones.not_in_zone";
        }
        if (!player.hasPermissions(2) && !zone.roleOf(player).canStartEvent()) {
            return "message.yblk_zones.not_owner";
        }
        if (!zone.isEventEnabled()) {
            return "message.yblk_zones.event_disabled";
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        if (now < zone.getEventCooldownUntil()) {
            return "message.yblk_zones.event_cooldown";
        }
        zone.setEventActive(true);
        zone.setEventStartedAt(now);
        zone.resetEventRuntime();
        if (zone.isDenyExitWhileEvent()) {
            zone.setDenyExit(true);
        }
        // First wave immediately (scripts + Event spawn settings)
        ZoneScriptEngine.run(level, zone, ZoneTrigger.ON_EVENT_START, player);
        ZoneSettings startSettings = zone.settings();
        if (startSettings.eventSpawnCount() > 0 && !startSettings.eventSpawnMob().isBlank()) {
            zone.setEventWave(1);
            int spawned = spawnEventMobs(level, zone, startSettings);
            zone.setLastEventSpawnAt(now);
            if (spawned > 0) {
                zone.setEventHadMobs(true);
            }
            announceWave(level, zone, 1, startSettings.eventWaves());
        }
        ZoneLog.info("Event started on '" + zone.getName() + "' by " + player.getGameProfile().getName());
        data(level).markDirty();
        sync(level);
        return null;
    }

    public static String stopEvent(ServerPlayer player, Zone zone) {
        if (player == null || zone == null) {
            return "message.yblk_zones.not_in_zone";
        }
        if (!player.hasPermissions(2) && !zone.roleOf(player).canStartEvent()) {
            return "message.yblk_zones.not_owner";
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        int minTicks = zone.settings().eventMinTicks();
        if (zone.isEventActive() && minTicks > 0 && now - zone.getEventStartedAt() < minTicks) {
            return "message.yblk_zones.event_too_early";
        }
        finishEvent(level, zone, player);
        return null;
    }

    /** Auto-stop when duration expires (no permission / min-time checks). */
    public static void stopEventAuto(ServerLevel level, Zone zone) {
        if (level == null || zone == null || !zone.isEventActive()) {
            return;
        }
        finishEvent(level, zone, null);
    }

    private static void finishEvent(ServerLevel level, Zone zone, ServerPlayer player) {
        zone.setEventActive(false);
        zone.setDenyExit(false);
        long now = level.getGameTime();
        zone.setEventCooldownUntil(now + zone.settings().eventCooldownTicks());
        zone.setEventStartedAt(0);
        zone.resetEventRuntime();
        // Rewards / end scripts for everyone still inside (incl. auto-stop when duration ends)
        boolean ran = false;
        for (ServerPlayer p : level.players()) {
            if (zone.contains(p.getX(), p.getY() + 0.1, p.getZ())) {
                ZoneScriptEngine.run(level, zone, ZoneTrigger.ON_EVENT_END, p);
                ran = true;
            }
        }
        if (!ran && player != null) {
            ZoneScriptEngine.run(level, zone, ZoneTrigger.ON_EVENT_END, player);
        }
        ZoneLog.info("Event stopped on '" + zone.getName() + "'");
        data(level).markDirty();
        sync(level);
    }

    /** Frequent: keep mobs in zone + no sun burn. */
    public static void tickEventMobs(ServerLevel level) {
        for (Zone zone : data(level).all()) {
            if (zone.isEventActive()) {
                ZoneMobSpawner.tickContainment(level, zone);
            }
        }
    }

    public static void tickEvents(ServerLevel level) {
        long now = level.getGameTime();
        boolean dirty = false;
        for (Zone zone : data(level).all()) {
            if (!zone.isEventActive()) {
                continue;
            }
            ZoneSettings s = zone.settings();
            int duration = s.eventDurationTicks();
            if (duration > 0 && now - zone.getEventStartedAt() >= duration) {
                stopEventAuto(level, zone);
                dirty = true;
                continue;
            }
            boolean hasSpawn = s.eventSpawnCount() > 0 && !s.eventSpawnMob().isBlank();
            if (!hasSpawn) {
                continue;
            }

            int alive = ZoneMobSpawner.countAliveEventMobs(level, zone);
            int maxWaves = s.eventWaves();

            if (alive > 0) {
                zone.setEventClearStreak(0);
                zone.setEventHadMobs(true);
                continue;
            }
            if (!zone.isEventHadMobs()) {
                continue;
            }
            zone.setEventClearStreak(zone.getEventClearStreak() + 1);
            // ~1 check/sec → wait 2s after last kill before next wave / victory
            if (zone.getEventClearStreak() < 2) {
                continue;
            }

            // Next wave only after clear (never stack while previous wave lives)
            if (maxWaves > 0 && zone.getEventWave() < maxWaves) {
                int next = zone.getEventWave() + 1;
                zone.setEventWave(next);
                int spawned = spawnEventMobs(level, zone, s);
                zone.setLastEventSpawnAt(now);
                zone.setEventClearStreak(0);
                if (spawned > 0) {
                    zone.setEventHadMobs(true);
                    announceWave(level, zone, next, maxWaves);
                } else {
                    // Failed spawn — don't soft-lock; count as finished wave
                    zone.setEventHadMobs(false);
                }
                dirty = true;
            } else if (maxWaves <= 0 && !s.eventWinOnClear()) {
                // Unlimited: another wave after clear (still not while alive)
                int next = Math.max(1, zone.getEventWave()) + 1;
                zone.setEventWave(next);
                int spawned = spawnEventMobs(level, zone, s);
                zone.setLastEventSpawnAt(now);
                zone.setEventClearStreak(0);
                if (spawned > 0) {
                    zone.setEventHadMobs(true);
                    announceWave(level, zone, next, 0);
                }
                dirty = true;
            } else {
                ZoneLog.info("Event cleared on '" + zone.getName() + "' → victory");
                stopEventAuto(level, zone);
                dirty = true;
            }
        }
        if (dirty) {
            data(level).markDirty();
        }
    }

    private static int spawnEventMobs(ServerLevel level, Zone zone, ZoneSettings s) {
        ServerPlayer near = null;
        for (ServerPlayer p : level.players()) {
            if (zone.contains(p.getX(), p.getY() + 0.1, p.getZ())) {
                near = p;
                break;
            }
        }
        return ZoneMobSpawner.spawn(level, zone, s.eventSpawnMob(), s.eventSpawnCount(), near);
    }

    private static void announceWave(ServerLevel level, Zone zone, int wave, int maxWaves) {
        Component msg = maxWaves > 0
                ? Component.translatable("message.yblk_zones.event_wave", wave, maxWaves)
                : Component.translatable("message.yblk_zones.event_wave_open", wave);
        for (ServerPlayer p : level.players()) {
            if (zone.contains(p.getX(), p.getY() + 0.1, p.getZ())) {
                p.displayClientMessage(msg, false);
            }
        }
    }

    private static void deliverTo(ServerLevel level, UUID recipient, List<ItemStack> stacks) {
        ServerPlayer online = level.getServer().getPlayerList().getPlayer(recipient);
        for (ItemStack stack : stacks) {
            ItemStack copy = stack.copy();
            if (online != null) {
                if (!online.getInventory().add(copy)) {
                    online.drop(copy, false);
                }
            } else {
                // void leftover when seller offline — items already consumed from buyer
            }
        }
    }

    private static Item itemOf(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) {
            return null;
        }
        Item item = ForgeRegistries.ITEMS.getValue(id);
        if (item == null || item == net.minecraft.world.item.Items.AIR) {
            return null;
        }
        return item;
    }

    public static int countOf(Inventory inv, Item item) {
        int have = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(item)) {
                have += stack.getCount();
            }
        }
        return have;
    }

    public static boolean consume(Inventory inv, Item item, int count) {
        if (item == null || countOf(inv, item) < count) {
            return false;
        }
        int left = count;
        for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(item)) {
                int take = Math.min(left, stack.getCount());
                stack.shrink(take);
                left -= take;
            }
        }
        return true;
    }

    public static void sync(ServerLevel level) {
        List<Zone> snapshot = new ArrayList<>(data(level).all());
        SyncZonesPacket packet = SyncZonesPacket.of(level.dimension().location().toString(), snapshot);
        for (ServerPlayer player : level.players()) {
            ZonesNetwork.sendToPlayer(packet, player);
        }
    }

    public static void syncTo(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        List<Zone> snapshot = new ArrayList<>(data(level).all());
        ZonesNetwork.sendToPlayer(
                SyncZonesPacket.of(level.dimension().location().toString(), snapshot),
                player);
    }
}
