package indevo.items.consumables.fleet;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.ai.ModularFleetAIAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.List;

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

/*begin
- caught using no transpo
	- instant -3
        - demand transpo on to identify
	- if refuse, combat
	- if accept, fine and some more rep penalty (and now they know who you are)
	- if second time, cargo scan and confiscate everything they find
	- if third time, hostile and combat
- caught w/ transpo
	- instant -15
        - fine if first time
	- confiscate if second time
	- hostile and combat if third time

        special condition: "They used them first!"
        - Still forbidden in civ volume!
        - check if other fleet nearby has use tag
	- if yes, let off easy
	- if no, "dectecting no missile drive residue", call liar, rep penalty, confiscate for lying*/

public class MissileAIReactionManager {

    public static float FACTION_REP_LOSS_TIMEOUT_TIME = 14f;
    public static float FACTION_USES_MISSILES_TIME = 91f;
    public static float TIME_TO_FORGET_SHORT_TERM = 7f;
    public static float TIME_TO_FORGET_LONG_TERM = 31f;
    public static float TIME_REMOVE_SELF_FLAG = 7f;

    public static final float REP_LOSS_PLAYER_KNOWN = -0.15f;
    public static final float REP_LOSS_PLAYER_NOT_KNOWN = -0.02f; //only when confronted

    public static void reportFleetUsedMissile(CampaignFleetAPI offenderFleet, String missileType) {
        if (offenderFleet == null) return;

        MemoryAPI mem = offenderFleet.getMemoryWithoutUpdate();
        mem.set(MissileMemFlags.RECENTLY_USED_MISSILE, true, TIME_REMOVE_SELF_FLAG);

        //if used in non-civ system, no one cares, reacts or even remembers - even civ factions
        if (Misc.getMarketsInLocation(offenderFleet.getContainingLocation()).isEmpty()) return;

        List<String> factionList = new ArrayList<>();
        for (CampaignFleetAPI otherFleet : Misc.getVisibleFleets(offenderFleet, false)) {
            makeFleetRespondToMissileFire(otherFleet, offenderFleet);

            //rep loss due to missile fire
            if (Misc.caresAboutPlayerTransponder(otherFleet)) {
                String factionId = otherFleet.getFaction().getId();
                MemoryAPI factionMem = otherFleet.getFaction().getMemoryWithoutUpdate();
                boolean knowsWhoPlayerIs = otherFleet.getMemoryWithoutUpdate().getBoolean(MemFlags.MEMORY_KEY_SAW_PLAYER_WITH_TRANSPONDER_ON);

                //known and rep loss not timed out - no rep loss if no transponder as to not punish usage too much
                if (knowsWhoPlayerIs && !factionMem.getBoolean(MissileMemFlags.REP_LOSS_TIMEOUT_FLAG))
                    if (!factionList.contains(factionId)) factionList.add(factionId);
            }
        }

        for (String factionId : factionList) {
            CoreReputationPlugin.CustomRepImpact repImpact = new CoreReputationPlugin.CustomRepImpact();
            repImpact.delta = REP_LOSS_PLAYER_KNOWN;

            Global.getSector().adjustPlayerReputation(
                    new CoreReputationPlugin.RepActionEnvelope(CoreReputationPlugin.RepActions.CUSTOM,
                            repImpact, null, null, false, true),
                    factionId);

            MemoryAPI factionMem = Global.getSector().getFaction(factionId).getMemoryWithoutUpdate();
            factionMem.set(MissileMemFlags.USES_MISSILES_AGAINST_PLAYER, true, FACTION_USES_MISSILES_TIME);
            factionMem.set(MissileMemFlags.REP_LOSS_TIMEOUT_FLAG, true, FACTION_REP_LOSS_TIMEOUT_TIME);
        }
    }

    public static void reportFleetHitByMissile(CampaignFleetAPI firingFleet, CampaignFleetAPI hitFleet) {
        //todo make fleet respond to missile hit, not only fire
    }

