package yblk.zones.script;

/**
 * Script action kinds.
 * <ul>
 *   <li>{@code value} — main id/text (item, mob, sound, effect, message, command, flag)</li>
 *   <li>{@code intArg} — count / duration ticks / XP amount</li>
 *   <li>{@code floatArg} — amplifier / volume / heal hearts</li>
 *   <li>{@code boolArg} — extra toggle (chat title, deny exit on, flag on)</li>
 * </ul>
 */
public enum ZoneActionType {
    /** Potion effect: value=id, intArg=duration ticks, floatArg=amplifier */
    EFFECT,
    /** Spawn mobs in zone: value=entity id, intArg=count */
    SPAWN,
    /** Play sound: value=sound id, floatArg=volume */
    SOUND,
    /** Particles in zone: value=particle id, intArg=count */
    PARTICLE,
    /** Actionbar / title message: value=text, boolArg=also send chat */
    TITLE,
    /** Run command (needs Allow commands): value=command without / */
    COMMAND,
    /** Lock / unlock leaving the zone: boolArg=true to deny */
    DENY_EXIT,
    /** Remove mobs inside the zone */
    CLEAR_MOBS,
    /** Toggle a zone flag: value=FLAG_NAME or FLAG=true/false */
    FLAG_SET,
    /** Give item: value=item id, intArg=count */
    GIVE_ITEM,
    /** Give experience points: intArg=xp */
    GIVE_XP,
    /** Heal player: floatArg=hearts (1 = half-heart in MC is 1 damage unit; we use hearts*2) */
    HEAL
}
