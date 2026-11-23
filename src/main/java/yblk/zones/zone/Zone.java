package yblk.zones.zone;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import yblk.zones.script.ZoneAction;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Vertical prism over a polygon footprint (XZ). Two corners → rectangle;
 * three or more → free polygon (not axis-aligned rectangle).
 */
public final class Zone {
    private final UUID id;
    private String name;
    private int level;
    /** Floor block Y (inclusive). Zone occupies [floorY, floorY+height). */
    private int floorY;
    private int height;
    /** Footprint corners in block coords (XZ). At least 2. */
    private final List<BlockPos> corners = new ArrayList<>();
    private UUID owner;
    private final EnumSet<ZoneFlag> flags = EnumSet.noneOf(ZoneFlag.class);
    private final ZoneSettings settings = new ZoneSettings();
    private final List<SaleCost> buyCosts = new ArrayList<>();
    private boolean forSale;
    private PaymentDestination paymentDestination = PaymentDestination.VOID;
    private final List<SaleCost> leaseCosts = new ArrayList<>();
    private int leasePeriodTicks = 72000;
    private long leaseExpiresAt;
    private UUID landlord;
    private LeaseExpireAction onExpire = LeaseExpireAction.UNCLAIM;
    private int priority;
    private final List<ZoneMember> members = new ArrayList<>();
    private final List<ZoneAction> actions = new ArrayList<>();
    private boolean eventEnabled;
    private boolean eventNeedsConfirm;
    private boolean eventActive;
    private long eventStartedAt;
    private long eventCooldownUntil;
    /** Runtime: last timed/wave spawn gameTime. */
    private long lastEventSpawnAt;
    /** Runtime: current wave number (1-based while active). */
    private int eventWave;
    /** Runtime: at least one event mob was spawned this event. */
    private boolean eventHadMobs;
    /** Runtime: consecutive clear checks with 0 alive event mobs. */
    private int eventClearStreak;
    private boolean denyExit;
    private boolean denyExitWhileEvent;
    private boolean allowCommands;

    public Zone(UUID id, String name, List<BlockPos> corners, int floorY, int height) {
        this.id = id;
        this.name = name;
        this.level = 1;
        this.floorY = floorY;
        this.height = Math.max(1, height);
        setCorners(corners);
    }

    /** Rectangle helper. */
    public Zone(UUID id, String name, BlockPos a, BlockPos b) {
        this(id, name, rectCorners(a, b), Math.min(a.getY(), b.getY()),
                Math.abs(a.getY() - b.getY()) + 1);
    }

    private static List<BlockPos> rectCorners(BlockPos a, BlockPos b) {
        int x0 = Math.min(a.getX(), b.getX());
        int z0 = Math.min(a.getZ(), b.getZ());
        int x1 = Math.max(a.getX(), b.getX());
        int z1 = Math.max(a.getZ(), b.getZ());
        int y = Math.min(a.getY(), b.getY());
        return List.of(
                new BlockPos(x0, y, z0),
                new BlockPos(x1, y, z0),
                new BlockPos(x1, y, z1),
                new BlockPos(x0, y, z1));
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = Math.max(1, level);
    }

    public int getFloorY() {
        return floorY;
    }

    public void setFloorY(int floorY) {
        this.floorY = floorY;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = Math.max(1, height);
    }

    public List<BlockPos> getCorners() {
        return corners;
    }

    public void setCorners(List<BlockPos> next) {
        corners.clear();
        if (next == null || next.isEmpty()) {
            return;
        }
        for (BlockPos p : next) {
            corners.add(new BlockPos(p.getX(), floorY, p.getZ()));
        }
        if (corners.size() == 2) {
            BlockPos a = corners.get(0);
            BlockPos b = corners.get(1);
            corners.clear();
            corners.addAll(rectCorners(a, b));
        }
    }

    public void addCorner(BlockPos pos) {
        corners.add(new BlockPos(pos.getX(), floorY, pos.getZ()));
    }

    public UUID getOwner() {
        return owner;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
        if (owner == null) {
            for (ZoneMember member : members) {
                if (member.role() == ZoneRole.OWNER) {
                    member.setRole(ZoneRole.MEMBER);
                }
            }
        } else {
            syncOwnerMember(owner);
        }
    }

