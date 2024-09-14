package indevo.industries.petshop.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.BaseIndustryOptionProvider;
import com.fs.starfarer.api.campaign.listeners.DialogCreatorUI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import indevo.ids.Ids;
import indevo.industries.petshop.dialogue.PetShopDialogPlugin;
import indevo.utils.helper.MiscIE;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class PetCenterOptionProvider extends BaseIndustryOptionProvider {

    public static Object CUSTOM_PLUGIN = new Object();

    public static void register() {
        ListenerManagerAPI listeners = Global.getSector().getListenerManager();
        if (!listeners.hasListenerOfClass(PetCenterOptionProvider.class)) {
            listeners.addListener(new PetCenterOptionProvider(), true);
        }
    }

    @Override
    public boolean isUnsuitable(Industry ind, boolean allowUnderConstruction) {
        boolean isTarget = ind.getId().equals(Ids.PET_STORE) && ind.isFunctional();
        MarketAPI currentMarket = MiscIE.getCurrentInteractionTargetMarket();
        boolean isLocal = currentMarket != null && ind.getMarket().getId().equals(currentMarket.getId());

        return super.isUnsuitable(ind, allowUnderConstruction)
                || !isTarget || !isLocal;
    }

    @Override
    public List<IndustryOptionData> getIndustryOptions(Industry ind) {
        if (isUnsuitable(ind, false)) return null;

        List<IndustryOptionData> result = new ArrayList<IndustryOptionData>();

        IndustryOptionData opt = new IndustryOptionData("Manage Pets", CUSTOM_PLUGIN, ind, this);
        opt.color = new Color(150, 100, 255, 255);
        result.add(opt);

        return result;
    }

    @Override
    public void createTooltip(IndustryOptionData opt, TooltipMakerAPI tooltip, float width) {
        if (opt.id == CUSTOM_PLUGIN) {
            tooltip.addSectionHeading("Manage your Pets", Alignment.MID, 0f);
            tooltip.addPara("Store, move, rename or euthanize your pets at the pet center.", 10f);
            tooltip.addPara("Most services are provided for free.", 3f);
        }
    }

    @Override
    public void optionSelected(IndustryOptionData opt, DialogCreatorUI ui) {
        if (opt.id == CUSTOM_PLUGIN) {
            PetShopDialogPlugin plugin = new PetShopDialogPlugin(opt.ind);
            ui.showDialog(null, plugin);

            /*CustomDialogDelegate delegate = new PetManagerDialogueDelegate(opt.ind);
            ui.showDialog(PetManagerDialogueDelegate.WIDTH, PetManagerDialogueDelegate.HEIGHT, delegate);*/
        }
    }
}
