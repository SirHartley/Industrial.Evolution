package indevo.industries.relay.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.combat.StatBonus;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import indevo.industries.relay.condition.RelayConditionPlugin;

public class NetworkEntry {

    //assigned upon construction
    public float baseFleetSize;
    public String marketId;

    //assigned later
    public float targetMult;
    public String sourceMarketId;

    public NetworkEntry(MarketAPI market) {
        this.marketId = market.getId();
        this.baseFleetSize = calculateBaseFleetSize(market);
    }

    public void applyToMarket(){
        MarketAPI market = Global.getSector().getEconomy().getMarket(marketId);
        if (market == null) return;
        if (!market.hasCondition(RelayConditionPlugin.ID)) market.addCondition(RelayConditionPlugin.ID);

        RelayConditionPlugin plugin = RelayConditionPlugin.getRelayConditionPlugin(market);
        String id = plugin.getModId();

        plugin.unapply(id);
        plugin.setEntry(this);
        plugin.apply(id);
    }

    public void assignTargets(NetworkEntry largestEntry){
        this.targetMult = largestEntry.baseFleetSize / baseFleetSize;
        this.sourceMarketId = largestEntry.marketId;
    }

    //without relay bonuses
    private float calculateBaseFleetSize(MarketAPI market) {
        if (market == null) return 0;

        StatBonus fleetSizeMod = market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT);

        // fleet size is read as computeEffective(0f), so percent mods have no effect
        return getFlatModValue(fleetSizeMod) * getMultModValue(fleetSizeMod);
    }

    private float getFlatModValue(StatBonus fleetSizeMod) {
        float flatMod = 0f;

        if (!fleetSizeMod.getFlatBonuses().isEmpty())
            for (MutableStat.StatMod mod : fleetSizeMod.getFlatBonuses().values())
                if (!mod.getSource().equals(RelayConditionPlugin.ID)) flatMod += mod.value;

        return flatMod;
    }

    private float getMultModValue(StatBonus fleetSizeMod) {
        float mult = 1f;

        if (!fleetSizeMod.getMultBonuses().isEmpty())
            for (MutableStat.StatMod mod : fleetSizeMod.getMultBonuses().values())
                if (!mod.getSource().equals(RelayConditionPlugin.ID))
                    mult *= mod.value;

        return mult;
    }
}
