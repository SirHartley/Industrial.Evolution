package indevo.exploration.minefields.listeners;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.impl.campaign.abilities.InterdictionPulseAbility;
import com.fs.starfarer.api.impl.campaign.ids.Abilities;
import com.fs.starfarer.api.impl.campaign.terrain.BaseRingTerrain;
import indevo.exploration.minefields.MineBeltTerrainPlugin;

public class InterdictionPulseAbilityListener extends BaseCampaignEventListener {
    public InterdictionPulseAbilityListener(boolean permaRegister) {
        super(permaRegister);
    }

    @Override
    public void reportPlayerDeactivatedAbility(AbilityPlugin ability, Object param) {
        super.reportPlayerDeactivatedAbility(ability, param);

        AbilityPlugin plugin = (AbilityPlugin) param;
        if (!plugin.getSpec().getId().equals(Abilities.INTERDICTION_PULSE)) return;

        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();

        float range = InterdictionPulseAbility.getRange(fleet);
        
        for (SectorEntityToken t : fleet.getContainingLocation().getTerrainCopy()) {
            if (t.getCustomPlugin() instanceof MineBeltTerrainPlugin) {
                float 0f;

                // TODO: 14/04/2023 finish this and add the listener to the manager

            }
        }
    }

    //listen to AP ability use and spawn a "disabled" area
    
    //get minefields, get Plugin, generate disabled area
}
