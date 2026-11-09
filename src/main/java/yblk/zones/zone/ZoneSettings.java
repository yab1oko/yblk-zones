package yblk.zones.zone;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Per-flag extras: mobs, damage, claim trust, outline, ambience, effects. */
public final class ZoneSettings {
    public enum DamageKind {
        MAGIC, WITHER, GENERIC, FIRE, SWEET_BERRY, THORNS
    }

    /** FULL = lines+cubes+hatch; HATCH = soft hatch only; HIDDEN = nothing. */
    public enum OutlineStyle {
        FULL, HATCH, HIDDEN
    }

    public enum OutlineProximity {
        ALWAYS, NEAR_REVEAL, NEAR_HIDE
    }

    public enum HatchProximity {
        ALWAYS, NEAR_REVEAL, NEAR_HIDE
    }

    /** Which mobs Mob wall / Mob buff apply to. CUSTOM uses the id set. */
    public enum MobGroup {
        HOSTILE, PASSIVE, NEUTRAL, CUSTOM
    }

    public enum HudMode {
        TEXT, IMAGE, BOTH
    }

    /** How HUD images are sized relative to {@link #hudImageSize}. */
    public enum HudImageFit {
        /** Keep aspect ratio; longest edge = hudImageSize. */
        FIT,
        /** Stretch to a hudImageSize × hudImageSize square. */
        STRETCH,
        /** Original pixel size × (hudImageSize / 32). */
        NATIVE
    }

    /** Client-local sky time while inside the zone (does not change server time). */
    public enum SkyMode {
        DEFAULT(0L),
        DAY(6000L),
        NIGHT(18000L),
        DAWN(23000L),
        DUSK(13000L);

        public final long dayTimeOffset;

        SkyMode(long dayTimeOffset) {
            this.dayTimeOffset = dayTimeOffset;
        }
    }

    /** Client-local weather while inside the zone. */
    public enum WeatherMode {
        DEFAULT, CLEAR, RAIN, THUNDER
    }

    private static final int[] COLOR_PRESETS =
            {0x55CCFF, 0x55FF55, 0xFF5555, 0xFFAA00, 0xAA55FF, 0xFFFFFF, 0xFF55FF};

    private static final String[] SOUND_PRESETS = {
            "",
            "minecraft:ambient.cave",
            "minecraft:block.note_block.chime",
            "minecraft:entity.experience_orb.pickup",
            "minecraft:ui.toast.in",
            "minecraft:block.beacon.ambient",
    };

    private static final String[] PARTICLE_PRESETS = {
            "",
            "minecraft:end_rod",
            "minecraft:flame",
            "minecraft:smoke",
            "minecraft:cloud",
            "minecraft:heart",
            "minecraft:happy_villager",
            "minecraft:angry_villager",
            "minecraft:portal",
            "minecraft:witch",
            "minecraft:note",
            "minecraft:enchant",
            "minecraft:crit",
            "minecraft:enchanted_hit",
            "minecraft:firework",
            "minecraft:totem_of_undying",
            "minecraft:snowflake",
            "minecraft:drip_water",
            "minecraft:drip_lava",
            "minecraft:ash",
            "minecraft:white_ash",
            "minecraft:spore_blossom_air",
            "minecraft:cherry_leaves",
            "minecraft:glow",
            "minecraft:electric_spark",
            "minecraft:scrape",
            "minecraft:wax_on",
            "minecraft:wax_off",
            "minecraft:reverse_portal",
            "minecraft:crimson_spore",
            "minecraft:warped_spore",
            "minecraft:soul",
            "minecraft:soul_fire_flame",
            "minecraft:dripping_obsidian_tear",
    };

