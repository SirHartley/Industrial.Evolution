package com.fs.starfarer.api.splinterFleet.plugins.abilityAIs;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.ai.FleetAIFlags;
import com.fs.starfarer.api.campaign.ai.ModularFleetAIAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.abilities.EmergencyBurnAbility;
import com.fs.starfarer.api.impl.campaign.abilities.ai.BaseAbilityAI;
import com.fs.starfarer.api.impl.campaign.abilities.ai.EmergencyBurnAbilityAI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;

public class DetachmentEburnAbilityPlugin extends BaseAbilityAI {

    public static String AI_USE_TIMEOUT_KEY = "$ebai_timeout";
    public static float AI_USE_TIMEOUT_DAYS_MIN = 3f;
    public static float AI_USE_TIMEOUT_DAYS_MAX = 5f;

    public static float AI_FREQUENCY_MULT = 1f;

    protected IntervalUtil interval = new IntervalUtil(0.05f, 0.15f);

//	public EmergencyBurnAbilityAI(AbilityPlugin ability, ModularFleetAIAPI ai) {
//		super(ability, ai);
//	}

    protected void activate() {
        ability.activate();
        MemoryAPI mem = fleet.getMemoryWithoutUpdate();
        mem.set(AI_USE_TIMEOUT_KEY, true,
                AI_USE_TIMEOUT_DAYS_MIN + (AI_USE_TIMEOUT_DAYS_MAX - AI_USE_TIMEOUT_DAYS_MIN) * (float) Math.random());
    }

    public void advance(float days) {
        interval.advance(days * EmergencyBurnAbilityAI.AI_FREQUENCY_MULT * 0.25f);
        if (!interval.intervalElapsed()) return;

//		if (fleet.getName().contains("[5]")) {
//			System.out.println("ewfwefwe");
//		}
        if (ability.isActiveOrInProgress()) {
            MemoryAPI mem = fleet.getMemoryWithoutUpdate();
            mem.set(FleetAIFlags.HAS_SPEED_BONUS, true, 0.2f);
            mem.set(FleetAIFlags.HAS_VISION_PENALTY, true, 0.2f);
            return;
        }

        // max burn bonus wouldn't mean much due to a low multiplier, don't use it
        // DO NOT want to check HAS_SPEED_PENALTY here, as using this ability will cancel "Go Dark".
        // since EB now removes terrain penalties
        // but a *very* low mult can also be indicative of an interdict...
        //if (fleet.getStats().getFleetwideMaxBurnMod().getBonusMult() <= 0.3f) {
        if (fleet.getStats().getFleetwideMaxBurnMod().getBonusMult() <= 0.15f) {
            return;
        }

        if (fleet.getAI() != null && fleet.getAI().getCurrentAssignmentType() == FleetAssignment.STANDING_DOWN) {
            return;
        }

        MemoryAPI mem = fleet.getMemoryWithoutUpdate();
//		if (fleet.isInCurrentLocation()) {
//			System.out.println("23r23r23r3");
//		}
        if (mem.getBoolean(AI_USE_TIMEOUT_KEY)) {
            return;
        }

        CampaignFleetAPI pursueTarget = mem.getFleet(FleetAIFlags.PURSUIT_TARGET);
        CampaignFleetAPI fleeingFrom = mem.getFleet(FleetAIFlags.NEAREST_FLEEING_FROM);
        //Vector2f travelDest = mem.getVector2f(FleetAIFlags.TRAVEL_DEST);

        // need to evaluate whether ability is worth using: how desperate the situation is vs the CR hit

        // being pursued by a faster enemy that's relatively close: turn on
        if (fleeingFrom != null) {
            if (fleeingFrom.isStationMode()) return;


            SectorEntityToken.VisibilityLevel level = fleet.getVisibilityLevelTo(fleeingFrom);
            if (level == SectorEntityToken.VisibilityLevel.NONE) return; // they can't see us, don't make it easier

            if (!ability.isUsable()) return;

            if (fleeingFrom.isPlayerFleet()) {
                boolean avoidingPlayer = Misc.isAvoidingPlayerHalfheartedly(fleet);
                if (avoidingPlayer) return;
            }

            EmergencyBurnAbilityAI.UseCost cost = getUseCost();
            boolean hopelessFight = isGreatlyOutmatchedBy(fleeingFrom);
            float dist = Misc.getDistance(fleet.getLocation(), fleeingFrom.getLocation()) - fleet.getRadius() + fleeingFrom.getRadius();
            float detRange = fleeingFrom.getMaxSensorRangeToDetect(fleet);
            float ourSpeed = fleet.getFleetData().getBurnLevel();
            float theirSpeed = fleeingFrom.getFleetData().getBurnLevel();
            float closingSpeed = Misc.getClosingSpeed(fleet.getLocation(), fleeingFrom.getLocation(),
                    fleet.getVelocity(), fleeingFrom.getVelocity());
            if ((theirSpeed > ourSpeed && closingSpeed > 1) || (closingSpeed > 1 && dist < 100)) {
                if (hopelessFight && dist < 200) { // very close and really don't want to fight
                    activate();
                } else if ((cost == EmergencyBurnAbilityAI.UseCost.LOW || cost == EmergencyBurnAbilityAI.UseCost.MEDIUM) && dist < 500) { // low cost, getting decently close
                    activate();
                } else if ((cost == EmergencyBurnAbilityAI.UseCost.LOW || cost == EmergencyBurnAbilityAI.UseCost.MEDIUM) && dist < 100) { // medium cost, very close
                    activate();
                } else if ((cost == EmergencyBurnAbilityAI.UseCost.LOW || cost == EmergencyBurnAbilityAI.UseCost.MEDIUM) && dist > detRange - 100f) { // low cost, close to being able to get out of sight
                    activate();
                }
            }
            return;
        }

        // pursuing a faster enemy, and would be faster then them with EB on: turn on
        if (pursueTarget != null) {
            if (pursueTarget.isStationMode()) return;

            if (fleet.getAI() instanceof ModularFleetAIAPI) {
                ModularFleetAIAPI ai = (ModularFleetAIAPI) fleet.getAI();
                if (ai.getTacticalModule().isMaintainingContact()) {
                    return;
                }
            }

            SectorEntityToken.VisibilityLevel level = pursueTarget.getVisibilityLevelTo(fleet);
            if (level == SectorEntityToken.VisibilityLevel.NONE) return;

            if (pursueTarget.isPlayerFleet()) {
                level = fleet.getVisibilityLevelTo(pursueTarget);
                if (level == SectorEntityToken.VisibilityLevel.NONE) {
                    float closingSpeed = Misc.getClosingSpeed(pursueTarget.getLocation(), fleet.getLocation(),
                            pursueTarget.getVelocity(), fleet.getVelocity());
                    if (closingSpeed > 0) {
                        return;
                    }
                }
            }


            if (!ability.isUsable()) return;

            boolean targetInsignificant = otherInsignificant(pursueTarget);// && !pursueTarget.isPlayerFleet();
//			if (pursueTarget.isPlayerFleet()) {
//				System.out.println("test player fleet EB");
//			}

            EmergencyBurnAbilityAI.UseCost cost = getUseCost();
            float dist = Misc.getDistance(fleet.getLocation(), pursueTarget.getLocation()) - fleet.getRadius() - pursueTarget.getRadius();
            if (dist < 0) return;

            float detRange = pursueTarget.getMaxSensorRangeToDetect(fleet);
            float ourSpeed = fleet.getFleetData().getBurnLevel();
            float theirSpeed = pursueTarget.getFleetData().getBurnLevel();

            float closingSpeed = Misc.getClosingSpeed(fleet.getLocation(), pursueTarget.getLocation(),
                    fleet.getVelocity(), pursueTarget.getVelocity());

            if (cost == EmergencyBurnAbilityAI.UseCost.LOW && closingSpeed <= -1 && dist > detRange - 100f) { // about to lose sensor contact
                activate();
            } else if (cost == EmergencyBurnAbilityAI.UseCost.LOW && dist < 200 && closingSpeed < 50 && !targetInsignificant) { // close, pounce
                activate();
            } else if (cost == EmergencyBurnAbilityAI.UseCost.LOW && theirSpeed > ourSpeed && dist > 300 && !targetInsignificant) {
                activate();
            }
            return;
        }


        boolean useEB = mem.getBoolean(FleetAIFlags.USE_EB_FOR_TRAVEL);
        if (useEB) {
            if (!ability.isUsable()) return;
            activate();
            return;
        }

    }

