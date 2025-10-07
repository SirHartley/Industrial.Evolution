package indevo.dialogue.research.listeners;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.listeners.CommodityIconProvider;
import com.fs.starfarer.api.campaign.listeners.CommodityTooltipModifier;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

public class SurveyDataTooltipAmender implements CommodityTooltipModifier {

    public static void register(){
        Global.getSector().getListenerManager().addListener(new SurveyDataTooltipAmender(), true);
    }

    @Override
    public void addSectionAfterPrice(TooltipMakerAPI info, float width, boolean expanded, CargoStackAPI stack) {
        if (stack.isCommodityStack() && stack.getCommodityId().startsWith("survey_data_")) {
            info.addPara("Can be sold on a market or traded in at the Galatia Academy", 10f, Misc.getGrayColor());
        }
    }
}
