package yblk.zones.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import yblk.zones.ZonesConfig;
import yblk.zones.ZonesMod;
import yblk.zones.zone.Zone;
import yblk.zones.zone.ZoneFlag;
import yblk.zones.zone.ZoneSettings;

/**
 * Selection / zone outlines on block faces (outer edges), soft gradient, hatch,
 * cube corner markers. Active face (Alt+scroll) is highlighted.
 */
@Mod.EventBusSubscriber(modid = ZonesMod.MODID, value = Dist.CLIENT)
public final class ZoneRenderer {
    private static final ResourceLocation HATCH =
            new ResourceLocation(ZonesMod.MODID, "textures/misc/zone_hatch.png");
    private static final int GRADIENT_BANDS = 10;
    private static final int HATCH_BANDS = 8;
    private static final float CUBE = 0.055F;
    private static final float CUBE_ACTIVE = 0.12F;
    /** Lift outline slightly so it sits on grass tops, not inside. */
    private static final double Y_BIAS = 0.02;
    private static double outlineFloor;
    private static double outlineCeil;
    private static double outlineCx;
    private static double outlineCz;
    /** Beam sits on the block top. 0.12 left a visible gap. */
    private static final double SURFACE_LIFT = 0.0D;
    /** Soft band (blocks) at proximity radius for outline alpha. */
    private static final float PROX_SOFT = 0.3F;

    private ZoneRenderer() {
    }

