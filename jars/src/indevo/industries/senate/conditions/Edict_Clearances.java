package indevo.industries.senate.conditions;

import indevo.industries.senate.EdictEffect;
import indevo.industries.senate.EdictSupplyChange;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.List;
import indevo.industries.senate.EdictRequirement;

public class Edict_Clearances extends BaseEdict {

    @Override
    public void apply(String id) {
        super.apply(id);
        if (market.getPrimaryEntity() == null) return;

        boolean hasEffect = false;

        for (Industry i : market.getIndustries()) {
            int shipsup = i.getSupply(Commodities.SHIPS).getQuantity().getModifiedInt();
            if (shipsup > 1) {
                int supplyMalus = (int) Math.ceil(shipsup * 0.3);
                i.getSupply(Commodities.SHIPS).getQuantity().modifyFlat(getModId(), -supplyMalus, getName());
                hasEffect = true;
            }
        }

        if (hasEffect) {
            for (MarketAPI market : Misc.getFactionMarkets(Global.getSector().getPlayerFaction())) {
                if (!market.isPlayerOwned()) {
                    continue;
                }
                market.getStats().getDynamic().getMod(Stats.PRODUCTION_QUALITY_MOD).modifyFlat(getModId(), 0.2f, getName());
            }
        }
    }

    @Override
    public void unapply(String id) {
        super.unapply(id);
        if (market.getPrimaryEntity() == null) return;

        for (Industry i : market.getIndustries()) {
            int shipsup = i.getSupply(Commodities.SHIPS).getQuantity().getModifiedInt();
            if (shipsup > 0) {
                i.getSupply(Commodities.SHIPS).getQuantity().unmodify(getModId());
            }
        }

        for (MarketAPI market : Misc.getFactionMarkets(Global.getSector().getPlayerFaction())) {
            if (!market.isPlayerOwned()) {
                continue;
            }
            market.getStats().getDynamic().getMod(Stats.PRODUCTION_QUALITY_MOD).unmodify(getModId());
        }
    }

    @Override
    public List<EdictEffect> getEffects(MarketAPI market) {
        List<EdictEffect> effects = new ArrayList<>();
        effects.add(EdictEffect.good("Ship quality", "+20%"));
        effects.add(EdictEffect.bad("Ship hull output and production budget", "-30%"));
        if (!getOtherPlayerColonies(market).isEmpty()) effects.add(EdictEffect.good("All other colonies: ship quality", "+20%").onOtherColonies());
        return effects;
    }

    @Override
    public List<EdictSupplyChange> getSupplyChanges(MarketAPI market) {
        return getSupplyChanges(market, false, Commodities.SHIPS, s -> {
            int ships = s.getModifiedInt();
            if (ships > 1) s.modifyFlat(getModId(), -(int) Math.ceil(ships * 0.3));
        });
    }

    @Override
    public String getShortDesc() {
        return "Increases ship quality on all your colonies, decreases ship hull output and production budget.";
    }

    @Override
    protected void addRequirements(List<EdictRequirement> requirements, MarketAPI market) {
        requirements.add(shipProduction(market));
    }
}
