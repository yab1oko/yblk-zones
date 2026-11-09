package yblk.zones.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import yblk.zones.zone.Zone;

import java.util.UUID;

/**
 * N — новая, G — править, C / ПКМ — точка на грани (сплит), X — удалить точку.
 * Alt+scroll двигает грань (сегмент), не отдельную вершину.
 */
public final class ClientWand {
    private ClientWand() {
    }

    public static void appendCornerTip(java.util.List<Component> tip) {
        if (ZoneSelection.hasPreview()) {
            tip.add(Component.translatable("item.yblk_zones.zone_wand.size",
                    ZoneSelection.sizeX(), ZoneSelection.height(), ZoneSelection.sizeZ()));
            tip.add(Component.translatable("item.yblk_zones.zone_wand.hint_alt"));
        } else {
            tip.add(Component.translatable("item.yblk_zones.zone_wand.hint_idle"));
        }
    }

    /** ПКМ по блоку: только если уже есть черновик — добавить угол на грань. */
    public static void onRightClickBlock(BlockPos clicked, Direction face, boolean sneak) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        if (sneak) {
            // Buy for-sale zone; otherwise open settings / manage
            Zone atClick = ClientZones.at(clicked.getX() + 0.5, clicked.getY() + 0.5, clicked.getZ() + 0.5);
            if (atClick == null) {
                atClick = findExistingZone(mc);
            }
            if (atClick != null && atClick.isForSale()) {
                if (atClick.getOwner() != null && atClick.getOwner().equals(mc.player.getUUID())) {
                    mc.player.displayClientMessage(
                            Component.translatable("message.yblk_zones.already_owner"), true);
                    return;
                }
                tryBuy(atClick);
                return;
            }
            openSettingsFromHud();
            return;
        }
        if (!ZoneSelection.hasPreview()) {
            mc.player.displayClientMessage(Component.translatable("message.yblk_zones.use_n_or_g"), true);
            return;
        }
        addCornerFromHud();
    }

    public static void tryBuy(Zone zone) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || zone == null || !zone.isForSale()) {
            return;
        }
        mc.setScreen(new ConfirmBuyScreen(zone));
    }

    public static void openOwnerMenu(UUID zoneId) {
        Minecraft mc = Minecraft.getInstance();
        Zone z = ClientZones.byId(zoneId);
        if (z == null || mc.player == null) {
            return;
        }
        mc.setScreen(new ZoneOwnerScreen(z));
    }

    /** O — меню владельца (имя + доверенные) в зоне, которой ты владеешь. */
    public static void openOwnerMenuFromHud() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        Zone zone = findExistingZone(mc);
        if (zone == null) {
            mc.player.displayClientMessage(Component.translatable("message.yblk_zones.no_zone_yet"), true);
            return;
        }
        boolean owner = zone.getOwner() != null && zone.getOwner().equals(mc.player.getUUID());
        boolean op = mc.player.hasPermissions(2);
        if (!owner && !op) {
            mc.player.displayClientMessage(Component.translatable("message.yblk_zones.not_owner"), true);
            return;
        }
        mc.setScreen(new ZoneOwnerScreen(zone));
    }

    public static void startNewZone() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        if (ZoneSelection.hasPreview()) {
            mc.player.displayClientMessage(Component.translatable("message.yblk_zones.draft_exists"), true);
            return;
        }
        BlockPos pos;
        Direction face = Direction.UP;
        if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            pos = hit.getBlockPos();
            face = hit.getDirection();
        } else {
            pos = mc.player.blockPosition().below();
        }
        ZoneSelection.startAt(pos, face);
        mc.player.displayClientMessage(Component.translatable("message.yblk_zones.draft_started",
                ZoneSelection.sizeX(), ZoneSelection.height(), ZoneSelection.sizeZ()), true);
    }

    public static void openSettingsFromHud() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        if (ZoneSelection.hasPreview()) {
            ZoneSelection.openPreview();
            if (ZoneSelection.editingId() != null) {
                Zone z = ClientZones.byId(ZoneSelection.editingId());
                if (z != null) {
                    mc.setScreen(new ZoneEditScreen(z, true));
                    return;
                }
                // Orphan draft (zone gone on server) — drop it so edit/delete work again
                ZoneSelection.clear();
            }
            Zone existing = findExistingZone(mc);
            if (existing != null) {
                mc.setScreen(new ZoneEditScreen(existing));
                return;
            }
            mc.setScreen(new ZoneCreateScreen());
            return;
        }
        Zone existing = findExistingZone(mc);
        if (existing != null) {
            if (existing.isForSale()
                    && (existing.getOwner() == null || !existing.getOwner().equals(mc.player.getUUID()))) {
                // Owner menu not applicable; open buy tip via edit only for owner/admin
            }
            mc.setScreen(new ZoneEditScreen(existing));
            return;
        }
        mc.player.displayClientMessage(Component.translatable("message.yblk_zones.no_zone_yet"), true);
    }

    /** G: взять существующую зону в черновик для правки. */
    public static void beginEditFromHud() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        if (ZoneSelection.hasPreview() && ZoneSelection.editingId() != null) {
            ZoneSelection.resumePlacing();
            mc.player.displayClientMessage(Component.translatable("message.yblk_zones.editing"), true);
            return;
        }
        Zone existing = findExistingZone(mc);
        if (existing != null) {
            ZoneSelection.loadFromZone(existing.getId(), existing.getCorners(),
                    existing.getFloorY(), existing.getHeight());
            ZoneSelection.resumePlacing();
            mc.player.displayClientMessage(Component.translatable("message.yblk_zones.editing"), true);
            return;
        }
        mc.player.displayClientMessage(Component.translatable("message.yblk_zones.no_zone_yet"), true);
    }

    public static void addCornerFromHud() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        if (!ZoneSelection.hasPreview()) {
            mc.player.displayClientMessage(Component.translatable("message.yblk_zones.need_draft"), true);
            return;
        }
        if (ZoneSelection.addCornerSplit()) {
            mc.player.displayClientMessage(Component.translatable("message.yblk_zones.corner_added",
                    ZoneSelection.corners().size()), true);
        } else {
            mc.player.displayClientMessage(Component.translatable("message.yblk_zones.corner_fail"), true);
        }
    }

    public static void deleteCornerFromHud() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        if (!ZoneSelection.hasPreview()) {
            mc.player.displayClientMessage(Component.translatable("message.yblk_zones.need_draft"), true);
            return;
        }
        if (ZoneSelection.deleteHoveredCorner()) {
            mc.player.displayClientMessage(Component.translatable("message.yblk_zones.corner_removed",
                    ZoneSelection.corners().size()), true);
        } else {
            mc.player.displayClientMessage(Component.translatable("message.yblk_zones.corner_remove_fail"), true);
        }
    }

    /** Called from item {@code use} (not useOn). Clears draft only on true air miss. */
    public static void onUseAir(boolean sneak) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        if (sneak) {
            Zone sale = findExistingZone(mc);
            if (sale != null && sale.isForSale()) {
                if (sale.getOwner() != null && sale.getOwner().equals(mc.player.getUUID())) {
                    mc.player.displayClientMessage(
                            Component.translatable("message.yblk_zones.already_owner"), true);
                    return;
                }
                tryBuy(sale);
                return;
            }
            openSettingsFromHud();
            return;
        }
        // Looking at draft faces/edges — do not clear (felt like deleting the zone)
        if (ZoneSelection.hasPreview() && ZoneSelection.isLookingAtDraft(mc.player)) {
            return;
        }
        var hit = mc.hitResult;
        if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
            return;
        }
        onCancelOrAir(false);
    }

    public static void onCancelOrAir(boolean sneak) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        if (sneak) {
            openSettingsFromHud();
            return;
        }
        if (ZoneSelection.hasPreview()) {
            ZoneSelection.clear();
            mc.player.displayClientMessage(Component.translatable("message.yblk_zones.selection_cleared"), true);
        }
    }

    public static boolean holdingWand() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return false;
        }
        return mc.player.getMainHandItem().getItem() instanceof yblk.zones.item.ZoneWandItem
                || mc.player.getOffhandItem().getItem() instanceof yblk.zones.item.ZoneWandItem;
    }

    private static Zone findExistingZone(Minecraft mc) {
        if (mc.player == null) {
            return null;
        }
        if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            var p = hit.getBlockPos();
            Zone z = ClientZones.at(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
            if (z != null) {
                return z;
            }
        }
        return ClientZones.at(mc.player.getX(), mc.player.getY() + 0.1, mc.player.getZ());
    }
}
