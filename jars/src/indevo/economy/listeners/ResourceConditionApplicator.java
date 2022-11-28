package indevo.economy.listeners;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.campaign.listeners.PlayerColonizationListener;
import indevo.economy.conditions.ResourceCondition;

public class ResourceConditionApplicator implements PlayerColonizationListener, EconomyTickListener {

    public static void applyResourceConditionToAllMarkets() {
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            ResourceCondition.applyRessourceCond(m);
        }
    }

    @Override
    public void reportPlayerColonizedPlanet(PlanetAPI planetAPI) {
        MarketAPI m = planetAPI.getMarket();
        ResourceCondition.applyRessourceCond(m);
    }

    @Override
    public void reportPlayerAbandonedColony(MarketAPI marketAPI) {

    }

    @Override
    public void reportEconomyTick(int i) {
        applyResourceConditionToAllMarkets();
    }

    @Override
    public void reportEconomyMonthEnd() {

    }
}
