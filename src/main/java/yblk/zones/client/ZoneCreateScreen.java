package yblk.zones.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import yblk.zones.ZonesConfig;
import yblk.zones.network.CreateZonePacket;
import yblk.zones.network.ZonesNetwork;
import yblk.zones.script.ZoneAction;
import yblk.zones.script.ZoneActionType;
import yblk.zones.script.ZoneScriptBundle;
import yblk.zones.script.ZoneTrigger;
import yblk.zones.zone.EffectEntry;
import yblk.zones.zone.PaymentDestination;
import yblk.zones.zone.SaleCost;
import yblk.zones.zone.ZoneFlag;
import yblk.zones.zone.ZoneSettings;
import yblk.zones.zone.ZoneManager;
import yblk.zones.zone.ZoneTemplates;

import yblk.zones.client.ui.ToggleSwitch;

import net.minecraft.Util;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Two-panel create / edit menu: scrollable nav left, detail right. */
public class ZoneCreateScreen extends Screen {
    protected static final int PANEL = 0xFF16161C;
    protected static final int PANEL_EDGE = 0xFF2A2A32;
    protected static final int ACCENT = 0xFF4EC9B0;
    protected static final int ACCENT_BOTTOM = 0xFF0A0A0E;
    protected static final int ACCENT_DIM = 0xFF2A6B5E;
    protected static final int TEXT = 0xFFF0F0F5;
    protected static final int MUTED = 0xFF9A9AA8;
    protected static final int ROW = 0xFF1E1E26;
    protected static final int ROW_HOV = 0xFF2A4A44;
    protected static final int ON = 0xFF3D8B6E;
    protected static final int OFF = 0xFF3A3A44;
    protected static final int FOOTER = 0xF00E0E14;
    protected static final int NAV_W = 230;
    protected static final int TOGGLE_STRIP = 42;

    protected enum NavId { FLAG, GENERAL, EVENT, AMBIENCE, EFFECTS, SCRIPTS, SALE, TEMPLATE }

    /** Remembered when reopening create/edit from the wand. */
    private static NavId rememberedNav = NavId.GENERAL;
    private static ZoneFlag rememberedFlag = ZoneFlag.VISIBLE;

    protected EditBox nameBox;
    protected final EnumSet<ZoneFlag> flags = ZoneSelection.defaultFlags();
    protected final ZoneSettings settings = new ZoneSettings();
    protected int panelX, panelY, panelW, panelH;
    protected NavId nav = rememberedNav;
    protected ZoneFlag navFlag = rememberedFlag;
    protected int navScroll;
    protected int detailScroll;
    private int navScrollTarget;
    private int detailScrollTarget;
    private float navScrollF;
    private float detailScrollF;
    private float detailSlide;
    private int draggingBar;
    private long easeAt;
    private double pointerX = -1;
    private double pointerY = -1;
    private ZoneFlag hoveredFlag;
    private String hoveredTip;
    private int flagHoverTicks;
    private final Map<String, Float> toggleAnim = new HashMap<>();
    private final Map<AbstractWidget, Integer> navLayoutY = new IdentityHashMap<>();
    private final Map<AbstractWidget, Integer> detailLayoutY = new IdentityHashMap<>();
    protected int navContentHeight;
    protected int detailContentHeight;
    protected boolean saleExpanded;
    protected boolean saleEnabled;
    protected boolean creatorOwns = ZonesConfig.defaultCreatorOwns;
    protected PaymentDestination paymentDestination = PaymentDestination.VOID;
    protected final List<SaleCost> saleCosts = new ArrayList<>();
    protected final List<AbstractWidget> navDynamic = new ArrayList<>();
    protected final List<AbstractWidget> detailDynamic = new ArrayList<>();
    protected final List<AbstractWidget> footerBtns = new ArrayList<>();
    protected final List<AbstractWidget> headerBtns = new ArrayList<>();
    private final List<EditBox> saleCountBoxes = new ArrayList<>();
    private int trustPlayerIndex;
    protected int templateIndex;
    private int templatePreviewIndex;
    protected int priority;
    protected boolean allowCommands;
    protected boolean eventEnabled;
    protected boolean eventNeedsConfirm = true;
    protected boolean denyExitWhileEvent;
    protected final List<ZoneAction> draftActions = new ArrayList<>();
    /** Trigger used by quick-add buttons (On Start / On End / …). */
    private ZoneTrigger scriptAddTrigger = ZoneTrigger.ON_EVENT_START;
    private final List<EditBox> fieldBoxes = new ArrayList<>();
    private String mobDraft = "minecraft:sheep";
    private String enterTitleDraft = "";
    private static final int MAX_SALE_COSTS = 8;

    public ZoneCreateScreen() {
        super(Component.translatable("screen.yblk_zones.create"));
    }

    protected ZoneCreateScreen(Component title) {
        super(title);
    }

    protected int headerH() {
        return 52;
    }

    protected int footerH() {
        return 34;
    }

    protected int contentTop() {
        return panelY + headerH();
    }

    protected int contentBottom() {
        return panelY + panelH - footerH();
    }

    protected int navLeft() {
        return panelX + 6;
    }

    protected int navWidth() {
        return NAV_W - 12;
    }

    protected int detailLeft() {
        return panelX + NAV_W + 4;
    }

    protected int detailWidth() {
        return panelW - NAV_W - 14;
    }

    @Override
    protected void init() {
        clearWidgets();
        navDynamic.clear();
        detailDynamic.clear();
        footerBtns.clear();

        panelW = Math.min(680, width - 16);
        panelH = Math.min(360, height - 16);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;

        nameBox = new EditBox(font, panelX + 10, panelY + 30, panelW - 20, 14,
                Component.translatable("screen.yblk_zones.name"));
        nameBox.setMaxLength(48);
        if (nameBox.getValue().isEmpty()) {
            nameBox.setValue("Zone");
        }
        nameBox.setTextColor(TEXT);
        addRenderableWidget(nameBox);

        for (AbstractWidget w : headerBtns) {
            removeWidget(w);
        }
        headerBtns.clear();
        rebuildRows();
        buildFooter();
    }

    protected void buildFooter() {
        for (AbstractWidget w : footerBtns) {
            removeWidget(w);
        }
        footerBtns.clear();
        int btnY = panelY + panelH - 26;
        int gap = 6;
        int btnW = (panelW - 20 - gap * 3) / 4;
        ActionButton copy = new ActionButton(panelX + 10, btnY, btnW, 16,
                Component.translatable("screen.yblk_zones.copy_settings"), this::copySettings);
        ActionButton paste = new ActionButton(panelX + 10 + btnW + gap, btnY, btnW, 16,
                Component.translatable("screen.yblk_zones.paste_settings"), this::pasteSettings);
        ActionButton cancel = new ActionButton(panelX + 10 + (btnW + gap) * 2, btnY, btnW, 16,
                Component.translatable("gui.cancel"), this::onCancel);
        ActionButton ok = new ActionButton(panelX + 10 + (btnW + gap) * 3, btnY, btnW, 16,
                confirmLabel(), this::onConfirm);
        footerBtns.add(copy);
        footerBtns.add(paste);
        footerBtns.add(cancel);
        footerBtns.add(ok);
        addRenderableWidget(copy);
        addRenderableWidget(paste);
        addRenderableWidget(cancel);
        addRenderableWidget(ok);
    }

