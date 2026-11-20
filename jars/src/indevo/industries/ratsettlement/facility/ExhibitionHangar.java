package indevo.industries.ratsettlement.facility;

import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.config.Setting;
import indevo.ids.Ids;
import indevo.industries.museum.data.MuseumConstants;
import indevo.industries.museum.industry.Museum;
import indevo.submarkets.IndustrySubmarkets;

// museum storage on the settlement market; income goes through rat's settlement income stat, so rat's income setting applies
public class ExhibitionHangar extends SettlementFacility {

    public static final String MOD_ID = "IndEvo_rat_exhibition";
    public static final float INCOME_MULT = 0.5f;

    @Override
    protected Setting parentSetting() {
        return Setting.MUSEUM_ENABLED;
    }

    @Override
    public void apply() {
        getSettlementMarket().addSubmarket(Ids.MUSEUM_SUBMARKET);
        updateIncome();
    }

    @Override
    public void unapply() {
        IndustrySubmarkets.remove(getSettlementMarket(), Ids.MUSEUM_SUBMARKET);
        getSettlement().getStats().getIncome().unmodify(MOD_ID);
    }

    @Override
    public void reportEconomyMonthEnd() {
        updateIncome();
    }

    private void updateIncome() {
        SubmarketAPI sub = getSettlementMarket().getSubmarket(Ids.MUSEUM_SUBMARKET);
        if (sub == null) throw new IllegalStateException("Exhibition Hangar without its storage on " + getSettlementMarket().getName());

        float value = Museum.getTotalShipValue(sub.getCargo(), Museum.getShipValueStatistics(), MuseumConstants.DEFAULT_INCOME_MULT) * INCOME_MULT;
        getSettlement().getStats().getIncome().modifyFlat(MOD_ID, value, getName());
    }

    @Override
    public void addDescriptionToTooltip(TooltipMakerAPI tooltip) {
        tooltip.addPara("A hangar for rare ships. Each distinct hull stored here adds settlement income at %s the Museum rate. No parades.",
                0f, Misc.getHighlightColor(), "half");
        tooltip.addPara("Store ships through Manage Fleet.", Misc.getGrayColor(), 10f);

        if (!isFunctional()) return;
        float income = getSettlement().getStats().getIncome().getFlatStatMod(MOD_ID) != null ? getSettlement().getStats().getIncome().getFlatStatMod(MOD_ID).getValue() : 0f;
        tooltip.addPara("Exhibition income: %s per month.", 10f, Misc.getHighlightColor(), Misc.getDGSCredits(income));
    }
}
