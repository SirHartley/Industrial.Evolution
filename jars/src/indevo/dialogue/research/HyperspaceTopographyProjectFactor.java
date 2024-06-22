package indevo.dialogue.research;

import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseFactorTooltip;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseOneTimeFactor;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.Ids;

public class HyperspaceTopographyProjectFactor extends BaseOneTimeFactor {

    public HyperspaceTopographyProjectFactor(int points) {
        super(points);
    }

    @Override
    public String getDesc(BaseEventIntel intel) {
        return "Completed: " + ResearchProjectTemplateRepo.RESEARCH_PROJECTS.get(Ids.PROJ_PROSPECTOR).getName();
    }

    @Override
    public TooltipMakerAPI.TooltipCreator getMainRowTooltip(BaseEventIntel intel) {
        return new BaseFactorTooltip() {
            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                tooltip.addPara("Insights gathered by providing the Galatian Academy with survey data during the course of a research project." +
                                "This data is checked and validated by professional scientists.",
                        0f);
            }
        };
    }
}