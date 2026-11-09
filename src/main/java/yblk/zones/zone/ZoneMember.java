package yblk.zones.zone;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

public final class ZoneMember {
    private UUID id;
    private String name;
    private ZoneRole role;

    public ZoneMember(UUID id, String name, ZoneRole role) {
        this.id = id;
        this.name = name == null ? "" : name;
        this.role = role == null ? ZoneRole.MEMBER : role;
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public ZoneRole role() {
        return role;
    }

    public void setRole(ZoneRole role) {
        this.role = role == null ? ZoneRole.MEMBER : role;
    }

    public void setName(String name) {
        this.name = name == null ? "" : name;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (id != null) {
            tag.putUUID("Id", id);
        }
        tag.putString("Name", name);
        tag.putString("Role", role.name());
        return tag;
    }

    public static ZoneMember load(CompoundTag tag) {
        UUID id = tag.hasUUID("Id") ? tag.getUUID("Id") : null;
        String name = tag.getString("Name");
        ZoneRole role;
        try {
            role = ZoneRole.valueOf(tag.getString("Role"));
        } catch (Exception e) {
            role = ZoneRole.MEMBER;
        }
        return new ZoneMember(id, name, role);
    }

    public ZoneMember copy() {
        return new ZoneMember(id, name, role);
    }
}
