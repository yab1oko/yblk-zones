package yblk.zones.script;

import net.minecraft.nbt.CompoundTag;

/** One scripted action bound to a {@link ZoneTrigger}. */
public final class ZoneAction {
    private ZoneTrigger trigger = ZoneTrigger.ON_ENTER;
    private ZoneActionType type = ZoneActionType.TITLE;
    private String value = "";
    private int intArg;
    private float floatArg = 1.0F;
    private boolean boolArg;

    public ZoneAction() {
    }

    public ZoneAction(ZoneTrigger trigger, ZoneActionType type, String value) {
        this.trigger = trigger;
        this.type = type;
        this.value = value == null ? "" : value;
    }

    public ZoneTrigger trigger() {
        return trigger;
    }

    public void setTrigger(ZoneTrigger trigger) {
        this.trigger = trigger == null ? ZoneTrigger.ON_ENTER : trigger;
    }

    public ZoneActionType type() {
        return type;
    }

    public void setType(ZoneActionType type) {
        this.type = type == null ? ZoneActionType.TITLE : type;
    }

    public String value() {
        return value;
    }

    public void setValue(String value) {
        this.value = value == null ? "" : value;
    }

    public int intArg() {
        return intArg;
    }

    public void setIntArg(int intArg) {
        this.intArg = intArg;
    }

    public float floatArg() {
        return floatArg;
    }

    public void setFloatArg(float floatArg) {
        this.floatArg = floatArg;
    }

    public boolean boolArg() {
        return boolArg;
    }

    public void setBoolArg(boolean boolArg) {
        this.boolArg = boolArg;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Trigger", trigger.name());
        tag.putString("Type", type.name());
        tag.putString("Value", value);
        tag.putInt("Int", intArg);
        tag.putFloat("Float", floatArg);
        tag.putBoolean("Bool", boolArg);
        return tag;
    }

    public static ZoneAction load(CompoundTag tag) {
        ZoneAction a = new ZoneAction();
        try {
            a.trigger = ZoneTrigger.valueOf(tag.getString("Trigger"));
        } catch (Exception ignored) {
        }
        try {
            a.type = ZoneActionType.valueOf(tag.getString("Type"));
        } catch (Exception ignored) {
        }
        a.value = tag.getString("Value");
        a.intArg = tag.getInt("Int");
        a.floatArg = tag.contains("Float") ? tag.getFloat("Float") : 1.0F;
        a.boolArg = tag.getBoolean("Bool");
        return a;
    }

    public ZoneAction copy() {
        ZoneAction a = new ZoneAction(trigger, type, value);
        a.intArg = intArg;
        a.floatArg = floatArg;
        a.boolArg = boolArg;
        return a;
    }
}