    private void syncOwnerMember(UUID ownerId) {
        ZoneMember existing = findMember(ownerId);
        String name = existing != null ? existing.name() : "";
        for (ZoneMember member : members) {
            if (member.role() == ZoneRole.OWNER && (member.id() == null || !member.id().equals(ownerId))) {
                member.setRole(ZoneRole.MEMBER);
            }
        }
        setMember(ownerId, name, ZoneRole.OWNER);
    }

    public Set<ZoneFlag> getFlags() {
        return flags;
    }

    public boolean has(ZoneFlag flag) {
        return flags.contains(flag);
    }

    public void setFlag(ZoneFlag flag, boolean on) {
        if (on) {
            flags.add(flag);
        } else {
            flags.remove(flag);
        }
    }

    public ZoneSettings settings() {
        return settings;
    }

    public boolean isForSale() {
        return forSale;
    }

    public void setForSale(boolean forSale) {
        this.forSale = forSale;
    }

    public List<SaleCost> getBuyCosts() {
        return buyCosts;
    }

    /** First cost line — kept for HUD/commands that show a single summary. */
    public String getBuyItemId() {
        return buyCosts.isEmpty() ? "" : buyCosts.get(0).itemId();
    }

    public int getBuyCount() {
        return buyCosts.isEmpty() ? 0 : buyCosts.get(0).count();
    }

    public void setBuyCost(String itemId, int count) {
        buyCosts.clear();
        if (itemId != null && !itemId.isBlank() && count > 0) {
            buyCosts.add(new SaleCost(itemId, count));
        }
        refreshForSale();
    }

    public void setBuyCosts(List<SaleCost> costs) {
        buyCosts.clear();
        if (costs != null) {
            for (SaleCost c : costs) {
                if (c != null && c.isValid()) {
                    buyCosts.add(c.copy());
                }
            }
        }
        refreshForSale();
    }

    public void clearBuyCosts() {
        buyCosts.clear();
        forSale = false;
    }

    private void refreshForSale() {
        forSale = false;
        for (SaleCost c : buyCosts) {
            if (c.isValid()) {
                forSale = true;
                return;
            }
        }
    }

    public PaymentDestination getPaymentDestination() {
        return paymentDestination;
    }

    public void setPaymentDestination(PaymentDestination paymentDestination) {
        this.paymentDestination = paymentDestination == null ? PaymentDestination.VOID : paymentDestination;
    }

    public List<SaleCost> getLeaseCosts() {
        return leaseCosts;
    }

    public void setLeaseCosts(List<SaleCost> costs) {
        leaseCosts.clear();
        if (costs != null) {
            for (SaleCost c : costs) {
                if (c != null && c.isValid()) {
                    leaseCosts.add(c.copy());
                }
            }
        }
    }

    public void clearLeaseCosts() {
        leaseCosts.clear();
    }

    public int getLeasePeriodTicks() {
        return leasePeriodTicks;
    }

    public void setLeasePeriodTicks(int leasePeriodTicks) {
        this.leasePeriodTicks = Math.max(1, leasePeriodTicks);
    }

    public long getLeaseExpiresAt() {
        return leaseExpiresAt;
    }

    public void setLeaseExpiresAt(long leaseExpiresAt) {
        this.leaseExpiresAt = Math.max(0L, leaseExpiresAt);
    }

    public UUID getLandlord() {
        return landlord;
    }

    public void setLandlord(UUID landlord) {
        this.landlord = landlord;
    }

    public LeaseExpireAction getOnExpire() {
        return onExpire;
    }

