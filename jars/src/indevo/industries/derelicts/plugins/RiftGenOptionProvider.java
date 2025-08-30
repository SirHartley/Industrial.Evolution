package indevo.industries.derelicts.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.listeners.DialogCreatorUI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.impl.campaign.RuleBasedInteractionDialogPluginImpl;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import indevo.ids.Ids;
import indevo.industries.derelicts.industry.RiftGenerator;
import indevo.utils.plugins.SingleIndustrySimpifiedOptionProvider;

public class RiftGenOptionProvider extends SingleIndustrySimpifiedOptionProvider {

    public static void register() {
        ListenerManagerAPI listeners = Global.getSector().getListenerManager();
        if (!listeners.hasListenerOfClass(RiftGenOptionProvider.class)) {
            listeners.addListener(new RiftGenOptionProvider(), true);
        }
    }

    @Override
    public boolean isSuitable(Industry ind, boolean allowUnderConstruction) {
        return super.isSuitable(ind, allowUnderConstruction) && ((RiftGenerator) ind).isReadyToMove();
    }

    @Override
    public String getTargetIndustryId() {
        return Ids.RIFTGEN;
    }

    @Override
    public String getOptionLabel(Industry ind) {
        return "Visit the " + ind.getCurrentName()+ "...";
    }

    @Override
    public void onClick(IndustryOptionData opt, DialogCreatorUI ui) {
        SectorEntityToken target = opt.ind.getMarket().getPrimaryEntity();
        target.getMemoryWithoutUpdate().set("$IndEvo_closeDialogueOnNextReturn", true, 0);

        RuleBasedInteractionDialogPluginImpl plugin = new RuleBasedInteractionDialogPluginImpl("IndEvo_FireBaseRGOptionList");
        ui.showDialog(target, plugin);
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, IndustryOptionData opt) {
        tooltip.addSectionHeading("Visit the " + "Rift Generator", Alignment.MID, 0f);
        tooltip.addPara("Control the Rift Generator", 10f);
    }
}
