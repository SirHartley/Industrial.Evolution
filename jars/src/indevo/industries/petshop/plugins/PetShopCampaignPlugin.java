package indevo.industries.petshop.plugins;

import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.BaseCampaignPlugin;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import indevo.ids.Ids;
import indevo.industries.petshop.dialogue.PetShopDialogPlugin;

public class PetShopCampaignPlugin extends BaseCampaignPlugin {

    @Override
    public PluginPick<InteractionDialogPlugin> pickInteractionDialogPlugin(SectorEntityToken interactionTarget) {
        if (interactionTarget.hasTag("IndEvo_PetShop")) {
            return new PluginPick<InteractionDialogPlugin>(new PetShopDialogPlugin(interactionTarget.getMarket().getIndustry(Ids.PET_STORE)), PickPriority.MOD_SPECIFIC);
        }

        return super.pickInteractionDialogPlugin(interactionTarget);
    }
}