    public void setOnExpire(LeaseExpireAction onExpire) {
        this.onExpire = onExpire == null ? LeaseExpireAction.UNCLAIM : onExpire;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public List<ZoneMember> getMembers() {
        return members;
    }

    public List<ZoneAction> getActions() {
        return actions;
    }

    public List<ZoneAction> actions() {
        return actions;
    }

    public void setActions(List<ZoneAction> next) {
        actions.clear();
        if (next != null) {
            for (ZoneAction a : next) {
                if (a != null) {
                    actions.add(a.copy());
                }
            }
        }
    }

    public boolean isEventEnabled() {
        return eventEnabled;
    }

    public void setEventEnabled(boolean eventEnabled) {
        this.eventEnabled = eventEnabled;
    }

    public boolean isEventNeedsConfirm() {
        return eventNeedsConfirm;
    }

    public void setEventNeedsConfirm(boolean eventNeedsConfirm) {
        this.eventNeedsConfirm = eventNeedsConfirm;
    }

    public boolean isEventActive() {
        return eventActive;
    }

    public void setEventActive(boolean eventActive) {
        this.eventActive = eventActive;
    }

    public long getEventStartedAt() {
        return eventStartedAt;
    }

    public void setEventStartedAt(long eventStartedAt) {
        this.eventStartedAt = Math.max(0L, eventStartedAt);
    }

    public long getEventCooldownUntil() {
        return eventCooldownUntil;
    }

    public void setEventCooldownUntil(long eventCooldownUntil) {
        this.eventCooldownUntil = Math.max(0L, eventCooldownUntil);
    }

    public long getLastEventSpawnAt() {
        return lastEventSpawnAt;
    }

    public void setLastEventSpawnAt(long lastEventSpawnAt) {
        this.lastEventSpawnAt = Math.max(0L, lastEventSpawnAt);
    }

    public int getEventWave() {
        return eventWave;
    }

    public void setEventWave(int eventWave) {
        this.eventWave = Math.max(0, eventWave);
    }

    public boolean isEventHadMobs() {
        return eventHadMobs;
    }

    public void setEventHadMobs(boolean eventHadMobs) {
        this.eventHadMobs = eventHadMobs;
    }

    public int getEventClearStreak() {
        return eventClearStreak;
    }

    public void setEventClearStreak(int eventClearStreak) {
        this.eventClearStreak = Math.max(0, eventClearStreak);
    }

    public void resetEventRuntime() {
        lastEventSpawnAt = 0L;
        eventWave = 0;
        eventHadMobs = false;
        eventClearStreak = 0;
    }

    public boolean isDenyExit() {
        return denyExit;
    }

    public void setDenyExit(boolean denyExit) {
        this.denyExit = denyExit;
    }

    public boolean isDenyExitWhileEvent() {
        return denyExitWhileEvent;
    }

    public void setDenyExitWhileEvent(boolean denyExitWhileEvent) {
        this.denyExitWhileEvent = denyExitWhileEvent;
    }

    public boolean isAllowCommands() {
        return allowCommands;
    }

    public void setAllowCommands(boolean allowCommands) {
        this.allowCommands = allowCommands;
    }

    public boolean allowCommands() {
        return allowCommands;
    }

    public ZoneRole roleOf(Player player) {
        return player == null ? ZoneRole.VISITOR : roleOf(player.getUUID());
    }

    public ZoneRole roleOf(UUID id) {
        if (id == null) {
            return ZoneRole.VISITOR;
        }
        if (owner != null && owner.equals(id)) {
            return ZoneRole.OWNER;
        }
        ZoneMember member = findMember(id);
        return member != null ? member.role() : ZoneRole.VISITOR;
    }

    public void setMember(UUID id, String name, ZoneRole role) {
        ZoneRole r = role == null ? ZoneRole.MEMBER : role;
        ZoneMember member = id != null ? findMember(id) : null;
        if (member == null && name != null && !name.isBlank()) {
            member = findMemberByName(name);
        }
        if (member != null) {
            int idx = members.indexOf(member);
            if (id != null && !id.equals(member.id())) {
                members.set(idx, new ZoneMember(id, name == null ? member.name() : name, r));
            } else {
                member.setName(name == null ? member.name() : name);
                member.setRole(r);
            }
            return;
        }
        members.add(new ZoneMember(id, name == null ? "" : name, r));
    }

    public void removeMember(UUID id) {
        if (id == null) {
            return;
        }
        members.removeIf(m -> id.equals(m.id()));
    }

    public void clearMembers() {
        members.clear();
    }

    public boolean isMemberWithRole(Player player, ZoneRole role) {
        return player != null && isMemberWithRole(player.getUUID(), role);
    }

    public boolean isMemberWithRole(UUID id, ZoneRole role) {
        if (role == null) {
            return false;
        }
        return roleOf(id) == role;
    }

    private ZoneMember findMember(UUID id) {
        for (ZoneMember member : members) {
            if (id.equals(member.id())) {
                return member;
            }
        }
        return null;
    }

    private ZoneMember findMemberByName(String name) {
        for (ZoneMember member : members) {
            if (member.name().equalsIgnoreCase(name)) {
                return member;
            }
        }
        return null;
    }

    private void migrateTrustedToMembers() {
        for (UUID id : settings.trustedIds()) {
            if (id != null) {
                setMember(id, "", ZoneRole.MEMBER);
            }
        }
        for (String name : settings.trustedNames()) {
            if (name != null && !name.isBlank()) {
                setMember(null, name, ZoneRole.MEMBER);
            }
        }
    }

    public BlockPos getMin() {
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        for (BlockPos c : corners) {
            minX = Math.min(minX, c.getX());
            minZ = Math.min(minZ, c.getZ());
        }
        return new BlockPos(minX, floorY, minZ);
    }

    public BlockPos getMax() {
        int maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos c : corners) {
            maxX = Math.max(maxX, c.getX());
            maxZ = Math.max(maxZ, c.getZ());
        }
        return new BlockPos(maxX, floorY + height - 1, maxZ);
    }