    private final Set<ResourceLocation> denyMobs = new LinkedHashSet<>();
    private final Set<ResourceLocation> buffMobs = new LinkedHashSet<>();
    private MobGroup denyMobGroup = MobGroup.HOSTILE;
    private MobGroup buffMobGroup = MobGroup.HOSTILE;
    private DamageKind damageKind = DamageKind.MAGIC;
    private float damageAmount = 1.0F;
    private int damageInterval = 40;
    private boolean damageWither = true;
    private int buffAmplifier = 0;
    /** 0 = off; 1–5 = chest loot quantity/quality inside the zone. */
    private int harvestLevel = 0;
    private OutlineStyle outlineStyle = OutlineStyle.FULL;
    private int outlineColor = 0x55CCFF;
    private OutlineProximity outlineProximity = OutlineProximity.ALWAYS;
    private float proximityRadius = 3.5F;
    private HatchProximity hatchProximity = HatchProximity.ALWAYS;
    private float hatchApproachDistance = 7.0F;
    /** How tall the hatch/gradient band is (blocks). */
    private float hatchHeight = 2.5F;
    private int eventDurationTicks = 6000;
    private int eventMinTicks;
    private int eventCooldownTicks = 1200;
    private String eventSpawnMob = "";
    private int eventSpawnCount;
    private int eventSpawnInterval = 100;
    /** 0 = timed interval only; >0 = spawn this many waves, next wave when previous cleared. */
    private int eventWaves;
    /** When true, clearing the last wave (or sole spawn) finishes the event → ON_EVENT_END rewards. */
    private boolean eventWinOnClear;
    /** Trusted player names (case-insensitive). Legacy — also mirrored into members. */
    private final Set<String> trustedNames = new LinkedHashSet<>();
    private final Set<UUID> trustedIds = new LinkedHashSet<>();
    /** Scoreboard team name; empty = unused. */
    private String trustedTeam = "";

    private final List<EffectEntry> effects = new ArrayList<>();
    private String enterTitle = "";
    private String enterSubtitle = "";
    private String enterSound = "";
    private float enterSoundVolume = 1.0F;
    private String loopSound = "";
    private float loopSoundVolume = 0.4F;
    private String particleInterior = "";
    private String particleBorder = "";
    private int particleCount = 12;
    private int particleRate = 10;
    private HudMode hudMode = HudMode.TEXT;
    private int hudColor = 0x55CCFF;
    private float hudScale = 1.0F;
    private String hudImage = "";
    private String hudText = "";
    /** Pixel size of HUD image/icon (8–128). Meaning depends on {@link #hudImageFit}. */
    private int hudImageSize = 32;
    private HudImageFit hudImageFit = HudImageFit.FIT;
    /** Seconds after entering the zone before name/image hide. 0 = always show. */
    private int hudFadeSeconds = 0;
    private SkyMode skyMode = SkyMode.DEFAULT;
    private WeatherMode weatherMode = WeatherMode.DEFAULT;
    /** Used while the zone event is active; DEFAULT = fall back to ambient sky/weather. */
    private SkyMode eventSkyMode = SkyMode.DEFAULT;
    private WeatherMode eventWeatherMode = WeatherMode.DEFAULT;

    public Set<ResourceLocation> denyMobs() {
        return denyMobs;
    }

    public Set<ResourceLocation> buffMobs() {
        return buffMobs;
    }

    public MobGroup denyMobGroup() {
        return denyMobGroup;
    }

    public void setDenyMobGroup(MobGroup group) {
        this.denyMobGroup = group == null ? MobGroup.HOSTILE : group;
    }

    public void cycleDenyMobGroup() {
        MobGroup[] all = MobGroup.values();
        denyMobGroup = all[(denyMobGroup.ordinal() + 1) % all.length];
    }

    public MobGroup buffMobGroup() {
        return buffMobGroup;
    }

    public void setBuffMobGroup(MobGroup group) {
        this.buffMobGroup = group == null ? MobGroup.HOSTILE : group;
    }

    public void cycleBuffMobGroup() {
        MobGroup[] all = MobGroup.values();
        buffMobGroup = all[(buffMobGroup.ordinal() + 1) % all.length];
    }

    public DamageKind damageKind() {
        return damageKind;
    }

    public void setDamageKind(DamageKind kind) {
        this.damageKind = kind == null ? DamageKind.MAGIC : kind;
    }

    public float damageAmount() {
        return damageAmount;
    }

    public void setDamageAmount(float amount) {
        this.damageAmount = Math.max(0F, amount);
    }

    public int damageInterval() {
        return damageInterval;
    }

    public void setDamageInterval(int ticks) {
        this.damageInterval = Math.max(5, ticks);
    }

    public boolean damageWither() {
        return damageWither;
    }

    public void setDamageWither(boolean on) {
        this.damageWither = on;
    }

