package indevo.industries.courierport.plugin;

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

public class CourierPortOptionProvider extends SingleIndustrySimpifiedOptionProvider {

    public static void register() {
        ListenerManagerAPI listeners = Global.getSector().getListenerManager();
        if (!listeners.hasListenerOfClass(CourierPortOptionProvider.class)) {
            listeners.addListener(new CourierPortOptionProvider(), true);
        }
    }

    @Override
    public String getTargetIndustryId() {
        return Ids.PORT;
    }

    @Override
    public String getOptionLabel(Industry ind) {
        return "Visit the " + ind.getCurrentName() + "...";
    }

    @Override
    public void onClick(IndustryOptionData opt, DialogCreatorUI ui) {
        SectorEntityToken target = opt.ind.getMarket().getPrimaryEntity();
        target.getMemoryWithoutUpdate().set("$IndEvo_closeDialogueOnNextReturn", true, 0);

        RuleBasedInteractionDialogPluginImpl plugin = new RuleBasedInteractionDialogPluginImpl("IndEvo_FireBaseSPOptionList");
        ui.showDialog(target, plugin);
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, IndustryOptionData opt) {
        tooltip.addSectionHeading("Visit the " + "Courier Port", Alignment.MID, 0f);
        tooltip.addPara("Manage your shipping contracts, or create new ones.", 10f);
    }
}
