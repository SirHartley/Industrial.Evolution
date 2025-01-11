package indevo.items.consumables.fleet;

public class MissileMemFlags {
    // Generic flags
    public static final String RECENTLY_USED_MISSILE = "$missile_recentlyUsed";
    public static final String HAS_SEEN_PLAYER_USE_MISSILE = "$missile_hasSeenPlayerUse";

    // Once a fleet has encountered the player using missiles,
    // they might use missiles themselves
    public static final String USES_MISSILES_AGAINST_PLAYER = "$missile_nowUsesMissiles";

    // Track offense count so we know if this is the 'second time'
    public static final String OFFENSE_COUNT = "$missile_offenseCount";
    public static final String CONFIRMED_OFFENSE_COUNT = "$missile_confirmedOffenseCount";

    // If fleet wants to confiscate on second offense
    public static final String CONFISCATE_ON_SECOND_OFFENSE = "$missile_confiscateSecondOffense";

}
