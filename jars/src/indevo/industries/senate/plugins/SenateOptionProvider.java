package indevo.industries.senate.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.DialogCreatorUI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.Ids;
import indevo.industries.senate.EdictHelper;
import indevo.industries.senate.dialogue.EdictPanelDelegate;
import indevo.utils.plugins.SingleIndustrySimpifiedOptionProvider;

import java.util.List;

public class SenateOptionProvider extends SingleIndustrySimpifiedOptionProvider {

    public static void register() {
        ListenerManagerAPI listeners = Global.getSector().getListenerManager();
        if (!listeners.hasListenerOfClass(SenateOptionProvider.class)) {
            listeners.addListener(new SenateOptionProvider(), true);
        }
    }

    @Override
    public String getTargetIndustryId() {
        return Ids.SENATE;
    }

    @Override
    public String getOptionLabel(Industry ind) {
        return "Visit the " + ind.getCurrentName() + "...";
    }

    // no docked check, vanilla also lists industry options on remote colony screens
    @Override
    public boolean isSuitable(Industry ind, boolean allowUnderConstruction) {
        return ind != null
                && ind.getMarket() != null
                && Ids.SENATE.equals(ind.getId())
                && ind.isFunctional()
                && ind.getMarket().isPlayerOwned();
    }

    @Override
    public void onClick(IndustryOptionData opt, DialogCreatorUI ui) {
        EdictPanelDelegate.show(ui, opt.ind.getMarket());
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, IndustryOptionData opt) {
        List<MarketAPI> colonies = EdictHelper.getColoniesCoveredBy(opt.ind.getMarket());
        int active = 0;
        for (MarketAPI colony : colonies) {
            if (EdictHelper.getActiveEdict(colony) != null) active++;
        }

        tooltip.addSectionHeading("Visit the Senate", Alignment.MID, 0f);
        tooltip.addPara("Enact an Edict or manage your current ones, on every colony in range of this Senate.", 10f);
        tooltip.addPara("Active: %s of %s colonies in range.", 10f, Misc.getHighlightColor(), active + "", colonies.size() + "");
    }
}