    //if fleet cares about transpo, go yell at player, if not, ignore but remember
    public static void makeFleetRespondToMissileFire(final CampaignFleetAPI reactingFleet, final CampaignFleetAPI offenderFleet) {
        if (reactingFleet == null || offenderFleet == null) return;

        MemoryAPI mem = reactingFleet.getMemoryWithoutUpdate();

        int offenses = mem.getInt(MissileMemFlags.OFFENSE_COUNT);
        offenses++;
        mem.set(MissileMemFlags.HAS_SEEN_PLAYER_USE_MISSILE_LONG_TERM, true, TIME_TO_FORGET_LONG_TERM); //transponder no transponder???
        mem.set(MissileMemFlags.OFFENSE_COUNT, offenses, TIME_TO_FORGET_LONG_TERM);

        //make a filter to check if the reacting fleet is valid
        Misc.FleetFilter filter = new Misc.FleetFilter() {
            public boolean accept(CampaignFleetAPI curr) {
                if (!Misc.caresAboutPlayerTransponder(curr)) return false;
                if (curr.getFaction().getId().equals(offenderFleet.getFaction().getId())) return false;
                if (curr.getFaction().isPlayerFaction()) return false;
                if (curr.isHostileTo(offenderFleet)) return false;
                if (curr.isStationMode()) return false;
                if (!curr.getMemoryWithoutUpdate().getBoolean(MemFlags.MEMORY_KEY_PATROL_FLEET)) return false;
                if (curr.getAI() instanceof ModularFleetAIAPI) {
                    ModularFleetAIAPI ai = (ModularFleetAIAPI) curr.getAI();
                    if (ai.isFleeing()) return false;
                    if (curr.getInteractionTarget() instanceof CampaignFleetAPI) return false;
                }
                SectorEntityToken.VisibilityLevel vis = offenderFleet.getVisibilityLevelTo(curr);
                if (vis == SectorEntityToken.VisibilityLevel.NONE) return false;
                return true;
            }
        };

        if (filter.accept(reactingFleet)) {
            //if (!mem.getBoolean(MissileMemFlags.HAS_SEEN_PLAYER_USE_MISSILE_SHORT_TERM)) reactingFleet.addFloatingText("Missile use detected", reactingFleet.getIndicatorColor(), 1f);

            //if the player is not vis after a day we abort the chase so it's not infinite
            reactingFleet.addScript(new EveryFrameScript() {
                final float CHASE_NO_VIS_DUR_DAYS = 1f;

                final CampaignFleetAPI fleet = reactingFleet;
                final CampaignFleetAPI targetFleet = offenderFleet;
                float elapsedNoVis = 0f;

                @Override
                public boolean isDone() {
                    return elapsedNoVis > Global.getSector().getClock().convertToSeconds(CHASE_NO_VIS_DUR_DAYS);
                }

                @Override
                public boolean runWhilePaused() {
                    return false;
                }

                @Override
                public void advance(float amount) {
                    if (targetFleet.getVisibilityLevelTo(fleet) == SectorEntityToken.VisibilityLevel.NONE) elapsedNoVis += amount;
                    else elapsedNoVis = 0f;

                    MemoryAPI mem = fleet.getMemoryWithoutUpdate();
                    Misc.setFlagWithReason(mem, MemFlags.MEMORY_KEY_PURSUE_PLAYER, "missileReaction", true, 1f);
                    Misc.setFlagWithReason(mem, MemFlags.MEMORY_KEY_STICK_WITH_PLAYER_IF_ALREADY_TARGET, "missileReaction", true, TIME_TO_FORGET_SHORT_TERM);
                    mem.set(MissileMemFlags.HAS_SEEN_PLAYER_USE_MISSILE_SHORT_TERM, true, TIME_TO_FORGET_SHORT_TERM);
                    if (targetFleet.isPlayerFleet()) mem.set(MissileMemFlags.CHASING_PLAYER_DUE_TO_MISSILE_USE, true, TIME_TO_FORGET_SHORT_TERM);
                }
            });

            //todo unset these on all in-system fleets once the player is caught so no other fleets do dupe encounters
        }
    }
}