    public int buffAmplifier() {
        return buffAmplifier;
    }

    public void setBuffAmplifier(int amp) {
        this.buffAmplifier = Math.max(0, Math.min(4, amp));
    }

    public int harvestLevel() {
        return harvestLevel;
    }

    public void setHarvestLevel(int level) {
        this.harvestLevel = Math.max(0, Math.min(5, level));
    }

    public void cycleHarvestLevel() {
        harvestLevel = (harvestLevel + 1) % 6;
    }

    public OutlineStyle outlineStyle() {
        return outlineStyle;
    }

    public void setOutlineStyle(OutlineStyle style) {
        this.outlineStyle = style == null ? OutlineStyle.FULL : style;
    }

    public void cycleOutlineStyle() {
        OutlineStyle[] all = OutlineStyle.values();
        outlineStyle = all[(outlineStyle.ordinal() + 1) % all.length];
    }

    public int outlineColor() {
        return outlineColor & 0xFFFFFF;
    }

    public void setOutlineColor(int rgb) {
        this.outlineColor = rgb & 0xFFFFFF;
    }

    public void cycleOutlineColor() {
        outlineColor = cycleColorPreset(outlineColor());
    }

    public HudMode hudMode() {
        return hudMode;
    }

    public void setHudMode(HudMode mode) {
        this.hudMode = mode == null ? HudMode.TEXT : mode;
    }

    public void cycleHudMode() {
        HudMode[] all = HudMode.values();
        hudMode = all[(hudMode.ordinal() + 1) % all.length];
    }

    public int hudColor() {
        return hudColor & 0xFFFFFF;
    }

    public void setHudColor(int rgb) {
        this.hudColor = rgb & 0xFFFFFF;
    }

    public void cycleHudColor() {
        hudColor = cycleColorPreset(hudColor());
    }

    public float hudScale() {
        return hudScale;
    }

    public void setHudScale(float scale) {
        this.hudScale = Mth.clamp(scale, 0.5F, 3.0F);
    }

    public void cycleHudScale() {
        float next = hudScale + 0.25F;
        if (next > 3.0F) {
            next = 0.5F;
        }
        hudScale = next;
    }

    public String hudImage() {
        return hudImage;
    }

    public void setHudImage(String path) {
        this.hudImage = path == null ? "" : path;
    }

    public int hudImageSize() {
        return hudImageSize;
    }

    public void setHudImageSize(int size) {
        this.hudImageSize = Mth.clamp(size, 8, 128);
    }

    public void cycleHudImageSize() {
        int next = hudImageSize + 8;
        if (next > 96) {
            next = 16;
        }
        hudImageSize = next;
    }

    public HudImageFit hudImageFit() {
        return hudImageFit;
    }

    public void setHudImageFit(HudImageFit fit) {
        this.hudImageFit = fit == null ? HudImageFit.FIT : fit;
    }

    public void cycleHudImageFit() {
        HudImageFit[] all = HudImageFit.values();
        hudImageFit = all[(hudImageFit.ordinal() + 1) % all.length];
    }

    public int hudFadeSeconds() {
        return hudFadeSeconds;
    }

    public void setHudFadeSeconds(int seconds) {
        this.hudFadeSeconds = Mth.clamp(seconds, 0, 120);
    }

    public void cycleHudFadeSeconds() {
        int[] steps = {0, 3, 5, 8, 10, 15, 30, 60};
        int idx = 0;
        for (int i = 0; i < steps.length; i++) {
            if (steps[i] == hudFadeSeconds) {
                idx = i;
                break;
            }
        }
        hudFadeSeconds = steps[(idx + 1) % steps.length];
    }

    public SkyMode skyMode() {
        return skyMode;
    }

    public void setSkyMode(SkyMode mode) {
        this.skyMode = mode == null ? SkyMode.DEFAULT : mode;
    }

    public void cycleSkyMode() {
        SkyMode[] all = SkyMode.values();
        skyMode = all[(skyMode.ordinal() + 1) % all.length];
    }

    public WeatherMode weatherMode() {
        return weatherMode;
    }

    public void setWeatherMode(WeatherMode mode) {
        this.weatherMode = mode == null ? WeatherMode.DEFAULT : mode;
    }