    public boolean contains(BlockPos pos) {
        return contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    public boolean contains(double x, double y, double z) {
        if (y < floorY || y >= floorY + height) {
            return false;
        }
        return pointInPolygon(x, z);
    }

    public boolean containsXZ(double x, double z) {
        return pointInPolygon(x, z);
    }

    /** AABB hit — used when polygon is broken / too few corners (zombie outline fix). */
    public boolean containsAabb(double x, double y, double z) {
        AABB box = aabb();
        return x >= box.minX && x < box.maxX
                && y >= box.minY && y < box.maxY
                && z >= box.minZ && z < box.maxZ;
    }

    private boolean pointInPolygon(double x, double z) {
        if (corners.isEmpty()) {
            return false;
        }
        if (corners.size() < 3) {
            BlockPos min = getMin();
            BlockPos max = getMax();
            return x >= min.getX() && x < max.getX() + 1
                    && z >= min.getZ() && z < max.getZ() + 1;
        }
        if (isAxisAlignedRect()) {
            BlockPos min = getMin();
            BlockPos max = getMax();
            return x >= min.getX() && x < max.getX() + 1
                    && z >= min.getZ() && z < max.getZ() + 1;
        }
        // Ray cast against outer edges of corner blocks (block min corner → next)
        boolean inside = false;
        int n = corners.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = corners.get(i).getX();
            double zi = corners.get(i).getZ();
            double xj = corners.get(j).getX();
            double zj = corners.get(j).getZ();
            boolean intersect = ((zi > z) != (zj > z))
                    && (x < (xj - xi) * (z - zi) / (zj - zi + 1.0E-9) + xi);
            if (intersect) {
                inside = !inside;
            }
        }
        return inside;
    }

    public boolean isAxisAlignedRect() {
        if (corners.size() != 4) {
            return false;
        }
        int minX = getMin().getX();
        int maxX = getMax().getX();
        int minZ = getMin().getZ();
        int maxZ = getMax().getZ();
        boolean[] seen = new boolean[4];
        for (BlockPos c : corners) {
            boolean corner =
                    (c.getX() == minX || c.getX() == maxX) && (c.getZ() == minZ || c.getZ() == maxZ);
            if (!corner) {
                return false;
            }
            int idx = (c.getX() == minX ? 0 : 1) + (c.getZ() == minZ ? 0 : 2);
            seen[idx] = true;
        }
        return seen[0] && seen[1] && seen[2] && seen[3];
    }

    public AABB aabb() {
        BlockPos min = getMin();
        BlockPos max = getMax();
        return new AABB(min.getX(), floorY, min.getZ(), max.getX() + 1.0, floorY + height, max.getZ() + 1.0);
    }

    /**
     * Inclusive block corners → outer wireframe edge vertices (exclusive max),
     * matching client selection handles so saved zones keep their polygon shape.
     */
    public List<BlockPos> wireCorners() {
        if (corners.isEmpty()) {
            return List.of();
        }
        int maxBX = Integer.MIN_VALUE;
        int maxBZ = Integer.MIN_VALUE;
        for (BlockPos c : corners) {
            maxBX = Math.max(maxBX, c.getX());
            maxBZ = Math.max(maxBZ, c.getZ());
        }
        List<BlockPos> out = new ArrayList<>(corners.size());
        for (BlockPos c : corners) {
            int ex = c.getX() == maxBX ? c.getX() + 1 : c.getX();
            int ez = c.getZ() == maxBZ ? c.getZ() + 1 : c.getZ();
            out.add(new BlockPos(ex, floorY, ez));
        }
        return out;
    }

