package yblk.zones.zone;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/** Potion effect applied by zone, resolved via ForgeRegistries.MOB_EFFECTS. */
public final class EffectEntry {
    private String effectId;
    private int amplifier;
    private int durationTicks;
    private boolean whileInside;
    private boolean onEnter;
    private boolean showParticles = true;

    public EffectEntry(String effectId, int amplifier, int durationTicks, boolean whileInside, boolean onEnter) {
        this(effectId, amplifier, durationTicks, whileInside, onEnter, true);
    }

    public EffectEntry(String effectId, int amplifier, int durationTicks, boolean whileInside, boolean onEnter,
                       boolean showParticles) {
        this.effectId = effectId == null ? "" : effectId;
        this.amplifier = Math.max(0, amplifier);
        this.durationTicks = Math.max(1, durationTicks);
        this.whileInside = whileInside;
        this.onEnter = onEnter;
        this.showParticles = showParticles;
    }

    public static EffectEntry of(String id) {
        return new EffectEntry(id, 0, 40, true, false, true);
    }

    public String effectId() {
        return effectId;
    }

    public void setEffectId(String id) {
        this.effectId = id == null ? "" : id;
    }

    public int amplifier() {
        return amplifier;
    }

    public void setAmplifier(int amplifier) {
        this.amplifier = Math.max(0, Math.min(255, amplifier));
    }

    public int durationTicks() {
        return durationTicks;
    }

    public void setDurationTicks(int durationTicks) {
        this.durationTicks = Math.max(1, durationTicks);
    }

    public boolean whileInside() {
        return whileInside;
    }

    public void setWhileInside(boolean whileInside) {
        this.whileInside = whileInside;
    }

    public boolean onEnter() {
        return onEnter;
    }

    public void setOnEnter(boolean onEnter) {
        this.onEnter = onEnter;
    }

    public boolean showParticles() {
        return showParticles;
    }

    public void setShowParticles(boolean showParticles) {
        this.showParticles = showParticles;
    }

    public ResourceLocation id() {
        return ResourceLocation.tryParse(effectId);
    }

    public boolean isValid() {
        return id() != null && !effectId.isBlank();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", effectId);
        tag.putInt("Amp", amplifier);
        tag.putInt("Dur", durationTicks);
        tag.putBoolean("While", whileInside);
        tag.putBoolean("Enter", onEnter);
        tag.putBoolean("ShowParticles", showParticles);
        return tag;
    }

    public static EffectEntry load(CompoundTag tag) {
        return new EffectEntry(
                tag.getString("Id"),
                tag.getInt("Amp"),
                tag.contains("Dur") ? tag.getInt("Dur") : 40,
                !tag.contains("While") || tag.getBoolean("While"),
                tag.getBoolean("Enter"),
                !tag.contains("ShowParticles") || tag.getBoolean("ShowParticles"));
    }

    public EffectEntry copy() {
        return new EffectEntry(effectId, amplifier, durationTicks, whileInside, onEnter, showParticles);
    }
}
