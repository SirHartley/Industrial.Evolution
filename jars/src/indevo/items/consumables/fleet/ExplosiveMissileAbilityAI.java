package indevo.items.consumables.fleet;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.ai.FleetAIFlags;
import com.fs.starfarer.api.campaign.ai.ModularFleetAIAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.impl.campaign.abilities.InterdictionPulseAbility;
import com.fs.starfarer.api.impl.campaign.abilities.ai.BaseAbilityAI;
import com.fs.starfarer.api.impl.campaign.ids.Abilities;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;

public class ExplosiveMissileAbilityAI extends BaseAbilityAI {
    private IntervalUtil interval = new IntervalUtil(0.05f, 0.15f);

    //we want to use this IF
    //- enemy fleet is already slowed or affected by movement debuff
    //- we are reasonably sure to catch the fleet
    //- we want them Very Dead (low rep or combat target)
    //- we are being chased and will have to fight and have already used all other missiles
    //AND:
    //- not hitting allies, not hitting self, no other missiles of same type currently being deployed or in flight with chance of hitting target, target not recently hit with missile

    public void advance(float days) {
        interval.advance(days);
        if (!interval.intervalElapsed()) return;

        if (fleet.getAI() instanceof ModularFleetAIAPI) {
            ModularFleetAIAPI ai = (ModularFleetAIAPI) fleet.getAI();
            if (ai.getTacticalModule().isMaintainingContact()) {
                return;
            }
        }

        MemoryAPI mem = fleet.getMemoryWithoutUpdate();
        if (ability.isActiveOrInProgress()) {
            mem.set(MissileMemFlags.RECENTLY_USED_MISSILE, true, 0.5f);
            return;
        }

        if (!ability.isUsable()) return;

        //Actual decision process starts here

        CampaignFleetAPI pursueTarget = mem.getFleet(FleetAIFlags.PURSUIT_TARGET);
        CampaignFleetAPI fleeingFrom = mem.getFleet(FleetAIFlags.NEAREST_FLEEING_FROM);

        //we shoot this missile if:

        //TARGET
        //- enemy fleet is already slowed or affected by movement debuff
        //- OR It's slow
        //- AND we can hit it probably
        //- AND we're not hitting allies, not hitting self

        if(true) return; //todo remove for release

        float activationTime =  ability.getSpec().getActivationDays() * Global.getSector().getClock().getSecondsPerDay();
        if (fleeingFrom != null) {

            float range = InterdictionPulseAbility.getRange(fleet);
            float dist = Misc.getDistance(fleet.getLocation(), fleeingFrom.getLocation());
            if (dist > range + 200) return;

            SectorEntityToken.VisibilityLevel level = fleeingFrom.getVisibilityLevelTo(fleet);
            if (level == SectorEntityToken.VisibilityLevel.NONE) return;

            if (fleet.getAI() != null) {
                if (!fleet.getAI().isHostileTo(fleeingFrom)) return;
            }

            //float speed = Math.max(1f, fleeingFrom.getTravelSpeed());
            float speed = Math.max(1f, fleeingFrom.getVelocity().length());
            float time = dist / speed;

            boolean usingHasBenefit = false;

            float interdictDur = InterdictionPulseAbility.getInterdictSeconds(fleet, fleeingFrom);

            if (interdictDur > 0 && fleeingFrom.getVelocity().length() > fleet.getVelocity().length()) {
                for (AbilityPlugin ability : fleeingFrom.getAbilities().values()) {
                    if (!ability.getSpec().hasTag(Abilities.TAG_BURN + "+")) continue;
                    if (ability.isActiveOrInProgress()) {
                        usingHasBenefit = true;
                        break;
                    }
                }

                AbilityPlugin eb = fleet.getAbility(Abilities.EMERGENCY_BURN);
                if (eb != null && eb.getCooldownLeft() < activationTime + 1f) usingHasBenefit = true;
            }

            if (time > activationTime + 2f && time < activationTime + 7f && usingHasBenefit) {
                ability.activate();
            }
            return;
        }

        if (pursueTarget != null) {
            float range = InterdictionPulseAbility.getRange(fleet);
            float dist = Misc.getDistance(fleet.getLocation(), pursueTarget.getLocation());
            if (dist > range + 200) return;

            SectorEntityToken.VisibilityLevel level = pursueTarget.getVisibilityLevelTo(fleet);
            if (level == SectorEntityToken.VisibilityLevel.NONE) return;

            if (fleet.getAI() != null) {
                if (!fleet.getAI().isHostileTo(pursueTarget)) return;
            }


            //float speed = Math.max(1f, pursueTarget.getTravelSpeed());
            float speed = Math.max(1f, pursueTarget.getVelocity().length());
            float closingSpeed = Misc.getClosingSpeed(fleet.getLocation(), pursueTarget.getLocation(),
                    fleet.getVelocity(), pursueTarget.getVelocity());
            speed = Math.max(1f, (speed - closingSpeed) / 2f);
            float time = Math.max(200, (range - dist)) / speed;
            float timeToReach = dist / fleet.getVelocity().length();

            boolean usingHasBenefit = false;
            float interdictDur = InterdictionPulseAbility.getInterdictSeconds(fleet, pursueTarget);

            if (interdictDur > 0 && pursueTarget.getVelocity().length() > fleet.getVelocity().length()) {
                for (AbilityPlugin ability : pursueTarget.getAbilities().values()) {
                    if (!ability.getSpec().hasTag(Abilities.TAG_BURN + "+")) continue;
                    if (ability.isActiveOrInProgress()) {
                        usingHasBenefit = true;
                        break;
                    }
                }

//				eb = fleet.getAbility(Abilities.EMERGENCY_BURN);
//				if (eb != null && eb.getCooldownLeft() < activationTime + 1f) usingHasBenefit = true;
            }

            AbilityPlugin tj = pursueTarget.getAbility(Abilities.TRANSVERSE_JUMP);
            if (tj != null && tj.isActiveOrInProgress() && timeToReach > activationTime &&
                    dist < range) {
                usingHasBenefit = true;
            }

            AbilityPlugin sb = pursueTarget.getAbility(Abilities.SUSTAINED_BURN);
            if (sb != null && sb.isActiveOrInProgress() &&
                    sb.getProgressFraction() > 0.25f &&
                    sb.getProgressFraction() <= 0.5f) {
                usingHasBenefit = true;
            }

            if (usingHasBenefit && time > activationTime + 0.5f) {
                ability.activate();
            }

            return;
        }

    }
}
