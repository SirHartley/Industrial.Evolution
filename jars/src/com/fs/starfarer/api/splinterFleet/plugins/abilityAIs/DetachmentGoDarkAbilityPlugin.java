package com.fs.starfarer.api.splinterFleet.plugins.abilityAIs;

import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.abilities.ai.BaseAbilityAI;
import com.fs.starfarer.api.impl.campaign.ids.Abilities;
import com.fs.starfarer.api.splinterFleet.plugins.FleetUtils;
import com.fs.starfarer.api.util.Misc;

public class DetachmentGoDarkAbilityPlugin extends BaseAbilityAI {

    @Override
    public void advance(float days) {
        if (fleet.getBattle() != null) return;

        MemoryAPI mem = fleet.getMemoryWithoutUpdate();
        if (mem.contains(FleetUtils.USE_ABILITY_MEM_KEY) && mem.get(FleetUtils.USE_ABILITY_MEM_KEY).equals(Abilities.GO_DARK) && !ability.isActive())
            ability.activate();
        else if (fleet.getAI() != null && fleet.getAI().getCurrentAssignment() != null) {
            FleetAssignment curr = fleet.getAI().getCurrentAssignmentType();
            SectorEntityToken target = fleet.getAI().getCurrentAssignment().getTarget();
            boolean inSameLocation = target != null && target.getContainingLocation() == fleet.getContainingLocation();
            float distToTarget = Float.MAX_VALUE;
            if (inSameLocation) {
                distToTarget = Misc.getDistance(target.getLocation(), fleet.getLocation());
            }

            boolean targetIsPlayer = target != null && target.isPlayerFleet();
            boolean playerIsGoDark = targetIsPlayer && target.getAbility(Abilities.GO_DARK).isActive();

            //Fleet is around player, player is go dark - activate
            //if too far away, deactivate

            if (targetIsPlayer && (curr == FleetAssignment.ORBIT_PASSIVE)) {
                if (playerIsGoDark && !ability.isActive() && distToTarget < 600f) ability.activate();
                //too far away, turn it off
                if (distToTarget > 1000f && ability.isActive()) ability.deactivate();
                if (!playerIsGoDark && ability.isActive()) ability.deactivate();
            } else if (!mem.contains(FleetUtils.USE_ABILITY_MEM_KEY)) ability.deactivate();
        } else if (!mem.contains(FleetUtils.USE_ABILITY_MEM_KEY)) ability.deactivate();
    }
}
