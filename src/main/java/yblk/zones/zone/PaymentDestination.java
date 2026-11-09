package yblk.zones.zone;

/** Where purchase/lease items go after a successful buy. */
public enum PaymentDestination {
    /** Give items to the previous owner / landlord if online, else drop or void. */
    SELLER,
    /** Consume items with no recipient. */
    VOID
}