    /** Nearest point just outside the XZ footprint. */
    public Vec3 nearestOutside(double x, double y, double z) {
        BlockPos min = getMin();
        BlockPos max = getMax();
        double dx0 = x - min.getX();
        double dx1 = (max.getX() + 1) - x;
        double dz0 = z - min.getZ();
        double dz1 = (max.getZ() + 1) - z;
        double best = Math.min(Math.min(dx0, dx1), Math.min(dz0, dz1));
        if (best == dx0) {
            return new Vec3(min.getX() - 0.05, y, z);
        }
        if (best == dx1) {
            return new Vec3(max.getX() + 1.05, y, z);
        }
        if (best == dz0) {
            return new Vec3(x, y, min.getZ() - 0.05);
        }
        return new Vec3(x, y, max.getZ() + 1.05);
    }

    /**
     * Resolve entity AABB against the zone volume as a solid (block-like).
     * Returns horizontal separation delta (dx, 0, dz); zero if no overlap in Y or XZ.
     */
    public Vec3 collideSolid(AABB entityBox) {
        AABB solid = aabb();
        if (!entityBox.intersects(solid)) {
            return Vec3.ZERO;
        }
        // Only collide if vertically overlapping the prism
        if (entityBox.maxY <= solid.minY || entityBox.minY >= solid.maxY) {
            return Vec3.ZERO;
        }

        double penWest = entityBox.maxX - solid.minX;   // push entity −X
        double penEast = solid.maxX - entityBox.minX;   // push entity +X
        double penNorth = entityBox.maxZ - solid.minZ;  // push −Z
        double penSouth = solid.maxZ - entityBox.minZ;  // push +Z

        double best = Math.min(Math.min(penWest, penEast), Math.min(penNorth, penSouth));
        final double eps = 0.001;
        if (best == penWest) {
            return new Vec3(-(penWest + eps), 0, 0);
        }
        if (best == penEast) {
            return new Vec3(penEast + eps, 0, 0);
        }
        if (best == penNorth) {
            return new Vec3(0, 0, -(penNorth + eps));
        }
        return new Vec3(0, 0, penSouth + eps);
    }

    public int sizeX() {
        return getMax().getX() - getMin().getX() + 1;
    }

    public int sizeY() {
        return height;
    }

    public int sizeZ() {
        return getMax().getZ() - getMin().getZ() + 1;
    }

    public int volume() {
        return sizeX() * sizeY() * sizeZ();
    }

