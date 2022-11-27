package indevo.abilities.splitfleet.abilityAIs;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.abilities.ai.TransponderAbilityAI;
import com.fs.starfarer.api.impl.campaign.ids.Abilities;
import indevo.abilities.splitfleet.FleetUtils;
import com.fs.starfarer.api.util.Misc;

public class DetachmentTransponderAbilityAI extends TransponderAbilityAI {

    //turn on when player has it, turn off when player does not

    public void advance(float days) {
        if (fleet.getBattle() != null) return;

        MemoryAPI mem = this.fleet.getMemoryWithoutUpdate();
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();

        if ((mem.contains(FleetUtils.USE_ABILITY_MEM_KEY) && mem.get(FleetUtils.USE_ABILITY_MEM_KEY).equals(Abilities.GO_DARK)) || !player.isTransponderOn()) {
            if (this.ability.isActive()) {
                this.ability.deactivate();
            }
        } else if (fleet.isInCurrentLocation() && Misc.getDistance(player, fleet) < 2000 && player.isTransponderOn()) {
            if (!this.ability.isActive()) {
                this.ability.activate();
            }
        } else super.advance(days);
    }
}