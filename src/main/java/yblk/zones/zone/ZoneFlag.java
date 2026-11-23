package yblk.zones.zone;

/**
 * Independent zone behaviours. Several can be combined on one zone.
 * {@link #VISIBLE} controls world outline; {@link #DISPLAY} controls the HUD name.
 */
public enum ZoneFlag {
    /** World outline / gradient (if off — only when holding the wand). */
    VISIBLE(0x55CCFF),
    /** Show name / level on HUD while inside. */
    DISPLAY(0x66DDFF),
    /** Only owner (and ops) may build / break. */
    CLAIM(0x55FF55),
    /** Adventure players may break blocks here. */
    ADVENTURE_BREAK(0xFFAA00),
    /** Adventure players may place blocks here. */
    ADVENTURE_BUILD(0xFFCC44),
    /** Hostile (or listed) mobs inside get a strength boost. */
    MOB_BUFF(0xFF5555),
    /** Listed mobs cannot enter — solid wall feel. */
    MOB_DENY_ENTRY(0xAA55FF),
    /** Natural / spawner mob spawns blocked. */
    MOB_DENY_SPAWN(0x8855AA),
    /** Periodic player damage. */
    PLAYER_DAMAGE(0xFF2222),
    /** Players cannot hit each other here. */
    DENY_PVP(0xC43B3B),
    /** Explosions do not break blocks or hurt entities here. */
    DENY_EXPLOSIONS(0xE07A2F),
    /** Containers stay shut even without a claim. */
    DENY_CONTAINERS(0x3A7CA5),
    /** Strangers are pushed back out. */
    DENY_ENTRY(0x6B4C9A),
    /** Pearls, chorus fruit, and /tp cannot cross the border. */
    DENY_TELEPORT(0x2F6F6A);

    public final int colorRgb;

    ZoneFlag(int colorRgb) {
        this.colorRgb = colorRgb;
    }
}