    public void cycleWeatherMode() {
        WeatherMode[] all = WeatherMode.values();
        weatherMode = all[(weatherMode.ordinal() + 1) % all.length];
    }

    public SkyMode eventSkyMode() {
        return eventSkyMode;
    }

    public void setEventSkyMode(SkyMode mode) {
        this.eventSkyMode = mode == null ? SkyMode.DEFAULT : mode;
    }

    public void cycleEventSkyMode() {
        SkyMode[] all = SkyMode.values();
        eventSkyMode = all[(eventSkyMode.ordinal() + 1) % all.length];
    }

    public WeatherMode eventWeatherMode() {
        return eventWeatherMode;
    }

    public void setEventWeatherMode(WeatherMode mode) {
        this.eventWeatherMode = mode == null ? WeatherMode.DEFAULT : mode;
    }

    public void cycleEventWeatherMode() {
        WeatherMode[] all = WeatherMode.values();
        eventWeatherMode = all[(eventWeatherMode.ordinal() + 1) % all.length];
    }

    public String hudText() {
        return hudText;
    }

    public void setHudText(String text) {
        this.hudText = text == null ? "" : text;
    }

    public void cycleEnterSound() {
        enterSound = cycleStringPreset(enterSound, SOUND_PRESETS);
    }

    public void cycleLoopSound() {
        loopSound = cycleStringPreset(loopSound, SOUND_PRESETS);
    }

    public void cycleParticleInterior() {
        particleInterior = cycleStringPreset(particleInterior, PARTICLE_PRESETS);
    }

    public void cycleParticleBorder() {
        particleBorder = cycleStringPreset(particleBorder, PARTICLE_PRESETS);
    }

    public OutlineProximity outlineProximity() {
        return outlineProximity;
    }

    public void setOutlineProximity(OutlineProximity mode) {
        this.outlineProximity = mode == null ? OutlineProximity.ALWAYS : mode;
    }

    public void cycleOutlineProximity() {
        OutlineProximity[] all = OutlineProximity.values();
        outlineProximity = all[(outlineProximity.ordinal() + 1) % all.length];
    }

    public float proximityRadius() {
        return proximityRadius;
    }

    public void setProximityRadius(float radius) {
        this.proximityRadius = Mth.clamp(radius, 1.5F, 8.0F);
    }

    public void cycleProximityRadius() {
        float next = proximityRadius + 0.5F;
        if (next > 8.0F) {
            next = 1.5F;
        }
        proximityRadius = next;
    }

    public HatchProximity hatchProximity() {
        return hatchProximity;
    }

    public void setHatchProximity(HatchProximity mode) {
        this.hatchProximity = mode == null ? HatchProximity.ALWAYS : mode;
    }

    public void cycleHatchProximity() {
        HatchProximity[] all = HatchProximity.values();
        hatchProximity = all[(hatchProximity.ordinal() + 1) % all.length];
    }

    public int eventDurationTicks() {
        return eventDurationTicks;
    }

    public void setEventDurationTicks(int ticks) {
        this.eventDurationTicks = Math.max(0, ticks);
    }

    public int eventMinTicks() {
        return eventMinTicks;
    }

    public void setEventMinTicks(int ticks) {
        this.eventMinTicks = Math.max(0, ticks);
    }

    public int eventCooldownTicks() {
        return eventCooldownTicks;
    }

    public void setEventCooldownTicks(int ticks) {
        this.eventCooldownTicks = Math.max(0, ticks);
    }

    public String eventSpawnMob() {
        return eventSpawnMob;
    }

    public void setEventSpawnMob(String id) {
        this.eventSpawnMob = id == null ? "" : id.trim();
    }

    public int eventSpawnCount() {
        return eventSpawnCount;
    }

    public void setEventSpawnCount(int count) {
        this.eventSpawnCount = Math.max(0, Math.min(32, count));
    }

    public int eventSpawnInterval() {
        return eventSpawnInterval;
    }

    public void setEventSpawnInterval(int ticks) {
        this.eventSpawnInterval = Math.max(20, ticks);
    }

    public int eventWaves() {
        return eventWaves;
    }

    public void setEventWaves(int waves) {
        this.eventWaves = Math.max(0, Math.min(50, waves));
    }

    public boolean eventWinOnClear() {
        return eventWinOnClear;
    }

