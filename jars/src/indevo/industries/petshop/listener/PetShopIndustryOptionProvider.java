package indevo.industries.petshop.listener;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomDialogDelegate;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.listeners.BaseIndustryOptionProvider;
import com.fs.starfarer.api.campaign.listeners.DialogCreatorUI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.impl.campaign.PlanetInteractionDialogPluginImpl;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.Ids;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class PetShopIndustryOptionProvider extends BaseIndustryOptionProvider {
    public static Object CUSTOM_PLUGIN = new Object();

    //show 3 buttons with:
    //- Manage Pets
    // - fleet / storage pet list
    // - select pet -> show data and rename field -> "assign" button, "store" button, "euthanize" button
    // - assign button moves UI to the left and shows fleet members on the right

    //- Buy Pet
    // - shows buy delegate


    public static void register() {
        ListenerManagerAPI listeners = Global.getSector().getListenerManager();
        if (!listeners.hasListenerOfClass(PetShopIndustryOptionProvider.class)) {
            listeners.addListener(new PetShopIndustryOptionProvider(), true);
        }
    }

    @Override
    public boolean isUnsuitable(Industry ind, boolean allowUnderConstruction) {
        boolean isTarget = ind.getId().equals(Ids.PET_STORE);

        return super.isUnsuitable(ind, allowUnderConstruction) || !isTarget;
    }


    @Override
    public List<IndustryOptionData> getIndustryOptions(Industry ind) {
        if (isUnsuitable(ind, false)) return null;

        List<IndustryOptionData> result = new ArrayList<IndustryOptionData>();

        IndustryOptionData opt = new IndustryOptionData("Visit the Pet Center", CUSTOM_PLUGIN, ind, this);
        opt.color = new Color(150, 100, 255, 255);
        result.add(opt);

        return result;
    }

    @Override
    public void createTooltip(IndustryOptionData opt, TooltipMakerAPI tooltip, float width) {
        if (opt.id == CUSTOM_PLUGIN) {
            tooltip.addSectionHeading("Visit the Pet Center", Alignment.MID, 0f);
            tooltip.addPara("Allows you to %s your pets. Inventory rotates every month, so check back often for a chance at a rare specimen!", 10f, Misc.getHighlightColor(), "buy, store or manage");
        }
    }

    @Override
    public void optionSelected(IndustryOptionData opt, DialogCreatorUI ui) {
        if (opt.id == CUSTOM_PLUGIN) {

        }
    }
}
