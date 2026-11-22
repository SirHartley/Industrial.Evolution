package indevo.industries.petshop.plugins;

import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.BaseCampaignPlugin;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.SectorEntityToken;

// the param overload runs only for a click on a message icon
public class PetCampaignPlugin extends BaseCampaignPlugin {

    @Override
    public PluginPick<InteractionDialogPlugin> pickInteractionDialogPlugin(Object param, SectorEntityToken interactionTarget) {
        InteractionDialogPlugin plugin = PetsDialogPlugin.follow(interactionTarget);
        return plugin != null ? new PluginPick<>(plugin, PickPriority.MOD_SPECIFIC) : null;
    }
}
