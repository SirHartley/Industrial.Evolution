package indevo.utils.plugins;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.listeners.DialogCreatorUI;
import com.fs.starfarer.api.campaign.listeners.IndustryOptionProvider;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public abstract class BaseSimpleBaseIndustryOptionProvider implements IndustryOptionProvider, SimplifiedIndustryOptionProvider {
    public static Color BASE_COLOUR = new Color(150, 100, 255, 255);
    public static Object CUSTOM_PLUGIN = new Object();

    @Override
    public java.util.List<IndustryOptionData> getIndustryOptions(Industry ind) {
        if (!isSuitable(ind, true)) return null;

        List<IndustryOptionData> result = new ArrayList<IndustryOptionData>();

        IndustryOptionData opt = new IndustryOptionData(getOptionLabel(ind), CUSTOM_PLUGIN, ind, this);
        opt.color = getOptionColour(opt);
        opt.enabled = optionEnabled(opt);
        result.add(opt);

        return result;
    }

    @Override
    public void createTooltip(IndustryOptionData opt, TooltipMakerAPI tooltip, float width) {
        if (opt.id == CUSTOM_PLUGIN) {
            createTooltip(tooltip, opt);
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

    public boolean optionEnabled(IndustryOptionData opt){
        return true;
    }

    public Color getOptionColour(IndustryOptionData opt){
        return BASE_COLOUR;
    }
}
