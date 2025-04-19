package indevo.utils.plugins;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.DialogCreatorUI;
import com.fs.starfarer.api.campaign.listeners.IndustryOptionProvider;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import indevo.utils.helper.MiscIE;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public abstract class SimpleIndustryOptionProvider implements IndustryOptionProvider {
    public static Color BASE_COLOUR = new Color(150, 100, 255, 255);
    public static Object CUSTOM_PLUGIN = new Object();

    public boolean isSuitable(Industry ind, boolean allowUnderConstruction){
        if (ind == null
                || ind.getMarket() == null
                || (!allowUnderConstruction && (ind.isBuilding() || ind.isUpgrading()))) return false;

        boolean isTarget = ind.getId().equals(getTargetIndustryId()) && ind.isFunctional();
        MarketAPI currentMarket = MiscIE.getCurrentInteractionTargetMarket();
        boolean isLocal = currentMarket != null && ind.getMarket().getId().equals(currentMarket.getId());
        return isTarget && isLocal;
    }

    @Override
    public List<IndustryOptionData> getIndustryOptions(Industry ind) {
        if (!isSuitable(ind, false)) return null;

        List<IndustryOptionData> result = new ArrayList<IndustryOptionData>();

        IndustryOptionData opt = new IndustryOptionData(getOptionLabel(ind), CUSTOM_PLUGIN, ind, this);
        opt.color = BASE_COLOUR;
        result.add(opt);

        return result;
    }

    @Override
    public void createTooltip(IndustryOptionData opt, TooltipMakerAPI tooltip, float width) {
        if (opt.id == CUSTOM_PLUGIN) {
            createTooltip(tooltip);
        }
    }

    @Override
    public void addToIndustryTooltip(Industry ind, Industry.IndustryTooltipMode mode, TooltipMakerAPI tooltip, float width, boolean expanded) {

    }

    @Override
    public void optionSelected(IndustryOptionData opt, DialogCreatorUI ui) {
        if (opt.id == CUSTOM_PLUGIN) {
            onClick(opt, ui);
        }
    }

    public abstract void onClick(IndustryOptionData opt, DialogCreatorUI ui);
    public abstract void createTooltip(TooltipMakerAPI tooltip);
    public abstract String getOptionLabel(Industry ind);
    public abstract String getTargetIndustryId();
}
