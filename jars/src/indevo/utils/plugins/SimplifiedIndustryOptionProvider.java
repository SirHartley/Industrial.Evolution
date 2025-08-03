package indevo.utils.plugins;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.listeners.DialogCreatorUI;
import com.fs.starfarer.api.campaign.listeners.IndustryOptionProvider;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

public interface SimplifiedIndustryOptionProvider {
    boolean isSuitable(Industry ind, boolean allowUnderConstruction);
    void onClick(IndustryOptionProvider.IndustryOptionData opt, DialogCreatorUI ui);
    void createTooltip(TooltipMakerAPI tooltip, IndustryOptionProvider.IndustryOptionData opt);
    String getOptionLabel(Industry ind);
}
