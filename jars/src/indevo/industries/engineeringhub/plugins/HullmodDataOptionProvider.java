package indevo.industries.engineeringhub.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.listeners.DialogCreatorUI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.Ids;
import indevo.ids.ItemIds;
import indevo.industries.engineeringhub.dialogue.HullmodDataPanelDelegate;
import indevo.industries.engineeringhub.memory.RecoveredHullmods;
import indevo.utils.plugins.BaseSimpleBaseIndustryOptionProvider;

public class HullmodDataOptionProvider extends BaseSimpleBaseIndustryOptionProvider {

    public static void register() {
        ListenerManagerAPI listeners = Global.getSector().getListenerManager();
        if (!listeners.hasListenerOfClass(HullmodDataOptionProvider.class)) {
            listeners.addListener(new HullmodDataOptionProvider(), true);
        }
    }

    @Override
    public boolean isSuitable(Industry ind, boolean allowUnderConstruction) {
        return ind != null
                && ind.getMarket() != null
                && Ids.ENGHUB.equals(ind.getId())
                && ind.isFunctional()
                && ind.getMarket().isPlayerOwned();
    }

    @Override
    public void onClick(IndustryOptionData opt, DialogCreatorUI ui) {
        ui.showDialog(HullmodDataPanelDelegate.WIDTH, HullmodDataPanelDelegate.getHeight(opt.ind.getMarket()), new HullmodDataPanelDelegate(opt.ind.getMarket()));
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, IndustryOptionData opt) {
        float opad = 10f;
        int recovered = RecoveredHullmods.getLearnable(opt.ind.getMarket()).size();

        tooltip.addPara("Learn hullmods recovered from ships the hub stripped, paid with %s.", 0f, Misc.getHighlightColor(),
                Global.getSettings().getCommoditySpec(ItemIds.RARE_PARTS).getName());
        tooltip.addPara("Recovered: %s", opad, Misc.getHighlightColor(), recovered + "");
        tooltip.addPara(HullmodDataPanelDelegate.LORE, Misc.getGrayColor(), opad);
    }

    @Override
    public String getOptionLabel(Industry ind) {
        return "Hullmod Data";
    }
}
