package indevo.industries.senate.conditions;

/*Edict:	Customs control
		Upside	increase accessability of this colony by 5% for every other colony in the system
		    "Routing all cargo shipments through this polity has a very good effect on the local economy, but is detrimental to the accessability of the other planets"
		Downside	decrease accessability of all other colonies by 10%
		Prerequisite	megaport, no other planet has it active
		Min. Runtime:	6M
*/

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.utils.helper.MiscIE;

import java.util.List;
import java.awt.*;
import indevo.industries.senate.EdictRequirement;

public class Edict_Customs extends BaseEdict {

    private final float foreignAccessMod = -0.07F;

    public void apply(String id) {
        super.apply(id);
        if (market.getPrimaryEntity() == null) return;

        int foreignCount = 0;

        for (MarketAPI market : MiscIE.getMarketsInLocation(this.market.getStarSystem(), this.market.getFactionId())) {
            if (!market.getId().equals(this.market.getId())) {
                market.getAccessibilityMod().modifyFlat(id, foreignAccessMod, getName());
                foreignCount++;
            }
        }

        if (foreignCount > 0) {
            market.getAccessibilityMod().modifyFlat(id, 0.05F * foreignCount, getName());
        }
    }

    public void unapply(String id) {
        super.unapply(id);
        if (market.getPrimaryEntity() == null) return;

        for (MarketAPI market : MiscIE.getMarketsInLocation(this.market.getStarSystem(), this.market.getFactionId())) {
            if (!market.getId().equals(this.market.getId())) {
                market.getAccessibilityMod().unmodifyFlat(id);
            }
        }

        if (market.getAccessibilityMod().getFlatBonus(id) != null) {
            market.getAccessibilityMod().unmodifyFlat(id);
        }
    }

    @Override
    public void addEffectText(TooltipMakerAPI tooltip, MarketAPI market, float pad) {
        int others = getOtherColoniesInSystem(market).size();

        if (others == 0) {
            tooltip.addPara("Accessibility of this colony is unchanged, as there are %s in the star system.", pad, Misc.getNegativeHighlightColor(), "no other colonies");
            return;
        }

        tooltip.addPara("Accessibility of this colony %s, %s for every other colony in the star system.", pad,
                new Color[]{Misc.getPositiveHighlightColor(), Misc.getHighlightColor()}, "increased by " + others * 5 + "%", "5%");
        tooltip.addPara("Accessibility of all other colonies in the star system %s.", 3f, Misc.getNegativeHighlightColor(), "reduced by " + Math.round(-foreignAccessMod * 100) + "%");
    }

    @Override
    public String getShortDesc() {
        return "Increases accessibility of this planet for every other colony in the system. Reduces accessibility for all other colonies.";
    }

    @Override
    protected void addRequirements(List<EdictRequirement> requirements, MarketAPI market) {
        requirements.add(new EdictRequirement("Megaport on this colony", "Megaport", market.hasIndustry(Industries.MEGAPORT)));
        requirements.add(twoColonies(market));
        requirements.add(uniqueInSystem(market));
    }
}
