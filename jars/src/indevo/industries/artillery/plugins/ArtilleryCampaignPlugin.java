package indevo.industries.artillery.plugins;

import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.BaseCampaignPlugin;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import indevo.exploration.gacha.GachaStationDialoguePlugin;

public class ArtilleryCampaignPlugin extends BaseCampaignPlugin {

    @Override
    public PluginPick<InteractionDialogPlugin> pickInteractionDialogPlugin(SectorEntityToken interactionTarget) {
        if(interactionTarget instanceof PlanetAPI && interactionTarget.isStar()) {
            return new PluginPick<InteractionDialogPlugin>(new SunInteractionDialogPluginImpl(), PickPriority.MOD_SPECIFIC);
        }

        return super.pickInteractionDialogPlugin(interactionTarget);
    }

}
