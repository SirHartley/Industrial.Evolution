package indevo.industries.academy.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.listeners.DialogCreatorUI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.impl.campaign.RuleBasedInteractionDialogPluginImpl;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import indevo.ids.Ids;
import indevo.utils.plugins.SimpleIndustryOptionProvider;

public class AcademyOptionProvider extends SimpleIndustryOptionProvider {

    public static void register() {
        ListenerManagerAPI listeners = Global.getSector().getListenerManager();
        if (!listeners.hasListenerOfClass(AcademyOptionProvider.class)) {
            listeners.addListener(new AcademyOptionProvider(), true);
        }
    }

    @Override
    public String getTargetIndustryId() {
        return Ids.ACADEMY;
    }

    @Override
    public String getOptionLabel(Industry ind) {
        return "Visit the " + ind.getCurrentName();
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip) {
        tooltip.addSectionHeading("Visit the " + "Academy", Alignment.MID, 0f);
        tooltip.addPara("Manage, Store and train officers or administrators.", 10f);
    }

    @Override
    public void onClick(IndustryOptionData opt, DialogCreatorUI ui) {
        SectorEntityToken target = opt.ind.getMarket().getPrimaryEntity();
        target.getMemoryWithoutUpdate().set("$IndEvo_closeDialogueOnNextReturn", true, 0);

        RuleBasedInteractionDialogPluginImpl plugin = new RuleBasedInteractionDialogPluginImpl("IndEvo_FireBaseAcademyOptionList");
        ui.showDialog(target, plugin);
    }
}
