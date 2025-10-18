package indevo.industries.relay.condition;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.StatBonus;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import indevo.industries.relay.industry.MilitaryRelay;
import indevo.industries.relay.plugins.NetworkEntry;

public class RelayConditionPlugin extends BaseMarketConditionPlugin {

    public static final String ID = "IndEvo_RelayCondition";

    private NetworkEntry entry = null;

    public static RelayConditionPlugin getRelayConditionPlugin(MarketAPI market) {
        if (!market.hasCondition(ID)) market.addCondition(ID);
        return (RelayConditionPlugin) market.getCondition(ID).getPlugin();
    }

    public void setEntry(NetworkEntry entry) {
        this.entry = entry;
    }

    public NetworkEntry getEntry() {
        return entry;
    }

    @Override
    public void apply(String id) {
        super.apply(id);

        Industry relay = market.getIndustries().stream()
                .filter(industry -> industry instanceof MilitaryRelay && industry.isFunctional())
                .findFirst()
                .orElse(null);

        if (entry != null && relay != null){
            MarketAPI m = Global.getSector().getEconomy().getMarket(entry.sourceMarketId);

            if (m != null){
                float bestFleetSize = entry.baseFleetSize * entry.targetMult;
                float localFleetSize = entry.baseFleetSize;

                float transferFraction = ((MilitaryRelay) relay).getFleetSizeTransferFraction();
                float transferrableFleetSizeFromBest = bestFleetSize * transferFraction;

                float targetFleetSizeForPlanet = Math.min(bestFleetSize, localFleetSize + transferrableFleetSizeFromBest);
                float multForTransfer = targetFleetSizeForPlanet / localFleetSize;

                if (multForTransfer <= 1) return;

                StatBonus fleetSize = market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT);
                fleetSize.modifyMult(ID, multForTransfer, "Relay Network (" + m.getName() + ")");
            }
        }
    }

    @Override
    public void unapply(String id) {
        super.unapply(id);
        market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).unmodify(ID);
    }

    @Override
    public boolean showIcon() {
        return false;
    }
}