    public void setEventWinOnClear(boolean eventWinOnClear) {
        this.eventWinOnClear = eventWinOnClear;
    }

    public float hatchApproachDistance() {
        return hatchApproachDistance;
    }

    public void setHatchApproachDistance(float distance) {
        this.hatchApproachDistance = Mth.clamp(distance, 2.0F, 32.0F);
    }

    public void cycleHatchApproach() {
        float next = hatchApproachDistance + 1.0F;
        if (next > 12.0F) {
            next = 4.0F;
        }
        hatchApproachDistance = next;
    }

    public float hatchHeight() {
        return hatchHeight;
    }

    public void setHatchHeight(float height) {
        this.hatchHeight = Mth.clamp(height, 0.5F, 16.0F);
    }

    public Set<String> trustedNames() {
        return trustedNames;
    }

    public Set<UUID> trustedIds() {
        return trustedIds;
    }

    public String trustedTeam() {
        return trustedTeam;
    }

    public void setTrustedTeam(String team) {
        this.trustedTeam = team == null ? "" : team;
    }

    public void addTrustedName(String name) {
        if (name != null && !name.isBlank()) {
            trustedNames.add(name.trim());
        }
    }

    public void removeTrustedName(String name) {
        trustedNames.removeIf(n -> n.equalsIgnoreCase(name));
    }

    public boolean isTrusted(Player player) {
        if (player == null) {
            return false;
        }
        if (trustedIds.contains(player.getUUID())) {
            return true;
        }
        String name = player.getGameProfile().getName();
        for (String n : trustedNames) {
            if (n.equalsIgnoreCase(name)) {
                return true;
            }
        }
        if (!trustedTeam.isEmpty() && player.getScoreboard() != null) {
            PlayerTeam team = player.getScoreboard().getPlayersTeam(name);
            return team != null && trustedTeam.equalsIgnoreCase(team.getName());
        }
        return false;
    }

    public List<EffectEntry> effects() {
        return effects;
    }

    public String enterTitle() {
        return enterTitle;
    }

    public void setEnterTitle(String title) {
        this.enterTitle = title == null ? "" : title;
    }

    public String enterSubtitle() {
        return enterSubtitle;
    }

    public void setEnterSubtitle(String subtitle) {
        this.enterSubtitle = subtitle == null ? "" : subtitle;
    }

    public String enterSound() {
        return enterSound;
    }

    public void setEnterSound(String sound) {
        this.enterSound = sound == null ? "" : sound;
    }

    public float enterSoundVolume() {
        return enterSoundVolume;
    }

    public void setEnterSoundVolume(float volume) {
        this.enterSoundVolume = Math.max(0F, Math.min(2F, volume));
    }

    public String loopSound() {
        return loopSound;
    }

    public void setLoopSound(String sound) {
        this.loopSound = sound == null ? "" : sound;
    }

    public float loopSoundVolume() {
        return loopSoundVolume;
    }

    public void setLoopSoundVolume(float volume) {
        this.loopSoundVolume = Math.max(0F, Math.min(2F, volume));
    }

    public String particleInterior() {
        return particleInterior;
    }

    public void setParticleInterior(String id) {
        this.particleInterior = id == null ? "" : id;
    }

    public String particleBorder() {
        return particleBorder;
    }

    public void setParticleBorder(String id) {
        this.particleBorder = id == null ? "" : id;
    }

    public int particleCount() {
        return particleCount;
    }

    public void setParticleCount(int count) {
        this.particleCount = Math.max(0, Math.min(64, count));
    }

    public int particleRate() {
        return particleRate;
    }

    public void setParticleRate(int rate) {
        this.particleRate = Math.max(1, Math.min(100, rate));
    }

    public boolean denies(EntityType<?> type) {
        ResourceLocation id = EntityType.getKey(type);
        return id != null && denyMobs.contains(id);
    }