    protected void copySettings() {
        Minecraft mc = Minecraft.getInstance();
        CompoundTag tag = settings.save();
        tag.putInt("YblkPriority", priority);
        tag.putBoolean("YblkAllowCommands", allowCommands);
        tag.putBoolean("YblkEventEnabled", eventEnabled);
        tag.putBoolean("YblkEventNeedsConfirm", eventNeedsConfirm);
        tag.putBoolean("YblkDenyExitWhileEvent", denyExitWhileEvent);
        tag.putString("YblkPayment", paymentDestination.name());
        mc.keyboardHandler.setClipboard(tag.toString());
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.translatable("message.yblk_zones.settings_copied"), true);
        }
    }

    protected void pasteSettings() {
        Minecraft mc = Minecraft.getInstance();
        String clip = mc.keyboardHandler.getClipboard();
        if (clip == null || clip.isBlank()) {
            return;
        }
        try {
            CompoundTag tag = TagParser.parseTag(clip);
            settings.load(tag);
            if (tag.contains("YblkPriority")) {
                priority = Mth.clamp(tag.getInt("YblkPriority"), 0, 10);
            }
            allowCommands = tag.getBoolean("YblkAllowCommands");
            eventEnabled = tag.getBoolean("YblkEventEnabled");
            eventNeedsConfirm = !tag.contains("YblkEventNeedsConfirm") || tag.getBoolean("YblkEventNeedsConfirm");
            denyExitWhileEvent = tag.getBoolean("YblkDenyExitWhileEvent");
            if (tag.contains("YblkPayment")) {
                try {
                    paymentDestination = PaymentDestination.valueOf(tag.getString("YblkPayment"));
                } catch (Exception ignored) {
                }
            }
            rebuildRows();
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.translatable("message.yblk_zones.settings_pasted"), true);
            }
        } catch (Exception e) {
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.translatable("message.yblk_zones.settings_paste_fail"), true);
            }
        }
    }

    protected Component confirmLabel() {
        return Component.translatable("screen.yblk_zones.confirm");
    }

    protected boolean flagHasSubsettings(ZoneFlag flag) {
        return switch (flag) {
            case VISIBLE, DISPLAY, CLAIM, MOB_DENY_ENTRY, MOB_BUFF, PLAYER_DAMAGE -> true;
            default -> false;
        };
    }

    /** Rebuilds left nav and right detail panels. */
    protected void rebuildRows() {
        for (AbstractWidget w : navDynamic) {
            removeWidget(w);
        }
        for (AbstractWidget w : detailDynamic) {
            removeWidget(w);
        }
        navDynamic.clear();
        detailDynamic.clear();
        navLayoutY.clear();
        detailLayoutY.clear();
        fieldBoxes.clear();
        saleCountBoxes.clear();

        rebuildNav();
        rebuildDetail();
        updateColumnVisibility();
    }

    private void rebuildNav() {
        int y = 0;
        int rowH = 26;
        int gap = 2;
        int x = navLeft();
        int w = navWidth();

        y = addNavSection(y, Component.translatable("screen.yblk_zones.section_flags"));
        for (ZoneFlag flag : ZoneFlag.values()) {
            addNav(new NavRow(x, navY(y), w, rowH, NavId.FLAG, flag,
                    Component.translatable("flag.yblk_zones." + flag.name().toLowerCase()),
                    flags.contains(flag), () -> selectFlag(flag), () -> toggleFlag(flag)));
            y += rowH + gap;
        }

        y = addNavSection(y, Component.translatable("screen.yblk_zones.section_ambience"));
        addNav(new NavRow(x, navY(y), w, rowH, NavId.AMBIENCE, null,
                Component.translatable("screen.yblk_zones.section_ambience"), true,
                () -> selectNav(NavId.AMBIENCE), null));
        y += rowH + gap;

        y = addNavSection(y, Component.translatable("screen.yblk_zones.section_event"));
        addNav(new NavRow(x, navY(y), w, rowH, NavId.EVENT, null,
                Component.translatable("screen.yblk_zones.section_event"),
                eventEnabled, () -> selectNav(NavId.EVENT), () -> {
                    eventEnabled = !eventEnabled;
                    rebuildRows();
                }));
        y += rowH + gap;

        y = addNavSection(y, Component.translatable("screen.yblk_zones.section_general"));
        addNav(new NavRow(x, navY(y), w, rowH, NavId.GENERAL, null,
                Component.translatable("screen.yblk_zones.section_general"), true,
                () -> selectNav(NavId.GENERAL), null));
        y += rowH + gap;
        addNav(new NavRow(x, navY(y), w, rowH, NavId.TEMPLATE, null,
                Component.translatable("screen.yblk_zones.section_template"), true,
                () -> selectNav(NavId.TEMPLATE), null));
        y += rowH + gap;

        y = addNavSection(y, Component.translatable("screen.yblk_zones.section_effects"));
        addNav(new NavRow(x, navY(y), w, rowH, NavId.EFFECTS, null,
                Component.translatable("screen.yblk_zones.section_effects"), true,
                () -> selectNav(NavId.EFFECTS), null));
        y += rowH + gap;
        addNav(new NavRow(x, navY(y), w, rowH, NavId.SCRIPTS, null,
                Component.translatable("screen.yblk_zones.section_scripts"), true,
                () -> selectNav(NavId.SCRIPTS), null));
        y += rowH + gap;

        y = addNavSection(y, Component.translatable("screen.yblk_zones.section_sale"));
        addNav(new NavRow(x, navY(y), w, rowH, NavId.SALE, null,
                Component.translatable("screen.yblk_zones.sale_mode"), saleEnabled,
                () -> selectNav(NavId.SALE), () -> {
                    saleEnabled = !saleEnabled;
                    rebuildRows();
                }));
        y += rowH + gap;

        navContentHeight = y + 8;
    }

    private void rebuildDetail() {
        int y = 0;
        int gap = 3;
        int rowH = 16;
        int x = detailLeft();
        int w = detailWidth();

        String sectionHint = switch (nav) {
            case FLAG -> null;
            case AMBIENCE -> "screen.yblk_zones.section_ambience.hint";
            case EVENT -> "screen.yblk_zones.section_event.hint";
            case GENERAL -> "screen.yblk_zones.section_general.hint";
            case TEMPLATE -> "screen.yblk_zones.section_template.hint";
            case EFFECTS -> "screen.yblk_zones.section_effects.hint";
            case SCRIPTS -> "screen.yblk_zones.section_scripts.hint";
            case SALE -> "screen.yblk_zones.section_sale.hint";
        };
        if (sectionHint != null) {
            int hintBoxH = nav == NavId.EVENT ? 52 : (nav == NavId.SCRIPTS ? 40 : 36);
            addDetail(new FlagHintBox(x, detailY(y), w, hintBoxH, null,
                    Component.translatable(sectionHint).getString()));
            y += hintBoxH + gap;
        }

        switch (nav) {
            case FLAG -> y = buildFlagDetail(navFlag, y, x, w, rowH, gap);
            case AMBIENCE -> y = buildAmbienceDetail(y, x, w, rowH, gap);
            case EVENT -> y = buildEventDetail(y, x, w, rowH, gap);
            case GENERAL -> y = buildGeneralDetail(y, x, w, gap);
            case TEMPLATE -> y = buildTemplateDetail(y, x, w, rowH, gap);
            case EFFECTS -> y = buildEffectsDetail(y, x, w, rowH, gap);
            case SCRIPTS -> y = buildScriptsDetail(y, x, w, 20, 5);
            case SALE -> y = buildSaleDetail(y, x, w, gap);
        }

        detailContentHeight = y + 8;
    }

    private int buildFlagDetail(ZoneFlag flag, int y, int x, int w, int rowH, int gap) {
        addDetail(new FlagHintBox(x, detailY(y), w, 48, flag));
        y += 48 + gap;
        if (!flagHasSubsettings(flag)) {
            return y;
        }
        return switch (flag) {
            case VISIBLE -> buildVisibleDetail(y, x, w, rowH, gap);
            case DISPLAY -> buildDisplayDetail(y, x, w, rowH, gap);
            case CLAIM -> buildClaimDetail(y, x, w, rowH, gap);
            case MOB_DENY_ENTRY, MOB_BUFF -> buildMobFilterSettings(flag, y, x, w, rowH, gap);
            case PLAYER_DAMAGE -> buildPlayerDamageDetail(y, x, w, rowH, gap);
            default -> y;
        };
    }

    private int buildVisibleDetail(int y, int x, int w, int rowH, int gap) {
        addDetail(new ActionButton(x, detailY(y), w, rowH,
                Component.translatable("screen.yblk_zones.outline", settings.outlineStyle().name()),
                () -> {
                    settings.cycleOutlineStyle();
                    rebuildRows();
                }));
        y += rowH + gap;
        addDetail(new ActionButton(x, detailY(y), w, rowH,
                Component.translatable("screen.yblk_zones.outline_proximity", settings.outlineProximity().name()),
                () -> {
                    settings.cycleOutlineProximity();
                    rebuildRows();
                }));
        y += rowH + gap;
        y = addDetailInlineFloat(y, x, w, gap, "screen.yblk_zones.proximity_radius_label",
                settings.proximityRadius(), v -> settings.setProximityRadius(v));
        addDetail(new ActionButton(x, detailY(y), w, rowH,
                Component.translatable("screen.yblk_zones.hatch_proximity", settings.hatchProximity().name()),
                () -> {
                    settings.cycleHatchProximity();
                    rebuildRows();
                }));
        y += rowH + gap;
        y = addDetailInlineFloat(y, x, w, gap, "screen.yblk_zones.hatch_approach_label",
                settings.hatchApproachDistance(), v -> settings.setHatchApproachDistance(v));
        y = addDetailInlineFloat(y, x, w, gap, "screen.yblk_zones.hatch_height_label",
                settings.hatchHeight(), v -> settings.setHatchHeight(v));
        addDetail(new ColorSwatchButton(x, detailY(y), w, rowH,
                Component.translatable("screen.yblk_zones.outline_color",
                        String.format("#%06X", settings.outlineColor())),
                settings.outlineColor(),
                () -> {
                    settings.cycleOutlineColor();
                    rebuildRows();
                }));
        return y + rowH + gap;
    }

    private int buildDisplayDetail(int y, int x, int w, int rowH, int gap) {
        addDetail(new ActionButton(x, detailY(y), w, rowH,
                Component.translatable("screen.yblk_zones.hud_mode", settings.hudMode().name()),
                () -> {
                    settings.cycleHudMode();
                    rebuildRows();
                }));
        y += rowH + gap;
        addDetail(new ColorSwatchButton(x, detailY(y), w, rowH,
                Component.translatable("screen.yblk_zones.hud_color", String.format("#%06X", settings.hudColor())),
                settings.hudColor(),
                () -> {
                    settings.cycleHudColor();
                    rebuildRows();
                }));
        y += rowH + gap;
        addDetail(new ActionButton(x, detailY(y), w, rowH,
                Component.translatable("screen.yblk_zones.hud_scale", String.format("%.2f", settings.hudScale())),
                () -> {
                    settings.cycleHudScale();
                    rebuildRows();
                }));
        y += rowH + gap;
        String imgLabel = hudImageLabel(settings.hudImage());
        addDetail(new ActionButton(x, detailY(y), w / 2 - 2, rowH,
                Component.translatable("screen.yblk_zones.hud_pick_item"),
                () -> minecraft.setScreen(new ItemPickerScreen(this, id -> {
                    settings.setHudImage(ZoneHudOverlay.ITEM_PREFIX + id);
                    minecraft.setScreen(this);
                    rebuildRows();
                }))));
        addDetail(new ActionButton(x + w / 2 + 2, detailY(y), w / 2 - 2, rowH,
                Component.translatable("screen.yblk_zones.hud_pick_emoji"),
                () -> minecraft.setScreen(new EmojiPickerScreen(this, emoji -> {
                    settings.setHudText(emoji + " " + nameBox.getValue());
                    minecraft.setScreen(this);
                    rebuildRows();
                }))));
        y += rowH + gap;
        addDetail(new ActionButton(x, detailY(y), w / 2 - 2, rowH,
                Component.translatable("screen.yblk_zones.hud_pick_image"),
                () -> minecraft.setScreen(new HudImagePickerScreen(this, stored -> {
                    settings.setHudImage(stored);
                    minecraft.setScreen(this);
                    rebuildRows();
                    if (minecraft.player != null) {
                        minecraft.player.displayClientMessage(
                                Component.translatable("message.yblk_zones.hud_image_ok"), true);
                    }
                }))));
        addDetail(new ActionButton(x + w / 2 + 2, detailY(y), w / 2 - 2, rowH,
                Component.translatable("screen.yblk_zones.clear"), () -> {
                    settings.setHudImage("");
                    rebuildRows();
                }));
        y += rowH + gap;
        addDetail(new ActionButton(x, detailY(y), w, rowH,
                Component.translatable("screen.yblk_zones.hud_image", imgLabel), () -> {
                }));
        y += rowH + gap;
        addDetail(new ActionButton(x, detailY(y), w, rowH,
                Component.translatable("screen.yblk_zones.hud_image_size", settings.hudImageSize()),
                () -> {
                    settings.cycleHudImageSize();
                    rebuildRows();
                }));
        y += rowH + gap;
        addDetail(new ActionButton(x, detailY(y), w, rowH,
                Component.translatable("screen.yblk_zones.hud_image_fit",
                        Component.translatable("screen.yblk_zones.hud_image_fit."
                                + settings.hudImageFit().name().toLowerCase()).getString()),
                () -> {
                    settings.cycleHudImageFit();
                    rebuildRows();
                }));
        y += rowH + gap;
        addDetail(new HintRow(x, detailY(y), w, hintRowHeight("screen.yblk_zones.hud_fade.hint"),
                Component.translatable("screen.yblk_zones.hud_fade",
                        settings.hudFadeSeconds() <= 0
                                ? Component.translatable("screen.yblk_zones.hud_fade_off").getString()
                                : settings.hudFadeSeconds() + "s"),
                "screen.yblk_zones.hud_fade.hint", () -> {
                    settings.cycleHudFadeSeconds();
                    rebuildRows();
                }));
        y += hintRowHeight("screen.yblk_zones.hud_fade.hint") + gap;
        y = addDetailLabeledText(y, x, w, gap, "screen.yblk_zones.hud_text_label",
                "screen.yblk_zones.hud_text.hint",
                settings.hudText().isBlank() ? nameBox.getValue() : settings.hudText(),
                settings::setHudText);
        return y;
    }

    private static String hudImageLabel(String stored) {
        if (stored == null || stored.isBlank()) {
            return "—";
        }
        if (stored.startsWith(ZoneHudOverlay.ITEM_PREFIX)) {
            return shortId(stored.substring(ZoneHudOverlay.ITEM_PREFIX.length()));
        }
        if (stored.startsWith(ClientHudImages.FILE_PREFIX)) {
            String path = stored.substring(ClientHudImages.FILE_PREFIX.length());
            int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
            return slash >= 0 ? path.substring(slash + 1) : path;
        }
        return "—";
    }

    private int buildClaimDetail(int y, int x, int w, int rowH, int gap) {
        String trustLabel = settings.trustedNames().isEmpty()
                ? Component.translatable("screen.yblk_zones.trust_empty").getString()
                : String.join(", ", settings.trustedNames());
        if (trustLabel.length() > 36) {
            trustLabel = trustLabel.substring(0, 34) + "…";
        }
        addDetail(new ActionButton(x, detailY(y), w, rowH,
                Component.translatable("screen.yblk_zones.trust_list", trustLabel), () -> {
                }));
        y += rowH + gap;
        addDetail(new ActionButton(x, detailY(y), w, rowH,
                Component.translatable("screen.yblk_zones.trust_add_online"), this::addOnlineTrusted));
        y += rowH + gap;
        addDetail(new ActionButton(x, detailY(y), w, rowH,
                Component.translatable("screen.yblk_zones.trust_clear"), () -> {
                    settings.trustedNames().clear();
                    settings.trustedIds().clear();
                    rebuildRows();
                }));
        y += rowH + gap;
        String team = settings.trustedTeam().isEmpty() ? "—" : settings.trustedTeam();
        addDetail(new ActionButton(x, detailY(y), w, rowH,
                Component.translatable("screen.yblk_zones.trust_team", team), this::cycleTeam));
        y += rowH + gap;
        y = claimRule(y, x, w, rowH, gap, "screen.yblk_zones.claim_break", settings.claimDenyBreak(),
                settings::setClaimDenyBreak);
        y = claimRule(y, x, w, rowH, gap, "screen.yblk_zones.claim_place", settings.claimDenyPlace(),
                settings::setClaimDenyPlace);
        y = claimRule(y, x, w, rowH, gap, "screen.yblk_zones.claim_block_use", settings.claimDenyBlockUse(),
                settings::setClaimDenyBlockUse);
        y = claimRule(y, x, w, rowH, gap, "screen.yblk_zones.claim_entity_use", settings.claimDenyEntityUse(),
                settings::setClaimDenyEntityUse);
        y = claimRule(y, x, w, rowH, gap, "screen.yblk_zones.claim_attack", settings.claimDenyAttack(),
                settings::setClaimDenyAttack);
        y = claimRule(y, x, w, rowH, gap, "screen.yblk_zones.claim_hurt", settings.claimDenyHurt(),
                settings::setClaimDenyHurt);
        return y;
    }

    private int claimRule(int y, int x, int w, int rowH, int gap, String key, boolean deny,
                          java.util.function.Consumer<Boolean> set) {
        String state = Component.translatable(deny ? "screen.yblk_zones.claim_no" : "screen.yblk_zones.claim_yes")
                .getString();
        addDetail(new ActionButton(x, detailY(y), w, rowH, Component.translatable(key, state), () -> {
            set.accept(!deny);
            rebuildRows();
        }));
        return y + rowH + gap;
    }

    private int buildPlayerDamageDetail(int y, int x, int w, int rowH, int gap) {
        addDetail(new ActionButton(x, detailY(y), w, rowH,
                Component.translatable("screen.yblk_zones.damage_kind",
                        Component.translatable("screen.yblk_zones.damage_kind."
                                + settings.damageKind().name().toLowerCase()).getString()),
                () -> {
                    var kinds = ZoneSettings.DamageKind.values();
                    settings.setDamageKind(kinds[(settings.damageKind().ordinal() + 1) % kinds.length]);
                    rebuildRows();
                }));
        y += rowH + gap;
        y = addDetailInlineFloat(y, x, w, gap, "screen.yblk_zones.damage_amt_label",
                settings.damageAmount(), v -> settings.setDamageAmount(Math.max(0F, v)));
        y = addDetailInlineInt(y, x, w, gap, "screen.yblk_zones.damage_interval_label",
                settings.damageInterval(), v -> settings.setDamageInterval(Math.max(5, v)));
        addDetail(new ActionButton(x, detailY(y), w, rowH,
                Component.translatable("screen.yblk_zones.damage_wither", settings.damageWither() ? "ON" : "OFF"),
                () -> {
                    settings.setDamageWither(!settings.damageWither());
                    rebuildRows();
                }));
        return y + rowH + gap;
    }

    private int buildAmbienceDetail(int y, int x, int w, int rowH, int gap) {
        String es = settings.enterSound().isBlank() ? "—" : settings.enterSound();
        addDetail(new ActionButton(x, detailY(y), w, rowH,
                Component.translatable("screen.yblk_zones.enter_sound", shortId(es)),
                () -> minecraft.setScreen(new SoundPickerScreen(this, id -> {
                    settings.setEnterSound(id);
                    minecraft.setScreen(this);
                    rebuildRows();
                }))));
        y += rowH + gap;
        String ls = settings.loopSound().isBlank() ? "—" : settings.loopSound();
        addDetail(new ActionButton(x, detailY(y), w, rowH,
                Component.translatable("screen.yblk_zones.loop_sound", shortId(ls)),
                () -> minecraft.setScreen(new SoundPickerScreen(this, id -> {
                    settings.setLoopSound(id);
                    minecraft.setScreen(this);
                    rebuildRows();
                }))));
        y += rowH + gap;
        String pi = settings.particleInterior().isBlank()
                ? Component.translatable("screen.yblk_zones.off").getString()
                : shortId(settings.particleInterior());
        addDetail(new ActionButton(x, detailY(y), w - 52, rowH,
                Component.translatable("screen.yblk_zones.particle_interior", pi),
                () -> minecraft.setScreen(new ParticlePickerScreen(this, id -> {
                    settings.setParticleInterior(id);
                    minecraft.setScreen(this);
                    rebuildRows();
                }))));
        addDetail(new ActionButton(x + w - 48, detailY(y), 48, rowH,
                Component.translatable("screen.yblk_zones.off"), () -> {
                    settings.setParticleInterior("");
                    rebuildRows();
                }));
        y += rowH + gap;
        String pb = settings.particleBorder().isBlank()
                ? Component.translatable("screen.yblk_zones.off").getString()
                : shortId(settings.particleBorder());
        addDetail(new ActionButton(x, detailY(y), w - 52, rowH,
                Component.translatable("screen.yblk_zones.particle_border", pb),
                () -> minecraft.setScreen(new ParticlePickerScreen(this, id -> {
                    settings.setParticleBorder(id);
                    minecraft.setScreen(this);
                    rebuildRows();
                }))));
        addDetail(new ActionButton(x + w - 48, detailY(y), 48, rowH,
                Component.translatable("screen.yblk_zones.off"), () -> {
                    settings.setParticleBorder("");
                    rebuildRows();
                }));
        y += rowH + gap;
        y = addDetailLabeledInt(y, x, w, gap, "screen.yblk_zones.particle_count",
                "", settings.particleCount(), v -> settings.setParticleCount(v));
        y = addDetailLabeledInt(y, x, w, gap, "screen.yblk_zones.particle_rate",
                "", settings.particleRate(), v -> settings.setParticleRate(v));
        if (enterTitleDraft.isEmpty() && !settings.enterTitle().isEmpty()) {
            enterTitleDraft = settings.enterTitle();
        }
        y = addDetailLabeledText(y, x, w, gap,
                "screen.yblk_zones.enter_title_label",
                "screen.yblk_zones.enter_title.hint",
                enterTitleDraft.isEmpty() ? settings.enterTitle() : enterTitleDraft,
                v -> {
                    enterTitleDraft = v;
                    settings.setEnterTitle(v);
                });
        addDetail(new HintRow(x, detailY(y), w, hintRowHeight("screen.yblk_zones.sky_mode.hint"),
                Component.translatable("screen.yblk_zones.sky_mode",
                        Component.translatable("screen.yblk_zones.sky_mode."
                                + settings.skyMode().name().toLowerCase()).getString()),
                "screen.yblk_zones.sky_mode.hint", () -> {
                    settings.cycleSkyMode();
                    rebuildRows();
                }));
        y += hintRowHeight("screen.yblk_zones.sky_mode.hint") + gap;
        addDetail(new HintRow(x, detailY(y), w, hintRowHeight("screen.yblk_zones.weather_mode.hint"),
                Component.translatable("screen.yblk_zones.weather_mode",
                        Component.translatable("screen.yblk_zones.weather_mode."
                                + settings.weatherMode().name().toLowerCase()).getString()),
                "screen.yblk_zones.weather_mode.hint", () -> {
                    settings.cycleWeatherMode();
                    rebuildRows();
                }));
        return y + hintRowHeight("screen.yblk_zones.weather_mode.hint") + gap;
    }

    private int buildEventDetail(int y, int x, int w, int rowH, int gap) {
        addDetail(new ActionButton(x, detailY(y), w, rowH,
                Component.translatable("screen.yblk_zones.event_needs_confirm", eventNeedsConfirm ? "ON" : "OFF"),
                () -> {
                    eventNeedsConfirm = !eventNeedsConfirm;
                    rebuildRows();
                }));
        y += rowH + gap;
        addDetail(new ActionButton(x, detailY(y), w, rowH,
                Component.translatable("screen.yblk_zones.event_deny_exit_flag", denyExitWhileEvent ? "ON" : "OFF"),
                () -> {
                    denyExitWhileEvent = !denyExitWhileEvent;
                    rebuildRows();
                }));
        y += rowH + gap;
        y = addDetailLabeledInt(y, x, w, gap, "screen.yblk_zones.event_duration_label",
                "screen.yblk_zones.event_duration.hint", settings.eventDurationTicks() / 20,
                v -> settings.setEventDurationTicks(Math.max(0, v) * 20));
        y = addDetailLabeledInt(y, x, w, gap, "screen.yblk_zones.event_min_label",
                "screen.yblk_zones.event_min.hint", settings.eventMinTicks() / 20,
                v -> settings.setEventMinTicks(Math.max(0, v) * 20));
        y = addDetailLabeledInt(y, x, w, gap, "screen.yblk_zones.event_cooldown_label",
                "screen.yblk_zones.event_cooldown.hint", settings.eventCooldownTicks() / 20,
                v -> settings.setEventCooldownTicks(Math.max(0, v) * 20));
        int spawnHintH = hintRowHeight("screen.yblk_zones.event_spawn_mob.hint");
        addDetail(new HintRow(x, detailY(y), w, spawnHintH,
                Component.translatable("screen.yblk_zones.event_spawn_mob_label"),
                "screen.yblk_zones.event_spawn_mob.hint", () -> {
                }));
        y += spawnHintH + 2;
        int pickW = 56;
        EditBox spawnMobBox = makeField(x, detailY(y), w - pickW - 4,
                settings.eventSpawnMob() == null ? "" : settings.eventSpawnMob());
        spawnMobBox.setResponder(settings::setEventSpawnMob);
        addDetail(spawnMobBox);
        fieldBoxes.add(spawnMobBox);
        addDetail(new ActionButton(x + w - pickW, detailY(y), pickW, rowH,
                Component.translatable("screen.yblk_zones.script_pick"),
                () -> minecraft.setScreen(new EntityPickerScreen(this, id -> {
                    settings.setEventSpawnMob(id);
                    rebuildRows();
                }))));
        y += Math.max(16, rowH) + gap;
        y = addDetailLabeledInt(y, x, w, gap, "screen.yblk_zones.event_spawn_count_label",
                "screen.yblk_zones.event_spawn_count.hint", settings.eventSpawnCount(),
                v -> settings.setEventSpawnCount(Math.max(0, v)));
        y = addDetailLabeledInt(y, x, w, gap, "screen.yblk_zones.event_waves_label",
                "screen.yblk_zones.event_waves.hint", settings.eventWaves(),
                v -> settings.setEventWaves(Math.max(0, v)));
        y = addDetailLabeledInt(y, x, w, gap, "screen.yblk_zones.event_spawn_interval_label",
                "screen.yblk_zones.event_spawn_interval.hint", settings.eventSpawnInterval() / 20,
                v -> settings.setEventSpawnInterval(Math.max(1, v) * 20));
        addDetail(new ActionButton(x, detailY(y), w, rowH,
                Component.translatable("screen.yblk_zones.event_win_on_clear",
                        settings.eventWinOnClear() ? "ON" : "OFF"),
                () -> {
                    settings.setEventWinOnClear(!settings.eventWinOnClear());
                    rebuildRows();
                }));
        y += rowH + gap;
        addDetail(new HintRow(x, detailY(y), w, hintRowHeight("screen.yblk_zones.event_sky.hint"),
                Component.translatable("screen.yblk_zones.event_sky",
                        Component.translatable("screen.yblk_zones.sky_mode."
                                + settings.eventSkyMode().name().toLowerCase()).getString()),
                "screen.yblk_zones.event_sky.hint", () -> {
                    settings.cycleEventSkyMode();
                    rebuildRows();
                }));
        y += hintRowHeight("screen.yblk_zones.event_sky.hint") + gap;
        addDetail(new HintRow(x, detailY(y), w, hintRowHeight("screen.yblk_zones.event_weather.hint"),
                Component.translatable("screen.yblk_zones.event_weather",
                        Component.translatable("screen.yblk_zones.weather_mode."
                                + settings.eventWeatherMode().name().toLowerCase()).getString()),
                "screen.yblk_zones.event_weather.hint", () -> {
                    settings.cycleEventWeatherMode();
                    rebuildRows();
                }));
        return y + hintRowHeight("screen.yblk_zones.event_weather.hint") + gap;
    }

    private int buildScriptsDetail(int y, int x, int w, int rowH, int gap) {
        int half = w / 2 - 2;
        int third = (w - 8) / 3;
        addDetail(new ActionButton(x, detailY(y), third, rowH,
                Component.translatable("screen.yblk_zones.script_preset_raid"), this::applyRaidScriptPreset));
        addDetail(new ActionButton(x + third + 4, detailY(y), third, rowH,
                Component.translatable("screen.yblk_zones.script_open_editor"),
                () -> minecraft.setScreen(new ScriptTextEditorScreen(this, draftActions, list -> {
                    draftActions.clear();
                    draftActions.addAll(list);
                    rebuildRows();
                }))));
        addDetail(new ActionButton(x + 2 * (third + 4), detailY(y), w - 2 * (third + 4), rowH,
                Component.translatable("screen.yblk_zones.script_library_open"), this::openScriptLibrary));
        y += rowH + gap + 2;

        // Big current-when banner
        addDetail(new FlagHintBox(x, detailY(y), w, 28, null,
                Component.translatable("screen.yblk_zones.script_add_when_current",
                        triggerLabel(scriptAddTrigger)).getString()));
        y += 28 + gap;

        y = addScriptTriggerPicker(y, x, w, rowH, gap, scriptAddTrigger, t -> {
            scriptAddTrigger = t;
            rebuildRows();
        });

        addDetail(new SectionLabel(x, detailY(y), w,
                Component.translatable("screen.yblk_zones.script_add_actions_label")));
        y += 16 + gap;

        ZoneTrigger addAt = scriptAddTrigger;
        // 2 columns — readable labels, not 3 cramped chips
        y = addScriptQuickRow(y, x, w, rowH, gap, half,
                "screen.yblk_zones.script_quick_spawn",
                () -> addScriptQuick(addAt, ZoneActionType.SPAWN, "minecraft:zombie", 4, 1F, false),
                "screen.yblk_zones.script_quick_reward",
                () -> addScriptQuick(addAt, ZoneActionType.GIVE_ITEM, "minecraft:diamond", 1, 1F, false));
        y = addScriptQuickRow(y, x, w, rowH, gap, half,
                "screen.yblk_zones.script_quick_start_msg",
                () -> addScriptQuick(addAt, ZoneActionType.TITLE, "Go!", 0, 1F, true),
                "screen.yblk_zones.script_quick_effect",
                () -> addScriptQuick(addAt, ZoneActionType.EFFECT, "minecraft:darkness", 200, 0F, false));
        y = addScriptQuickRow(y, x, w, rowH, gap, half,
                "screen.yblk_zones.script_quick_sound",
                () -> addScriptQuick(addAt, ZoneActionType.SOUND, "minecraft:entity.wither.spawn", 0, 0.8F, false),
                "screen.yblk_zones.script_quick_particle",
                () -> addScriptQuick(addAt, ZoneActionType.PARTICLE, "minecraft:flame", 32, 1F, false));
        y = addScriptQuickRow(y, x, w, rowH, gap, half,
                "screen.yblk_zones.script_quick_xp",
                () -> addScriptQuick(addAt, ZoneActionType.GIVE_XP, "", 50, 1F, false),
                "screen.yblk_zones.script_quick_heal",
                () -> addScriptQuick(addAt, ZoneActionType.HEAL, "", 0, 10F, true));
        y = addScriptQuickRow(y, x, w, rowH, gap, half,
                "screen.yblk_zones.script_quick_clear_mobs",
                () -> addScriptQuick(addAt, ZoneActionType.CLEAR_MOBS, "", 0, 1F, false),
                "screen.yblk_zones.script_quick_deny_exit",
                () -> addScriptQuick(addAt, ZoneActionType.DENY_EXIT, "true", 0, 1F, true));
        addDetail(new ActionButton(x, detailY(y), w, rowH,
                Component.translatable("screen.yblk_zones.script_quick_command"),
                () -> addScriptQuick(addAt, ZoneActionType.COMMAND, "say done", 0, 1F, false)));
        y += rowH + gap + 4;

        if (draftActions.isEmpty()) {
            addDetail(new FlagHintBox(x, detailY(y), w, 40, null,
                    Component.translatable("screen.yblk_zones.script_empty").getString()));
            return y + 40 + gap;
        }

        addDetail(new SectionLabel(x, detailY(y), w,
                Component.translatable("screen.yblk_zones.script_list_label", draftActions.size())));
        y += 16 + gap;

        for (int i = 0; i < draftActions.size(); i++) {
            final int idx = i;
            ZoneAction a = draftActions.get(i);
            addDetail(new ActionButton(x, detailY(y), w - 44, rowH,
                    Component.literal("#" + (i + 1) + "  " + scriptSummary(a)), () -> {
                    }));
            addDetail(new ActionButton(x + w - 40, detailY(y), 40, rowH, Component.literal("✕"), () -> {
                if (idx >= 0 && idx < draftActions.size()) {
                    draftActions.remove(idx);
                }
                rebuildRows();
            }));
            y += rowH + gap;

            addDetail(new SectionLabel(x, detailY(y), w,
                    Component.translatable("screen.yblk_zones.script_action_when_label")));
            y += 14 + gap;
            y = addScriptTriggerPicker(y, x, w, rowH, gap, a.trigger(), t -> {
                a.setTrigger(t);
                rebuildRows();
            });

            addDetail(new ActionButton(x, detailY(y), w, rowH,
                    Component.translatable("screen.yblk_zones.script_field_do",
                            Component.translatable("screen.yblk_zones.script_do."
                                    + a.type().name().toLowerCase()).getString()),
                    () -> minecraft.setScreen(new ScriptActionTypePickerScreen(this, a.type(), type -> {
                        a.setType(type);
                        applyScriptTypeDefaults(a);
                        rebuildRows();
                    }))));
            y += rowH + gap;

            y = buildScriptActionFields(y, x, w, rowH, gap, a);
            y += gap + 6;
        }
        return y;
    }

    private int addScriptQuickRow(int y, int x, int w, int rowH, int gap, int half,
                                  String leftKey, Runnable left,
                                  String rightKey, Runnable right) {
        addDetail(new ActionButton(x, detailY(y), half, rowH, Component.translatable(leftKey), left));
        addDetail(new ActionButton(x + half + 4, detailY(y), half, rowH, Component.translatable(rightKey), right));
        return y + rowH + gap;
    }

    /** 2×2 large When picker + full-width More. Selected = green. */
    private int addScriptTriggerPicker(int y, int x, int w, int rowH, int gap, ZoneTrigger selected,
                                       java.util.function.Consumer<ZoneTrigger> apply) {
        int half = w / 2 - 2;
        int bigH = Math.max(22, rowH + 2);
        addDetail(new SelectButton(x, detailY(y), half, bigH,
                Component.translatable("screen.yblk_zones.script_chip_start"),
                selected == ZoneTrigger.ON_EVENT_START,
                () -> apply.accept(ZoneTrigger.ON_EVENT_START)));
        addDetail(new SelectButton(x + half + 4, detailY(y), half, bigH,
                Component.translatable("screen.yblk_zones.script_chip_end"),
                selected == ZoneTrigger.ON_EVENT_END,
                () -> apply.accept(ZoneTrigger.ON_EVENT_END)));
        y += bigH + gap;
        addDetail(new SelectButton(x, detailY(y), half, bigH,
                Component.translatable("screen.yblk_zones.script_chip_enter"),
                selected == ZoneTrigger.ON_ENTER,
                () -> apply.accept(ZoneTrigger.ON_ENTER)));
        addDetail(new SelectButton(x + half + 4, detailY(y), half, bigH,
                Component.translatable("screen.yblk_zones.script_chip_exit"),
                selected == ZoneTrigger.ON_EXIT,
                () -> apply.accept(ZoneTrigger.ON_EXIT)));
        y += bigH + gap;

        boolean other = selected != ZoneTrigger.ON_EVENT_START
                && selected != ZoneTrigger.ON_EVENT_END
                && selected != ZoneTrigger.ON_ENTER
                && selected != ZoneTrigger.ON_EXIT;
        Component moreLabel = other
                ? Component.translatable("screen.yblk_zones.script_chip_more_selected", triggerLabel(selected))
                : Component.translatable("screen.yblk_zones.script_chip_more");
        addDetail(new SelectButton(x, detailY(y), w, bigH, moreLabel, other,
                () -> apply.accept(nextExtraTrigger(selected))));
        return y + bigH + gap;
    }

    private static String triggerLabel(ZoneTrigger t) {
        return Component.translatable("screen.yblk_zones.script_when." + t.name().toLowerCase()).getString();
    }

    private static ZoneTrigger nextExtraTrigger(ZoneTrigger current) {
        ZoneTrigger[] extras = {
                ZoneTrigger.ON_EVENT_TICK,
                ZoneTrigger.BEFORE_BUY,
                ZoneTrigger.AFTER_BUY
        };
        for (int i = 0; i < extras.length; i++) {
            if (current == extras[i]) {
                return extras[(i + 1) % extras.length];
            }
        }
        return extras[0];
    }

    private void addScriptQuick(ZoneTrigger trigger, ZoneActionType type, String value,
                                int intArg, float floatArg, boolean boolArg) {
        ZoneAction a = new ZoneAction(trigger, type, value);
        a.setIntArg(intArg);
        a.setFloatArg(floatArg);
        a.setBoolArg(boolArg);
        draftActions.add(a);
        rebuildRows();
    }

    private void openScriptLibrary() {
        ZoneScriptBundle bundle = ZoneScriptBundle.fromEditor(
                draftActions, eventEnabled, eventNeedsConfirm, denyExitWhileEvent, settings);
        minecraft.setScreen(new ScriptLibraryScreen(this, bundle, imported -> {
            draftActions.clear();
            for (ZoneAction a : imported.actions()) {
                draftActions.add(a.copy());
            }
            if (imported.hasEventMeta()) {
                ZoneScriptBundle.BooleanHolder en = new ZoneScriptBundle.BooleanHolder(eventEnabled);
                ZoneScriptBundle.BooleanHolder conf = new ZoneScriptBundle.BooleanHolder(eventNeedsConfirm);
                ZoneScriptBundle.BooleanHolder deny = new ZoneScriptBundle.BooleanHolder(denyExitWhileEvent);
                imported.applyEventTo(settings, en, conf, deny);
                eventEnabled = en.value;
                eventNeedsConfirm = conf.value;
                denyExitWhileEvent = deny.value;
            }
            rebuildRows();
            if (minecraft != null && minecraft.player != null) {
                minecraft.player.displayClientMessage(
                        Component.translatable("message.yblk_zones.script_imported", draftActions.size()),
                        false);
            }
        }));
    }

    private static <E extends Enum<E>> E nextEnum(E current, E[] values) {
        return values[(current.ordinal() + 1) % values.length];
    }

    private static void applyScriptTypeDefaults(ZoneAction a) {
        switch (a.type()) {
            case SPAWN -> {
                if (a.value().isBlank()) {
                    a.setValue("minecraft:zombie");
                }
                if (a.intArg() <= 0) {
                    a.setIntArg(3);
                }
            }
            case GIVE_ITEM -> {
                if (a.value().isBlank()) {
                    a.setValue("minecraft:diamond");
                }
                if (a.intArg() <= 0) {
                    a.setIntArg(1);
                }
            }
            case EFFECT -> {
                if (a.value().isBlank()) {
                    a.setValue("minecraft:strength");
                }
                if (a.intArg() <= 0) {
                    a.setIntArg(200);
                }
            }
            case SOUND -> {
                if (a.value().isBlank()) {
                    a.setValue("minecraft:entity.experience_orb.pickup");
                }
                if (a.floatArg() <= 0) {
                    a.setFloatArg(1.0F);
                }
            }
            case PARTICLE -> {
                if (a.value().isBlank()) {
                    a.setValue("minecraft:flame");
                }
                if (a.intArg() <= 0) {
                    a.setIntArg(16);
                }
            }
            case TITLE -> {
                if (a.value().isBlank()) {
                    a.setValue("...");
                }
            }
            case GIVE_XP -> {
                if (a.intArg() <= 0) {
                    a.setIntArg(20);
                }
            }
            case HEAL -> {
                if (a.floatArg() <= 0) {
                    a.setFloatArg(10.0F);
                }
            }
            case COMMAND -> {
                if (a.value().isBlank()) {
                    a.setValue("say hello");
                }
            }
            case FLAG_SET -> {
                if (a.value().isBlank()) {
                    a.setValue("CLAIM=true");
                }
            }
            default -> {
            }
        }
    }

    private int buildScriptActionFields(int y, int x, int w, int rowH, int gap, ZoneAction a) {
        return switch (a.type()) {
            case SPAWN -> {
                y = addScriptValueWithPick(y, x, w, rowH, gap, a,
                        "screen.yblk_zones.script_hint_mob",
                        () -> minecraft.setScreen(new EntityPickerScreen(this, id -> {
                            a.setValue(id);
                            rebuildRows();
                        })));
                yield addDetailInlineInt(y, x, w, gap, "screen.yblk_zones.script_field_count",
                        Math.max(1, a.intArg()), a::setIntArg);
            }
            case GIVE_ITEM -> {
                y = addScriptValueWithPick(y, x, w, rowH, gap, a,
                        "screen.yblk_zones.script_hint_item",
                        () -> minecraft.setScreen(new ItemPickerScreen(this, id -> {
                            a.setValue(id);
                            rebuildRows();
                        })));
                yield addDetailInlineInt(y, x, w, gap, "screen.yblk_zones.script_field_count",
                        Math.max(1, a.intArg()), a::setIntArg);
            }
            case EFFECT -> {
                y = addScriptValueWithPick(y, x, w, rowH, gap, a,
                        "screen.yblk_zones.script_hint_effect",
                        () -> minecraft.setScreen(new EffectPickerScreen(this, id -> {
                            a.setValue(id);
                            rebuildRows();
                        })));
                y = addDetailInlineInt(y, x, w, gap, "screen.yblk_zones.script_field_duration",
                        Math.max(1, a.intArg()), a::setIntArg);
                yield addDetailInlineInt(y, x, w, gap, "screen.yblk_zones.script_field_amplifier",
                        Math.max(0, (int) a.floatArg()), v -> a.setFloatArg(v));
            }
            case SOUND -> {
                y = addScriptValueWithPick(y, x, w, rowH, gap, a,
                        "screen.yblk_zones.script_hint_sound",
                        () -> minecraft.setScreen(new SoundPickerScreen(this, id -> {
                            a.setValue(id);
                            rebuildRows();
                        })));
                yield addDetailInlineFloat(y, x, w, gap, "screen.yblk_zones.script_field_volume",
                        a.floatArg() > 0 ? a.floatArg() : 1.0F, a::setFloatArg);
            }
            case PARTICLE -> {
                y = addScriptValueWithPick(y, x, w, rowH, gap, a,
                        "screen.yblk_zones.script_hint_particle",
                        () -> minecraft.setScreen(new ParticlePickerScreen(this, id -> {
                            a.setValue(id);
                            rebuildRows();
                        })));
                yield addDetailInlineInt(y, x, w, gap, "screen.yblk_zones.script_field_count",
                        Math.max(1, a.intArg()), a::setIntArg);
            }
            case TITLE -> {
                y = addDetailInlineText(y, x, w, gap, "screen.yblk_zones.script_field_text", a.value(), a::setValue);
                yield addScriptBoolToggle(y, x, w, rowH, gap, a,
                        "screen.yblk_zones.script_field_also_chat");
            }
            case COMMAND -> addDetailInlineText(y, x, w, gap, "screen.yblk_zones.script_field_command",
                    a.value(), a::setValue);
            case DENY_EXIT -> addScriptBoolToggle(y, x, w, rowH, gap, a,
                    "screen.yblk_zones.script_field_deny_on");
            case FLAG_SET -> {
                y = addDetailInlineText(y, x, w, gap, "screen.yblk_zones.script_field_flag",
                        a.value(), a::setValue);
                yield addScriptBoolToggle(y, x, w, rowH, gap, a,
                        "screen.yblk_zones.script_field_flag_on");
            }
            case GIVE_XP -> addDetailInlineInt(y, x, w, gap, "screen.yblk_zones.script_field_xp",
                    Math.max(1, a.intArg()), a::setIntArg);
            case HEAL -> {
                y = addDetailInlineFloat(y, x, w, gap, "screen.yblk_zones.script_field_hearts",
                        a.floatArg() > 0 ? a.floatArg() : 10.0F, a::setFloatArg);
                yield addScriptBoolToggle(y, x, w, rowH, gap, a,
                        "screen.yblk_zones.script_field_feed");
            }
            case CLEAR_MOBS -> {
                addDetail(new FlagHintBox(x, detailY(y), w, 22, null,
                        Component.translatable("screen.yblk_zones.script_clear_hint").getString()));
                yield y + 22;
            }
        };
    }

    private int addScriptValueWithPick(int y, int x, int w, int rowH, int gap, ZoneAction a,
                                       String hintKey, Runnable openPicker) {
        int pickW = 56;
        EditBox valueBox = makeField(x, detailY(y), w - pickW - 4, a.value());
        valueBox.setMaxLength(256);
        valueBox.setHint(Component.translatable(hintKey));
        valueBox.setResponder(a::setValue);
        addDetail(valueBox);
        fieldBoxes.add(valueBox);
        addDetail(new ActionButton(x + w - pickW, detailY(y), pickW, rowH,
                Component.translatable("screen.yblk_zones.script_pick"), openPicker));
        return y + Math.max(16, rowH) + gap;
    }

    private int addScriptBoolToggle(int y, int x, int w, int rowH, int gap, ZoneAction a, String labelKey) {
        addDetail(new ActionButton(x, detailY(y), w, rowH,
                Component.translatable(labelKey, a.boolArg() ? "ON" : "OFF"), () -> {
                    a.setBoolArg(!a.boolArg());
                    rebuildRows();
                }));
        return y + rowH + gap;
    }

    private static String scriptSummary(ZoneAction a) {
        String when = Component.translatable("screen.yblk_zones.script_when."
                + a.trigger().name().toLowerCase()).getString();
        String what = Component.translatable("screen.yblk_zones.script_do."
                + a.type().name().toLowerCase()).getString();
        String extra = switch (a.type()) {
            case SPAWN, GIVE_ITEM, PARTICLE -> a.value().isBlank() ? "" : " ×" + Math.max(1, a.intArg());
            case GIVE_XP -> " +" + Math.max(1, a.intArg());
            case TITLE -> a.value().isBlank() ? "" : " \"" + shorten(a.value(), 18) + "\"";
            default -> a.value().isBlank() ? "" : " " + shorten(a.value(), 16);
        };
        return when + " → " + what + extra;
    }

    private static String shorten(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private void applyRaidScriptPreset() {
        eventEnabled = true;
        eventNeedsConfirm = false;
        denyExitWhileEvent = true;
        settings.setEventSkyMode(ZoneSettings.SkyMode.NIGHT);
        settings.setEventWeatherMode(ZoneSettings.WeatherMode.RAIN);
        settings.setEventSpawnMob("minecraft:zombie");
        settings.setEventSpawnCount(3);
        settings.setEventWaves(3);
        settings.setEventWinOnClear(true);
        settings.setEventSpawnInterval(200); // unused in wave mode; fallback if waves=0
        settings.setEventDurationTicks(12000); // safety timeout 10 min
        settings.setEventMinTicks(0);
        settings.setEventCooldownTicks(1200);
        draftActions.clear();

        ZoneAction title = new ZoneAction(ZoneTrigger.ON_EVENT_START, ZoneActionType.TITLE, "Raid!");
        title.setBoolArg(true);
        draftActions.add(title);

        ZoneAction boom = new ZoneAction(ZoneTrigger.ON_EVENT_START, ZoneActionType.SOUND,
                "minecraft:entity.wither.spawn");
        boom.setFloatArg(0.85F);
        draftActions.add(boom);

        ZoneAction flames = new ZoneAction(ZoneTrigger.ON_EVENT_START, ZoneActionType.PARTICLE, "minecraft:flame");
        flames.setIntArg(40);
        draftActions.add(flames);

        ZoneAction dark = new ZoneAction(ZoneTrigger.ON_EVENT_START, ZoneActionType.EFFECT, "minecraft:darkness");
        dark.setIntArg(160);
        dark.setFloatArg(0);
        draftActions.add(dark);

        ZoneAction lock = new ZoneAction(ZoneTrigger.ON_EVENT_START, ZoneActionType.DENY_EXIT, "true");
        lock.setBoolArg(true);
        draftActions.add(lock);

        draftActions.add(new ZoneAction(ZoneTrigger.ON_EVENT_END, ZoneActionType.CLEAR_MOBS, ""));

        ZoneAction reward = new ZoneAction(ZoneTrigger.ON_EVENT_END, ZoneActionType.GIVE_ITEM, "minecraft:diamond");
        reward.setIntArg(3);
        draftActions.add(reward);

        ZoneAction xp = new ZoneAction(ZoneTrigger.ON_EVENT_END, ZoneActionType.GIVE_XP, "");
        xp.setIntArg(50);
        draftActions.add(xp);

        ZoneAction heal = new ZoneAction(ZoneTrigger.ON_EVENT_END, ZoneActionType.HEAL, "");
        heal.setFloatArg(10.0F);
        heal.setBoolArg(true);
        draftActions.add(heal);

        ZoneAction win = new ZoneAction(ZoneTrigger.ON_EVENT_END, ZoneActionType.TITLE, "Victory!");
        win.setBoolArg(true);
        draftActions.add(win);

        ZoneAction fanfare = new ZoneAction(ZoneTrigger.ON_EVENT_END, ZoneActionType.SOUND,
                "minecraft:ui.toast.challenge_complete");
        fanfare.setFloatArg(1.0F);
        draftActions.add(fanfare);

        snapDetail();
        nav = NavId.SCRIPTS;
        rememberNav();
        Minecraft.getInstance().execute(() -> {
            rebuildRows();
            if (minecraft != null && minecraft.player != null) {
                minecraft.player.displayClientMessage(
                        Component.translatable("message.yblk_zones.script_preset_applied", draftActions.size()),
                        false);
            }
        });
    }

    private int buildGeneralDetail(int y, int x, int w, int gap) {
        int ownsH = hintRowHeight("screen.yblk_zones.creator_owns.hint");
        addDetail(new HintRow(x, detailY(y), w, ownsH,
                Component.translatable("screen.yblk_zones.creator_owns", creatorOwns ? "ON" : "OFF"),
                "screen.yblk_zones.creator_owns.hint", () -> {
                    creatorOwns = !creatorOwns;
                    rebuildRows();
                }));
        y += ownsH + gap;
        y = addDetailLabeledInt(y, x, w, gap, "screen.yblk_zones.harvest_level_label",
                "screen.yblk_zones.harvest_level.hint", settings.harvestLevel(),
                v -> settings.setHarvestLevel(Mth.clamp(v, 0, 5)));
        y = addDetailLabeledInt(y, x, w, gap, "screen.yblk_zones.priority_label",
                "screen.yblk_zones.priority.hint", priority, v -> priority = Mth.clamp(v, 0, 20));
        int cmdH = hintRowHeight("screen.yblk_zones.allow_commands.hint");
        addDetail(new HintRow(x, detailY(y), w, cmdH,
                Component.translatable("screen.yblk_zones.allow_commands", allowCommands ? "ON" : "OFF"),
                "screen.yblk_zones.allow_commands.hint", () -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player == null || !mc.player.hasPermissions(2)) {
                        if (mc.player != null) {
                            mc.player.displayClientMessage(Component.translatable("message.yblk_zones.commands_op_only"), true);
                        }
                        return;
                    }
                    allowCommands = !allowCommands;
                    rebuildRows();
                }));
        return y + cmdH + gap;
    }

    private int buildTemplateDetail(int y, int x, int w, int rowH, int gap) {
        var templates = ZoneTemplates.all();
        if (templates.isEmpty()) {
            addDetail(new SectionLabel(x, detailY(y), w,
                    Component.translatable("screen.yblk_zones.template_empty")));
            return y + 14;
        }
        if (templatePreviewIndex >= templates.size()) {
            templatePreviewIndex = 0;
        }
        for (int i = 0; i < templates.size(); i++) {
            final int idx = i;
            ZoneTemplates.Template tpl = templates.get(i);
            boolean selected = idx == templatePreviewIndex;
            boolean applied = idx == templateIndex;
            Component label = Component.literal((applied ? "● " : "") + (selected ? "▸ " : "") + tpl.name());
            addDetail(new ActionButton(x, detailY(y), w, rowH, label, () -> {
                templatePreviewIndex = idx;
                rebuildRows();
            }));
            y += rowH + gap;
        }
        ZoneTemplates.Template preview = templates.get(templatePreviewIndex);
        String flagsLine = preview.flags().isEmpty() ? "—" : String.join(", ", preview.flags());
        int hl = preview.settingsNbt().contains("harvestLevel")
                ? preview.settingsNbt().getInt("harvestLevel") : -1;
        String previewText = preview.name() + "\n"
                + Component.translatable("screen.yblk_zones.template_preview", flagsLine).getString()
                + (hl >= 0 ? "\nLoot: " + hl : "");
        addDetail(new FlagHintBox(x, detailY(y), w, 40, null, previewText));
        y += 40 + gap;
        addDetail(new ActionButton(x, detailY(y), w, rowH,
                Component.translatable("screen.yblk_zones.template_apply"), () -> {
            templateIndex = templatePreviewIndex;
            flags.clear();
            settings.denyMobs().clear();
            settings.buffMobs().clear();
            ZoneTemplates.apply(templates.get(templateIndex), settings, flags);
            if (minecraft != null && minecraft.player != null) {
                minecraft.player.displayClientMessage(
                        Component.translatable("message.yblk_zones.template_applied",
                                templates.get(templateIndex).name()), true);
            }
            rebuildRows();
        }));
        return y + rowH + gap;
    }

    private int buildEffectsDetail(int y, int x, int w, int rowH, int gap) {
        List<EffectEntry> effects = settings.effects();
        for (int i = 0; i < effects.size(); i++) {
            final int idx = i;
            EffectEntry e = effects.get(i);
            addDetail(new EffectRowButton(x, detailY(y), w - 100, 20, e, () -> {
            }));
            addDetail(new ActionButton(x + w - 96, detailY(y), 56, 20,
                    Component.literal(e.showParticles() ? "Fx ON" : "Fx OFF"), () -> {
                        e.setShowParticles(!e.showParticles());
                        rebuildRows();
                    }));
            addDetail(new ActionButton(x + w - 36, detailY(y), 36, 20, Component.literal("✕"), () -> {
                if (idx >= 0 && idx < settings.effects().size()) {
                    settings.effects().remove(idx);
                }
                rebuildRows();
            }));
            y += 20 + gap;
        }
        addDetail(new ActionButton(x, detailY(y), w, rowH,
                Component.translatable("screen.yblk_zones.pick_effect"),
                () -> minecraft.setScreen(new EffectPickerScreen(this, id -> {
                    settings.effects().add(EffectEntry.of(id));
                    minecraft.setScreen(this);
                    rebuildRows();
                }))));
        return y + rowH + gap;
    }

    private int buildSaleDetail(int y, int x, int w, int gap) {
        saleCountBoxes.clear();
        if (saleCosts.isEmpty()) {
            saleCosts.add(new SaleCost("", 1));
        }
        for (int i = 0; i < saleCosts.size(); i++) {
            final int idx = i;
            SaleCost cost = saleCosts.get(i);
            boolean canRemove = saleCosts.size() > 1;
            SaleCostWidget row = new SaleCostWidget(x, detailY(y), w, this, idx, cost,
                    c -> saleCosts.get(idx).setCount(c),
                    canRemove ? () -> {
                        saleCosts.remove(idx);
                        rebuildRows();
                    } : null);
            addDetail(row);
            EditBox box = row.countBox();
            saleCountBoxes.add(box);
            addDetail(box);
            y += 36 + gap;
        }
        addDetail(new ActionButton(x, detailY(y), w, 14,
                Component.translatable("screen.yblk_zones.payment_dest", paymentDestination.name()),
                () -> {
                    paymentDestination = paymentDestination == PaymentDestination.VOID
                            ? PaymentDestination.SELLER
                            : PaymentDestination.VOID;
                    rebuildRows();
                }));
        y += 14 + gap;
        if (saleCosts.size() < MAX_SALE_COSTS) {
            addDetail(new ActionButton(x, detailY(y), w, 14,
                    Component.translatable("screen.yblk_zones.sale_add"),
                    () -> {
                        saleCosts.add(new SaleCost("", 1));
                        rebuildRows();
                    }));
            y += 14 + gap;
        }
        return y;
    }

    private int buildMobFilterSettings(ZoneFlag flag, int y, int x, int w, int rowH, int gap) {
        boolean deny = flag == ZoneFlag.MOB_DENY_ENTRY;
        ZoneSettings.MobGroup group = deny ? settings.denyMobGroup() : settings.buffMobGroup();
        addDetail(new ActionButton(x, detailY(y), w, rowH,
                Component.translatable("screen.yblk_zones.mob_group",
                        Component.translatable("screen.yblk_zones.mob_group." + group.name().toLowerCase()).getString()),
                () -> {
                    if (deny) {
                        settings.cycleDenyMobGroup();
                    } else {
                        settings.cycleBuffMobGroup();
                    }
                    rebuildRows();
                }));
        y += rowH + gap;
        if (group == ZoneSettings.MobGroup.CUSTOM) {
            var set = deny ? settings.denyMobs() : settings.buffMobs();
            String list = set.isEmpty() ? "—" : set.stream().map(ResourceLocation::getPath)
                    .reduce((a, b) -> a + ", " + b).orElse("—");
            if (list.length() > 48) {
                list = list.substring(0, 46) + "…";
            }
            addDetail(new ActionButton(x, detailY(y), w, rowH,
                    Component.translatable("screen.yblk_zones.mob_list", list), () -> {
                    }));
            y += rowH + gap;
            y = addDetailInlineText(y, x, w, gap, "screen.yblk_zones.mob_id_label", mobDraft, v -> mobDraft = v);
            addDetail(new ActionButton(x, detailY(y), w / 2 - 2, rowH,
                    Component.translatable("screen.yblk_zones.mob_add"), () -> {
                        ResourceLocation rl = ResourceLocation.tryParse(mobDraft.trim());
                        if (rl != null) {
                            set.add(rl);
                        }
                        rebuildRows();
                    }));
            addDetail(new ActionButton(x + w / 2 + 2, detailY(y), w / 2 - 2, rowH,
                    Component.translatable("screen.yblk_zones.mob_clear"), () -> {
                        set.clear();
                        rebuildRows();
                    }));
            y += rowH + gap;
        }
        if (flag == ZoneFlag.MOB_BUFF) {
            y = addDetailInlineInt(y, x, w, gap, "screen.yblk_zones.buff_amp_label",
                    settings.buffAmplifier() + 1, v -> settings.setBuffAmplifier(Mth.clamp(v, 1, 5) - 1));
        }
        return y;
    }

    private void selectFlag(ZoneFlag flag) {
        nav = NavId.FLAG;
        navFlag = flag;
        snapDetail();
        rememberNav();
        rebuildRows();
    }

    /** Switch left-nav section and reset detail scroll so top buttons stay clickable. */
    protected void selectNav(NavId id) {
        nav = id;
        snapDetail();
        rememberNav();
    }

    private void snapDetail() {
        detailScroll = 0;
        detailScrollTarget = 0;
        detailScrollF = 0;
        detailSlide = 10f;
    }

    private void easeScrollFrame() {
        long now = Util.getMillis();
        float dt = easeAt == 0L ? 0.016F : Math.min(0.05F, (now - easeAt) / 1000F);
        easeAt = now;
        float k = 1F - (float) Math.exp(-18F * dt);
        navScrollF = Mth.lerp(k, navScrollF, navScrollTarget);
        detailScrollF = Mth.lerp(k, detailScrollF, detailScrollTarget);
        if (Math.abs(navScrollF - navScrollTarget) < 0.04F) {
            navScrollF = navScrollTarget;
        }
        if (Math.abs(detailScrollF - detailScrollTarget) < 0.04F) {
            detailScrollF = detailScrollTarget;
        }
        detailSlide = detailSlide <= 0.05F ? 0F : detailSlide * (float) Math.exp(-14F * dt);
        int nextNav = Math.round(navScrollF);
        int nextDetail = Math.round(detailScrollF);
        if (nextNav != navScroll || nextDetail != detailScroll) {
            navScroll = nextNav;
            detailScroll = nextDetail;
            applyScroll();
        }
    }

    private void trackFlagHover() {
        String under = null;
        ZoneFlag underFlag = null;
        for (AbstractWidget w : navDynamic) {
            if (w instanceof NavRow row && row.visible
                    && pointerX >= row.getX() && pointerX < row.getX() + row.getWidth()
                    && pointerY >= row.getY() && pointerY < row.getY() + row.getHeight()) {
                under = tipKey(row);
                underFlag = row.flag;
                break;
            }
        }
        if (under != null && under.equals(hoveredTip)) {
            if (flagHoverTicks < 40) {
                flagHoverTicks++;
            }
        } else {
            hoveredTip = under;
            hoveredFlag = underFlag;
            flagHoverTicks = 0;
        }
    }

    private static String tipKey(NavRow row) {
        if (row.flag != null) {
            return "flag.yblk_zones." + row.flag.name().toLowerCase(java.util.Locale.ROOT) + ".desc";
        }
        return switch (row.navId) {
            case AMBIENCE -> "screen.yblk_zones.section_ambience.hint";
            case EVENT -> "screen.yblk_zones.section_event.hint";
            case GENERAL -> "screen.yblk_zones.section_general.hint";
            case TEMPLATE -> "screen.yblk_zones.section_template.hint";
            case EFFECTS -> "screen.yblk_zones.section_effects.hint";
            case SCRIPTS -> "screen.yblk_zones.section_scripts.hint";
            case SALE -> "screen.yblk_zones.section_sale.hint";
            case FLAG -> null;
        };
    }

    private void applyScroll() {
        int top = contentTop();
        int bot = contentBottom();
        for (AbstractWidget w : navDynamic) {
            Integer layout = navLayoutY.get(w);
            if (layout != null) {
                w.setY(layout - navScroll);
            }
            boolean show = w.getY() + w.getHeight() > top && w.getY() < bot;
            w.visible = show;
            w.active = show;
        }
        for (AbstractWidget w : detailDynamic) {
            Integer layout = detailLayoutY.get(w);
            if (layout != null) {
                w.setY(layout - detailScroll);
            }
            boolean show = w.getY() + w.getHeight() > top && w.getY() < bot;
            w.visible = show;
            w.active = show;
        }
    }

    private void toggleFlag(ZoneFlag flag) {
        if (flags.contains(flag)) {
            flags.remove(flag);
        } else {
            flags.add(flag);
        }
        rebuildRows();
    }

    protected void rememberNav() {
        rememberedNav = nav;
        rememberedFlag = navFlag;
    }

    private int addNavSection(int y, Component label) {
        addNav(new SectionLabel(navLeft(), navY(y), navWidth(), label));
        return y + 12;
    }

    private int navY(int contentY) {
        return contentTop() + contentY - navScroll;
    }

    private int detailY(int contentY) {
        return contentTop() + contentY - detailScroll;
    }

    private void updateColumnVisibility() {
        int top = contentTop();
        int bot = contentBottom();
        for (AbstractWidget w : navDynamic) {
            boolean show = w.getY() + w.getHeight() > top && w.getY() < bot;
            w.visible = show;
            w.active = show;
        }
        for (AbstractWidget w : detailDynamic) {
            boolean show = w.getY() + w.getHeight() > top && w.getY() < bot && w.getX() >= detailLeft() - 2;
            w.visible = show;
            w.active = show;
        }
    }

    public void setSaleItemId(int index, String id) {
        while (saleCosts.size() <= index) {
            saleCosts.add(new SaleCost("", 1));
        }
        saleCosts.get(index).setItemId(id == null ? "" : id);
    }

    protected List<SaleCost> validSaleCosts() {
        List<SaleCost> out = new ArrayList<>();
        for (SaleCost c : saleCosts) {
            if (c != null && c.isValid()) {
                out.add(c.copy());
            }
        }
        return out;
    }

    protected boolean saleHasItem() {
        return !validSaleCosts().isEmpty();
    }

    private void addOnlineTrusted() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) {
            return;
        }
        List<PlayerInfo> list = new ArrayList<>(mc.getConnection().getOnlinePlayers());
        if (list.isEmpty()) {
            return;
        }
        trustPlayerIndex = trustPlayerIndex % list.size();
        String name = list.get(trustPlayerIndex).getProfile().getName();
        trustPlayerIndex++;
        if (settings.trustedNames().stream().anyMatch(n -> n.equalsIgnoreCase(name))) {
            settings.removeTrustedName(name);
        } else {
            settings.addTrustedName(name);
        }
        rebuildRows();
    }

    private void cycleTeam() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        var teams = new ArrayList<>(mc.level.getScoreboard().getPlayerTeams());
        if (teams.isEmpty()) {
            settings.setTrustedTeam("");
            rebuildRows();
            return;
        }
        String cur = settings.trustedTeam();
        int idx = -1;
        for (int i = 0; i < teams.size(); i++) {
            if (teams.get(i).getName().equalsIgnoreCase(cur)) {
                idx = i;
                break;
            }
        }
        idx++;
        if (idx >= teams.size()) {
            settings.setTrustedTeam("");
        } else {
            settings.setTrustedTeam(teams.get(idx).getName());
        }
        rebuildRows();
    }

    private int addDetailLabeledInt(int y, int x, int w, int gap, String labelKey, String hintKey, int value,
                                    java.util.function.IntConsumer apply) {
        int hintH = hintRowHeight(hintKey);
        addDetail(new HintRow(x, detailY(y), w, hintH, Component.translatable(labelKey, value), hintKey, () -> {
        }));
        y += hintH + 2;
        EditBox box = makeField(x, detailY(y), w, String.valueOf(value));
        box.setResponder(s -> {
            try {
                apply.accept(Integer.parseInt(s.trim()));
            } catch (NumberFormatException ignored) {
            }
        });
        addDetail(box);
        fieldBoxes.add(box);
        return y + 16 + gap;
    }

    private int addDetailLabeledText(int y, int x, int w, int gap, String labelKey, String hintKey, String value,
                                     java.util.function.Consumer<String> apply) {
        int hintH = hintRowHeight(hintKey);
        addDetail(new HintRow(x, detailY(y), w, hintH, Component.translatable(labelKey), hintKey, () -> {
        }));
        y += hintH + 2;
        EditBox box = makeField(x, detailY(y), w, value == null ? "" : value);
        box.setResponder(apply);
        addDetail(box);
        fieldBoxes.add(box);
        return y + 16 + gap;
    }

    private int hintRowHeight(String hintKey) {
        if (hintKey == null || hintKey.isEmpty()) {
            return 18;
        }
        int textMax = Math.max(40, detailWidth() - 14);
        String desc = Component.translatable(hintKey).getString();
        int lines = Math.min(3, font.split(Component.literal(desc), textMax).size());
        return 16 + lines * 9 + 4;
    }

    private int addDetailInlineInt(int y, int x, int w, int gap, String labelKey, int value,
                                   java.util.function.IntConsumer apply) {
        addDetail(new ActionButton(x, detailY(y), w / 2 - 2, 16, Component.translatable(labelKey), () -> {
        }));
        EditBox box = makeField(x + w / 2, detailY(y), w / 2, String.valueOf(value));
        box.setResponder(s -> {
            try {
                apply.accept(Integer.parseInt(s.trim()));
            } catch (NumberFormatException ignored) {
            }
        });
        addDetail(box);
        fieldBoxes.add(box);
        return y + 16 + gap;
    }

    private int addDetailInlineFloat(int y, int x, int w, int gap, String labelKey, float value,
                                     java.util.function.Consumer<Float> apply) {
        addDetail(new ActionButton(x, detailY(y), w / 2 - 2, 16, Component.translatable(labelKey), () -> {
        }));
        EditBox box = makeField(x + w / 2, detailY(y), w / 2, String.format("%.2f", value));
        box.setResponder(s -> {
            try {
                apply.accept(Float.parseFloat(s.trim().replace(',', '.')));
            } catch (NumberFormatException ignored) {
            }
        });
        addDetail(box);
        fieldBoxes.add(box);
        return y + 16 + gap;
    }

    private int addDetailInlineText(int y, int x, int w, int gap, String labelKey, String value,
                                  java.util.function.Consumer<String> apply) {
        addDetail(new ActionButton(x, detailY(y), w / 2 - 2, 16, Component.translatable(labelKey), () -> {
        }));
        EditBox box = makeField(x + w / 2, detailY(y), w / 2, value == null ? "" : value);
        box.setResponder(apply);
        addDetail(box);
        fieldBoxes.add(box);
        return y + 16 + gap;
    }

    private EditBox makeField(int x, int y, int w, String value) {
        EditBox box = new EditBox(font, x, y, w, 14, Component.empty());
        box.setMaxLength(256);
        box.setValue(value);
        box.setTextColor(TEXT);
        return box;
    }

    protected void addNav(AbstractWidget w) {
        navLayoutY.put(w, w.getY() + navScroll);
        navDynamic.add(w);
        addRenderableWidget(w);
    }

    protected void addDetail(AbstractWidget w) {
        detailLayoutY.put(w, w.getY() + detailScroll);
        detailDynamic.add(w);
        addRenderableWidget(w);
    }

    @Override
    public void tick() {
        super.tick();
        nameBox.tick();
        trackFlagHover();
        for (EditBox box : saleCountBoxes) {
            box.tick();
        }
        for (EditBox box : fieldBoxes) {
            box.tick();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (EditBox box : fieldBoxes) {
            if (box.visible && box.active && box.mouseClicked(mouseX, mouseY, button)) {
                setFocused(box);
                return true;
            }
        }
        for (EditBox box : saleCountBoxes) {
            if (box.visible && box.active && box.mouseClicked(mouseX, mouseY, button)) {
                setFocused(box);
                return true;
            }
        }
        if (button == 0 && beginBarDrag(mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingBar != 0 && button == 0) {
            dragBar(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            draggingBar = 0;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean beginBarDrag(double mouseX, double mouseY) {
        int view = contentBottom() - contentTop();
        if (navContentHeight > view && hitBar(mouseX, mouseY, panelX + NAV_W - 5, navScroll, navContentHeight, view)) {
            draggingBar = 1;
            dragBar(mouseY);
            return true;
        }
        if (detailContentHeight > view && hitBar(mouseX, mouseY, panelX + panelW - 5, detailScroll, detailContentHeight, view)) {
            draggingBar = 2;
            dragBar(mouseY);
            return true;
        }
        return false;
    }

    private boolean hitBar(double mouseX, double mouseY, int barX, int scroll, int contentHeight, int view) {
        int barH = Math.max(12, view * view / contentHeight);
        int barY = contentTop() + (int) ((view - barH) * (scroll / (float) Math.max(1, contentHeight - view)));
        return mouseX >= barX - 3 && mouseX <= barX + 7 && mouseY >= barY && mouseY <= barY + barH;
    }

    private void dragBar(double mouseY) {
        int view = contentBottom() - contentTop();
        boolean nav = draggingBar == 1;
        int content = nav ? navContentHeight : detailContentHeight;
        int max = Math.max(1, content - view);
        int barH = Math.max(12, view * view / Math.max(content, 1));
        double t = (mouseY - contentTop() - barH / 2.0) / Math.max(1, view - barH);
        int next = Mth.clamp((int) Math.round(t * max), 0, max);
        if (nav) {
            navScrollTarget = next;
            navScrollF = next;
            navScroll = next;
        } else {
            detailScrollTarget = next;
            detailScrollF = next;
            detailScroll = next;
        }
        rebuildRows();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (getFocused() instanceof EditBox box && box.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (getFocused() instanceof EditBox box && box.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= panelX && mouseX <= panelX + panelW && mouseY >= contentTop() && mouseY <= contentBottom()) {
            int view = contentBottom() - contentTop();
            if (mouseX < panelX + NAV_W) {
                int maxScroll = Math.max(0, navContentHeight - view);
                navScrollTarget = Mth.clamp(navScrollTarget - (int) (delta * 18), 0, maxScroll);
                return true;
            }
            int maxScroll = Math.max(0, detailContentHeight - view);
            detailScrollTarget = Mth.clamp(detailScrollTarget - (int) (delta * 18), 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private static String shortId(String id) {
        if (id == null || id.isBlank() || "—".equals(id)) {
            return "—";
        }
        int slash = id.indexOf(':');
        String path = slash >= 0 ? id.substring(slash + 1) : id;
        return path.length() > 28 ? path.substring(0, 26) + "…" : path;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        pointerX = mouseX;
        pointerY = mouseY;
        easeScrollFrame();
        renderBackground(g);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL);
        drawGradientBorder(g, panelX, panelY, panelW, panelH, ACCENT, ACCENT_BOTTOM);

        g.drawString(font, title, panelX + 10, panelY + 6, TEXT, false);
        g.drawString(font, Component.translatable("screen.yblk_zones.name"), panelX + 10, panelY + 18, MUTED, false);

        g.fill(panelX + NAV_W, contentTop(), panelX + NAV_W + 1, contentBottom(), PANEL_EDGE);
        g.fill(panelX, contentBottom(), panelX + panelW, panelY + panelH, FOOTER);
        g.fill(panelX + 10, contentBottom(), panelX + panelW - 10, contentBottom() + 1, ACCENT_DIM);

        int view = contentBottom() - contentTop();

        g.enableScissor(panelX + 2, contentTop(), panelX + NAV_W - 1, contentBottom());
        g.pose().pushPose();
        g.pose().translate(0.0D, navScroll - navScrollF, 0.0D);
        for (AbstractWidget w : navDynamic) {
            if (w.visible) {
                w.render(g, mouseX, mouseY, partialTick);
            }
        }
        g.pose().popPose();
        g.disableScissor();

        g.enableScissor(panelX + NAV_W + 2, contentTop(), panelX + panelW - 2, contentBottom());
        g.pose().pushPose();
        g.pose().translate(0.0D, detailScroll - detailScrollF + detailSlide, 0.0D);
        for (AbstractWidget w : detailDynamic) {
            if (w.visible) {
                w.render(g, mouseX, mouseY, partialTick);
            }
        }
        g.pose().popPose();
        g.disableScissor();

        nameBox.render(g, mouseX, mouseY, partialTick);
        for (AbstractWidget w : footerBtns) {
            w.render(g, mouseX, mouseY, partialTick);
        }

        if (navContentHeight > view) {
            drawScrollBar(g, panelX + NAV_W - 5, navScrollF, navContentHeight, view);
        }
        if (detailContentHeight > view) {
            drawScrollBar(g, panelX + panelW - 5, detailScrollF, detailContentHeight, view);
        }
        if (hoveredTip != null && flagHoverTicks >= 14) {
            g.renderTooltip(font, Component.translatable(hoveredTip), (int) pointerX, (int) pointerY);
        }
    }

    /** Glyphs the vanilla font draws as a tiny mark get a full 7px icon instead. */
    private static boolean paintMark(GuiGraphics g, ZoneFlag flag, int x, int y, int color) {
        if (flag == ZoneFlag.CLAIM) {
            g.fill(x, y + 6, x + 7, y + 7, color);
            g.fill(x, y + 3, x + 2, y + 6, color);
            g.fill(x + 3, y + 1, x + 5, y + 6, color);
            g.fill(x + 6, y + 3, x + 8, y + 6, color);
            g.fill(x, y + 2, x + 1, y + 3, color);
            g.fill(x + 3, y, x + 5, y + 1, color);
            g.fill(x + 7, y + 2, x + 8, y + 3, color);
            return true;
        }
        if (flag == ZoneFlag.DENY_CONTAINERS) {
            g.fill(x, y + 1, x + 8, y + 2, color);
            g.fill(x, y + 2, x + 1, y + 7, color);
            g.fill(x + 7, y + 2, x + 8, y + 7, color);
            g.fill(x, y + 7, x + 8, y + 8, color);
            g.fill(x + 1, y + 4, x + 7, y + 5, color);
            g.fill(x + 3, y + 2, x + 5, y + 4, color);
            return true;
        }
        return false;
    }

    private void drawScrollBar(GuiGraphics g, int barX, float scroll, int contentHeight, int view) {
        int barH = Math.max(12, view * view / contentHeight);
        int barY = contentTop() + Math.round((view - barH) * (scroll / (float) Math.max(1, contentHeight - view)));
        g.fill(barX, contentTop(), barX + 3, contentBottom(), 0xFF1A1A22);
        g.fill(barX, barY, barX + 3, barY + barH, ACCENT);
    }

    protected static void drawGradientBorder(GuiGraphics graphics, int x, int y, int w, int h,
                                             int topRgb, int bottomRgb) {
        for (int i = 0; i < h; i++) {
            float t = h <= 1 ? 0f : i / (float) (h - 1);
            int color = lerpColor(topRgb, bottomRgb, t) | 0xFF000000;
            graphics.fill(x, y + i, x + 1, y + i + 1, color);
            graphics.fill(x + w - 1, y + i, x + w, y + i + 1, color);
        }
        graphics.fill(x, y, x + w, y + 1, topRgb | 0xFF000000);
        graphics.fill(x, y + h - 1, x + w, y + h, bottomRgb | 0xFF000000);
    }

    private static int lerpColor(int a, int b, float t) {
        t = Mth.clamp(t, 0f, 1f);
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = Mth.floor(Mth.lerp(t, ar, br));
        int g = Mth.floor(Mth.lerp(t, ag, bg));
        int bl = Mth.floor(Mth.lerp(t, ab, bb));
        return (r << 16) | (g << 8) | bl;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    protected void onCancel() {
        ZoneSelection.clear();
        onClose();
    }

    protected void onConfirm() {
        if (!ZoneSelection.canFinish()) {
            onCancel();
            return;
        }
        Minecraft mcGate = Minecraft.getInstance();
        if (mcGate.player != null && !ZoneManager.mayWand(mcGate.player, ZoneManager.WandPower.CREATE)) {
            mcGate.player.displayClientMessage(Component.translatable("message.yblk_zones.need_edit_perm"), true);
            return;
        }
        if (mcGate.player != null && !ZoneManager.isWithinSizeFor(mcGate.player, ZoneSelection.corners(), ZoneSelection.height())) {
            mcGate.player.displayClientMessage(Component.translatable("message.yblk_zones.too_big",
                    ZoneManager.edgeCap(mcGate.player)), true);
            return;
        }
        if (saleEnabled && !saleHasItem()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.translatable("message.yblk_zones.sale_need_item"), true);
            }
            nav = NavId.SALE;
            rebuildRows();
            return;
        }
        ZoneSelection.markConfirmed();
        List<SaleCost> costs = saleEnabled ? validSaleCosts() : List.of();
        ZonesNetwork.CHANNEL.sendToServer(new CreateZonePacket(
                List.copyOf(ZoneSelection.corners()),
                ZoneSelection.floorY(),
                ZoneSelection.height(),
                nameBox.getValue(),
                CreateZonePacket.packFlags(flags),
                settings,
                costs,
                creatorOwns,
                paymentDestination,
                priority,
                allowCommands,
                List.copyOf(draftActions),
                eventEnabled,
                eventNeedsConfirm,
                denyExitWhileEvent));
        ZoneSelection.clear();
        onClose();
    }

    @Override
    public void onClose() {
        ZoneSelection.clearUnlessConfirmed();
        super.onClose();
    }

    protected boolean navSelected(NavId id, ZoneFlag flag) {
        if (id == NavId.FLAG) {
            return nav == NavId.FLAG && navFlag == flag;
        }
        return nav == id;
    }

    protected class NavRow extends AbstractWidget {
        private final NavId navId;
        private final ZoneFlag flag;
        private final boolean hasToggle;
        private final boolean toggleOn;
        private final Runnable onSelect;
        private final Runnable onToggle;

        NavRow(int x, int y, int w, int h, NavId navId, ZoneFlag flag, Component label, boolean toggleOn,
               Runnable onSelect, Runnable onToggle) {
            super(x, y, w, h, label);
            this.navId = navId;
            this.flag = flag;
            this.hasToggle = onToggle != null;
            this.toggleOn = toggleOn;
            this.onSelect = onSelect;
            this.onToggle = onToggle;
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            if (hasToggle && mouseX >= getX() + width - TOGGLE_STRIP) {
                onToggle.run();
            } else {
                onSelect.run();
                if (navId != NavId.FLAG) {
                    rebuildRows();
                }
            }
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
            boolean selected = navSelected(navId, flag);
            boolean hov = isHoveredOrFocused();
            g.fill(getX(), getY(), getX() + width, getY() + height, hov ? ROW_HOV : ROW);
            if (selected) {
                g.fill(getX(), getY(), getX() + 2, getY() + height, ACCENT);
            }
            int cube = flag != null ? flag.colorRgb : navSectionColor(navId);
            int markX = getX() + 6;
            int markY = getY() + (height - 8) / 2;
            if (!paintMark(g, flag, markX, markY, 0xFF000000 | cube)) {
                g.drawString(font, markFor(flag, navId), markX, markY, 0xFF000000 | cube, false);
            }
            int textX = markX + 10;
            int textMax = width - (textX - getX()) - (hasToggle ? TOGGLE_STRIP : 8);
            g.drawString(font, font.plainSubstrByWidth(getMessage().getString(), Math.max(8, textMax)),
                    textX, markY, TEXT, false);
            if (hasToggle) {
                String key = flag != null ? flag.name() : navId.name();
                float shown = toggleAnim.getOrDefault(key, toggleOn ? 1f : 0f);
                shown = Mth.lerp(0.45F, shown, toggleOn ? 1f : 0f);
                toggleAnim.put(key, shown);
                int sw = 34;
                int sh = 14;
                int sx = getX() + width - sw - 4;
                int sy = getY() + (height - sh) / 2;
                ToggleSwitch.paint(g, sx, sy, sw, sh, shown);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            defaultButtonNarrationText(out);
        }
    }

    private static String markFor(ZoneFlag flag, NavId id) {
        if (flag != null) {
            return switch (flag) {
                case VISIBLE -> "\u2600";
                case DISPLAY -> "\u2605";
                case CLAIM -> "\u265A";
                case ADVENTURE_BREAK -> "\u25A0";
                case ADVENTURE_BUILD -> "\u25B2";
                case MOB_BUFF -> "\u2665";
                case MOB_DENY_ENTRY -> "\u2620";
                case MOB_DENY_SPAWN -> "\u2639";
                case PLAYER_DAMAGE -> "\u2660";
                case DENY_PVP -> "\u2694";
                case DENY_EXPLOSIONS -> "\u2602";
                case DENY_CONTAINERS -> "\u25A3";
                case DENY_ENTRY -> "\u26D4";
                case DENY_TELEPORT -> "\u2194";
            };
        }
        return switch (id) {
            case FLAG -> "\u2666";
            case AMBIENCE -> "\u266A";
            case EVENT -> "\u2605";
            case GENERAL -> "\u263A";
            case TEMPLATE -> "\u25C6";
            case EFFECTS -> "\u2665";
            case SCRIPTS -> "\u266B";
            case SALE -> "\u2600";
        };
    }

    private static int navSectionColor(NavId id) {
        return switch (id) {
            case FLAG -> 0x55CCFF;
            case AMBIENCE -> 0x88CCAA;
            case EVENT -> 0xFFAA55;
            case GENERAL -> 0xA0A0B0;
            case TEMPLATE -> 0x66AADD;
            case EFFECTS -> 0xCC66FF;
            case SCRIPTS -> 0x55FFAA;
            case SALE -> 0xFFDD55;
        };
    }

    protected class EffectRowButton extends AbstractWidget {
        private final EffectEntry entry;

        EffectRowButton(int x, int y, int w, int h, EffectEntry entry, Runnable action) {
            super(x, y, w, h, Component.literal(entry.effectId()));
            this.entry = entry;
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
            g.fill(getX(), getY(), getX() + width, getY() + height, ROW);
            var effect = net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getValue(entry.id());
            if (effect != null) {
                var sprite = Minecraft.getInstance().getMobEffectTextures().get(effect);
                g.blit(getX() + 2, getY() + 2, 0, 16, 16, sprite);
            } else {
                g.fill(getX() + 2, getY() + 2, getX() + 18, getY() + 18, 0xFF666666);
            }
            String label = (effect != null
                    ? Component.translatable(effect.getDescriptionId()).getString()
                    : entry.effectId()) + " ×" + (entry.amplifier() + 1);
            g.drawString(font, font.plainSubstrByWidth(label, width - 24),
                    getX() + 22, getY() + 6, TEXT, false);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            defaultButtonNarrationText(out);
        }
    }

    protected class ActionButton extends AbstractWidget {
        private final Runnable action;

        ActionButton(int x, int y, int w, int h, Component label, Runnable action) {
            super(x, y, w, h, label);
            this.action = action;
            attachDesc(this, label);
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            action.run();
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
            boolean hov = isHoveredOrFocused();
            g.fill(getX(), getY(), getX() + width, getY() + height, hov ? 0xFF2A2A32 : 0xFF1A1A22);
            drawGradientBorder(g, getX(), getY(), width, height, hov ? ACCENT : 0xFF9A9AA8, ACCENT_BOTTOM);
            String label = getMessage().getString();
            int maxW = width - 8;
            g.drawCenteredString(font, font.plainSubstrByWidth(label, maxW),
                    getX() + width / 2, getY() + (height - 8) / 2, hov ? TEXT : 0xFFC0C0C8);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            defaultButtonNarrationText(out);
        }
    }

    /** Toggle-style choice button — green fill when selected. */
    protected class SelectButton extends AbstractWidget {
        private final Runnable action;
        private final boolean selected;

        SelectButton(int x, int y, int w, int h, Component label, boolean selected, Runnable action) {
            super(x, y, w, h, label);
            this.selected = selected;
            this.action = action;
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            action.run();
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
            boolean hov = isHoveredOrFocused();
            int bg = selected ? ON : (hov ? ROW_HOV : ROW);
            g.fill(getX(), getY(), getX() + width, getY() + height, bg);
            drawGradientBorder(g, getX(), getY(), width, height,
                    selected || hov ? ACCENT : 0xFF6A6A78, ACCENT_BOTTOM);
            String label = getMessage().getString();
            g.drawCenteredString(font, font.plainSubstrByWidth(label, width - 10),
                    getX() + width / 2, getY() + (height - 8) / 2, TEXT);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            defaultButtonNarrationText(out);
        }
    }

    protected class HintRow extends AbstractWidget {
        private final String hintKey;
        private final Runnable action;

        HintRow(int x, int y, int w, int h, Component title, String hintKey, Runnable action) {
            super(x, y, w, h, title);
            this.hintKey = hintKey;
            this.action = action;
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            action.run();
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
            boolean hov = isHoveredOrFocused();
            g.fill(getX(), getY(), getX() + width, getY() + height, hov ? ROW_HOV : ROW);
            if (hov) {
                g.fill(getX(), getY(), getX() + 2, getY() + height, ACCENT);
            }
            int textMax = Math.max(20, width - 14);
            g.drawString(font, font.plainSubstrByWidth(getMessage().getString(), textMax),
                    getX() + 11, getY() + 5, TEXT, false);
            if (hintKey != null && !hintKey.isEmpty()) {
                String desc = Component.translatable(hintKey).getString();
                drawWrapped(g, desc, getX() + 11, getY() + 16, textMax, MUTED, 2);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            defaultButtonNarrationText(out);
        }
    }

    protected class ColorSwatchButton extends AbstractWidget {
        private final Runnable action;
        private final int swatchRgb;

        ColorSwatchButton(int x, int y, int w, int h, Component label, int swatchRgb, Runnable action) {
            super(x, y, w, h, label);
            this.action = action;
            this.swatchRgb = swatchRgb & 0xFFFFFF;
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            action.run();
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
            boolean hov = isHoveredOrFocused();
            g.fill(getX(), getY(), getX() + width, getY() + height, hov ? 0xFF2A2A32 : 0xFF1A1A22);
            drawGradientBorder(g, getX(), getY(), width, height, hov ? ACCENT : 0xFF9A9AA8, ACCENT_BOTTOM);
            int sx = getX() + 4;
            int sy = getY() + 3;
            g.drawString(font, "\u2666", sx, getY() + (height - 8) / 2, 0xFF000000 | swatchRgb, false);
            g.drawString(font, font.plainSubstrByWidth(getMessage().getString(), Math.max(20, width - 22)),
                    getX() + 18, getY() + (height - 8) / 2, TEXT, false);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            defaultButtonNarrationText(out);
        }
    }

    protected class SectionLabel extends AbstractWidget {
        SectionLabel(int x, int y, int w, Component label) {
            super(x, y, w, 10, label);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
            g.drawString(font, getMessage().getString(), getX(), getY() + 1, MUTED, false);
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            out.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE, getMessage());
        }
    }

    protected void drawWrapped(GuiGraphics g, String text, int x, int y, int maxW, int color, int maxLines) {
        var lines = font.split(Component.literal(text), maxW);
        for (int i = 0; i < Math.min(maxLines, lines.size()); i++) {
            g.drawString(font, lines.get(i), x, y + i * 9, color, false);
        }
    }

    private static void attachDesc(AbstractWidget widget, Component label) {
        if (label.getContents() instanceof TranslatableContents text) {
            String desc = text.getKey() + ".desc";
            if (I18n.exists(desc)) {
                widget.setTooltip(Tooltip.create(Component.translatable(desc)));
            }
        }
    }

    protected class FlagHintBox extends AbstractWidget {
        private final ZoneFlag flag;
        private final String overrideText;

        FlagHintBox(int x, int y, int w, int h, ZoneFlag flag) {
            this(x, y, w, h, flag, null);
        }

        FlagHintBox(int x, int y, int w, int h, ZoneFlag flag, String overrideText) {
            super(x, y, w, h, Component.empty());
            this.flag = flag;
            this.overrideText = overrideText;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
            g.fill(getX(), getY(), getX() + width, getY() + height, ROW);
            g.fill(getX(), getY(), getX() + 2, getY() + height, ACCENT);
            if (overrideText != null) {
                drawWrapped(g, overrideText, getX() + 8, getY() + 4, width - 12, TEXT, 4);
                return;
            }
            String desc = Component.translatable("flag.yblk_zones." + flag.name().toLowerCase() + ".desc")
                    .getString();
            drawWrapped(g, desc, getX() + 8, getY() + 4, width - 12, TEXT, 2);
            if (!flagHasSubsettings(flag)) {
                String extra = Component.translatable("screen.yblk_zones.no_extra").getString();
                g.drawString(font, font.plainSubstrByWidth(extra, width - 12), getX() + 8, getY() + height - 11, MUTED, false);
            }
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            out.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE, getMessage());
        }
    }
}
