package indevo.industries.academy.rules;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.Ids;
import indevo.industries.academy.industry.Academy;
import indevo.industries.academy.plugins.AcademyDialogPlugin;

import java.util.List;
import java.util.Map;

public class IndEvo_ShowAcademyPanel extends BaseCommandPlugin {

    private static final String GALATIA_ACADEMY_STATION = "station_galatia_academy";

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        String returnOption = params.get(0).getString(memoryMap);
        Academy academy = Academy.get(getAcademyMarket(memoryMap));
        if (academy == null) return false;

        AcademyDialogPlugin plugin = new AcademyDialogPlugin(academy, dialog.getPlugin(), memoryMap, returnOption);
        dialog.setPlugin(plugin);
        plugin.init(dialog);
        return true;
    }

    // the vanilla galatia station has no market with the academy, ModPlugin adds a hidden one
    private static MarketAPI getAcademyMarket(Map<String, MemoryAPI> memoryMap) {
        if (GALATIA_ACADEMY_STATION.equals(memoryMap.get(MemKeys.LOCAL).getString("$id"))) {
            return Global.getSector().getEconomy().getMarket(Ids.ACADEMY_MARKET_ID);
        }

        MemoryAPI marketMemory = memoryMap.get(MemKeys.MARKET);
        return marketMemory != null ? Global.getSector().getEconomy().getMarket(marketMemory.getString("$id")) : null;
    }
}
