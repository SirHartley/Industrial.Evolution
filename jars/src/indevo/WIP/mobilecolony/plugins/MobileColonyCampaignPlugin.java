package indevo.WIP.mobilecolony.plugins;

import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.BaseCampaignPlugin;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import indevo.WIP.mobilecolony.dialogue.MobileColonyInteractionDialoguePlugin;
import indevo.WIP.mobilecolony.utility.MobileColonyFactory;

public class MobileColonyCampaignPlugin extends BaseCampaignPlugin {

    @Override
    public PluginPick<InteractionDialogPlugin> pickInteractionDialogPlugin(SectorEntityToken interactionTarget) {
        if (interactionTarget.getMemoryWithoutUpdate().contains(MobileColonyFactory.MOBILE_COLONY_IDENTIFIER)) {
            return new PluginPick<InteractionDialogPlugin>(new MobileColonyInteractionDialoguePlugin(), PickPriority.MOD_SPECIFIC);
        }

        return super.pickInteractionDialogPlugin(interactionTarget);
    }
}
