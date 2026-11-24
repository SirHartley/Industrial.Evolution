package indevo.industries.petshop.rules;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireAll;
import com.fs.starfarer.api.impl.campaign.rulecmd.ShowDefaultVisual;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.Ids;
import indevo.industries.petshop.industry.PetShop;
import indevo.industries.petshop.plugins.PetsDialogPlugin;
import indevo.industries.petshop.plugins.PetsView;

import java.util.List;
import java.util.Map;

// market menu "Visit the Animal Center": the Pets panel in this dialog, Return selects the option in params
public class IndEvo_ShowPetCenter extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return false;

        String returnOption = params.get(0).getString(memoryMap);
        MemoryAPI marketMemory = memoryMap.get(MemKeys.MARKET);
        MarketAPI market = marketMemory != null ? Global.getSector().getEconomy().getMarket(marketMemory.getString("$id")) : null;
        Industry industry = market != null ? market.getIndustry(Ids.PET_STORE) : null;
        if (!(industry instanceof PetShop shop) || !shop.isFunctional()) return false;

        InteractionDialogPlugin rules = dialog.getPlugin();
        PetsDialogPlugin plugin = new PetsDialogPlugin(shop, new PetsView(), d -> {
            d.setPlugin(rules);
            d.showTextPanel();
            d.showVisualPanel();
            new ShowDefaultVisual().execute(null, d, Misc.tokenize(""), memoryMap);
            memoryMap.get(MemKeys.LOCAL).set("$option", returnOption, 0f);
            FireAll.fire(null, d, memoryMap, "DialogOptionSelected");
        });
        dialog.setPlugin(plugin);
        plugin.init(dialog);
        return true;
    }
}