    public int primaryColor() {
        for (ZoneFlag flag : ZoneFlag.values()) {
            if (flags.contains(flag)) {
                return flag.colorRgb;
            }
        }
        return ZoneFlag.VISIBLE.colorRgb;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putString("Name", name);
        tag.putInt("Level", level);
        tag.putInt("FloorY", floorY);
        tag.putInt("Height", height);
        ListTag cornerList = new ListTag();
        for (BlockPos c : corners) {
            CompoundTag ct = new CompoundTag();
            ct.putInt("X", c.getX());
            ct.putInt("Z", c.getZ());
            cornerList.add(ct);
        }
        tag.put("Corners", cornerList);
        // legacy AABB for older readers
        BlockPos min = getMin();
        BlockPos max = getMax();
        tag.putInt("MinX", min.getX());
        tag.putInt("MinY", floorY);
        tag.putInt("MinZ", min.getZ());
        tag.putInt("MaxX", max.getX());
        tag.putInt("MaxY", floorY + height - 1);
        tag.putInt("MaxZ", max.getZ());
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        ListTag flagList = new ListTag();
        for (ZoneFlag flag : flags) {
            flagList.add(StringTag.valueOf(flag.name()));
        }
        tag.put("Flags", flagList);
        tag.put("Settings", settings.save());
        tag.putBoolean("ForSale", forSale);
        ListTag costs = new ListTag();
        for (SaleCost c : buyCosts) {
            costs.add(c.save());
        }
        tag.put("BuyCosts", costs);
        // Legacy single-cost fields for older clients / backups
        tag.putString("BuyItem", getBuyItemId());
        tag.putInt("BuyCount", getBuyCount());
        tag.putString("PaymentDestination", paymentDestination.name());
        ListTag leaseList = new ListTag();
        for (SaleCost c : leaseCosts) {
            leaseList.add(c.save());
        }
        tag.put("LeaseCosts", leaseList);
        tag.putInt("LeasePeriodTicks", leasePeriodTicks);
        tag.putLong("LeaseExpiresAt", leaseExpiresAt);
        if (landlord != null) {
            tag.putUUID("Landlord", landlord);
        }
        tag.putString("OnExpire", onExpire.name());
        tag.putInt("Priority", priority);
        ListTag memberList = new ListTag();
        for (ZoneMember member : members) {
            memberList.add(member.save());
        }
        tag.put("Members", memberList);
        ListTag actionList = new ListTag();
        for (ZoneAction action : actions) {
            actionList.add(action.save());
        }
        tag.put("Actions", actionList);
        tag.putBoolean("EventEnabled", eventEnabled);
        tag.putBoolean("EventNeedsConfirm", eventNeedsConfirm);
        tag.putBoolean("EventActive", eventActive);
        tag.putLong("EventStartedAt", eventStartedAt);
        tag.putLong("EventCooldownUntil", eventCooldownUntil);
        tag.putLong("LastEventSpawnAt", lastEventSpawnAt);
        tag.putInt("EventWave", eventWave);
        tag.putBoolean("EventHadMobs", eventHadMobs);
        tag.putInt("EventClearStreak", eventClearStreak);
        tag.putBoolean("DenyExit", denyExit);
        tag.putBoolean("DenyExitWhileEvent", denyExitWhileEvent);
        tag.putBoolean("AllowCommands", allowCommands);
        return tag;
    }

    /** Geometry and presentation for clients. Script text stays on the server unless the viewer is an operator. */
    public CompoundTag saveFor(net.minecraft.world.entity.player.Player viewer) {
        CompoundTag tag = save();
        tag.remove("LastEventSpawnAt");
        tag.remove("EventWave");
        tag.remove("EventHadMobs");
        tag.remove("EventClearStreak");
        if (viewer == null || !viewer.hasPermissions(2)) {
            tag.remove("Actions");
            tag.putBoolean("AllowCommands", false);
        }
        return tag;
    }

