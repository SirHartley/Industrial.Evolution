package indevo.industries.senate.conditions;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MutableCommodityQuantity;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;


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
    public void addEffectText(TooltipMakerAPI tooltip, MarketAPI market, float pad) {
        tooltip.addPara("All industries on this colony have their output %s.", pad, Misc.getPositiveHighlightColor(), "increased by 1");
        tooltip.addPara("Upkeep for those industries is %s.", 3f, Misc.getNegativeHighlightColor(), "doubled");
    }

    @Override
    public String getShortDesc() {
        return "Increases all industry output by 1, doubles upkeep.";
    }
}
