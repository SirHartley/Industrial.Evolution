package indevo.industries.senate.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.listeners.DialogCreatorUI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.impl.campaign.RuleBasedInteractionDialogPluginImpl;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import indevo.ids.Ids;
import indevo.utils.plugins.SingleIndustrySimpifiedOptionProvider;

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
        return "Visit the " + ind.getCurrentName();
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip) {
        tooltip.addSectionHeading("Visit the " + "Senate", Alignment.MID, 0f);
        tooltip.addPara("Enact an Edict or manage your current one.", 10f);
    }

    @Override
    public boolean isSuitable(Industry ind, boolean allowUnderConstruction) {
        return super.isSuitable(ind, allowUnderConstruction) && ind.getMarket().isPlayerOwned();
    }

    @Override
    public void onClick(IndustryOptionData opt, DialogCreatorUI ui) {
        SectorEntityToken target = opt.ind.getMarket().getPrimaryEntity();
        target.getMemoryWithoutUpdate().set("$IndEvo_closeDialogueOnNextReturn", true, 0);

        RuleBasedInteractionDialogPluginImpl plugin = new RuleBasedInteractionDialogPluginImpl("IndEvo_FireBaseSenateOptionList");
        ui.showDialog(target, plugin);
    }
}
