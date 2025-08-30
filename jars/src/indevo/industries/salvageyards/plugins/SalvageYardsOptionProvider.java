package indevo.industries.salvageyards.plugins;

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

public class SalvageYardsOptionProvider extends SingleIndustrySimpifiedOptionProvider {

    public static void register() {
        ListenerManagerAPI listeners = Global.getSector().getListenerManager();
        if (!listeners.hasListenerOfClass(SalvageYardsOptionProvider.class)) {
            listeners.addListener(new SalvageYardsOptionProvider(), true);
        }
    }

    @Override
    public String getTargetIndustryId() {
        return Ids.SCRAPYARD;
    }

    @Override
    public String getOptionLabel(Industry ind) {
        return "Visit the " + ind.getCurrentName()+ "...";
    }

    @Override
    public void onClick(IndustryOptionData opt, DialogCreatorUI ui) {
        SectorEntityToken target = opt.ind.getMarket().getPrimaryEntity();
        target.getMemoryWithoutUpdate().set("$IndEvo_closeDialogueOnNextReturn", true, 0);

        RuleBasedInteractionDialogPluginImpl plugin = new RuleBasedInteractionDialogPluginImpl("IndEvo_FireBaseSYOptionList");
        ui.showDialog(target, plugin);
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, IndustryOptionData opt) {
        tooltip.addSectionHeading("Visit the " + "Salvage Yards", Alignment.MID, 0f);
        tooltip.addPara("Manufacture ships from scrap or transfer D-Mods between hulls of similar make.", 10f);
    }
}