    public static Zone load(CompoundTag tag) {
        UUID id = tag.getUUID("Id");
        int floorY = tag.contains("FloorY") ? tag.getInt("FloorY") : tag.getInt("MinY");
        int height = tag.contains("Height")
                ? tag.getInt("Height")
                : (tag.getInt("MaxY") - tag.getInt("MinY") + 1);
        List<BlockPos> corners = new ArrayList<>();
        if (tag.contains("Corners", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Corners", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag ct = list.getCompound(i);
                corners.add(new BlockPos(ct.getInt("X"), floorY, ct.getInt("Z")));
            }
        }
        if (corners.isEmpty()) {
            BlockPos min = new BlockPos(tag.getInt("MinX"), floorY, tag.getInt("MinZ"));
            BlockPos max = new BlockPos(tag.getInt("MaxX"), floorY, tag.getInt("MaxZ"));
            corners.addAll(rectCorners(min, max));
        }
        Zone zone = new Zone(id, tag.getString("Name"), corners, floorY, height);
        zone.level = Math.max(1, tag.getInt("Level"));
        if (tag.hasUUID("Owner")) {
            zone.owner = tag.getUUID("Owner");
        }
        ListTag flagList = tag.getList("Flags", Tag.TAG_STRING);
        for (int i = 0; i < flagList.size(); i++) {
            try {
                zone.flags.add(ZoneFlag.valueOf(flagList.getString(i)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        // Migrate old DISPLAY-only to also show outline
        if (zone.flags.contains(ZoneFlag.DISPLAY) && !zone.flags.contains(ZoneFlag.VISIBLE)
                && !tag.contains("Settings")) {
            zone.flags.add(ZoneFlag.VISIBLE);
        }
        if (tag.contains("Settings", Tag.TAG_COMPOUND)) {
            zone.settings.load(tag.getCompound("Settings"));
        }
        zone.buyCosts.clear();
        if (tag.contains("BuyCosts", Tag.TAG_LIST)) {
            ListTag costs = tag.getList("BuyCosts", Tag.TAG_COMPOUND);
            for (int i = 0; i < costs.size(); i++) {
                SaleCost c = SaleCost.load(costs.getCompound(i));
                if (c.isValid()) {
                    zone.buyCosts.add(c);
                }
            }
        } else if (tag.contains("BuyItem")) {
            String item = tag.getString("BuyItem");
            int count = tag.getInt("BuyCount");
            if (!item.isBlank() && count > 0) {
                zone.buyCosts.add(new SaleCost(item, count));
            }
        }
        zone.refreshForSale();
        if (tag.contains("ForSale") && tag.getBoolean("ForSale") && zone.buyCosts.isEmpty()) {
            // keep flag only if we somehow have costs
            zone.forSale = false;
        }
        if (tag.contains("PaymentDestination")) {
            try {
                zone.paymentDestination = PaymentDestination.valueOf(tag.getString("PaymentDestination"));
            } catch (IllegalArgumentException ignored) {
                zone.paymentDestination = PaymentDestination.VOID;
            }
        }
        zone.leaseCosts.clear();
        if (tag.contains("LeaseCosts", Tag.TAG_LIST)) {
            ListTag leaseList = tag.getList("LeaseCosts", Tag.TAG_COMPOUND);
            for (int i = 0; i < leaseList.size(); i++) {
                SaleCost c = SaleCost.load(leaseList.getCompound(i));
                if (c.isValid()) {
                    zone.leaseCosts.add(c);
                }
            }
        }
        zone.leasePeriodTicks = tag.contains("LeasePeriodTicks") ? tag.getInt("LeasePeriodTicks") : 72000;
        zone.leaseExpiresAt = tag.contains("LeaseExpiresAt") ? tag.getLong("LeaseExpiresAt") : 0L;
        if (tag.hasUUID("Landlord")) {
            zone.landlord = tag.getUUID("Landlord");
        }
        if (tag.contains("OnExpire")) {
            try {
                zone.onExpire = LeaseExpireAction.valueOf(tag.getString("OnExpire"));
            } catch (IllegalArgumentException ignored) {
                zone.onExpire = LeaseExpireAction.UNCLAIM;
            }
        }
        zone.priority = tag.contains("Priority") ? tag.getInt("Priority") : 0;
        zone.members.clear();
        if (tag.contains("Members", Tag.TAG_LIST)) {
            ListTag memberList = tag.getList("Members", Tag.TAG_COMPOUND);
            for (int i = 0; i < memberList.size(); i++) {
                zone.members.add(ZoneMember.load(memberList.getCompound(i)));
            }
        }
        if (zone.members.isEmpty()) {
            zone.migrateTrustedToMembers();
        }
        zone.actions.clear();
        if (tag.contains("Actions", Tag.TAG_LIST)) {
            ListTag actionList = tag.getList("Actions", Tag.TAG_COMPOUND);
            for (int i = 0; i < actionList.size(); i++) {
                zone.actions.add(ZoneAction.load(actionList.getCompound(i)));
            }
        }
        zone.eventEnabled = tag.getBoolean("EventEnabled");
        zone.eventNeedsConfirm = tag.getBoolean("EventNeedsConfirm");
        zone.eventActive = tag.getBoolean("EventActive");
        zone.eventStartedAt = tag.contains("EventStartedAt") ? tag.getLong("EventStartedAt") : 0L;
        zone.eventCooldownUntil = tag.contains("EventCooldownUntil") ? tag.getLong("EventCooldownUntil") : 0L;
        zone.lastEventSpawnAt = tag.contains("LastEventSpawnAt") ? tag.getLong("LastEventSpawnAt") : 0L;
        zone.eventWave = tag.contains("EventWave") ? tag.getInt("EventWave") : 0;
        zone.eventHadMobs = tag.getBoolean("EventHadMobs");
        zone.eventClearStreak = tag.contains("EventClearStreak") ? tag.getInt("EventClearStreak") : 0;
        zone.denyExit = tag.getBoolean("DenyExit");
        zone.denyExitWhileEvent = tag.getBoolean("DenyExitWhileEvent");
        zone.allowCommands = tag.getBoolean("AllowCommands");
        if (zone.owner != null) {
            zone.syncOwnerMember(zone.owner);
        }
        return zone;
    }
}
