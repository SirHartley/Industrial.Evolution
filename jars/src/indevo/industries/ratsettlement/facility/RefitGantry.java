package indevo.industries.ratsettlement.facility;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.config.Setting;
import indevo.industries.salvageyards.plugins.SalvageYardsDialogPlugin;
import indevo.industries.salvageyards.plugins.SalvageYardsView;

public class RefitGantry extends SettlementFacility {

    private static final String OPEN = "open";

    @Override
    protected Setting parentSetting() {
        return Setting.SCRAPYARD;
    }

    @Override
    protected boolean hasOptions() {
        return true;
    }

    @Override
    protected void addOptions(InteractionDialogAPI dialog) {
        addOption(dialog, "Transfer D-mods and S-mods", OPEN);
    }

    @Override
    protected void onOption(InteractionDialogAPI dialog, String action) {
        openPanel(dialog, onReturn -> new SalvageYardsDialogPlugin(getSettlementMarket(), new SalvageYardsView(SalvageYardsView.Tab.DMODS), onReturn, true));
    }

    @Override
    public void addDescriptionToTooltip(TooltipMakerAPI tooltip) {
        tooltip.addPara("Moves D-mods and S-mods between ships in your fleet and in settlement storage, at Salvage Yards costs. S-mods also cost %s.",
                0f, Misc.getHighlightColor(), "Relic Components");
        tooltip.addPara("Has no ship assembly and uses no salvage units.", Misc.getGrayColor(), 10f);
    }
}
