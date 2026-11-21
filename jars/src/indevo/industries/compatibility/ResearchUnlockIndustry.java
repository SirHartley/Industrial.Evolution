package indevo.industries.compatibility;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.loading.Description;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

// research reward stand in for a specialization or government, never built; spec data is "<description id>|<host industry id>"
public class ResearchUnlockIndustry extends BaseIndustry {

    @Override
    public void apply() {
    }

    @Override
    public boolean isAvailableToBuild() {
        return false;
    }

    @Override
    public boolean showWhenUnavailable() {
        return false;
    }

    // vok draws this on its preview market, bmo takes the first label as the title
    @Override
    public void createTooltip(IndustryTooltipMode mode, TooltipMakerAPI tooltip, boolean expanded) {
        float opad = 10f;
        String[] data = getSpec().getData() == null ? new String[0] : getSpec().getData().split("\\|");

        tooltip.addTitle(getCurrentName());
        if (data.length > 0) tooltip.addPara(Global.getSettings().getDescription(data[0], Description.Type.CUSTOM).getText2(), opad);
        if (data.length < 2) return;

        if (Industries.POPULATION.equals(data[1])) {
            tooltip.addPara("Unlocks %s as a government type.", opad, Misc.getHighlightColor(), getCurrentName());
        } else {
            tooltip.addPara("Unlocks %s as a specialization of %s.", opad, Misc.getHighlightColor(),
                    getCurrentName(), Global.getSettings().getIndustrySpec(data[1]).getName());
        }
    }
}
