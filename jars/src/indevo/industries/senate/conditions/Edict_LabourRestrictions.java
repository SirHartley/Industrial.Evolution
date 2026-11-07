package indevo.industries.senate.conditions;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MutableCommodityQuantity;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.List;
import indevo.industries.senate.EdictRequirement;

/*Edict:	Substance Legalization
		Upside	Increase pop growth slightly, increase stab, legalize drug export
		Downside	Reduce industry output by 1, increase required drugs by 2, unrest on change to freeport - "cancelled policy"
		Prerequisite	size >4, drug demand filled, not a free port
		Min. Runtime:	6M
*/
public class Edict_LabourRestrictions extends BaseEdict {
    private final int stabBonus = 4;

    @Override
    public void apply(String id) {
        super.apply(id);
        if (market.getPrimaryEntity() == null) return;

        market.getStability().modifyFlat(getModId(), stabBonus, getName());

        for (Industry industry : market.getIndustries()) {
            if (industry.isIndustry()) {
                for (MutableCommodityQuantity supply : industry.getAllSupply()) {
                    industry.getSupply(supply.getCommodityId()).getQuantity().modifyFlat(getModId(), -1, getName());
                }
                for (MutableCommodityQuantity demand : industry.getAllDemand()) {
                    industry.getDemand(demand.getCommodityId()).getQuantity().modifyFlat(getModId(), -1, getName());
                }
            }
        }
    }

    @Override
    public void unapply(String id) {
        super.unapply(id);
        if (market.getPrimaryEntity() == null) return;

        market.getStability().unmodify(getModId());

        for (Industry industry : market.getIndustries()) {
            if (industry.isIndustry()) {
                for (MutableCommodityQuantity supply : industry.getAllSupply()) {
                    industry.getSupply(supply.getCommodityId()).getQuantity().unmodify(getModId());
                }
                for (MutableCommodityQuantity demand : industry.getAllDemand()) {
                    industry.getDemand(demand.getCommodityId()).getQuantity().unmodify(getModId());
                }
            }
        }
    }

    @Override
    public void addEffectText(TooltipMakerAPI tooltip, MarketAPI market, float pad) {
        tooltip.addPara("Stability %s.", pad, Misc.getPositiveHighlightColor(), "increased by " + stabBonus);
        tooltip.addPara("All industries on this colony have their output %s.", 3f, Misc.getNegativeHighlightColor(), "decreased by 1");
    }

    @Override
    public String getShortDesc() {
        return "Increases stability, decreases all industry output.";
    }

    @Override
    protected void addRequirements(List<EdictRequirement> requirements, MarketAPI market) {
        String text = market.isFreePort() ? market.getName() + " is a free port and will not abide by restrictions" : "Not a free port";
        requirements.add(new EdictRequirement(text, "Not a free port", !market.isFreePort()));
    }
}
