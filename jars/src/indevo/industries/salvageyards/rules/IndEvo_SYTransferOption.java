package indevo.industries.salvageyards.rules;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.List;
import java.util.Map;

public class IndEvo_SYTransferOption extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return false;

        String option = params.get(0).getString(memoryMap);
        String type = params.get(1).getString(memoryMap);
        MarketAPI market = Global.getSector().getEconomy().getMarket(memoryMap.get(MemKeys.MARKET).getString("$id"));

        String text;
        final String reason;

        switch (type) {
            case "dmod":
                text = "The salvage yards can transfer D-Mods between ships of the same size and manufacturer.";
                reason = IndEvo_InitSYMoveDModDiag.getUnavailableReason(market);
                break;
            case "smod":
                text = "The salvage yards can move S-mods between ships of the same size and manufacturer, for relic components and credits. The target ship needs a free S-mod slot and the hullmod installed.";
                reason = IndEvo_InitSYMoveSModDiag.getUnavailableReason(market);
                break;
            default:
                throw new IllegalArgumentException("IndEvo_SYTransferOption: unknown type " + type);
        }

        OptionPanelAPI opts = dialog.getOptionPanel();
        opts.setTooltip(option, text);
        opts.setEnabled(option, reason == null);

        if (reason != null) {
            opts.addOptionTooltipAppender(option, new OptionPanelAPI.OptionTooltipCreator() {
                @Override
                public void createTooltip(TooltipMakerAPI tooltip, boolean hadOtherText) {
                    tooltip.addPara(reason, Misc.getNegativeHighlightColor(), hadOtherText ? 10f : 0f);
                }
            });
        }

        return true;
    }
}
