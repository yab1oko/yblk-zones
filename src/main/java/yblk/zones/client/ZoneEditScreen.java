package yblk.zones.client;

import net.minecraft.network.chat.Component;
import yblk.zones.network.DeleteZonePacket;
import yblk.zones.network.UpdateZonePacket;
import yblk.zones.network.ZonesNetwork;
import yblk.zones.zone.SaleCost;
import yblk.zones.zone.Zone;

import java.util.List;
import java.util.UUID;

/** Edit existing zone — same compact layout as create, with Save + Delete. */
public class ZoneEditScreen extends ZoneCreateScreen {
    private final UUID zoneId;
    private final Zone source;

    public ZoneEditScreen(Zone zone) {
        super(Component.translatable("screen.yblk_zones.edit"));
        this.zoneId = zone.getId();
        this.source = zone;
        flags.clear();
        flags.addAll(zone.getFlags());
        settings.load(zone.settings().save());
        paymentDestination = zone.getPaymentDestination();
        priority = zone.getPriority();
        allowCommands = zone.allowCommands();
        eventEnabled = zone.isEventEnabled();
        eventNeedsConfirm = zone.isEventNeedsConfirm();
        denyExitWhileEvent = zone.isDenyExitWhileEvent();
        draftActions.clear();
        for (var a : zone.getActions()) {
            draftActions.add(a.copy());
        }
        loadSaleFrom(zone);
        ZoneSelection.loadFromZone(zone.getId(), zone.getCorners(), zone.getFloorY(), zone.getHeight());
    }

    public ZoneEditScreen(Zone zone, boolean keepSelection) {
        super(Component.translatable("screen.yblk_zones.edit"));
        this.zoneId = zone.getId();
        this.source = zone;
        flags.clear();
        flags.addAll(zone.getFlags());
        settings.load(zone.settings().save());
        paymentDestination = zone.getPaymentDestination();
        priority = zone.getPriority();
        allowCommands = zone.allowCommands();
        eventEnabled = zone.isEventEnabled();
        eventNeedsConfirm = zone.isEventNeedsConfirm();
        denyExitWhileEvent = zone.isDenyExitWhileEvent();
        draftActions.clear();
        for (var a : zone.getActions()) {
            draftActions.add(a.copy());
        }
        loadSaleFrom(zone);
        ZoneSelection.setEditingId(zone.getId());
        if (!keepSelection) {
            ZoneSelection.loadFromZone(zone.getId(), zone.getCorners(), zone.getFloorY(), zone.getHeight());
        } else {
            ZoneSelection.openPreview();
        }
    }

    private void loadSaleFrom(Zone zone) {
        saleEnabled = zone.isForSale();
        saleCosts.clear();
        for (SaleCost c : zone.getBuyCosts()) {
            saleCosts.add(c.copy());
        }
        if (saleCosts.isEmpty() && saleEnabled) {
            saleCosts.add(new SaleCost("", 1));
        }
        saleExpanded = saleEnabled;
    }

    @Override
    protected int footerH() {
        return 50;
    }

    @Override
    protected void buildFooter() {
        for (var w : footerBtns) {
            removeWidget(w);
        }
        footerBtns.clear();

        int gap = 6;
        int btnW = (panelW - 20 - gap * 3) / 4;
        int btnY = panelY + panelH - 44;
        ActionButton del = new ActionButton(panelX + 10, btnY, panelW - 20, 14,
                Component.translatable("screen.yblk_zones.delete"), this::onDelete);
        ActionButton copy = new ActionButton(panelX + 10, btnY + 16, btnW, 14,
                Component.translatable("screen.yblk_zones.copy_settings"), this::copySettings);
        ActionButton paste = new ActionButton(panelX + 10 + btnW + gap, btnY + 16, btnW, 14,
                Component.translatable("screen.yblk_zones.paste_settings"), this::pasteSettings);
        ActionButton cancel = new ActionButton(panelX + 10 + (btnW + gap) * 2, btnY + 16, btnW, 14,
                Component.translatable("gui.cancel"), this::onCancel);
        ActionButton ok = new ActionButton(panelX + 10 + (btnW + gap) * 3, btnY + 16, btnW, 14,
                confirmLabel(), this::onConfirm);
        footerBtns.add(del);
        footerBtns.add(copy);
        footerBtns.add(paste);
        footerBtns.add(cancel);
        footerBtns.add(ok);
        addRenderableWidget(del);
        addRenderableWidget(copy);
        addRenderableWidget(paste);
        addRenderableWidget(cancel);
        addRenderableWidget(ok);
    }

    @Override
    protected void init() {
        super.init();
        if (nameBox != null) {
            nameBox.setValue(source.getName());
        }
    }

    @Override
    protected Component confirmLabel() {
        return Component.translatable("screen.yblk_zones.save");
    }

    @Override
    protected void onConfirm() {
        if (saleEnabled && !saleHasItem()) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(
                        Component.translatable("message.yblk_zones.sale_need_item"), true);
            }
            nav = NavId.SALE;
            rebuildRows();
            return;
        }
        ZoneSelection.markConfirmed();
        List<SaleCost> costs = saleEnabled ? validSaleCosts() : List.of();
        ZonesNetwork.CHANNEL.sendToServer(new UpdateZonePacket(
                zoneId,
                List.copyOf(ZoneSelection.corners()),
                ZoneSelection.floorY(),
                ZoneSelection.height(),
                nameBox.getValue(),
                yblk.zones.network.CreateZonePacket.packFlags(flags),
                settings,
                costs,
                paymentDestination,
                priority,
                allowCommands,
                eventEnabled,
                eventNeedsConfirm,
                denyExitWhileEvent,
                List.copyOf(draftActions)));
        ZoneSelection.clear();
        onClose();
    }

    private void onDelete() {
        ZonesNetwork.CHANNEL.sendToServer(new DeleteZonePacket(zoneId));
        ZoneSelection.clear();
        onClose();
    }
}