    public boolean buffs(EntityType<?> type) {
        ResourceLocation id = EntityType.getKey(type);
        return id != null && buffMobs.contains(id);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("DenyMobs", ids(denyMobs));
        tag.put("BuffMobs", ids(buffMobs));
        tag.putString("DenyMobGroup", denyMobGroup.name());
        tag.putString("BuffMobGroup", buffMobGroup.name());
        tag.putString("DamageKind", damageKind.name());
        tag.putFloat("DamageAmount", damageAmount);
        tag.putInt("DamageInterval", damageInterval);
        tag.putBoolean("DamageWither", damageWither);
        tag.putInt("BuffAmp", buffAmplifier);
        tag.putInt("HarvestLevel", harvestLevel);
        tag.putString("Outline", outlineStyle.name());
        tag.putInt("OutlineColor", outlineColor());
        tag.putString("OutlineProximity", outlineProximity.name());
        tag.putFloat("ProximityRadius", proximityRadius);
        tag.putString("HatchProximity", hatchProximity.name());
        tag.putFloat("HatchApproach", hatchApproachDistance);
        tag.putFloat("HatchHeight", hatchHeight);
        tag.putInt("EventDuration", eventDurationTicks);
        tag.putInt("EventMin", eventMinTicks);
        tag.putInt("EventCooldown", eventCooldownTicks);
        tag.putString("EventSpawnMob", eventSpawnMob);
        tag.putInt("EventSpawnCount", eventSpawnCount);
        tag.putInt("EventSpawnInterval", eventSpawnInterval);
        tag.putInt("EventWaves", eventWaves);
        tag.putBoolean("EventWinOnClear", eventWinOnClear);
        tag.putString("TrustedTeam", trustedTeam);
        ListTag names = new ListTag();
        for (String n : trustedNames) {
            names.add(StringTag.valueOf(n));
        }
        tag.put("TrustedNames", names);
        ListTag uuids = new ListTag();
        for (UUID id : trustedIds) {
            uuids.add(StringTag.valueOf(id.toString()));
        }
        tag.put("TrustedIds", uuids);
        ListTag effectList = new ListTag();
        for (EffectEntry e : effects) {
            effectList.add(e.save());
        }
        tag.put("Effects", effectList);
        tag.putString("EnterTitle", enterTitle);
        tag.putString("EnterSubtitle", enterSubtitle);
        tag.putString("EnterSound", enterSound);
        tag.putFloat("EnterSoundVol", enterSoundVolume);
        tag.putString("LoopSound", loopSound);
        tag.putFloat("LoopSoundVol", loopSoundVolume);
        tag.putString("ParticleInterior", particleInterior);
        tag.putString("ParticleBorder", particleBorder);
        tag.putInt("ParticleCount", particleCount);
        tag.putInt("ParticleRate", particleRate);
        tag.putString("HudMode", hudMode.name());
        tag.putInt("HudColor", hudColor());
        tag.putFloat("HudScale", hudScale);
        tag.putString("HudImage", hudImage);
        tag.putString("HudText", hudText);
        tag.putInt("HudImageSize", hudImageSize);
        tag.putString("HudImageFit", hudImageFit.name());
        tag.putInt("HudFadeSeconds", hudFadeSeconds);
        tag.putString("SkyMode", skyMode.name());
        tag.putString("WeatherMode", weatherMode.name());
        tag.putString("EventSkyMode", eventSkyMode.name());
        tag.putString("EventWeatherMode", eventWeatherMode.name());
        return tag;
    }