    public static enum UseCost {
        LOW,
        MEDIUM,
        HIGH
    }

    private EmergencyBurnAbilityAI.UseCost getUseCost() {
        float count = 0;
        float numCritAlready = 0;
        float numCrit = 0;
        float numLow = 0;
        float numOk = 0;

        float crCrit = Global.getSettings().getCRPlugin().getCriticalMalfunctionThreshold(null);
        float crLow = Global.getSettings().getCRPlugin().getMalfunctionThreshold(null) + 0.01f;

        boolean allCRMaxed = true;
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            count++;

            if (member.isCivilian()) {
                numOk++;
                continue;
            }

            float useCost = member.getDeployCost() * EmergencyBurnAbility.CR_COST_MULT;
            float cr = member.getRepairTracker().getCR();
            float maxCR = member.getRepairTracker().getMaxCR();

            float crAfter = cr - useCost;

            if (cr < maxCR) {
                allCRMaxed = false;
            }

            if (cr <= crCrit * 0.5f) {
                numCritAlready++;
            }
            if (crAfter <= crCrit) {
                numCrit++;
            } else if (crAfter <= crLow) {
                numLow++;
            } else {
                numOk++;
            }
        }

        if (numCritAlready >= count) return EmergencyBurnAbilityAI.UseCost.LOW;

        if (allCRMaxed) return EmergencyBurnAbilityAI.UseCost.LOW;
        if (numOk + numLow >= count) return EmergencyBurnAbilityAI.UseCost.MEDIUM;
        //if (numOk + numLow >= count && numOk * 0.5f >= numLow) return UseCost.LOW;
        //if (numLow > numCrit * 0.5f) return UseCost.MEDIUM;
        return EmergencyBurnAbilityAI.UseCost.HIGH;
    }


    protected boolean isGreatlyOutmatchedBy(CampaignFleetAPI other) {
        float us = getStrength(fleet);
        float them = getStrength(other);

        if (us < 0.1f) us = 0.1f;
        if (them < 0.1f) them = 0.1f;
        return them > us * 3f;
    }

    protected boolean otherInsignificant(CampaignFleetAPI other) {
        float us = getStrength(fleet);
        float them = getStrength(other);

        if (us < 0.1f) us = 0.1f;
        if (them < 0.1f) them = 0.1f;
        return us > them * 5f;
    }

    public static float getStrength(CampaignFleetAPI fleet) {
        float str = 0f;
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            if (member.canBeDeployedForCombat()) {
                float strength = member.getMemberStrength();
                str += strength;
            }
        }
        return str;
    }
}