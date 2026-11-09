package yblk.zones.zone;

public enum ZoneRole {
    OWNER,
    MANAGER,
    MEMBER,
    VISITOR;

    public boolean canBuild() {
        return this == OWNER || this == MANAGER || this == MEMBER;
    }

    public boolean canInvite() {
        return this == OWNER || this == MANAGER;
    }

    public boolean canEditFlags() {
        return this == OWNER || this == MANAGER;
    }

    public boolean canSellTransfer() {
        return this == OWNER;
    }

    public boolean canStartEvent() {
        return this == OWNER || this == MANAGER;
    }

    public boolean canManageLease() {
        return this == OWNER || this == MANAGER;
    }
}
