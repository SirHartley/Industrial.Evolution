package indevo.exploration.gacha;

import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.BaseCampaignPlugin;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.SectorEntityToken;

public class IndEvo_GachaStationCampaignPlugin extends BaseCampaignPlugin {

    @Override
    public PluginPick<InteractionDialogPlugin> pickInteractionDialogPlugin(SectorEntityToken interactionTarget) {
        if(interactionTarget.hasTag("IndEvo_GachaStation")) {
            return new PluginPick<InteractionDialogPlugin>(new IndEvo_GachaStationDialoguePlugin(), PickPriority.MOD_SPECIFIC);
        }

        return super.pickInteractionDialogPlugin(interactionTarget);
    }
}
