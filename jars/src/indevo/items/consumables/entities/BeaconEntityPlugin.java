package indevo.items.consumables.entities;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.WarningBeaconEntityPlugin;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.*;

public class BeaconEntityPlugin extends WarningBeaconEntityPlugin {

    public String message;

    @Override
    public void init(SectorEntityToken entity, Object pluginParams) {
        super.init(entity, pluginParams);

        this.message = (String) pluginParams;
    }

    @Override
    public void appendToCampaignTooltip(TooltipMakerAPI tooltip, SectorEntityToken.VisibilityLevel level) {
        super.appendToCampaignTooltip(tooltip, level);

        if (level == SectorEntityToken.VisibilityLevel.COMPOSITION_AND_FACTION_DETAILS ||
                level == SectorEntityToken.VisibilityLevel.COMPOSITION_DETAILS) {

            String post = "";
            Color color = Misc.getTextColor();
            Color postColor = Misc.getDarkPlayerColor();

            tooltip.setParaFontDefault();
            tooltip.addPara(BaseIntelPlugin.BULLET + "%s " + (message.isEmpty() ? "static noise.": message), 10f, color, postColor, "Broadcasting:");

        }
    }
}
