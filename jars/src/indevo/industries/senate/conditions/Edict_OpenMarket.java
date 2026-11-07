package indevo.industries.senate.conditions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;


/*Edict:	Substance Legalization
		Upside	Increase pop growth slightly, increase stab, legalize drug export
		Downside	Reduce industry output by 1, increase required drugs by 2, unrest on change to freeport - "cancelled policy"
		Prerequisite	size >4, drug demand filled, not a free port
		Min. Runtime:	6M
*/
public class Edict_OpenMarket extends BaseEdict {
    private final float INCOME_RED_MULT = -10f;

    public static float STABILITY_PELANTY = 2f;
    protected transient SubmarketAPI saved = null;

    @Override
    public void apply(String id) {
        super.apply(id);
        if (market.getPrimaryEntity() == null) return;

        if (market.isPlayerOwned() && !market.hasIndustry(Industries.COMMERCE)) {
            market.getStability().modifyFlat(getModId(), -STABILITY_PELANTY, getName());

            SubmarketAPI open = market.getSubmarket(Submarkets.SUBMARKET_OPEN);
            if (open == null) {
                if (saved != null) {
                    market.addSubmarket(saved);
                } else {
                    market.addSubmarket(Submarkets.SUBMARKET_OPEN);
                    SubmarketAPI sub = market.getSubmarket(Submarkets.SUBMARKET_OPEN);
                    sub.setFaction(Global.getSector().getFaction(Factions.INDEPENDENT));
                    Global.getSector().getEconomy().forceStockpileUpdate(market);
                }
            }

        }
        //market.getIncomeMult().modifyPercent(getModId(), INCOME_RED_MULT, getName());
    }

    @Override
    public void unapply(String id) {
        super.unapply(id);
        if (market.getPrimaryEntity() == null) return;

        // commerce owns the open market then, and edict removal does not reapply industries
        if (market.isPlayerOwned() && !market.hasIndustry(Industries.COMMERCE)) {
            SubmarketAPI open = market.getSubmarket(Submarkets.SUBMARKET_OPEN);
            saved = open;

            market.removeSubmarket(Submarkets.SUBMARKET_OPEN);
        }

        market.getStability().unmodifyFlat(getModId());
        //market.getIncomeMult().unmodifyPercent(getModId());
    }

    @Override
    public void addEffectText(TooltipMakerAPI tooltip, MarketAPI market, float pad) {
        if (market.hasIndustry(Industries.COMMERCE)) {
            tooltip.addPara("%s on this colony already runs an open market, so this Edict has no effect here.", pad, Misc.getHighlightColor(), "Commerce");
            return;
        }

        tooltip.addPara("Adds an %s to trade on.", pad, Misc.getPositiveHighlightColor(), "open market");
        tooltip.addPara("Stability %s.", 3f, Misc.getNegativeHighlightColor(), "reduced by " + Math.round(STABILITY_PELANTY));
    }

    @Override
    public String getShortDesc() {
        return "Adds an open market, reduces stability.";
    }
}