    public void load(CompoundTag tag) {
        denyMobs.clear();
        buffMobs.clear();
        trustedNames.clear();
        trustedIds.clear();
        effects.clear();
        readIds(tag.getList("DenyMobs", Tag.TAG_STRING), denyMobs);
        readIds(tag.getList("BuffMobs", Tag.TAG_STRING), buffMobs);
        try {
            denyMobGroup = MobGroup.valueOf(tag.getString("DenyMobGroup"));
        } catch (Exception e) {
            denyMobGroup = denyMobs.isEmpty() ? MobGroup.HOSTILE : MobGroup.CUSTOM;
        }
        try {
            buffMobGroup = MobGroup.valueOf(tag.getString("BuffMobGroup"));
        } catch (Exception e) {
            buffMobGroup = buffMobs.isEmpty() ? MobGroup.HOSTILE : MobGroup.CUSTOM;
        }
        try {
            damageKind = DamageKind.valueOf(tag.getString("DamageKind"));
        } catch (Exception e) {
            damageKind = DamageKind.MAGIC;
        }
        if (tag.contains("DamageAmount")) {
            damageAmount = tag.getFloat("DamageAmount");
        }
        if (tag.contains("DamageInterval")) {
            damageInterval = tag.getInt("DamageInterval");
        }
        damageWither = !tag.contains("DamageWither") || tag.getBoolean("DamageWither");
        buffAmplifier = tag.getInt("BuffAmp");
        harvestLevel = tag.contains("HarvestLevel") ? tag.getInt("HarvestLevel") : 0;
        try {
            outlineStyle = OutlineStyle.valueOf(tag.getString("Outline"));
        } catch (Exception e) {
            outlineStyle = OutlineStyle.FULL;
        }
        if (tag.contains("OutlineColor")) {
            outlineColor = tag.getInt("OutlineColor");
        }
        try {
            outlineProximity = OutlineProximity.valueOf(tag.getString("OutlineProximity"));
        } catch (Exception e) {
            outlineProximity = OutlineProximity.ALWAYS;
        }
        if (tag.contains("ProximityRadius")) {
            setProximityRadius(tag.getFloat("ProximityRadius"));
        }
        try {
            String hp = tag.getString("HatchProximity");
            if ("ON_APPROACH".equals(hp)) {
                hatchProximity = HatchProximity.NEAR_REVEAL;
            } else {
                hatchProximity = HatchProximity.valueOf(hp);
            }
        } catch (Exception e) {
            hatchProximity = HatchProximity.ALWAYS;
        }
        if (tag.contains("HatchApproach")) {
            setHatchApproachDistance(tag.getFloat("HatchApproach"));
        }
        if (tag.contains("HatchHeight")) {
            setHatchHeight(tag.getFloat("HatchHeight"));
        }
        if (tag.contains("EventDuration")) {
            eventDurationTicks = tag.getInt("EventDuration");
        }
        if (tag.contains("EventMin")) {
            eventMinTicks = tag.getInt("EventMin");
        }
        if (tag.contains("EventCooldown")) {
            eventCooldownTicks = tag.getInt("EventCooldown");
        }
        eventSpawnMob = tag.getString("EventSpawnMob");
        if (tag.contains("EventSpawnCount")) {
            eventSpawnCount = tag.getInt("EventSpawnCount");
        }
        if (tag.contains("EventSpawnInterval")) {
            eventSpawnInterval = Math.max(20, tag.getInt("EventSpawnInterval"));
        }
        if (tag.contains("EventWaves")) {
            eventWaves = Math.max(0, tag.getInt("EventWaves"));
        }
        eventWinOnClear = tag.getBoolean("EventWinOnClear");
        trustedTeam = tag.getString("TrustedTeam");
        ListTag names = tag.getList("TrustedNames", Tag.TAG_STRING);
        for (int i = 0; i < names.size(); i++) {
            trustedNames.add(names.getString(i));
        }
        ListTag uuids = tag.getList("TrustedIds", Tag.TAG_STRING);
        for (int i = 0; i < uuids.size(); i++) {
            try {
                trustedIds.add(UUID.fromString(uuids.getString(i)));
            } catch (Exception ignored) {
            }
        }
        if (tag.contains("Effects", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Effects", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                EffectEntry e = EffectEntry.load(list.getCompound(i));
                if (e.isValid()) {
                    effects.add(e);
                }
            }
        }
        enterTitle = tag.getString("EnterTitle");
        enterSubtitle = tag.getString("EnterSubtitle");
        enterSound = tag.getString("EnterSound");
        if (tag.contains("EnterSoundVol")) {
            enterSoundVolume = tag.getFloat("EnterSoundVol");
        }
        loopSound = tag.getString("LoopSound");
        if (tag.contains("LoopSoundVol")) {
            loopSoundVolume = tag.getFloat("LoopSoundVol");
        }
        particleInterior = tag.getString("ParticleInterior");
        particleBorder = tag.getString("ParticleBorder");
        if (tag.contains("ParticleCount")) {
            particleCount = tag.getInt("ParticleCount");
        }
        if (tag.contains("ParticleRate")) {
            particleRate = tag.getInt("ParticleRate");
        }
        try {
            hudMode = HudMode.valueOf(tag.getString("HudMode"));
        } catch (Exception e) {
            hudMode = HudMode.TEXT;
        }
        if (tag.contains("HudColor")) {
            hudColor = tag.getInt("HudColor");
        }
        if (tag.contains("HudScale")) {
            setHudScale(tag.getFloat("HudScale"));
        }
        hudImage = tag.getString("HudImage");
        hudText = tag.getString("HudText");
        if (tag.contains("HudImageSize")) {
            setHudImageSize(tag.getInt("HudImageSize"));
        }
        try {
            hudImageFit = HudImageFit.valueOf(tag.getString("HudImageFit"));
        } catch (Exception e) {
            hudImageFit = HudImageFit.FIT;
        }
        if (tag.contains("HudFadeSeconds")) {
            setHudFadeSeconds(tag.getInt("HudFadeSeconds"));
        }
        try {
            skyMode = SkyMode.valueOf(tag.getString("SkyMode"));
        } catch (Exception e) {
            skyMode = SkyMode.DEFAULT;
        }
        try {
            weatherMode = WeatherMode.valueOf(tag.getString("WeatherMode"));
        } catch (Exception e) {
            weatherMode = WeatherMode.DEFAULT;
        }
        try {
            eventSkyMode = SkyMode.valueOf(tag.getString("EventSkyMode"));
        } catch (Exception e) {
            eventSkyMode = SkyMode.DEFAULT;
        }
        try {
            eventWeatherMode = WeatherMode.valueOf(tag.getString("EventWeatherMode"));
        } catch (Exception e) {
            eventWeatherMode = WeatherMode.DEFAULT;
        }
    }

