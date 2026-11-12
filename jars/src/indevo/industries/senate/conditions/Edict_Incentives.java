package indevo.industries.senate.conditions;

import indevo.industries.senate.EdictEffect;
import indevo.industries.senate.EdictSupplyChange;

import java.util.ArrayList;
import java.util.List;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MutableCommodityQuantity;


/*Edict:	Industrial incentives
		Upside	output of industries +1
		Downside	triple industry upkeep or upkeep reduction skills no longer apply
		Prerequisite	at least 2 industries
		Min. Runtime:	6M
*/
public class Edict_Incentives extends BaseEdict {

    @Override
    public void apply(String id) {
        super.apply(id);
        if (market.getPrimaryEntity() == null) return;

        for (Industry industry : market.getIndustries()) {
            if (industry.isIndustry()) {
                for (MutableCommodityQuantity supply : industry.getAllSupply()) {
                    industry.getSupply(supply.getCommodityId()).getQuantity().modifyFlat(getModId(), 1, getName());
                }
                for (MutableCommodityQuantity demand : industry.getAllDemand()) {
                    industry.getDemand(demand.getCommodityId()).getQuantity().modifyFlat(getModId(), 1, getName());
                }
                industry.getUpkeep().modifyMult(getModId(), 2f, getName());
            }
        }
    }

    @Override
    public void unapply(String id) {
        super.unapply(id);
        if (market.getPrimaryEntity() == null) return;

        for (Industry industry : market.getIndustries()) {
            if (industry.isIndustry()) {
                for (MutableCommodityQuantity supply : industry.getAllSupply()) {
                    industry.getSupply(supply.getCommodityId()).getQuantity().unmodify(getModId());
                }
                for (MutableCommodityQuantity demand : industry.getAllDemand()) {
                    industry.getDemand(demand.getCommodityId()).getQuantity().unmodify(getModId());
                }
                industry.getUpkeep().unmodify(getModId());
            }
        }
    }

    @Override
    public List<EdictEffect> getEffects(MarketAPI market) {
        List<EdictEffect> effects = new ArrayList<>();
        effects.add(EdictEffect.good("Supply and demand of all industries", "+1"));
        effects.add(EdictEffect.bad("Upkeep of those industries", "x2"));
        return effects;
    }

    @Override
    public List<EdictSupplyChange> getSupplyChanges(MarketAPI market) {
        return getSupplyChanges(market, true, null, s -> s.modifyFlat(getModId(), 1));
    }

    @Override
    public String getShortDesc() {
        return "Increases all industry output by 1, doubles upkeep.";
    }
}
