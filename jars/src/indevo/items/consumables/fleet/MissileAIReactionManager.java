package indevo.items.consumables.fleet;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.util.Misc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles AI reactions to player missile usage:
 * <p>
 * - If transponder off:
 * 1) If seen firing but no friendly hit: chase & demand identification
 * (rep loss if caught), demand fine or bribe; if it's the second time, confiscate.
 * 2) If missile hits friendly: chase, demand identification. If identification is confirmed,
 * they go hostile and engage in combat.
 * <p>
 * - If transponder on:
 * 1) Seen fired, no hit: rep loss, chase, confiscate or fine.
 * 2) Seen fired, hits friendly: immediate hostility, engage, request backup.
 * <p>
 * - In all cases:
 * - Other Fleets now also use missiles (in this system).
 * - If a fleet ignores transponders, it ignores missile usage too (except may demand bribe).
 */
public class MissileAIReactionManager {

    public static float DEFAULT_MISSILE_USE_TIMEOUT_DAYS = 30f;
    public static float OTHER_FLEET_FORGET_SEEN_MISSILE_USE = 62f;
    public static float FACTION_USES_MISSILES_TIME = 91f;

    public static final float REP_LOSS_PLAYER_KNOWN = -0.15f;
    public static final float REP_LOSS_PLAYER_NOT_KNOWN = -0.02f;

    public static void reportFleetUsedMissile(CampaignFleetAPI offenderFleet, String missileType) {
        if (offenderFleet == null) return;

        MemoryAPI mem = offenderFleet.getMemoryWithoutUpdate();
        mem.set(MissileMemFlags.RECENTLY_USED_MISSILE, true, DEFAULT_MISSILE_USE_TIMEOUT_DAYS);

        Map<String, Boolean> factionMap = new HashMap<>();

        //iterate nearby fleets
        for (CampaignFleetAPI otherFleet : Misc.getVisibleFleets(offenderFleet, false)) {

            makeFleetRespondToMissileFire(otherFleet, offenderFleet);

            //rep loss due to missile fire if needed
            if (Misc.caresAboutPlayerTransponder(otherFleet)) {
                String factionId = otherFleet.getFaction().getId();
                boolean knowsWhoPlayerIs = otherFleet.getMemoryWithoutUpdate().getBoolean(MemFlags.MEMORY_KEY_SAW_PLAYER_WITH_TRANSPONDER_ON);

                //if a fleet that knows who player is saw you, override the entry in the map of no-knowledge fleet, but not the other way round
                if (factionMap.containsKey(factionId) && !factionMap.get(factionId)) factionMap.put(factionId, knowsWhoPlayerIs);
                else if (!factionMap.containsKey(factionId)) factionMap.put(factionId, knowsWhoPlayerIs);
            }
        }

        for (Map.Entry<String, Boolean> factionEntry : factionMap.entrySet()){
            float repHit = factionEntry.getValue() ? REP_LOSS_PLAYER_KNOWN : REP_LOSS_PLAYER_NOT_KNOWN;
            CoreReputationPlugin.CustomRepImpact repImpact = new CoreReputationPlugin.CustomRepImpact();
            repImpact.delta = repHit;

            Global.getSector().adjustPlayerReputation(
                    new CoreReputationPlugin.RepActionEnvelope(CoreReputationPlugin.RepActions.CUSTOM,
                            repImpact, null, null, false, true),
                    factionEntry.getKey());

            if (factionEntry.getValue()) Global.getSector().getFaction(factionEntry.getKey()).getMemoryWithoutUpdate().set(MissileMemFlags.USES_MISSILES_AGAINST_PLAYER, true, FACTION_USES_MISSILES_TIME);
        }
    }

    public static void reportFleetHitByMissile(CampaignFleetAPI firingFleet, CampaignFleetAPI hitFleet){
        //todo make fleet respond to missile hit, not only fire
    }

    public static void makeFleetRespondToMissileFire(CampaignFleetAPI reactingFleet,
                                                     CampaignFleetAPI offenderFleet) {

        if (reactingFleet == null || offenderFleet == null) return;

        boolean transponderOn = offenderFleet.isTransponderOn();
        MemoryAPI mem = reactingFleet.getMemoryWithoutUpdate();

        boolean ignoresTransponder = Misc.caresAboutPlayerTransponder(reactingFleet);

        int offenses = mem.getInt(MissileMemFlags.OFFENSE_COUNT);
        offenses++;

        //gotta make offenses "sticky" when fleet catches you so the second time is confiscation instead of fine

        mem.set(MissileMemFlags.HAS_SEEN_PLAYER_USE_MISSILE, true, OTHER_FLEET_FORGET_SEEN_MISSILE_USE); //transponder no transponder???
        mem.set(MissileMemFlags.OFFENSE_COUNT, offenses, OTHER_FLEET_FORGET_SEEN_MISSILE_USE);

        if (ignoresTransponder) return; //no transpo fleet ignores missiles too

        //make the fleet engage and reprimand the firing fleet

        if (transponderOn) {
            mem.set(MemFlags.MEMORY_KEY_MAKE_HOSTILE, true, 31f);
            mem.set(MemFlags.MEMORY_KEY_PURSUE_PLAYER, true, 31f);
            mem.set(MemFlags.MEMORY_KEY_STICK_WITH_PLAYER_IF_ALREADY_TARGET, true, 31f);
        } else {
            mem.set(MemFlags.MEMORY_KEY_PURSUE_PLAYER, true, 31f);
            mem.set(MemFlags.MEMORY_KEY_STICK_WITH_PLAYER_IF_ALREADY_TARGET, true, 31f);
        }
    }
}
