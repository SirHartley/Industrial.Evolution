package indevo.industries.ruinfra.listener;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ColonyDecivListener;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import indevo.ids.Ids;
import indevo.industries.ruinfra.conditions.DerelictInfrastructureCondition;
import indevo.utils.helper.MiscIE;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class RuinfraOnDecivListener implements ColonyDecivListener {

    public static void register() {
        ListenerManagerAPI manager = Global.getSector().getListenerManager();
        if (!manager.hasListenerOfClass(RuinfraOnDecivListener.class))
            manager.addListener(new RuinfraOnDecivListener(), true);
    }

    @Override
    public void reportColonyAboutToBeDecivilized(MarketAPI market, boolean fullyDestroyed) {
        if (market.getSize() < 4 || !(market.getPrimaryEntity() instanceof PlanetAPI)) return;

        List<Industry> marketIndustries = market.getIndustries();
        Set<String> validIndustryIDs = MiscIE.getCSVSetFromMemory(Ids.RUIND_LIST);
        WeightedRandomPicker<Industry> picker = new WeightedRandomPicker<>();

        for (Industry ind : new ArrayList<>(marketIndustries)) if (validIndustryIDs.contains(ind.getId())) picker.add(ind);
        if (picker.isEmpty()) return;

        DerelictInfrastructureCondition.resetRuinfraState(market);
        DerelictInfrastructureCondition.setUpgradeSpec(market, picker.pick().getId());
    }

    @Override
    public void reportColonyDecivilized(MarketAPI market, boolean fullyDestroyed) {
        if (DerelictInfrastructureCondition.marketPrimedForCondition(market)) market.addCondition(Ids.RUINFRA);
    }
}