    private static List<Vec3> wireVerts(Zone zone) {
        List<BlockPos> wire = zone.wireCorners();
        List<Vec3> out = new ArrayList<>(wire.size());
        for (BlockPos c : wire) {
            out.add(new Vec3(c.getX(), zone.getFloorY(), c.getZ()));
        }
        return out;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ZoneSelection.tickSmooth(1.0F);
        }
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }

        ZoneSelection.tickSmooth(event.getPartialTick());

        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f mat = pose.last().pose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.enableDepthTest();
        RenderSystem.enablePolygonOffset();
        RenderSystem.polygonOffset(-3.0F, -3.0F);

        float scroll = (mc.level.getGameTime() + event.getPartialTick()) * 0.012F;
        boolean wand = ClientWand.holdingWand();

        if (ZoneSelection.hasPreview()) {
            double[] mn = ZoneSelection.displayMin();
            double[] mx = ZoneSelection.displayMax();
            double y0 = mn[1] + Y_BIAS;
            double y1 = mx[1] + Y_BIAS;
            outlineFloor = y0;
            outlineCeil = y1;
            var handles = ZoneSelection.displayCorners();
            setOutlineCenter(handles, (mn[0] + mx[0]) * 0.5, (mn[2] + mx[2]) * 0.5);
            float gh = ZonesConfig.gradientHeight > 0 ? ZonesConfig.gradientHeight : 2.2F;
            double bandTop = Math.min(y0 + gh, y1);
            if (handles.size() >= 2) {
                softGradientWallsPolygon(mat, handles, y0, bandTop, 0.35f, 0.92f, 0.75f);
                scrollingHatchPolygon(mat, handles, y0, bandTop, 0.35f, 0.92f, 0.75f, scroll);
                drawPolygonWire(mat, handles, y0, y1, 0.45f, 0.95f, 0.85f);
                highlightActiveEdge(mat, handles, y0, y1);
            } else {
                softGradientWalls(mat, mn[0], y0, mn[2], mx[0], bandTop, mx[2], 0.35f, 0.92f, 0.75f);
                scrollingHatch(mat, mn[0], y0, mn[2], mx[0], bandTop, mx[2], 0.35f, 0.92f, 0.75f, scroll);
                drawWireBox(mat, mn[0], y0, mn[2], mx[0], y1, mx[2], 0.45f, 0.95f, 0.85f, true);
                highlightFace(mat, mn[0], y0, mn[2], mx[0], y1, mx[2], ZoneSelection.activeFace());
            }
            drawSelectionCorners(mat, handles, y0, y1);
        }

        float defaultGh = ZonesConfig.gradientHeight > 0 ? ZonesConfig.gradientHeight : 2.2F;
        UUID editing = ZoneSelection.editingId();
        Vec3 playerPos = mc.player.position();
        for (Zone zone : ClientZones.all()) {
            // Avoid double outline while editing this zone as a draft
            if (editing != null && editing.equals(zone.getId()) && ZoneSelection.hasPreview()) {
                continue;
            }
            ZoneSettings settings = zone.settings();
            ZoneSettings.OutlineStyle style = settings.outlineStyle();
            if (style == ZoneSettings.OutlineStyle.HIDDEN && !wand) {
                continue;
            }
            if (!zone.has(ZoneFlag.VISIBLE) && !wand && style != ZoneSettings.OutlineStyle.HATCH) {
                continue;
            }
            if (style == ZoneSettings.OutlineStyle.HIDDEN) {
                continue;
            }
            int rgb = settings.outlineColor();
            if (rgb == 0) {
                rgb = 0x55CCFF;
            }
            float r = ((rgb >> 16) & 0xFF) / 255f;
            float g = ((rgb >> 8) & 0xFF) / 255f;
            float b = (rgb & 0xFF) / 255f;
            AABB aabb = zone.aabb();
            double y0 = aabb.minY + Y_BIAS;
            double y1 = aabb.maxY + Y_BIAS;
            outlineFloor = y0;
            outlineCeil = y1;
            List<Vec3> poly = wireVerts(zone);
            setOutlineCenter(poly, (aabb.minX + aabb.maxX) * 0.5, (aabb.minZ + aabb.maxZ) * 0.5);
            float gh = settings.hatchHeight() > 0 ? settings.hatchHeight() : defaultGh;
            double bandTop = Math.min(y0 + gh, y1);
            // Never force ALWAYS just because the wand is held — that made Edge/Hatch proximity look broken.
            ZoneSettings.OutlineProximity outlineProx = settings.outlineProximity();
            float proxRadius = settings.proximityRadius();
            ZoneSettings.OutlineProximity hatchProx = switch (settings.hatchProximity()) {
                case NEAR_REVEAL -> ZoneSettings.OutlineProximity.NEAR_REVEAL;
                case NEAR_HIDE -> ZoneSettings.OutlineProximity.NEAR_HIDE;
                default -> ZoneSettings.OutlineProximity.ALWAYS;
            };
            float hatchRadius = Math.max(settings.hatchApproachDistance(), proxRadius);
            if (poly.size() >= 2) {
                softGradientWallsPolygon(mat, poly, y0, bandTop, r, g, b, hatchProx, hatchRadius, playerPos);
                scrollingHatchPolygon(mat, poly, y0, bandTop, r, g, b, scroll, hatchProx, hatchRadius, playerPos);
                if (style != ZoneSettings.OutlineStyle.HATCH) {
                    drawPolygonWire(mat, poly, y0, y1, r, g, b, outlineProx, proxRadius, playerPos);
                }
            } else if (style == ZoneSettings.OutlineStyle.HATCH) {
                softGradientWalls(mat, aabb.minX, y0, aabb.minZ, aabb.maxX, bandTop, aabb.maxZ, r, g, b,
                        hatchProx, hatchRadius, playerPos);
                scrollingHatch(mat, aabb.minX, y0, aabb.minZ, aabb.maxX, bandTop, aabb.maxZ, r, g, b, scroll,
                        hatchProx, hatchRadius, playerPos);
            } else {
                softGradientWalls(mat, aabb.minX, y0, aabb.minZ, aabb.maxX, bandTop, aabb.maxZ, r, g, b,
                        hatchProx, hatchRadius, playerPos);
                scrollingHatch(mat, aabb.minX, y0, aabb.minZ, aabb.maxX, bandTop, aabb.maxZ, r, g, b, scroll,
                        hatchProx, hatchRadius, playerPos);
                drawWireBox(mat, aabb.minX, y0, aabb.minZ, aabb.maxX, y1, aabb.maxZ, r, g, b, false,
                        outlineProx, proxRadius, playerPos);
            }
        }

        RenderSystem.disablePolygonOffset();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        pose.popPose();
    }

    private static void drawSelectionCorners(Matrix4f mat, List<Vec3> list, double y0, double y1) {
        if (list.isEmpty()) {
            return;
        }
        int sel = ZoneSelection.selectedCorner();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < list.size(); i++) {
            var c = list.get(i);
            boolean onFace = ZoneSelection.cornerOnActiveEdge(i);
            boolean hovered = i == sel;
            float r = hovered ? 1f : onFace ? 1f : 0.4f;
            float g = hovered ? 0.55f : onFace ? 0.85f : 0.95f;
            float b = hovered ? 0.2f : onFace ? 0.35f : 0.85f;
            float size = onFace || hovered ? CUBE_ACTIVE : CUBE;
            cube(buf, mat, c.x, y0, c.z, r, g, b, size);
            cube(buf, mat, c.x, y1, c.z, r, g, b, size);
        }
        tess.end();
    }

    private static void highlightActiveEdge(Matrix4f mat, List<Vec3> verts, double y0, double y1) {
        int edge = ZoneSelection.activeEdge();
        if (edge < 0 || verts.size() < 2) {
            double[] mn = ZoneSelection.displayMin();
            double[] mx = ZoneSelection.displayMax();
            highlightFace(mat, mn[0], y0, mn[2], mx[0], y1, mx[2], ZoneSelection.activeFace());
            return;
        }
        var a = verts.get(edge);
        var b = verts.get((edge + 1) % verts.size());
        float r = 1f, g = 0.78f, bl = 0.2f;
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        quad(buf, mat,
                a.x, y0, a.z,
                b.x, y0, b.z,
                b.x, y1, b.z,
                a.x, y1, a.z,
                r, g, bl, 0.28f);
        tess.end();
        buf = tess.getBuilder();
        buf.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        line(buf, mat, a.x, y0, a.z, b.x, y0, b.z, r, g, bl, 1f);
        line(buf, mat, a.x, y1, a.z, b.x, y1, b.z, r, g, bl, 1f);
        line(buf, mat, a.x, y0, a.z, a.x, y1, a.z, r, g, bl, 1f);
        line(buf, mat, b.x, y0, b.z, b.x, y1, b.z, r, g, bl, 1f);
        tess.end();
    }

    private static void softGradientWallsPolygon(
            Matrix4f mat, List<Vec3> verts,
            double y0, double y1,
            float r, float g, float b) {
        softGradientWallsPolygon(mat, verts, y0, y1, r, g, b,
                ZoneSettings.OutlineProximity.ALWAYS, 2f, Vec3.ZERO);
    }

    private static void softGradientWallsPolygon(
            Matrix4f mat, List<Vec3> verts,
            double y0, double y1,
            float r, float g, float b,
            ZoneSettings.OutlineProximity prox, float radius, Vec3 player) {
        if (verts.size() < 2) {
            return;
        }
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        double height = y1 - y0;
        int n = verts.size();
        for (int e = 0; e < n; e++) {
            var a = verts.get(e);
            var c = verts.get((e + 1) % n);
            gradientColumns(buf, mat, player, a.x, a.z, c.x, c.z, height, r, g, b, prox, radius);
        }
        tess.end();
    }

    private static void scrollingHatchPolygon(
            Matrix4f mat, List<Vec3> verts,
            double y0, double y1,
            float r, float g, float b,
            float scroll) {
        scrollingHatchPolygon(mat, verts, y0, y1, r, g, b, scroll,
                ZoneSettings.OutlineProximity.ALWAYS, 2f, Vec3.ZERO);
    }

    private static void scrollingHatchPolygon(
            Matrix4f mat, List<Vec3> verts,
            double y0, double y1,
            float r, float g, float b,
            float scroll,
            ZoneSettings.OutlineProximity prox, float radius, Vec3 player) {
        if (verts.size() < 2) {
            return;
        }
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, HATCH);
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        float uOff = scroll;
        float vOff = scroll * 0.55F;
        double height = y1 - y0;
        int n = verts.size();
        for (int e = 0; e < n; e++) {
            var a = verts.get(e);
            var c = verts.get((e + 1) % n);
            hatchColumns(buf, mat, player, a.x, a.z, c.x, c.z, height, r, g, b, uOff, vOff, prox, radius);
        }
        tess.end();
    }

    /** One flat column per block the edge actually crosses. Same spans as the wire. */
    private static void gradientColumns(
            BufferBuilder buf, Matrix4f mat, Vec3 player,
            double ax, double az, double bx, double bz, double height,
            float r, float g, float b,
            ZoneSettings.OutlineProximity prox, float radius) {
        walkBlocks(ax, az, bx, bz, (x0, z0, x1, z1, base, prev) -> {
            double top = Math.min(base + height, outlineCeil);
            if (top <= base + 0.05) {
                return;
            }
            double h = top - base;
            for (int i = 0; i < GRADIENT_BANDS; i++) {
                double u0 = i / (double) GRADIENT_BANDS;
                double u1 = (i + 1) / (double) GRADIENT_BANDS;
                float a0 = (float) (0.42 * Math.pow(1.0 - u0, 1.65));
                float a1 = (float) (0.42 * Math.pow(1.0 - u1, 1.65));
                proximityWallBand(buf, mat, player, x0, z0, x1, z1,
                        base + h * u0, base + h * u1, r, g, b, a0, a1, prox, radius);
            }
        });
    }

    private static void hatchColumns(
            BufferBuilder buf, Matrix4f mat, Vec3 player,
            double ax, double az, double bx, double bz, double height,
            float r, float g, float b, float uOff, float vOff,
            ZoneSettings.OutlineProximity prox, float radius) {
        walkBlocks(ax, az, bx, bz, (x0, z0, x1, z1, base, prev) -> {
            double top = Math.min(base + height, outlineCeil);
            if (top <= base + 0.05) {
                return;
            }
            double h = top - base;
            for (int i = 0; i < HATCH_BANDS; i++) {
                double u0 = i / (double) HATCH_BANDS;
                double u1 = (i + 1) / (double) HATCH_BANDS;
                float a0 = (float) (0.48 * Math.pow(1.0 - u0, 1.85));
                float a1 = (float) (0.48 * Math.pow(1.0 - u1, 1.85));
                proximityHatchBand(buf, mat, player, x0, z0, x1, z1,
                        base + h * u0, base + h * u1, r, g, b, a0, a1, uOff, vOff, prox, radius);
            }
        });
    }

    /** Subdivide a wall edge so NEAR_REVEAL only lights the nearby strip. */
    private static void proximityWallBand(
            BufferBuilder buf, Matrix4f mat, Vec3 player,
            double ax, double az, double bx, double bz,
            double ya, double yb,
            float r, float g, float b, float a0, float a1,
            ZoneSettings.OutlineProximity prox, float radius) {
        if (prox == ZoneSettings.OutlineProximity.ALWAYS) {
            bandWallEdge(buf, mat, ax, az, bx, bz, ya, yb, r, g, b, a0, a1);
            return;
        }
        double dx = bx - ax;
        double dz = bz - az;
        double len = Math.hypot(dx, dz);
        int parts = Math.max(1, (int) Math.ceil(len / 0.45));
        for (int i = 0; i < parts; i++) {
            double t0 = i / (double) parts;
            double t1 = (i + 1) / (double) parts;
            double x0 = ax + dx * t0, z0 = az + dz * t0;
            double x1 = ax + dx * t1, z1 = az + dz * t1;
            double dist = horizDistToSegment(player.x, player.z, x0, z0, x1, z1);
            float mult = proximityAlpha(dist, prox, radius);
            if (mult > 0.001f) {
                bandWallEdge(buf, mat, x0, z0, x1, z1, ya, yb, r, g, b, a0 * mult, a1 * mult);
            }
        }
    }

    private static void proximityHatchBand(
            BufferBuilder buf, Matrix4f mat, Vec3 player,
            double ax, double az, double bx, double bz,
            double ya, double yb,
            float r, float g, float b, float a0, float a1,
            float uOff, float vOff,
            ZoneSettings.OutlineProximity prox, float radius) {
        if (prox == ZoneSettings.OutlineProximity.ALWAYS) {
            hatchBandEdge(buf, mat, ax, az, bx, bz, ya, yb, r, g, b, a0, a1, uOff, vOff);
            return;
        }
        double dx = bx - ax;
        double dz = bz - az;
        double len = Math.hypot(dx, dz);
        int parts = Math.max(1, (int) Math.ceil(len / 0.45));
        for (int i = 0; i < parts; i++) {
            double t0 = i / (double) parts;
            double t1 = (i + 1) / (double) parts;
            double x0 = ax + dx * t0, z0 = az + dz * t0;
            double x1 = ax + dx * t1, z1 = az + dz * t1;
            double dist = horizDistToSegment(player.x, player.z, x0, z0, x1, z1);
            float mult = proximityAlpha(dist, prox, radius);
            if (mult > 0.001f) {
                hatchBandEdge(buf, mat, x0, z0, x1, z1, ya, yb, r, g, b, a0 * mult, a1 * mult, uOff, vOff);
            }
        }
    }

    private static void bandWallEdge(
            BufferBuilder buf, Matrix4f mat,
            double ax, double az, double bx, double bz,
            double ya, double yb,
            float r, float g, float b, float a0, float a1) {
        vert(buf, mat, ax, ya, az, r, g, b, a0);
        vert(buf, mat, bx, ya, bz, r, g, b, a0);
        vert(buf, mat, bx, yb, bz, r, g, b, a1);
        vert(buf, mat, ax, yb, az, r, g, b, a1);
    }

    private static void hatchBandEdge(
            BufferBuilder buf, Matrix4f mat,
            double ax, double az, double bx, double bz,
            double ya, double yb,
            float r, float g, float b, float a0, float a1,
            float uOff, float vOff) {
        float len = (float) Math.hypot(bx - ax, bz - az);
        float ht = (float) Math.abs(yb - ya);
        float u1 = len * 0.55F + uOff;
        float v0 = vOff + (float) (ya * 0.55);
        float v1 = v0 + ht * 0.55F;
        tex(buf, mat, ax, ya, az, uOff, v0, r, g, b, a0);
        tex(buf, mat, bx, ya, bz, u1, v0, r, g, b, a0);
        tex(buf, mat, bx, yb, bz, u1, v1, r, g, b, a1);
        tex(buf, mat, ax, yb, az, uOff, v1, r, g, b, a1);
    }

    /** Floor + top polygon + verticals from edge vertices. */
    private static void drawPolygonWire(
            Matrix4f mat,
            List<Vec3> verts,
            double y0, double y1,
            float r, float g, float b) {
        drawPolygonWire(mat, verts, y0, y1, r, g, b,
                ZoneSettings.OutlineProximity.ALWAYS, 2f, Vec3.ZERO);
    }

    private static void drawPolygonWire(
            Matrix4f mat,
            List<Vec3> verts,
            double y0, double y1,
            float r, float g, float b,
            ZoneSettings.OutlineProximity prox, float radius, Vec3 player) {
        int n = verts.size();
        if (n < 2) {
            return;
        }
        float edgeA = 1f;
        float topA = 0.7f;
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < n; i++) {
            var a = verts.get(i);
            var c = verts.get((i + 1) % n);
            terrainEdge(buf, mat, player, a.x, a.z, c.x, c.z, y0, y1, r, g, b, edgeA, prox, radius);
            proximityLine(buf, mat, player, a.x, y1, a.z, c.x, y1, c.z, r, g, b, topA, prox, radius);
            proximityLine(buf, mat, player, a.x, cornerGround(a.x, a.z, y0, y1), a.z, a.x, y1, a.z, r, g, b, 0.65f, prox, radius);
        }
        tess.end();
    }

    /** Full-height wireframe + tiny corner cubes (no floating top without verticals). */
    private static void drawWireBox(
            Matrix4f mat,
            double x0, double y0, double z0,
            double x1, double y1, double z1,
            float r, float g, float b,
            boolean selection) {
        drawWireBox(mat, x0, y0, z0, x1, y1, z1, r, g, b, selection,
                ZoneSettings.OutlineProximity.ALWAYS, 2f, Vec3.ZERO);
    }

    private static void drawWireBox(
            Matrix4f mat,
            double x0, double y0, double z0,
            double x1, double y1, double z1,
            float r, float g, float b,
            boolean selection,
            ZoneSettings.OutlineProximity prox, float radius, Vec3 player) {
        float edgeA = selection ? 1f : 0.85f;
        float topA = selection ? 0.7f : 0.5f;
        drawTerrainPerimeter(mat, x0, z0, x1, z1, y0, y1, r, g, b, edgeA, prox, radius, player);
        drawFloorPerimeter(mat, x0, y1, z0, x1, z1, r, g, b, topA, prox, radius, player);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        proximityLine(buf, mat, player, x0, cornerGround(x0, z0, y0, y1), z0, x0, y1, z0, r, g, b, 0.65f, prox, radius);
        proximityLine(buf, mat, player, x1, cornerGround(x1, z0, y0, y1), z0, x1, y1, z0, r, g, b, 0.65f, prox, radius);
        proximityLine(buf, mat, player, x1, cornerGround(x1, z1, y0, y1), z1, x1, y1, z1, r, g, b, 0.65f, prox, radius);
        proximityLine(buf, mat, player, x0, cornerGround(x0, z1, y0, y1), z1, x0, y1, z1, r, g, b, 0.65f, prox, radius);
        tess.end();
        drawCornerCubes(mat, x0, y0, z0, x1, z1, r, g, b);
        drawCornerCubes(mat, x0, y1, z0, x1, z1, r, g, b);
    }

    private static void highlightFace(
            Matrix4f mat,
            double x0, double y0, double z0,
            double x1, double y1, double z1,
            Direction face) {
        float r = 1f, g = 0.78f, b = 0.2f, a = 1f;
        // Soft filled face + bright edge
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float fa = 0.22f;
        switch (face) {
            case EAST -> quad(buf, mat, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, r, g, b, fa);
            case WEST -> quad(buf, mat, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, r, g, b, fa);
            case SOUTH -> quad(buf, mat, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, r, g, b, fa);
            case NORTH -> quad(buf, mat, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, r, g, b, fa);
            case UP -> quad(buf, mat, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, r, g, b, fa);
            case DOWN -> quad(buf, mat, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, r, g, b, fa);
        }
        tess.end();

        buf = tess.getBuilder();
        buf.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        switch (face) {
            case EAST -> {
                line(buf, mat, x1, y0, z0, x1, y0, z1, r, g, b, a);
                line(buf, mat, x1, y1, z0, x1, y1, z1, r, g, b, a);
                line(buf, mat, x1, y0, z0, x1, y1, z0, r, g, b, a);
                line(buf, mat, x1, y0, z1, x1, y1, z1, r, g, b, a);
            }
            case WEST -> {
                line(buf, mat, x0, y0, z0, x0, y0, z1, r, g, b, a);
                line(buf, mat, x0, y1, z0, x0, y1, z1, r, g, b, a);
                line(buf, mat, x0, y0, z0, x0, y1, z0, r, g, b, a);
                line(buf, mat, x0, y0, z1, x0, y1, z1, r, g, b, a);
            }
            case SOUTH -> {
                line(buf, mat, x0, y0, z1, x1, y0, z1, r, g, b, a);
                line(buf, mat, x0, y1, z1, x1, y1, z1, r, g, b, a);
                line(buf, mat, x0, y0, z1, x0, y1, z1, r, g, b, a);
                line(buf, mat, x1, y0, z1, x1, y1, z1, r, g, b, a);
            }
            case NORTH -> {
                line(buf, mat, x0, y0, z0, x1, y0, z0, r, g, b, a);
                line(buf, mat, x0, y1, z0, x1, y1, z0, r, g, b, a);
                line(buf, mat, x0, y0, z0, x0, y1, z0, r, g, b, a);
                line(buf, mat, x1, y0, z0, x1, y1, z0, r, g, b, a);
            }
            case UP -> {
                line(buf, mat, x0, y1, z0, x1, y1, z0, r, g, b, a);
                line(buf, mat, x1, y1, z0, x1, y1, z1, r, g, b, a);
                line(buf, mat, x1, y1, z1, x0, y1, z1, r, g, b, a);
                line(buf, mat, x0, y1, z1, x0, y1, z0, r, g, b, a);
            }
            case DOWN -> {
                line(buf, mat, x0, y0, z0, x1, y0, z0, r, g, b, a);
                line(buf, mat, x1, y0, z0, x1, y0, z1, r, g, b, a);
                line(buf, mat, x1, y0, z1, x0, y0, z1, r, g, b, a);
                line(buf, mat, x0, y0, z1, x0, y0, z0, r, g, b, a);
            }
        }
        tess.end();
    }

    /**
     * Top of the solid the column actually rests on. Climbs out of a block the
     * line would otherwise cut through, and drops into a valley below the zone floor.
     * Not clamped to the zone box — that clamp is what kept the wire from going down.
     */
    private static double groundY(double x, double z, double zoneFloor, double zoneCeil) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return zoneFloor + SURFACE_LIFT;
        }
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        int floor = Mth.floor(zoneFloor);
        int y = floor;
        int guard = 0;
        int topLimit = Math.max(Mth.floor(zoneCeil), floor) + 48;
        while (y < topLimit && guard++ < 96 && solidColumn(mc.level, bx, y, bz)) {
            y++;
        }
        if (!solidColumn(mc.level, bx, y - 1, bz)) {
            int down = y;
            int drops = 0;
            int bottom = floor - 64;
            while (down > bottom && drops++ < 96 && !solidColumn(mc.level, bx, down - 1, bz)) {
                down--;
            }
            if (solidColumn(mc.level, bx, down - 1, bz)) {
                y = down;
            }
        }
        double top = y;
        if (solidColumn(mc.level, bx, y - 1, bz)) {
            BlockPos pos = new BlockPos(bx, y - 1, bz);
            var shape = mc.level.getBlockState(pos).getCollisionShape(mc.level, pos);
            if (!shape.isEmpty()) {
                top = (y - 1) + shape.max(Direction.Axis.Y);
            }
        }
        return top + SURFACE_LIFT;
    }

    /** Corner post uses the block inside the zone, not the one just outside the edge. */
    private static double cornerGround(double x, double z, double zoneFloor, double zoneCeil) {
        double dx = outlineCx - x;
        double dz = outlineCz - z;
        double d = Math.hypot(dx, dz);
        if (d > 1.0E-4) {
            x += dx / d * 0.51;
            z += dz / d * 0.51;
        }
        return groundY(x, z, zoneFloor, zoneCeil);
    }

    private static void setOutlineCenter(List<Vec3> verts, double fallbackX, double fallbackZ) {
        if (verts.size() < 2) {
            outlineCx = fallbackX;
            outlineCz = fallbackZ;
            return;
        }
        double sx = 0;
        double sz = 0;
        for (Vec3 v : verts) {
            sx += v.x;
            sz += v.z;
        }
        outlineCx = sx / verts.size();
        outlineCz = sz / verts.size();
    }

    /**
     * Walk an edge one block column at a time. The sample is nudged inside the zone
     * so a boundary coordinate does not read the neighbour block.
     */
    private static void walkBlocks(double x0, double z0, double x1, double z1, BlockSpan span) {
        double dx = x1 - x0;
        double dz = z1 - z0;
        double len = Math.hypot(dx, dz);
        if (len < 1.0E-4) {
            return;
        }
        double ux = dx / len;
        double uz = dz / len;
        double nx = -uz;
        double nz = ux;
        double midX = (x0 + x1) * 0.5;
        double midZ = (z0 + z1) * 0.5;
        if ((outlineCx - midX) * nx + (outlineCz - midZ) * nz < 0) {
            nx = -nx;
            nz = -nz;
        }
        double traveled = 0;
        double px = x0;
        double pz = z0;
        double prevY = Double.NaN;
        int guard = 0;
        while (traveled < len - 1.0E-4 && guard++ < 4096) {
            double remain = len - traveled;
            double step = Math.min(remain, boundaryStep(px, pz, ux, uz));
            if (step < 1.0E-4) {
                step = Math.min(remain, 1.0E-3);
            }
            double nx1 = x0 + ux * (traveled + step);
            double nz1 = z0 + uz * (traveled + step);
            if (remain - step < 1.0E-4) {
                nx1 = x1;
                nz1 = z1;
                step = remain;
            }
            double sx = (px + nx1) * 0.5 + nx * 0.51;
            double sz = (pz + nz1) * 0.5 + nz * 0.51;
            double y = groundY(sx, sz, outlineFloor, outlineCeil);
            span.accept(px, pz, nx1, nz1, y, prevY);
            prevY = y;
            px = nx1;
            pz = nz1;
            traveled += step;
        }
    }

    private static double boundaryStep(double x, double z, double ux, double uz) {
        return Math.min(axisStep(x, ux), axisStep(z, uz));
    }

    /** Distance along the edge to the next integer grid plane. */
    private static double axisStep(double p, double u) {
        if (Math.abs(u) < 1.0E-8) {
            return Double.POSITIVE_INFINITY;
        }
        if (Math.abs(p - Math.rint(p)) < 1.0E-4) {
            return 1.0 / Math.abs(u);
        }
        double next = u > 0 ? Math.floor(p) + 1.0 : Math.ceil(p) - 1.0;
        return Math.max(1.0E-4, (next - p) / u);
    }

    @FunctionalInterface
    private interface BlockSpan {
        void accept(double ax, double az, double bx, double bz, double y, double prevY);
    }

    private static boolean solidColumn(net.minecraft.world.level.Level level, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    private static void terrainEdge(
            BufferBuilder buf, Matrix4f mat, Vec3 player,
            double x0, double z0, double x1, double z1,
            double zoneFloor, double zoneCeil,
            float r, float g, float b, float a,
            ZoneSettings.OutlineProximity prox, float radius) {
        walkBlocks(x0, z0, x1, z1, (ax, az, bx, bz, y, prev) -> {
            if (!Double.isNaN(prev) && Math.abs(y - prev) > 0.02) {
                proximityLine(buf, mat, player, ax, prev, az, ax, y, az, r, g, b, a, prox, radius);
            }
            proximityLine(buf, mat, player, ax, y, az, bx, y, bz, r, g, b, a, prox, radius);
        });
    }

    private static void drawTerrainPerimeter(
            Matrix4f mat,
            double x0, double z0, double x1, double z1,
            double zoneFloor, double zoneCeil,
            float r, float g, float b, float a,
            ZoneSettings.OutlineProximity prox, float radius, Vec3 player) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        terrainEdge(buf, mat, player, x0, z0, x1, z0, zoneFloor, zoneCeil, r, g, b, a, prox, radius);
        terrainEdge(buf, mat, player, x1, z0, x1, z1, zoneFloor, zoneCeil, r, g, b, a, prox, radius);
        terrainEdge(buf, mat, player, x1, z1, x0, z1, zoneFloor, zoneCeil, r, g, b, a, prox, radius);
        terrainEdge(buf, mat, player, x0, z1, x0, z0, zoneFloor, zoneCeil, r, g, b, a, prox, radius);
        tess.end();
    }

    private static void drawFloorPerimeter(
            Matrix4f mat,
            double x0, double y, double z0,
            double x1, double z1,
            float r, float g, float b, float a) {
        drawFloorPerimeter(mat, x0, y, z0, x1, z1, r, g, b, a,
                ZoneSettings.OutlineProximity.ALWAYS, 2f, Vec3.ZERO);
    }

    private static void drawFloorPerimeter(
            Matrix4f mat,
            double x0, double y, double z0,
            double x1, double z1,
            float r, float g, float b, float a,
            ZoneSettings.OutlineProximity prox, float radius, Vec3 player) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        proximityLine(buf, mat, player, x0, y, z0, x1, y, z0, r, g, b, a, prox, radius);
        proximityLine(buf, mat, player, x1, y, z0, x1, y, z1, r, g, b, a, prox, radius);
        proximityLine(buf, mat, player, x1, y, z1, x0, y, z1, r, g, b, a, prox, radius);
        proximityLine(buf, mat, player, x0, y, z1, x0, y, z0, r, g, b, a, prox, radius);
        tess.end();
    }

    private static void drawCornerCubes(
            Matrix4f mat,
            double x0, double y, double z0,
            double x1, double z1,
            float r, float g, float b) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        cube(buf, mat, x0, y, z0, r, g, b, CUBE);
        cube(buf, mat, x1, y, z0, r, g, b, CUBE);
        cube(buf, mat, x1, y, z1, r, g, b, CUBE);
        cube(buf, mat, x0, y, z1, r, g, b, CUBE);
        tess.end();
    }

    private static void softGradientWalls(
            Matrix4f mat,
            double x0, double y0, double z0,
            double x1, double y1, double z1,
            float r, float g, float b) {
        softGradientWalls(mat, x0, y0, z0, x1, y1, z1, r, g, b,
                ZoneSettings.OutlineProximity.ALWAYS, 2f, Vec3.ZERO);
    }

    private static void softGradientWalls(
            Matrix4f mat,
            double x0, double y0, double z0,
            double x1, double y1, double z1,
            float r, float g, float b,
            ZoneSettings.OutlineProximity prox, float radius, Vec3 player) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        double height = y1 - y0;
        gradientColumns(buf, mat, player, x0, z0, x1, z0, height, r, g, b, prox, radius);
        gradientColumns(buf, mat, player, x1, z1, x0, z1, height, r, g, b, prox, radius);
        gradientColumns(buf, mat, player, x0, z1, x0, z0, height, r, g, b, prox, radius);
        gradientColumns(buf, mat, player, x1, z0, x1, z1, height, r, g, b, prox, radius);
        tess.end();
    }

    /** Hatch in horizontal bands with the same exponential fade as the soft gradient. */
    private static void scrollingHatch(
            Matrix4f mat,
            double x0, double y0, double z0,
            double x1, double y1, double z1,
            float r, float g, float b,
            float scroll) {
        scrollingHatch(mat, x0, y0, z0, x1, y1, z1, r, g, b, scroll,
                ZoneSettings.OutlineProximity.ALWAYS, 2f, Vec3.ZERO);
    }

    private static void scrollingHatch(
            Matrix4f mat,
            double x0, double y0, double z0,
            double x1, double y1, double z1,
            float r, float g, float b,
            float scroll,
            ZoneSettings.OutlineProximity prox, float radius, Vec3 player) {
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, HATCH);
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        float uOff = scroll;
        float vOff = scroll * 0.55F;
        double height = y1 - y0;
        hatchColumns(buf, mat, player, x0, z0, x1, z0, height, r, g, b, uOff, vOff, prox, radius);
        hatchColumns(buf, mat, player, x0, z1, x1, z1, height, r, g, b, uOff, vOff, prox, radius);
        hatchColumns(buf, mat, player, x0, z0, x0, z1, height, r, g, b, uOff, vOff, prox, radius);
        hatchColumns(buf, mat, player, x1, z0, x1, z1, height, r, g, b, uOff, vOff, prox, radius);
        tess.end();
    }

    private static void hatchBand(
            BufferBuilder buf, Matrix4f mat,
            double x0, double y0, double z0,
            double x1, double y1, double z1,
            boolean alongX,
            float r, float g, float b,
            float a0, float a1,
            float uOff, float vOff) {
        float len = alongX ? (float) Math.abs(x1 - x0) : (float) Math.abs(z1 - z0);
        float ht = (float) Math.abs(y1 - y0);
        float u1 = len * 0.55F + uOff;
        float v0 = vOff + (float) (y0 * 0.55);
        float v1 = v0 + ht * 0.55F;
        if (alongX) {
            tex(buf, mat, x0, y0, z0, uOff, v0, r, g, b, a0);
            tex(buf, mat, x1, y0, z0, u1, v0, r, g, b, a0);
            tex(buf, mat, x1, y1, z0, u1, v1, r, g, b, a1);
            tex(buf, mat, x0, y1, z0, uOff, v1, r, g, b, a1);
        } else {
            tex(buf, mat, x0, y0, z0, uOff, v0, r, g, b, a0);
            tex(buf, mat, x0, y0, z1, u1, v0, r, g, b, a0);
            tex(buf, mat, x0, y1, z1, u1, v1, r, g, b, a1);
            tex(buf, mat, x0, y1, z0, uOff, v1, r, g, b, a1);
        }
    }

    private static void bandWall(
            BufferBuilder buf, Matrix4f mat,
            double x0, double y0, double z0,
            double x1, double y1, double z1,
            float r, float g, float b, float a0, float a1) {
        if (Math.abs(z0 - z1) < 1.0E-4) {
            double z = z0;
            vert(buf, mat, x0, y0, z, r, g, b, a0);
            vert(buf, mat, x1, y0, z, r, g, b, a0);
            vert(buf, mat, x1, y1, z, r, g, b, a1);
            vert(buf, mat, x0, y1, z, r, g, b, a1);
        } else {
            double x = x0;
            vert(buf, mat, x, y0, z0, r, g, b, a0);
            vert(buf, mat, x, y0, z1, r, g, b, a0);
            vert(buf, mat, x, y1, z1, r, g, b, a1);
            vert(buf, mat, x, y1, z0, r, g, b, a1);
        }
    }

    private static void cube(BufferBuilder buf, Matrix4f mat, double x, double y, double z,
            float r, float g, float b, float s) {
        double x0 = x - s, x1 = x + s;
        double y0 = y - s, y1 = y + s;
        double z0 = z - s, z1 = z + s;
        quad(buf, mat, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r, g, b, 1f);
        quad(buf, mat, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, r, g, b, 1f);
        quad(buf, mat, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, r, g, b, 1f);
        quad(buf, mat, x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1, r, g, b, 1f);
        quad(buf, mat, x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0, r, g, b, 1f);
        quad(buf, mat, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, r, g, b, 1f);
    }

    private static void quad(BufferBuilder buf, Matrix4f mat,
            double x0, double y0, double z0,
            double x1, double y1, double z1,
            double x2, double y2, double z2,
            double x3, double y3, double z3,
            float r, float g, float b, float a) {
        vert(buf, mat, x0, y0, z0, r, g, b, a);
        vert(buf, mat, x1, y1, z1, r, g, b, a);
        vert(buf, mat, x2, y2, z2, r, g, b, a);
        vert(buf, mat, x3, y3, z3, r, g, b, a);
    }

    /** Visible stroke. DEBUG_LINES vanish on the ground and leave a single vertical streak. */
    private static void beam(
            BufferBuilder buf, Matrix4f mat,
            double x0, double y0, double z0,
            double x1, double y1, double z1,
            float r, float g, float b, float a) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double dz = z1 - z0;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-5) {
            return;
        }
        dx /= len;
        dy /= len;
        dz /= len;
        double px;
        double py;
        double pz;
        if (Math.abs(dy) > 0.9) {
            px = 1;
            py = 0;
            pz = 0;
        } else {
            px = dz;
            py = 0;
            pz = -dx;
        }
        double plen = Math.sqrt(px * px + py * py + pz * pz);
        px /= plen;
        py /= plen;
        pz /= plen;
        double qx = dy * pz - dz * py;
        double qy = dz * px - dx * pz;
        double qz = dx * py - dy * px;
        double s = 0.035;
        double ax = x0 + px * s, ay = y0 + py * s, az = z0 + pz * s;
        double bx = x0 - px * s, by = y0 - py * s, bz = z0 - pz * s;
        double cx = x0 + qx * s, cy = y0 + qy * s, cz = z0 + qz * s;
        double dx0 = x0 - qx * s, dy0 = y0 - qy * s, dz0 = z0 - qz * s;
        double ex = x1 + px * s, ey = y1 + py * s, ez = z1 + pz * s;
        double fx = x1 - px * s, fy = y1 - py * s, fz = z1 - pz * s;
        double gx = x1 + qx * s, gy = y1 + qy * s, gz = z1 + qz * s;
        double hx = x1 - qx * s, hy = y1 - qy * s, hz = z1 - qz * s;
        quad(buf, mat, ax, ay, az, bx, by, bz, fx, fy, fz, ex, ey, ez, r, g, b, a);
        quad(buf, mat, cx, cy, cz, dx0, dy0, dz0, hx, hy, hz, gx, gy, gz, r, g, b, a);
    }

    private static void line(
            BufferBuilder buf, Matrix4f mat,
            double x0, double y0, double z0,
            double x1, double y1, double z1,
            float r, float g, float b, float a) {
        vert(buf, mat, x0, y0, z0, r, g, b, a);
        vert(buf, mat, x1, y1, z1, r, g, b, a);
    }

    private static void vert(BufferBuilder buf, Matrix4f mat, double x, double y, double z,
            float r, float g, float b, float a) {
        buf.vertex(mat, (float) x, (float) y, (float) z).color(r, g, b, a).endVertex();
    }

    private static void tex(BufferBuilder buf, Matrix4f mat, double x, double y, double z,
            float u, float v, float r, float g, float b, float a) {
        buf.vertex(mat, (float) x, (float) y, (float) z).uv(u, v).color(r, g, b, a).endVertex();
    }

    /**
     * Draw an edge with proximity. For NEAR_REVEAL / NEAR_HIDE the edge is split into
     * short segments so only the part near the player lights up (or fades), instead of
     * showing/hiding the entire face at once.
     */
    private static void proximityLine(
            BufferBuilder buf, Matrix4f mat, Vec3 player,
            double x0, double y0, double z0,
            double x1, double y1, double z1,
            float r, float g, float b, float baseA,
            ZoneSettings.OutlineProximity prox, float radius) {
        if (prox == ZoneSettings.OutlineProximity.ALWAYS) {
            beam(buf, mat, x0, y0, z0, x1, y1, z1, r, g, b, baseA);
            return;
        }
        double dx = x1 - x0;
        double dy = y1 - y0;
        double dz = z1 - z0;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int parts = Math.max(1, (int) Math.ceil(len / 0.45));
        for (int i = 0; i < parts; i++) {
            double t0 = i / (double) parts;
            double t1 = (i + 1) / (double) parts;
            double ax = x0 + dx * t0, ay = y0 + dy * t0, az = z0 + dz * t0;
            double bx = x0 + dx * t1, by = y0 + dy * t1, bz = z0 + dz * t1;
            // Horizontal distance to closest point on this segment (Y ignored so tall faces work while walking)
            double dist = horizDistToSegment(player.x, player.z, ax, az, bx, bz);
            float mult = proximityAlpha(dist, prox, radius);
            if (mult > 0.001f) {
                beam(buf, mat, ax, ay, az, bx, by, bz, r, g, b, baseA * mult);
            }
        }
    }

    /** Closest horizontal (XZ) distance from a point to a segment. */
    private static double horizDistToSegment(double px, double pz,
            double x0, double z0, double x1, double z1) {
        double dx = x1 - x0;
        double dz = z1 - z0;
        double len2 = dx * dx + dz * dz;
        if (len2 < 1.0E-12) {
            return Math.hypot(px - x0, pz - z0);
        }
        double t = Mth.clamp(((px - x0) * dx + (pz - z0) * dz) / len2, 0.0, 1.0);
        return Math.hypot(px - (x0 + t * dx), pz - (z0 + t * dz));
    }

    private static float proximityAlpha(double dist, ZoneSettings.OutlineProximity mode, float radius) {
        if (mode == ZoneSettings.OutlineProximity.ALWAYS) {
            return 1f;
        }
        float r0 = radius - PROX_SOFT;
        float r1 = radius + PROX_SOFT;
        if (mode == ZoneSettings.OutlineProximity.NEAR_REVEAL) {
            if (dist >= r1) {
                return 0f;
            }
            if (dist <= r0) {
                return 1f;
            }
            return 1f - (float) ((dist - r0) / (r1 - r0));
        }
        if (dist <= r0) {
            return 0f;
        }
        if (dist >= r1) {
            return 1f;
        }
        return (float) ((dist - r0) / (r1 - r0));
    }

    private static double distToSegment(Vec3 p,
            double x0, double y0, double z0,
            double x1, double y1, double z1) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double dz = z1 - z0;
        double len2 = dx * dx + dy * dy + dz * dz;
        if (len2 < 1.0E-12) {
            return p.distanceTo(new Vec3(x0, y0, z0));
        }
        double t = Mth.clamp(
                ((p.x - x0) * dx + (p.y - y0) * dy + (p.z - z0) * dz) / len2, 0.0, 1.0);
        return p.distanceTo(new Vec3(x0 + t * dx, y0 + t * dy, z0 + t * dz));
    }

    private static double distToAabb(Vec3 p, AABB box) {
        double cx = Mth.clamp(p.x, box.minX, box.maxX);
        double cy = Mth.clamp(p.y, box.minY, box.maxY);
        double cz = Mth.clamp(p.z, box.minZ, box.maxZ);
        return p.distanceTo(new Vec3(cx, cy, cz));
    }
}
