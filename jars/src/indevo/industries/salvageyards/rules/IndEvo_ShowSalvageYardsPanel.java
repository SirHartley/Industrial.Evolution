package indevo.industries.salvageyards.rules;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.Ids;
import indevo.industries.salvageyards.plugins.SalvageYardsDialogPlugin;
import indevo.industries.salvageyards.plugins.SalvageYardsView;

import java.util.List;
import java.util.Map;

// IndEvo_ShowSalvageYardsPanel <assembly|registry|dmods|smods>
public class IndEvo_ShowSalvageYardsPanel extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        MemoryAPI marketMemory = memoryMap.get(MemKeys.MARKET);
        MarketAPI market = marketMemory != null ? Global.getSector().getEconomy().getMarket(marketMemory.getString("$id")) : null;
        if (market == null || !market.hasIndustry(Ids.SCRAPYARD)) return false;

        String tab = params.isEmpty() ? "assembly" : params.get(0).getString(memoryMap);
        SalvageYardsView view = new SalvageYardsView(switch (tab) {
            case "registry" -> SalvageYardsView.Tab.REGISTRY;
            case "dmods" -> SalvageYardsView.Tab.DMODS;
            case "smods" -> SalvageYardsView.Tab.SMODS;
            default -> SalvageYardsView.Tab.ASSEMBLY;
        });

        SalvageYardsDialogPlugin plugin = new SalvageYardsDialogPlugin(market, view, dialog.getPlugin(), memoryMap);
        dialog.setPlugin(plugin);
        plugin.init(dialog);
        return true;
    }
}
