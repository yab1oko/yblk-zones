package yblk.zones.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import yblk.zones.zone.ZoneFlag;
import yblk.zones.zone.ZoneManager;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Draft zone: rectangle or polygon footprint.
 * Alt+scroll moves the looked-at polygon edge (face segment) or height.
 * C inserts a vertex on an edge (splits one face into two).
 * X deletes the hovered vertex.
 * <p>
 * Internal {@code corners} are wireframe edge vertices (exclusive max for a rect).
 * {@link #corners()} converts to inclusive block corners for Zone packets.
 */
public final class ZoneSelection {
    public enum Stage { IDLE, PLACING, PREVIEW }

    private static final int MIN_CORNERS = 3;

    private static Stage stage = Stage.IDLE;
    /** Edge vertices (wireframe), not inclusive block mins. */
    private static final List<BlockPos> corners = new ArrayList<>();
    private static int floorY;
    private static int height = 8;
    private static boolean confirmed;
    private static UUID editingId;
    private static Direction activeFace = Direction.SOUTH;
    /** Edge index i → corners[i]..corners[(i+1)%n]; -1 when editing height (UP/DOWN). */
    private static int activeEdge = -1;
    /** Hovered vertex for delete / highlight; -1 if none. */
    private static int selectedCorner = -1;

    /** Inclusive block bounds derived from edge verts. */
    private static int minX, maxX, minZ, maxZ;

    private static double dispMinX, dispMinY, dispMinZ;
    private static double dispMaxX, dispMaxY, dispMaxZ;
    private static boolean displayInit;
    /** Lerped XZ for each corner (sideways smooth); Y follows floor/height AABB lerp. */
    private static final List<double[]> dispCorners = new ArrayList<>();

    private ZoneSelection() {
    }

    public static Stage stage() {
        return stage;
    }

    public static int floorY() {
        return floorY;
    }

    public static int height() {
        return height;
    }

    public static int sizeLimit() {
        Player player = Minecraft.getInstance().player;
        return ZoneManager.edgeCap(player);
    }

    public static void setHeight(int h) {
        height = Mth.clamp(h, 1, sizeLimit());
    }

    public static Direction activeFace() {
        return activeFace;
    }

    public static int activeEdge() {
        return activeEdge;
    }

    public static int selectedCorner() {
        return selectedCorner;
    }

    public static boolean cornerOnActiveEdge(int index) {
        if (activeEdge < 0 || corners.isEmpty()) {
            return false;
        }
        int n = corners.size();
        return index == activeEdge || index == (activeEdge + 1) % n;
    }

    public static boolean canDeleteCorner() {
        if (stage == Stage.IDLE || corners.size() <= MIN_CORNERS) {
            return false;
        }
        if (selectedCorner >= 0) {
            return true;
        }
        // Can delete a mid-point on the looked-at edge
        return activeEdge >= 0 && pickDeletableOnActiveEdge() >= 0;
    }

    public static List<BlockPos> cornerHandles() {
        return List.copyOf(corners);
    }

    /** Smooth display positions for wire / gradient / handles (lerped XZ). */
    public static List<Vec3> displayCorners() {
        syncDispCornerCount();
        List<Vec3> out = new ArrayList<>(dispCorners.size());
        double y = displayInit ? dispMinY : floorY;
        for (double[] xz : dispCorners) {
            out.add(new Vec3(xz[0], y, xz[1]));
        }
        return out;
    }

    /** True if crosshair hits draft geometry or volume (do not treat as air-cancel). */
    public static boolean isLookingAtDraft(Player player) {
        if (stage == Stage.IDLE || corners.isEmpty()) {
            return false;
        }
        updateActiveTarget(player);
        if (activeEdge >= 0 || activeFace.getAxis() == Direction.Axis.Y) {
            return true;
        }
        Vec3 eye = player.getEyePosition(1f);
        Vec3 end = eye.add(player.getLookAngle().scale(64));
        return targetAabb().clip(eye, end).isPresent();
    }

    public static void clear() {
        stage = Stage.IDLE;
        corners.clear();
        dispCorners.clear();
        displayInit = false;
        confirmed = false;
        editingId = null;
        activeFace = Direction.SOUTH;
        activeEdge = -1;
        selectedCorner = -1;
    }

    public static UUID editingId() {
        return editingId;
    }

    public static void setEditingId(UUID id) {
        editingId = id;
    }

    public static void clearUnlessConfirmed() {
        if (!confirmed) {
            clear();
        }
        confirmed = false;
    }

    public static void markConfirmed() {
        confirmed = true;
    }

    public static BlockPos floorFromHit(BlockPos clicked, Direction face) {
        int y = face == Direction.UP ? clicked.getY() + 1 : clicked.getY();
        return new BlockPos(clicked.getX(), y, clicked.getZ());
    }

    public static void startAt(BlockPos clicked, Direction face) {
        if (stage != Stage.IDLE) {
            return;
        }
        BlockPos p = floorFromHit(clicked, face);
        floorY = p.getY();
        minX = maxX = p.getX();
        minZ = maxZ = p.getZ();
        if (height < 1) {
            height = 8;
        }
        syncCornersFromAabb();
        stage = Stage.PLACING;
        displayInit = false;
        activeEdge = -1;
        selectedCorner = -1;
    }

    /** Insert a vertex on the looked-at polygon edge (splits that face into two). */
    public static boolean addCornerSplit() {
        if (stage == Stage.IDLE || corners.size() < 2) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return false;
        }
        updateActiveTarget(mc.player);
        if (activeEdge < 0 || activeFace.getAxis() == Direction.Axis.Y) {
            return false;
        }
        Vec3 eye = mc.player.getEyePosition(1f);
        Vec3 look = mc.player.getLookAngle();
        Vec3 end = eye.add(look.scale(64));
        BlockPos a = corners.get(activeEdge);
        BlockPos b = corners.get((activeEdge + 1) % corners.size());
        // Only axis-aligned edges get split points
        if (a.getX() != b.getX() && a.getZ() != b.getZ()) {
            return false;
        }
        Double t = rayPolygonEdge(eye, end, a, b);
        if (t == null) {
            return false;
        }
        Vec3 hit = eye.add(look.scale(t));
        int ix;
        int iz;
        if (a.getX() == b.getX()) {
            ix = a.getX();
            int z0 = Math.min(a.getZ(), b.getZ());
            int z1 = Math.max(a.getZ(), b.getZ());
            if (z1 - z0 < 2) {
                return false; // need room for a mid point
            }
            iz = Mth.clamp((int) Math.round(hit.z), z0 + 1, z1 - 1);
        } else {
            iz = a.getZ();
            int x0 = Math.min(a.getX(), b.getX());
            int x1 = Math.max(a.getX(), b.getX());
            if (x1 - x0 < 2) {
                return false;
            }
            ix = Mth.clamp((int) Math.round(hit.x), x0 + 1, x1 - 1);
        }
        BlockPos neu = new BlockPos(ix, floorY, iz);
        for (int i = 0; i < corners.size(); i++) {
            BlockPos c = corners.get(i);
            if (c.getX() == neu.getX() && c.getZ() == neu.getZ()) {
                selectedCorner = i;
                stage = Stage.PLACING;
                return true;
            }
        }
        int insertAt = activeEdge + 1;
        corners.add(insertAt, neu);
        selectedCorner = insertAt;
        stage = Stage.PLACING;
        syncDispCornerCount();
        updateActiveTarget(mc.player);
        return true;
    }

    /** Remove hovered / split vertex on the active edge. */
    public static boolean deleteHoveredCorner() {
        if (stage == Stage.IDLE || corners.size() <= MIN_CORNERS) {
            return false;
        }
        updateActiveTarget(Minecraft.getInstance().player);
        int idx = selectedCorner;
        // Prefer deleting a split mid-point on the looked-at edge
        if (idx < 0 && activeEdge >= 0) {
            idx = pickDeletableOnActiveEdge();
        }
        if (idx < 0 || idx >= corners.size() || corners.size() <= MIN_CORNERS) {
            return false;
        }
        corners.remove(idx);
        if (activeEdge >= corners.size()) {
            activeEdge = corners.isEmpty() ? -1 : corners.size() - 1;
        } else if (activeEdge >= idx) {
            activeEdge = Math.max(0, activeEdge - 1);
        }
        selectedCorner = -1;
        rebuildBounds();
        cleanupCollinear();
        syncDispCornerCount();
        return true;
    }

    /** Closest vertex of active edge that sits mid-line (safe to remove). */
    private static int pickDeletableOnActiveEdge() {
        if (activeEdge < 0 || corners.size() <= MIN_CORNERS) {
            return -1;
        }
        int n = corners.size();
        int i0 = activeEdge;
        int i1 = (activeEdge + 1) % n;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return isSplitVertex(i0) ? i0 : (isSplitVertex(i1) ? i1 : -1);
        }
        Vec3 eye = mc.player.getEyePosition(1f);
        Vec3 look = mc.player.getLookAngle();
        int best = -1;
        double bestDist = Double.MAX_VALUE;
        for (int i : new int[]{i0, i1}) {
            if (!isSplitVertex(i)) {
                continue;
            }
            BlockPos c = corners.get(i);
            Vec3 p = new Vec3(c.getX(), floorY + height * 0.5, c.getZ());
            Vec3 to = p.subtract(eye);
            double along = to.dot(look);
            if (along < 0) {
                continue;
            }
            double dist = to.subtract(look.scale(along)).lengthSqr();
            if (dist < bestDist) {
                bestDist = dist;
                best = i;
            }
        }
        return best;
    }

    private static boolean isSplitVertex(int i) {
        int n = corners.size();
        if (n < 3 || i < 0 || i >= n) {
            return false;
        }
        BlockPos p = corners.get((i - 1 + n) % n);
        BlockPos c = corners.get(i);
        BlockPos q = corners.get((i + 1) % n);
        return collinearXZ(p, c, q);
    }

    public static void openPreview() {
        if (stage == Stage.IDLE) {
            return;
        }
        stage = Stage.PREVIEW;
    }

    public static void resumePlacing() {
        if (stage == Stage.IDLE) {
            return;
        }
        stage = Stage.PLACING;
        confirmed = true;
    }

    public static void loadFromZone(UUID id, List<BlockPos> zoneCorners, int zoneFloorY, int zoneHeight) {
        editingId = id;
        floorY = zoneFloorY;
        height = zoneHeight;
        corners.clear();
        if (zoneCorners == null || zoneCorners.isEmpty()) {
            minX = maxX = 0;
            minZ = maxZ = 0;
            syncCornersFromAabb();
        } else {
            loadInclusiveAsEdges(zoneCorners);
            rebuildBounds();
        }
        stage = Stage.PREVIEW;
        displayInit = false;
        activeEdge = -1;
        selectedCorner = -1;
    }

    public static boolean canFinish() {
        return stage != Stage.IDLE;
    }

    /** Inclusive block corners for create/update packets (Zone storage). */
    public static List<BlockPos> corners() {
        return toInclusiveBlocks();
    }

    public static int sizeX() {
        return maxX - minX + 1;
    }

    public static int sizeZ() {
        return maxZ - minZ + 1;
    }

    public static void targetBounds(double[] outMin, double[] outMax) {
        outMin[0] = minX;
        outMin[1] = floorY;
        outMin[2] = minZ;
        outMax[0] = maxX + 1;
        outMax[1] = floorY + height;
        outMax[2] = maxZ + 1;
    }

    public static AABB targetAabb() {
        return new AABB(minX, floorY, minZ, maxX + 1.0, floorY + height, maxZ + 1.0);
    }

    public static boolean altScroll(double scrollDelta) {
        if (stage == Stage.IDLE) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return false;
        }
        updateActiveTarget(mc.player);
        int scrollUp = scrollDelta > 0 ? 1 : -1;

        // Height faces
        if (activeEdge < 0 && activeFace.getAxis() == Direction.Axis.Y) {
            Vec3 outward = Vec3.atLowerCornerOf(activeFace.getNormal());
            AABB box = targetAabb();
            Vec3 faceCenter = faceCenter(box, activeFace);
            Vec3 toPlayer = mc.player.getEyePosition(1f).subtract(faceCenter);
            boolean onOutwardSide = toPlayer.dot(outward) > 0.0;
            int towardPlayer = onOutwardSide ? 1 : -1;
            int outwardSteps = scrollUp < 0 ? towardPlayer : -towardPlayer;
            return moveFace(activeFace, outwardSteps);
        }

        // Polygon edge / face segment — never drag a single vertex
        if (activeEdge < 0 || activeEdge >= corners.size()) {
            return false;
        }
        BlockPos a = corners.get(activeEdge);
        BlockPos b = corners.get((activeEdge + 1) % corners.size());
        Vec3 mid = new Vec3((a.getX() + b.getX()) * 0.5, floorY + height * 0.5, (a.getZ() + b.getZ()) * 0.5);
        int[] nrm = edgeOutwardNormal(a, b);
        Vec3 outward = new Vec3(nrm[0], 0, nrm[1]);
        Vec3 toPlayer = mc.player.getEyePosition(1f).subtract(mid);
        boolean onOutwardSide = toPlayer.dot(outward) > 0.0;
        int towardPlayer = onOutwardSide ? 1 : -1;
        int outwardSteps = scrollUp < 0 ? towardPlayer : -towardPlayer;
        return moveEdge(activeEdge, outwardSteps);
    }

    private static Vec3 faceCenter(AABB box, Direction face) {
        return switch (face) {
            case EAST -> new Vec3(box.maxX, (box.minY + box.maxY) * 0.5, (box.minZ + box.maxZ) * 0.5);
            case WEST -> new Vec3(box.minX, (box.minY + box.maxY) * 0.5, (box.minZ + box.maxZ) * 0.5);
            case SOUTH -> new Vec3((box.minX + box.maxX) * 0.5, (box.minY + box.maxY) * 0.5, box.maxZ);
            case NORTH -> new Vec3((box.minX + box.maxX) * 0.5, (box.minY + box.maxY) * 0.5, box.minZ);
            case UP -> new Vec3((box.minX + box.maxX) * 0.5, box.maxY, (box.minZ + box.maxZ) * 0.5);
            case DOWN -> new Vec3((box.minX + box.maxX) * 0.5, box.minY, (box.minZ + box.maxZ) * 0.5);
        };
    }

    /** Pick polygon edge under look, or UP/DOWN for height. Also hover a vertex for delete. */
    public static void updateActiveTarget(Player player) {
        Vec3 eye = player.getEyePosition(1f);
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(64));

        int bestEdge = -1;
        double bestT = Double.MAX_VALUE;
        int n = corners.size();
        for (int i = 0; i < n; i++) {
            BlockPos a = corners.get(i);
            BlockPos b = corners.get((i + 1) % n);
            Double t = rayPolygonEdge(eye, end, a, b);
            if (t != null && t < bestT) {
                bestT = t;
                bestEdge = i;
            }
        }

        AABB box = targetAabb();
        Double tUp = rayFace(eye, end, box, Direction.UP);
        Double tDown = rayFace(eye, end, box, Direction.DOWN);
        if (tUp != null && tUp < bestT) {
            bestT = tUp;
            bestEdge = -1;
            activeFace = Direction.UP;
        }
        if (tDown != null && tDown < bestT) {
            bestEdge = -1;
            activeFace = Direction.DOWN;
        }

        activeEdge = bestEdge;
        if (bestEdge >= 0) {
            BlockPos a = corners.get(bestEdge);
            BlockPos b = corners.get((bestEdge + 1) % n);
            int[] nrm = edgeOutwardNormal(a, b);
            if (nrm[0] > 0) {
                activeFace = Direction.EAST;
            } else if (nrm[0] < 0) {
                activeFace = Direction.WEST;
            } else if (nrm[1] > 0) {
                activeFace = Direction.SOUTH;
            } else if (nrm[1] < 0) {
                activeFace = Direction.NORTH;
            }
        }

        pickHoveredCorner(player, eye, look);
    }

    /** @deprecated use {@link #updateActiveTarget} */
    @Deprecated
    public static void updateActiveFace(Player player) {
        updateActiveTarget(player);
    }

    private static void pickHoveredCorner(Player player, Vec3 eye, Vec3 look) {
        int best = -1;
        double bestScore = Double.MAX_VALUE;
        int n = corners.size();
        for (int i = 0; i < n; i++) {
            BlockPos c = corners.get(i);
            // Check both floor and mid-height handles
            for (double py : new double[]{floorY, floorY + height * 0.5, floorY + height}) {
                Vec3 p = new Vec3(c.getX(), py, c.getZ());
                Vec3 to = p.subtract(eye);
                double along = to.dot(look);
                if (along < 0.5 || along > 48) {
                    continue;
                }
                double perp = to.subtract(look.scale(along)).length();
                // Prefer verts on active edge; weight distance from look ray
                double score = perp + (cornerOnActiveEdge(i) ? 0 : 0.35);
                if (score < bestScore && perp < (cornerOnActiveEdge(i) ? 1.25 : 0.55)) {
                    bestScore = score;
                    best = i;
                }
            }
        }
        selectedCorner = best;
    }

    /** Outward unit step in XZ for an edge (axis-aligned preferred). */
    private static int[] edgeOutwardNormal(BlockPos a, BlockPos b) {
        double cx = 0, cz = 0;
        for (BlockPos c : corners) {
            cx += c.getX();
            cz += c.getZ();
        }
        cx /= corners.size();
        cz /= corners.size();
        double mx = (a.getX() + b.getX()) * 0.5;
        double mz = (a.getZ() + b.getZ()) * 0.5;
        int dx = b.getX() - a.getX();
        int dz = b.getZ() - a.getZ();
        // Perpendiculars: (-dz, dx) and (dz, -dx)
        double fx = mx - cx;
        double fz = mz - cz;
        double n1x = -dz, n1z = dx;
        double n2x = dz, n2z = -dx;
        double[] n = (n1x * fx + n1z * fz) >= (n2x * fx + n2z * fz)
                ? new double[]{n1x, n1z} : new double[]{n2x, n2z};
        // Snap to dominant axis for block steps
        if (Math.abs(n[0]) >= Math.abs(n[1])) {
            return new int[]{n[0] >= 0 ? 1 : -1, 0};
        }
        return new int[]{0, n[1] >= 0 ? 1 : -1};
    }

    private static Double rayPolygonEdge(Vec3 start, Vec3 end, BlockPos a, BlockPos b) {
        // Vertical wall quad: a→b at y floor..floor+height
        double ax = a.getX(), az = a.getZ();
        double bx = b.getX(), bz = b.getZ();
        double ex = bx - ax, ez = bz - az;
        double len2 = ex * ex + ez * ez;
        if (len2 < 1e-8) {
            return null;
        }
        Vec3 d = end.subtract(start);
        // Plane through edge, vertical: normal = (-ez, 0, ex) in XZ
        double nx = -ez, nz = ex;
        double denom = nx * d.x + nz * d.z;
        if (Math.abs(denom) < 1e-8) {
            return null;
        }
        double t = (nx * (ax - start.x) + nz * (az - start.z)) / denom;
        if (t < 0) {
            return null;
        }
        double hx = start.x + t * d.x;
        double hy = start.y + t * d.y;
        double hz = start.z + t * d.z;
        if (hy < floorY - 0.05 || hy > floorY + height + 0.05) {
            return null;
        }
        // Project hit onto segment in XZ
        double u = ((hx - ax) * ex + (hz - az) * ez) / len2;
        if (u < -0.02 || u > 1.02) {
            return null;
        }
        return t;
    }

    private static boolean moveEdge(int edgeIdx, int outwardSteps) {
        if (edgeIdx < 0 || edgeIdx >= corners.size() || outwardSteps == 0) {
            return false;
        }
        // Only axis-aligned edges — no diagonal walls
        int n = corners.size();
        int i0 = edgeIdx;
        int i1 = (edgeIdx + 1) % n;
        BlockPos a = corners.get(i0);
        BlockPos b = corners.get(i1);
        if (a.getX() != b.getX() && a.getZ() != b.getZ()) {
            return false;
        }
        int[] nrm = edgeOutwardNormal(a, b);
        int dx = nrm[0] * outwardSteps;
        int dz = nrm[1] * outwardSteps;
        if (dx == 0 && dz == 0) {
            return false;
        }
        int max = sizeLimit();
        List<BlockPos> old = new ArrayList<>(corners);
        BlockPos prev = corners.get((i0 - 1 + n) % n);
        BlockPos next = corners.get((i1 + 1) % n);
        // Mid-edge split points stay; corners of a full face move with the face
        boolean aIsSplit = collinearXZ(prev, a, b);
        boolean bIsSplit = collinearXZ(a, b, next);

        if (!aIsSplit && !bIsSplit) {
            // Whole face between two real corners — translate both (rectangle grows)
            corners.set(i0, new BlockPos(a.getX() + dx, floorY, a.getZ() + dz));
            corners.set(i1, new BlockPos(b.getX() + dx, floorY, b.getZ() + dz));
        } else {
            // Orthogonal extrude: keep A/B, insert A'/B' so walls stay axis-aligned
            // ... P - A - A' - B' - B - Q ...
            BlockPos ap = new BlockPos(a.getX() + dx, floorY, a.getZ() + dz);
            BlockPos bp = new BlockPos(b.getX() + dx, floorY, b.getZ() + dz);
            if (i0 < i1) {
                corners.add(i0 + 1, ap);
                corners.add(i0 + 2, bp);
                activeEdge = i0 + 1;
            } else {
                // Wrap-around edge (last → first)
                corners.add(ap);
                corners.add(bp);
                activeEdge = corners.size() - 2;
            }
        }
        rebuildBounds();
        cleanupCollinear();
        if (sizeX() < 1 || sizeZ() < 1 || sizeX() > max || sizeZ() > max) {
            corners.clear();
            corners.addAll(old);
            rebuildBounds();
            return false;
        }
        syncDispCornerCount();
        return true;
    }

    private static boolean collinearXZ(BlockPos a, BlockPos b, BlockPos c) {
        return (a.getX() == b.getX() && b.getX() == c.getX())
                || (a.getZ() == b.getZ() && b.getZ() == c.getZ());
    }

    /** Drop redundant midpoints on straight runs (keep orthognal loop clean). */
    private static void cleanupCollinear() {
        boolean changed = true;
        while (changed && corners.size() > MIN_CORNERS) {
            changed = false;
            int n = corners.size();
            for (int i = 0; i < n; i++) {
                if (corners.size() <= MIN_CORNERS) {
                    break;
                }
                int nn = corners.size();
                BlockPos p = corners.get((i - 1 + nn) % nn);
                BlockPos c = corners.get(i);
                BlockPos q = corners.get((i + 1) % nn);
                if (!collinearXZ(p, c, q)) {
                    continue;
                }
                // Strictly between p and q on the line
                boolean between = (p.getX() == q.getX()
                        && c.getZ() > Math.min(p.getZ(), q.getZ())
                        && c.getZ() < Math.max(p.getZ(), q.getZ()))
                        || (p.getZ() == q.getZ()
                        && c.getX() > Math.min(p.getX(), q.getX())
                        && c.getX() < Math.max(p.getX(), q.getX()));
                if (!between) {
                    continue;
                }
                corners.remove(i);
                if (activeEdge >= corners.size()) {
                    activeEdge = Math.max(0, corners.size() - 1);
                } else if (activeEdge > i) {
                    activeEdge--;
                }
                changed = true;
                break;
            }
        }
    }

    private static Double rayFace(Vec3 start, Vec3 end, AABB box, Direction dir) {
        Vec3 d = end.subtract(start);
        double t, x, y, z;
        switch (dir) {
            case EAST -> {
                if (Math.abs(d.x) < 1e-8) return null;
                t = (box.maxX - start.x) / d.x;
                y = start.y + t * d.y;
                z = start.z + t * d.z;
                if (t < 0 || y < box.minY || y > box.maxY || z < box.minZ || z > box.maxZ) return null;
                return t;
            }
            case WEST -> {
                if (Math.abs(d.x) < 1e-8) return null;
                t = (box.minX - start.x) / d.x;
                y = start.y + t * d.y;
                z = start.z + t * d.z;
                if (t < 0 || y < box.minY || y > box.maxY || z < box.minZ || z > box.maxZ) return null;
                return t;
            }
            case SOUTH -> {
                if (Math.abs(d.z) < 1e-8) return null;
                t = (box.maxZ - start.z) / d.z;
                x = start.x + t * d.x;
                y = start.y + t * d.y;
                if (t < 0 || x < box.minX || x > box.maxX || y < box.minY || y > box.maxY) return null;
                return t;
            }
            case NORTH -> {
                if (Math.abs(d.z) < 1e-8) return null;
                t = (box.minZ - start.z) / d.z;
                x = start.x + t * d.x;
                y = start.y + t * d.y;
                if (t < 0 || x < box.minX || x > box.maxX || y < box.minY || y > box.maxY) return null;
                return t;
            }
            case UP -> {
                if (Math.abs(d.y) < 1e-8) return null;
                t = (box.maxY - start.y) / d.y;
                x = start.x + t * d.x;
                z = start.z + t * d.z;
                if (t < 0 || x < box.minX || x > box.maxX || z < box.minZ || z > box.maxZ) return null;
                return t;
            }
            case DOWN -> {
                if (Math.abs(d.y) < 1e-8) return null;
                t = (box.minY - start.y) / d.y;
                x = start.x + t * d.x;
                z = start.z + t * d.z;
                if (t < 0 || x < box.minX || x > box.maxX || z < box.minZ || z > box.maxZ) return null;
                return t;
            }
        }
        return null;
    }

    private static boolean moveFace(Direction face, int outwardSteps) {
        int max = sizeLimit();
        // If still a simple rect (4 corners), move AABB then resync
        boolean simple = corners.size() == 4;
        if (simple) {
            switch (face) {
                case EAST -> {
                    int next = maxX + outwardSteps;
                    if (next < minX || next - minX + 1 > max) return false;
                    maxX = next;
                }
                case WEST -> {
                    int next = minX - outwardSteps;
                    if (next > maxX || maxX - next + 1 > max) return false;
                    minX = next;
                }
                case SOUTH -> {
                    int next = maxZ + outwardSteps;
                    if (next < minZ || next - minZ + 1 > max) return false;
                    maxZ = next;
                }
                case NORTH -> {
                    int next = minZ - outwardSteps;
                    if (next > maxZ || maxZ - next + 1 > max) return false;
                    minZ = next;
                }
                case UP -> {
                    int next = height + outwardSteps;
                    if (next < 1 || next > max) return false;
                    height = next;
                    return true;
                }
                case DOWN -> {
                    int nextH = height + outwardSteps;
                    if (nextH < 1 || nextH > max) return false;
                    floorY -= outwardSteps;
                    height = nextH;
                    for (int i = 0; i < corners.size(); i++) {
                        BlockPos c = corners.get(i);
                        corners.set(i, new BlockPos(c.getX(), floorY, c.getZ()));
                    }
                    return true;
                }
            }
            syncCornersFromAabb();
            return true;
        }
        // Polygon: move all corners that lie on that face
        if (face.getAxis() == Direction.Axis.Y) {
            if (face == Direction.UP) {
                int next = height + outwardSteps;
                if (next < 1 || next > max) return false;
                height = next;
                return true;
            }
            int nextH = height + outwardSteps;
            if (nextH < 1 || nextH > max) return false;
            floorY -= outwardSteps;
            height = nextH;
            return true;
        }
        int edgeMaxX = maxX + 1;
        int edgeMaxZ = maxZ + 1;
        List<BlockPos> old = new ArrayList<>(corners);
        for (int i = 0; i < corners.size(); i++) {
            BlockPos c = corners.get(i);
            boolean on = switch (face) {
                case EAST -> c.getX() == edgeMaxX;
                case WEST -> c.getX() == minX;
                case SOUTH -> c.getZ() == edgeMaxZ;
                case NORTH -> c.getZ() == minZ;
                default -> false;
            };
            if (on) {
                corners.set(i, c.relative(face, outwardSteps));
            }
        }
        rebuildBounds();
        if (sizeX() > max || sizeZ() > max || sizeX() < 1 || sizeZ() < 1) {
            corners.clear();
            corners.addAll(old);
            rebuildBounds();
            return false;
        }
        return true;
    }

    /** Wireframe corners: exclusive max edges match {@link #targetAabb()}. */
    private static void syncCornersFromAabb() {
        corners.clear();
        corners.add(new BlockPos(minX, floorY, minZ));
        corners.add(new BlockPos(maxX + 1, floorY, minZ));
        corners.add(new BlockPos(maxX + 1, floorY, maxZ + 1));
        corners.add(new BlockPos(minX, floorY, maxZ + 1));
    }

    private static void rebuildBounds() {
        if (corners.isEmpty()) {
            return;
        }
        int minVX = Integer.MAX_VALUE;
        int maxVX = Integer.MIN_VALUE;
        int minVZ = Integer.MAX_VALUE;
        int maxVZ = Integer.MIN_VALUE;
        for (BlockPos c : corners) {
            minVX = Math.min(minVX, c.getX());
            maxVX = Math.max(maxVX, c.getX());
            minVZ = Math.min(minVZ, c.getZ());
            maxVZ = Math.max(maxVZ, c.getZ());
        }
        minX = minVX;
        maxX = maxVX - 1;
        minZ = minVZ;
        maxZ = maxVZ - 1;
    }

    /** Edge verts → inclusive block corners for Zone packets. */
    private static List<BlockPos> toInclusiveBlocks() {
        if (corners.isEmpty()) {
            return List.of();
        }
        int maxVX = Integer.MIN_VALUE;
        int maxVZ = Integer.MIN_VALUE;
        for (BlockPos c : corners) {
            maxVX = Math.max(maxVX, c.getX());
            maxVZ = Math.max(maxVZ, c.getZ());
        }
        List<BlockPos> out = new ArrayList<>(corners.size());
        for (BlockPos c : corners) {
            int bx = c.getX() == maxVX ? c.getX() - 1 : c.getX();
            int bz = c.getZ() == maxVZ ? c.getZ() - 1 : c.getZ();
            out.add(new BlockPos(bx, floorY, bz));
        }
        return out;
    }

    /** Zone inclusive corners → edge verts for editing/display. */
    private static void loadInclusiveAsEdges(List<BlockPos> zoneCorners) {
        corners.clear();
        int maxBX = Integer.MIN_VALUE;
        int maxBZ = Integer.MIN_VALUE;
        for (BlockPos c : zoneCorners) {
            maxBX = Math.max(maxBX, c.getX());
            maxBZ = Math.max(maxBZ, c.getZ());
        }
        for (BlockPos c : zoneCorners) {
            int ex = c.getX() == maxBX ? c.getX() + 1 : c.getX();
            int ez = c.getZ() == maxBZ ? c.getZ() + 1 : c.getZ();
            corners.add(new BlockPos(ex, floorY, ez));
        }
    }

    private static void syncDispCornerCount() {
        while (dispCorners.size() > corners.size()) {
            dispCorners.remove(dispCorners.size() - 1);
        }
        while (dispCorners.size() < corners.size()) {
            BlockPos c = corners.get(dispCorners.size());
            dispCorners.add(new double[]{c.getX(), c.getZ()});
        }
    }

    private static void snapDispCorners() {
        dispCorners.clear();
        for (BlockPos c : corners) {
            dispCorners.add(new double[]{c.getX(), c.getZ()});
        }
    }

    public static void tickSmooth(float partialTick) {
        if (stage == Stage.IDLE) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.screen == null) {
            updateActiveTarget(mc.player);
        }
        double[] tMin = new double[3];
        double[] tMax = new double[3];
        targetBounds(tMin, tMax);
        syncDispCornerCount();
        if (!displayInit) {
            dispMinX = tMin[0];
            dispMinY = tMin[1];
            dispMinZ = tMin[2];
            dispMaxX = tMax[0];
            dispMaxY = tMax[1];
            dispMaxZ = tMax[2];
            snapDispCorners();
            displayInit = true;
            return;
        }
        // Very soft chase — Create-like smoothness (AABB + each corner XZ)
        float alpha = 1.0F - (float) Math.pow(0.00002, Math.max(0.05, partialTick));
        alpha = Mth.clamp(alpha, 0.03F, 0.12F);
        dispMinX = Mth.lerp(alpha, dispMinX, tMin[0]);
        dispMinY = Mth.lerp(alpha, dispMinY, tMin[1]);
        dispMinZ = Mth.lerp(alpha, dispMinZ, tMin[2]);
        dispMaxX = Mth.lerp(alpha, dispMaxX, tMax[0]);
        dispMaxY = Mth.lerp(alpha, dispMaxY, tMax[1]);
        dispMaxZ = Mth.lerp(alpha, dispMaxZ, tMax[2]);
        for (int i = 0; i < corners.size(); i++) {
            BlockPos c = corners.get(i);
            double[] d = dispCorners.get(i);
            d[0] = Mth.lerp(alpha, d[0], c.getX());
            d[1] = Mth.lerp(alpha, d[1], c.getZ());
        }
    }

    public static double[] displayMin() {
        return new double[]{dispMinX, dispMinY, dispMinZ};
    }

    public static double[] displayMax() {
        return new double[]{dispMaxX, dispMaxY, dispMaxZ};
    }

    public static boolean hasPreview() {
        return stage != Stage.IDLE;
    }

    public static EnumSet<ZoneFlag> defaultFlags() {
        return EnumSet.of(ZoneFlag.VISIBLE, ZoneFlag.DISPLAY, ZoneFlag.CLAIM);
    }
}
