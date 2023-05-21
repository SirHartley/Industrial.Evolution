package indevo.industries.derelicts.listeners;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.listeners.BaseIndustryOptionProvider;
import com.fs.starfarer.api.campaign.listeners.DialogCreatorUI;
import com.fs.starfarer.api.campaign.listeners.IndustryOptionProvider;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.derelicts.industry.AncientLaboratory;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class AncientLabCommoditySwitchOptionProvider extends BaseIndustryOptionProvider {
    public static final Object OPTION_COMMODITY_CHANGE = new Object();

    public static void register() {
        ListenerManagerAPI listeners = Global.getSector().getListenerManager();
        if (!listeners.hasListenerOfClass(AncientLabCommoditySwitchOptionProvider.class)) {
            listeners.addListener(new AncientLabCommoditySwitchOptionProvider(), true);
        }
    }

    @Override
    public boolean isUnsuitable(Industry ind, boolean allowUnderConstruction) {
        boolean isTarget = ind instanceof AncientLaboratory;
        boolean hasBeta = Commodities.BETA_CORE.equals(ind.getAICoreId());

        boolean targetAndBeta = isTarget && hasBeta;
        boolean playerOwned = ind.getMarket().isPlayerOwned();

        return super.isUnsuitable(ind, allowUnderConstruction) || !targetAndBeta || !playerOwned;
    }

    public java.util.List<IndustryOptionData> getIndustryOptions(Industry ind) {
        if (isUnsuitable(ind, false)) return null;

        List<IndustryOptionData> result = new ArrayList<IndustryOptionData>();

        IndustryOptionData opt = new IndustryOptionProvider.IndustryOptionData("Change output", OPTION_COMMODITY_CHANGE, ind, this);
        opt.color = new Color(150, 100, 255, 255);
        result.add(opt);

        return result;
    }

    @Override
    public void createTooltip(IndustryOptionProvider.IndustryOptionData opt, TooltipMakerAPI tooltip, float width) {
        if (opt.id == OPTION_COMMODITY_CHANGE) {
            AncientLaboratory ind = (AncientLaboratory) opt.ind;

            tooltip.addPara("Changes the %s from %s to %s.", 0f, Misc.getHighlightColor(),
                    "commodity bonus",
                    Global.getSettings().getCommoditySpec(ind.bonusCommodityId1).getName(),
                    Global.getSettings().getCommoditySpec(ind.bonusCommodityId2).getName());
        }
    }

    @Override
    public void optionSelected(IndustryOptionProvider.IndustryOptionData opt, DialogCreatorUI ui) {
        if (opt.id == OPTION_COMMODITY_CHANGE) {
            AncientLaboratory ind = (AncientLaboratory) opt.ind;
            String id1 = ind.bonusCommodityId1;
            ind.bonusCommodityId1 = ind.bonusCommodityId2;
            ind.bonusCommodityId2 = id1;
        }
    }
}
