package yblk.zones.zone;

public enum LeaseExpireAction {
    /** Clear owner and CLAIM. */
    UNCLAIM,
    /** Return ownership to landlord (if set), else unclaim. */
    RETURN_LANDLORD,
    /** Keep owner but lock build (CLAIM stays, members cleared to visitor). */
    LOCK
}