    /** Apply partial template keys without wiping unrelated fields. */
    public void loadPartialTemplate(CompoundTag tag) {
        if (tag.contains("Outline")) {
            try {
                outlineStyle = OutlineStyle.valueOf(tag.getString("Outline"));
            } catch (Exception ignored) {
            }
        }
        if (tag.contains("OutlineColor")) {
            outlineColor = tag.getInt("OutlineColor");
        }
        if (tag.contains("OutlineProximity")) {
            try {
                outlineProximity = OutlineProximity.valueOf(tag.getString("OutlineProximity"));
            } catch (Exception ignored) {
            }
        }
        if (tag.contains("ProximityRadius")) {
            setProximityRadius(tag.getFloat("ProximityRadius"));
        }
        if (tag.contains("HatchProximity")) {
            try {
                String hp = tag.getString("HatchProximity");
                if ("ON_APPROACH".equals(hp)) {
                    hatchProximity = HatchProximity.NEAR_REVEAL;
                } else {
                    hatchProximity = HatchProximity.valueOf(hp);
                }
            } catch (Exception ignored) {
            }
        }
        if (tag.contains("HatchApproach")) {
            setHatchApproachDistance(tag.getFloat("HatchApproach"));
        }
        if (tag.contains("HatchHeight")) {
            setHatchHeight(tag.getFloat("HatchHeight"));
        }
        if (tag.contains("HarvestLevel")) {
            setHarvestLevel(tag.getInt("HarvestLevel"));
        }
        if (tag.contains("EnterTitle")) {
            enterTitle = tag.getString("EnterTitle");
        }
        if (tag.contains("EnterSound")) {
            enterSound = tag.getString("EnterSound");
        }
    }

    private static ListTag ids(Set<ResourceLocation> set) {
        ListTag list = new ListTag();
        for (ResourceLocation id : set) {
            list.add(StringTag.valueOf(id.toString()));
        }
        return list;
    }

    private static void readIds(ListTag list, Set<ResourceLocation> out) {
        for (int i = 0; i < list.size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(list.getString(i));
            if (id != null) {
                out.add(id);
            }
        }
    }

    private static int cycleColorPreset(int cur) {
        int idx = 0;
        for (int i = 0; i < COLOR_PRESETS.length; i++) {
            if (COLOR_PRESETS[i] == cur) {
                idx = i;
                break;
            }
        }
        return COLOR_PRESETS[(idx + 1) % COLOR_PRESETS.length];
    }

    private static String cycleStringPreset(String current, String[] presets) {
        int idx = -1;
        String cur = current == null ? "" : current;
        for (int i = 0; i < presets.length; i++) {
            if (presets[i].equals(cur)) {
                idx = i;
                break;
            }
        }
        return presets[(idx + 1) % presets.length];
    }
}
